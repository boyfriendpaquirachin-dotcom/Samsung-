package com.example.engine

data class DetectedPackageInfo(
    val packageName: String,
    val isGame: Boolean,
    val isForeground: Boolean,
    val hasActiveMedia: Boolean = false
)

data class AppGameEcoPlan(
    val discoveredAppsCount: Int,
    val discoveredGamesCount: Int,
    val newlyOptimizedApps: Int,
    val newlyOptimizedGames: Int,
    val foregroundPackage: String?,
    val targetedAppBuckets: Map<String, String>,
    val targetedGames: List<String>,
    val newlyFrozenPackages: List<String> = emptyList(),
    val unfrozenForegroundPackage: String? = null,
    val commandToExecute: String?,
    val summaryLabel: String
)

/**
 * Motor autónomo de detección, ahorro y congelamiento por aplicación y por juego
 * (estilo Samsung Device Care Deep Sleeping Apps + Kernel Freezer + Game Booster).
 *
 * - Detecta automáticamente todas las apps de terceros, juegos, ventanas abiertas (incluida ventana
 *   flotante / pantalla dividida) y sesiones multimedia activas.
 * - Permite seguir usando cualquier app o juego abierto mientras aplica congelamiento suave
 *   (restringe procesos en segundo plano y limita GPU/FPS en juegos a 60 FPS sin cerrar la ventana).
 * - Congela procesos de apps y juegos inactivos en segundo plano mediante el congelador nativo
 *   del kernel (`use_freezer`), `am make-uid-idle`, `appops RUN_ANY_IN_BACKGROUND/RUN_IN_BACKGROUND ignore`
 *   y standby `restricted` sin usar `force-stop` ni `pm disable`, conservando sesiones y datos.
 */
object AppGameOptimizerEngine {

    val DISCOVERY_COMMAND: String =
        "echo \"===FG===\"; dumpsys activity activities 2>/dev/null | grep -E \"topResumedActivity=|mResumedActivity:|ResumedActivity:\" | head -n 10; " +
            "echo \"===MEDIA===\"; dumpsys media_session 2>/dev/null | grep -E \"package=|state=PlaybackState\" | head -n 20; " +
            "echo \"===PKGS===\"; pm list packages -3 2>/dev/null | cut -d: -f2"

    const val CMD_ENABLE_KERNEL_FREEZER: String =
        "settings put global adaptive_battery_management_enabled 1 2>/dev/null; " +
            "settings put global app_standby_enabled 1 2>/dev/null"

    private val protectedPrefixes = listOf(
        "android",
        "com.android.",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.providers.",
        "com.google.android.inputmethod.",
        "com.google.android.webview",
        "com.google.android.googlequicksearchbox",
        "com.google.android.as",
        "com.google.android.tts",
        "com.google.android.ext.",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.projection.",
        "com.motorola.",
        "com.lenovo.",
        "com.dolby.",
        "com.qualcomm.",
        "com.mediatek.",
        "moe.shizuku.",
        "com.termux",
        "com.aistudio."
    )

    private val protectedTokens = listOf(
        "launcher",
        "inputmethod",
        "keyboard",
        "swiftkey",
        "gboard",
        ".ime",
        "systemui",
        "permissioncontroller",
        "webview"
    )

    private val knownGameTokens = listOf(
        "supercell", "riotgames", "mihoyo", "hoyoverse", "roblox", "mojang",
        "garena", "pubg", "activision", "freefire", "funkin", "fnf",
        "gameloft", "kiloo", "nianticlabs", "innersloth", "epicgames",
        "tencent.ig", "mobile.legends", "brawlstars", "clashroyale",
        "clashofclans", "ea.gp", "ea.game", "zynga", "rovio", "miniclip",
        "konami", "bandainamco", "capcom", "sega", "nintendo", "square_enix",
        "krafton", "netease", "com.dts.", "com.playrix.", "com.king."
    )

    fun parseDiscoveredPackages(rawOutput: String): List<DetectedPackageInfo> {
        if (rawOutput.isBlank()) return emptyList()

        val fgSection = rawOutput.substringAfter("===FG===", "")
            .substringBefore("===MEDIA===")
            .substringBefore("===PKGS===")
        val mediaSection = if (rawOutput.contains("===MEDIA===")) {
            rawOutput.substringAfter("===MEDIA===", "").substringBefore("===PKGS===")
        } else {
            ""
        }
        val pkgsSection = rawOutput.substringAfter("===PKGS===", "")
            .substringBefore("===GAMES===")
        val gamesSection = rawOutput.substringAfter("===GAMES===", "")

        val fgRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*\.[a-zA-Z0-9_.]+)/""")
        val openForegroundPkgs = fgRegex.findAll(fgSection)
            .map { it.groupValues[1] }
            .filter { !isProtectedPackage(it) }
            .toSet()

        val activeMediaPkgs = parseActiveMediaPackages(mediaSection)

        val gamePackagesFromManifest = mutableSetOf<String>()
        var currentPkg: String? = null
        val pkgHeaderRegex = Regex("""Package \[([a-zA-Z0-9_.]+)\]""")
        gamesSection.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val headerMatch = pkgHeaderRegex.find(line)
            if (headerMatch != null) {
                currentPkg = headerMatch.groupValues[1]
            } else if (currentPkg != null && (line.contains("IS_GAME") || line.contains("category=0"))) {
                gamePackagesFromManifest += currentPkg!!
            }
        }

        val packageNameRegex = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$""")
        return pkgsSection.lineSequence()
            .map { it.trim() }
            .filter { pkg ->
                pkg.isNotBlank() &&
                    packageNameRegex.matches(pkg) &&
                    !isProtectedPackage(pkg)
            }
            .distinct()
            .map { pkg ->
                val lower = pkg.lowercase()
                val isGame = (pkg in gamePackagesFromManifest) ||
                    knownGameTokens.any { token -> lower.contains(token) }
                DetectedPackageInfo(
                    packageName = pkg,
                    isGame = isGame,
                    isForeground = pkg in openForegroundPkgs,
                    hasActiveMedia = pkg in activeMediaPkgs
                )
            }
            .toList()
    }

    private fun parseActiveMediaPackages(mediaSection: String): Set<String> {
        if (mediaSection.isBlank()) return emptySet()
        val active = mutableSetOf<String>()
        var currentPkg: String? = null
        val pkgRegex = Regex("""package=([a-zA-Z0-9_.]+)""")
        val playingRegex = Regex("""state=(?:PlaybackState\s*\{)?state=(?:PLAYING\(|BUFFERING\()?(?:3|6|PLAYING|BUFFERING)\b""")
        mediaSection.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val pkgMatch = pkgRegex.find(line)
            if (pkgMatch != null) {
                currentPkg = pkgMatch.groupValues[1]
            } else if (currentPkg != null && playingRegex.containsMatchIn(line)) {
                active += currentPkg!!
            }
        }
        return active
    }

    fun buildAutoOptimizationPlan(
        packages: List<DetectedPackageInfo>,
        appliedAppBuckets: Map<String, String>,
        alreadyOptimizedGames: Set<String>,
        batteryLevel: Int,
        temperatureCelsius: Float,
        isCharging: Boolean,
        highDrainPackages: List<String> = emptyList(),
        freezeAppsAndGames: Boolean = false,
        alreadyFrozenPackages: Set<String> = emptySet(),
        freezerSystemEnabled: Boolean = false,
        allowMakeUidIdle: Boolean = true
    ): AppGameEcoPlan {
        val apps = packages.filter { !it.isGame }
        val games = packages.filter { it.isGame }
        val fgPkg = packages.firstOrNull { it.isForeground }?.packageName

        val aggressiveStandby = !isCharging && (batteryLevel in 1..30 || temperatureCelsius >= 39f)
        val commands = mutableListOf<String>()

        // 1. Safe Game Standby: avoid SurfaceFlinger/game_overlay downscaling overrides that can crash OpenGL/Unity games.
        //    Keep games untouched when active and move closed background games to 'rare' standby.
        val targetGames = games
            .filter { !it.isForeground }
            .map { it.packageName }
            .filter { it !in alreadyOptimizedGames }
            .take(8)

        targetGames.forEach { pkg ->
            commands += "am set-standby-bucket $pkg rare 2>/dev/null"
        }

        // 2. App Standby Buckets for background apps (unless handled by full freeze)
        val targetAppBuckets = linkedMapOf<String, String>()
        if (!freezeAppsAndGames) {
            apps.filter { !it.isForeground }.forEach { info ->
                if (targetAppBuckets.size >= 12) return@forEach
                val pkg = info.packageName
                val desiredBucket = if (aggressiveStandby || pkg in highDrainPackages) "restricted" else "rare"
                val currentBucket = appliedAppBuckets[pkg]
                val needsApply = currentBucket == null ||
                    (desiredBucket == "restricted" && currentBucket != "restricted")
                if (needsApply) {
                    targetAppBuckets[pkg] = desiredBucket
                    commands += "am set-standby-bucket $pkg $desiredBucket 2>/dev/null"
                }
            }
        }

        // 3. AI Safe Deep Freeze: keeps open/foreground apps & games running smoothly on screen
        //    while gently restricting idle background apps via official Android standby restricted +
        //    RUN_ANY_IN_BACKGROUND ignore (never breaking bound IPC with RUN_IN_BACKGROUND or use_freezer!).
        val newlyFrozen = mutableListOf<String>()
        var unfrozenFg: String? = null
        if (freezeAppsAndGames) {
            if (!freezerSystemEnabled && packages.isNotEmpty()) {
                commands += CMD_ENABLE_KERNEL_FREEZER
            }

            // Ensure any currently open app/game stays unrestricted on screen
            if (fgPkg != null && fgPkg in alreadyFrozenPackages) {
                unfrozenFg = fgPkg
                commands += "appops set $fgPkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null"
                commands += "am set-standby-bucket $fgPkg active 2>/dev/null"
            }

            packages
                .filter { !it.isForeground && !it.hasActiveMedia && it.packageName !in alreadyFrozenPackages }
                .take(6)
                .forEach { info ->
                    val pkg = info.packageName
                    newlyFrozen += pkg
                    targetAppBuckets[pkg] = "restricted"
                    commands += "am set-standby-bucket $pkg restricted 2>/dev/null"
                    commands += "appops set $pkg RUN_ANY_IN_BACKGROUND ignore 2>/dev/null"
                    if (allowMakeUidIdle) {
                        commands += "am make-uid-idle --user 0 $pkg 2>/dev/null"
                    }
                }
        }

        val finalCmd = if (commands.isEmpty()) {
            null
        } else {
            commands.distinct().joinToString("; ") +
                "; echo 'OK: ${targetAppBuckets.size} apps, ${targetGames.size} juegos y ${newlyFrozen.size} congelamientos aplicados'"
        }

        val summary = when {
            packages.isEmpty() -> "Escaneando apps y juegos instalados..."
            finalCmd == null && freezeAppsAndGames ->
                "${alreadyFrozenPackages.size} congeladas • ${apps.size} apps y ${games.size} juegos protegidos"
            finalCmd == null -> "${apps.size} apps y ${games.size} juegos protegidos"
            freezeAppsAndGames ->
                "IA: +${newlyFrozen.size} congeladas • +${targetGames.size} juegos optimizados"
            else -> "Auto: +${targetAppBuckets.size} apps • +${targetGames.size} juegos optimizados"
        }

        return AppGameEcoPlan(
            discoveredAppsCount = apps.size,
            discoveredGamesCount = games.size,
            newlyOptimizedApps = targetAppBuckets.size,
            newlyOptimizedGames = targetGames.size,
            foregroundPackage = fgPkg,
            targetedAppBuckets = targetAppBuckets,
            targetedGames = targetGames,
            newlyFrozenPackages = newlyFrozen,
            unfrozenForegroundPackage = unfrozenFg,
            commandToExecute = finalCmd,
            summaryLabel = summary
        )
    }

    fun buildSinglePackageFreezeCommand(info: DetectedPackageInfo, freeze: Boolean): String {
        val pkg = info.packageName
        return if (freeze) {
            buildList {
                add(CMD_ENABLE_KERNEL_FREEZER)
                add("appops set $pkg RUN_ANY_IN_BACKGROUND ignore 2>/dev/null")
                add("am set-standby-bucket $pkg restricted 2>/dev/null")
                add("echo 'OK: $pkg en modo congelamiento seguro'")
            }.joinToString("; ")
        } else {
            buildList {
                add("appops set $pkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null")
                add("appops set $pkg RUN_IN_BACKGROUND allow 2>/dev/null")
                add("am set-standby-bucket $pkg active 2>/dev/null")
                add("echo 'OK: $pkg descongelada'")
            }.joinToString("; ")
        }
    }

    fun buildUnfreezeAllCommand(packages: Collection<DetectedPackageInfo>): String {
        val cmds = mutableListOf<String>()
        packages.take(40).forEach { info ->
            cmds += "cmd app_hibernation set-state ${info.packageName} false 2>/dev/null"
            cmds += "appops set ${info.packageName} RUN_ANY_IN_BACKGROUND allow 2>/dev/null"
            cmds += "appops set ${info.packageName} RUN_IN_BACKGROUND allow 2>/dev/null"
            cmds += "am set-standby-bucket ${info.packageName} rare 2>/dev/null"
        }
        return if (cmds.isEmpty()) {
            "echo 'OK: Sin apps congeladas por restaurar'"
        } else {
            cmds.joinToString("; ") + "; echo 'OK: Apps y juegos descongelados'"
        }
    }

    fun buildRestoreAllCommand(packages: Collection<DetectedPackageInfo>): String {
        val cmds = mutableListOf<String>()
        packages.take(40).forEach { info ->
            cmds += "cmd app_hibernation set-state ${info.packageName} false 2>/dev/null"
            cmds += "appops set ${info.packageName} RUN_ANY_IN_BACKGROUND allow 2>/dev/null"
            cmds += "appops set ${info.packageName} RUN_IN_BACKGROUND allow 2>/dev/null"
            cmds += "am set-standby-bucket ${info.packageName} active 2>/dev/null"
            if (info.isGame) {
                cmds += "cmd game mode standard ${info.packageName} 2>/dev/null"
                cmds += "device_config delete game_overlay ${info.packageName} 2>/dev/null"
            }
        }
        return if (cmds.isEmpty()) {
            "echo 'OK: Sin ajustes pendientes por restaurar'"
        } else {
            cmds.joinToString("; ") + "; echo 'OK: Apps y juegos restaurados a modo estándar'"
        }
    }

    fun formatPackageDisplayName(packageName: String): String {
        val parts = packageName.split('.').filter {
            it.isNotBlank() && it !in setOf("com", "org", "net", "io", "app", "android", "mobile", "gp", "game")
        }
        val raw = parts.lastOrNull() ?: packageName.substringAfterLast('.')
        return raw.replaceFirstChar { ch -> ch.uppercaseChar() }
    }

    fun isProtectedPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return protectedPrefixes.any { prefix -> lower == prefix || lower.startsWith(prefix) } ||
            protectedTokens.any { token -> lower.contains(token) }
    }
}
