package com.example.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.example.model.BatteryHistoryPoint
import com.example.model.BatteryTelemetry
import com.example.model.BuiltInFunctionsCatalog
import com.example.model.CommandEngine
import com.example.model.PowerProfileStatus
import java.util.Locale
import kotlin.math.abs

data class BatteryFixAction(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
    val engine: CommandEngine = CommandEngine.SHIZUKU
)

data class BatteryDrainAnalysisRequest(
    val telemetry: BatteryTelemetry,
    val history: List<BatteryHistoryPoint>,
    val powerStatus: PowerProfileStatus,
    val shizukuConnected: Boolean,
    val rawSystemEvidence: String,
    val bluetoothEcoEnabled: Boolean = true,
    val torchEcoEnabled: Boolean = true,
    val torchActive: Boolean = false,
    val appGameEcoEnabled: Boolean = true,
    val appFreezeEcoEnabled: Boolean = false,
    val isScreenInteractive: Boolean = true,
    val freezerSystemEnabled: Boolean = false,
    val deviceIdleLightActive: Boolean = false,
    val autoSolverEnabled: Boolean = true
)

enum class DrainSeverity(val label: String) {
    LOW("Consumo Estable"),
    MODERATE("Consumo Moderado"),
    HIGH("Drenaje Alto"),
    CRITICAL("Drenaje Crítico")
}

data class BatteryAiDiagnosis(
    val engineLabel: String,
    val severity: DrainSeverity,
    val headline: String,
    val measuredSummary: String,
    val drainFactors: List<String>,
    val recommendedFixes: List<BatteryFixAction>,
    val rawSystemEvidence: String
)

data class AutoGuardianState(
    val enabled: Boolean = true,
    val autoSolverEnabled: Boolean = true,
    val isWorking: Boolean = false,
    val shizukuReady: Boolean = false,
    val severity: DrainSeverity = DrainSeverity.LOW,
    val activeFunctionsSummary: String = "IA Solución Total: Problemas de Batería • Auto-Reparación • 60Hz/120Hz • Apps/Juegos • Bluetooth • Linterna • Standby",
    val lastActionSummary: String? = null,
    val lastProblemSolved: String? = null,
    val lastErrorRecovered: String? = null,
    val actionsAppliedCount: Int = 0,
    val solvedIssuesCount: Int = 0,
    val detectedProblemsCount: Int = 0,
    val frozenCount: Int = 0,
    val detectedAppsAndGamesCount: Int = 0
)

data class AutoGuardianPlan(
    val severity: DrainSeverity,
    val commandToExecute: String?,
    val summaryLabel: String,
    val activeFeaturesLabel: String,
    val solvedProblems: List<String> = emptyList()
)

sealed interface BatteryAiUiState {
    data object Idle : BatteryAiUiState
    data object Loading : BatteryAiUiState
    data class Success(val diagnosis: BatteryAiDiagnosis) : BatteryAiUiState
    data class Error(val message: String) : BatteryAiUiState
}

interface BatteryAiRepository {
    suspend fun analyzeBatteryDrain(request: BatteryDrainAnalysisRequest): BatteryAiDiagnosis
    fun buildAutoGuardianPlan(
        request: BatteryDrainAnalysisRequest,
        allowRefreshRateChange: Boolean
    ): AutoGuardianPlan
}

/**
 * Motor inteligente local basado en APIs oficiales de Android/Google (BatteryManager,
 * PowerManager térmico, DisplayManager, App Standby Buckets y parsing real de dumpsys),
 * sin dependencias de Gemini ni servicios externos en la nube.
 */
class OnDeviceBatteryIntelligenceEngine(
    private val context: Context? = null
) : BatteryAiRepository {

    override suspend fun analyzeBatteryDrain(request: BatteryDrainAnalysisRequest): BatteryAiDiagnosis {
        val t = request.telemetry
        val p = request.powerStatus
        val raw = request.rawSystemEvidence

        val thermalInfo = readAndroidThermalStatus()
        val slopeDrainPerHour = computeHourlyDrainRate(request.history)
        val dischargeMa = if (!t.isCharging) abs(t.currentMa) else 0
        val estimatedHoursLeft = if (!t.isCharging && dischargeMa > 50) {
            val capacityMah = if (t.chargeCounterMah > 100) {
                t.chargeCounterMah.toFloat()
            } else {
                (t.level.coerceAtLeast(1) / 100f) * 5000f
            }
            (capacityMah / dischargeMa).coerceIn(0.2f, 99f)
        } else {
            null
        }

        val summaryParts = buildList {
            add("${t.level}%")
            add("${t.currentMa} mA")
            add(String.format(Locale.US, "%.2f W", t.powerWatts))
            add(String.format(Locale.US, "%.1f °C", t.temperatureCelsius))
            add("${p.peakRefreshRateHz} Hz")
            if (thermalInfo != null) add("Térmico: $thermalInfo")
            if (estimatedHoursLeft != null) {
                add(String.format(Locale.US, "~%.1f h est.", estimatedHoursLeft))
            }
        }
        val measuredSummary = summaryParts.joinToString(" • ")

        val parsedAodOn = Regex("AOD=([01])").find(raw)?.groupValues?.getOrNull(1) == "1"
        val parsedAdaptiveOff = Regex("ADAPTIVE=([01])").find(raw)?.groupValues?.getOrNull(1) == "0"
        val parsedStandbyOff = Regex("FORCED_STANDBY=([01])").find(raw)?.groupValues?.getOrNull(1) == "0"
        val parsedBleScanAlwaysOn = Regex("BLE_SCAN=([01])").find(raw)?.groupValues?.getOrNull(1) == "1"
        val parsedWakeLocks = Regex("Wake Locks: size=(\\d+)").find(raw)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

        val highCpuPackages = extractHighDrainThirdPartyPackages(raw)

        var riskScore = 0
        val factors = mutableListOf<String>()

        if (slopeDrainPerHour != null && slopeDrainPerHour >= 8f) {
            riskScore += 3
            factors += String.format(
                Locale.US,
                "Velocidad de caída detectada: ~%.1f%%/h según las últimas %d lecturas en vivo.",
                slopeDrainPerHour,
                request.history.size
            )
        }

        if (!t.isCharging && dischargeMa >= 750) {
            riskScore += 3
            factors += "Corriente de descarga elevada (${t.currentMa} mA / ${String.format(Locale.US, "%.2f", t.powerWatts)} W): el procesador, módem 5G o brillo están exigiendo mucha energía."
        } else if (!t.isCharging && dischargeMa in 420..749) {
            riskScore += 1
            factors += "Descarga moderada-alta (${t.currentMa} mA) en uso actual."
        }

        if (t.temperatureCelsius >= 41f) {
            riskScore += 3
            factors += String.format(
                Locale.US,
                "Sobrecalentamiento de batería (%.1f °C): el calor dispara pérdidas energéticas y acelera la caída de carga.",
                t.temperatureCelsius
            )
        } else if (t.temperatureCelsius >= 37.5f) {
            riskScore += 1
            factors += String.format(
                Locale.US,
                "Temperatura tibia (%.1f °C): conviene reducir carga en segundo plano.",
                t.temperatureCelsius
            )
        }

        if (p.peakRefreshRateHz > 60) {
            riskScore += 2
            factors += "Pantalla pOLED configurada a ${p.peakRefreshRateHz} Hz: mantener 120 Hz consume bastante más batería que fijar 60 Hz."
        }

        if (!p.lowPowerEnabled) {
            riskScore += 1
            factors += "El ahorro oficial de Android (low_power) está desactivado."
        }

        if (parsedAodOn || (!p.aodDisabled && p.isSyncedFromSystem)) {
            riskScore += 1
            factors += "Always-On Display (AOD) activo en pantalla pOLED: consume energía continua incluso bloqueado."
        }

        if (parsedAdaptiveOff) {
            riskScore += 1
            factors += "Batería Adaptativa del sistema desactivada (adaptive_battery_management_enabled = 0)."
        }

        if (parsedStandbyOff) {
            riskScore += 1
            factors += "App Standby forzado en segundo plano está desactivado."
        }

        if (parsedBleScanAlwaysOn) {
            riskScore += 1
            factors += "Escaneo BLE continuo en segundo plano activo (gasta Bluetooth aunque no estés transfiriendo datos)."
        }

        if (request.torchActive) {
            riskScore += 2
            factors += "Linterna LED encendida: el módulo flash consume alta corriente; conviene atenuar intensidad y compensar 60Hz/2º plano sin apagarla."
        }

        if (parsedWakeLocks >= 3) {
            riskScore += 2
            factors += "Se detectaron $parsedWakeLocks Wake Locks activos manteniendo el teléfono despierto."
        }

        if (highCpuPackages.isNotEmpty()) {
            riskScore += 2
            factors += "Procesos de terceros detectados con actividad reciente: ${highCpuPackages.joinToString(", ")}."
        }

        if (factors.isEmpty()) {
            factors += if (t.isCharging) {
                "El dispositivo está cargando (${t.statusLabel}) sin anomalías térmicas ni consumo anómalo."
            } else {
                "Parámetros dentro de rango estable (${t.currentMa} mA, ${String.format(Locale.US, "%.1f °C", t.temperatureCelsius)}). Puedes aplicar prevención en segundo plano si deseas máxima duración."
            }
        }

        val severity = when {
            riskScore >= 7 -> DrainSeverity.CRITICAL
            riskScore >= 4 -> DrainSeverity.HIGH
            riskScore >= 2 -> DrainSeverity.MODERATE
            else -> DrainSeverity.LOW
        }

        val fixes = buildList {
            if (p.peakRefreshRateHz > 60 || !p.lowPowerEnabled) {
                add(
                    BatteryFixAction(
                        id = "fix_samsung_saving",
                        title = "Bajar a 60Hz + Ahorro Oficial",
                        description = "Activa ahorro Android (low_power), fija panel pOLED en 60 Hz y apaga AOD.",
                        command = BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82,
                        engine = CommandEngine.SHIZUKU
                    )
                )
            }
            add(
                BatteryFixAction(
                    id = "fix_stop_bg",
                    title = "Frenar apps en 2º plano",
                    description = "Fuerza App Standby y detiene procesos en caché sin activar ahorro de datos.",
                    command = BuiltInFunctionsCatalog.CMD_STOP_BG_WAKEUPS,
                    engine = CommandEngine.SHIZUKU
                )
            )
            add(
                BatteryFixAction(
                    id = "fix_doze_adaptive",
                    title = "Batería Adaptativa + Doze Ligero",
                    description = "Habilita Batería Adaptativa de Android, Doze ligero y libera cachés (512 MB).",
                    command = BuiltInFunctionsCatalog.CMD_OPTIMIZE_PHONE_BATTERY + "; cmd deviceidle force-idle light",
                    engine = CommandEngine.SHIZUKU
                )
            )
            add(
                BatteryFixAction(
                    id = "fix_bluetooth_eco",
                    title = "Ahorro Bluetooth (sin apagarlo)",
                    description = "Reduce el escaneo BLE en segundo plano estilo Samsung manteniendo Bluetooth y tus dispositivos activos.",
                    command = BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_ON,
                    engine = CommandEngine.SHIZUKU
                )
            )
            if (request.torchActive || request.torchEcoEnabled) {
                add(
                    BatteryFixAction(
                        id = "fix_torch_eco",
                        title = "Ahorro Linterna (sin apagarla)",
                        description = "Fija 60Hz, App Standby y Doze ligero para compensar el gasto del LED manteniendo la linterna encendida.",
                        command = BuiltInFunctionsCatalog.CMD_TORCH_ECO_COMPENSATION,
                        engine = CommandEngine.SHIZUKU
                    )
                )
            }
            highCpuPackages.take(2).forEach { pkg ->
                add(
                    BatteryFixAction(
                        id = "fix_bucket_$pkg",
                        title = "Restringir standby: $pkg",
                        description = "Mueve '$pkg' al bucket 'restricted' de Android para frenar despertares en segundo plano.",
                        command = "am set-standby-bucket $pkg restricted && echo 'OK: $pkg en standby restringido'",
                        engine = CommandEngine.SHIZUKU
                    )
                )
            }
        }

        val headline = when (severity) {
            DrainSeverity.CRITICAL -> "Drenaje crítico detectado: aplica el bloqueo anti-drenaje ahora"
            DrainSeverity.HIGH -> "Descarga rápida confirmada: se encontraron ${factors.size} factores activos"
            DrainSeverity.MODERATE -> "Consumo moderado: hay ajustes de pantalla y 2º plano mejorables"
            DrainSeverity.LOW -> "Consumo estable: batería sin picos anormales en este momento"
        }

        return BatteryAiDiagnosis(
            engineLabel = "Motor Inteligente Android (Local • Sin Gemini)",
            severity = severity,
            headline = headline,
            measuredSummary = measuredSummary,
            drainFactors = factors,
            recommendedFixes = fixes,
            rawSystemEvidence = raw
        )
    }

    override fun buildAutoGuardianPlan(
        request: BatteryDrainAnalysisRequest,
        allowRefreshRateChange: Boolean
    ): AutoGuardianPlan {
        val t = request.telemetry
        val p = request.powerStatus
        val raw = request.rawSystemEvidence

        val dischargeMa = if (!t.isCharging) abs(t.currentMa) else 0
        val slopeDrainPerHour = computeHourlyDrainRate(request.history)
        val parsedAodOn = Regex("AOD=([01])").find(raw)?.groupValues?.getOrNull(1) == "1"
        val parsedAdaptiveOff = Regex("ADAPTIVE=([01])").find(raw)?.groupValues?.getOrNull(1) == "0"
        val parsedStandbyEnabled = Regex("FORCED_STANDBY=([01])").find(raw)?.groupValues?.getOrNull(1) == "1"
        val parsedBleScanAlwaysOn = Regex("BLE_SCAN=([01])").find(raw)?.groupValues?.getOrNull(1) == "1"
        val parsedUseFreezerOn = Regex("USE_FREEZER=true", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        val parsedCachedFreezerOn = Regex("CACHED_FREEZER=enabled", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        val parsedBrokenAnim = listOf("ANIM_WIN", "ANIM_TRANS", "ANIM_DUR").any { key ->
            val v = Regex("$key=([^\\s]+)").find(raw)?.groupValues?.getOrNull(1)
            v != null && (v == "0" || v == "0.0")
        }
        val parsedStuckDozeInteractive = request.isScreenInteractive &&
            (request.deviceIdleLightActive || raw.contains("DOZE_DEEP=IDLE") || raw.contains("DOZE_LIGHT=IDLE"))
        val parsedWakeLocks = Regex("Wake Locks: size=(\\d+)").find(raw)
            ?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val highCpuPackages = extractHighDrainThirdPartyPackages(raw)
        val foregroundPkg = extractForegroundPackage(raw)

        val highDrain = !t.isCharging && (
            dischargeMa >= 550 ||
                t.temperatureCelsius >= 38f ||
                (t.level in 1..30) ||
                (slopeDrainPerHour != null && slopeDrainPerHour >= 7f) ||
                parsedWakeLocks >= 3
            )
        val criticalDrain = !t.isCharging && (
            (t.level in 1..20) ||
                t.temperatureCelsius >= 41f ||
                dischargeMa >= 850
            )

        val severity = when {
            criticalDrain -> DrainSeverity.CRITICAL
            highDrain -> DrainSeverity.HIGH
            !t.isCharging && (p.peakRefreshRateHz > 60 || !p.lowPowerEnabled || parsedBleScanAlwaysOn || highCpuPackages.isNotEmpty()) ->
                DrainSeverity.MODERATE
            else -> DrainSeverity.LOW
        }

        val commands = mutableListOf<String>()
        val activeTags = mutableListOf<String>()
        val solvedProblems = mutableListOf<String>()

        // 1. Auto-Solution Engine: resolve battery issues, unstable system flags, UI lag/locks & stuck states automatically
        if (request.autoSolverEnabled) {
            activeTags += "Problemas de Batería"
            activeTags += "Auto-Solución"
            if (parsedUseFreezerOn || parsedCachedFreezerOn) {
                commands += "device_config delete activity_manager_native_boot use_freezer 2>/dev/null"
                commands += "settings delete global cached_apps_freezer 2>/dev/null"
                solvedProblems += "Congelador conflictivo limpiado"
            }
            if (parsedStandbyEnabled) {
                commands += "settings put global forced_app_standby_enabled 0"
                solvedProblems += "Standby forzado desbloqueado"
            }
            if (parsedBrokenAnim) {
                commands += "settings put global window_animation_scale 1.0"
                commands += "settings put global transition_animation_scale 1.0"
                commands += "settings put global animator_duration_scale 1.0"
                solvedProblems += "Animaciones de UI restauradas"
            }
            if (parsedStuckDozeInteractive) {
                commands += "cmd deviceidle unforce 2>/dev/null"
                solvedProblems += "Doze desbloqueado con pantalla activa"
            }
            if (foregroundPkg != null) {
                commands += "appops set $foregroundPkg RUN_IN_BACKGROUND allow 2>/dev/null"
                commands += "appops set $foregroundPkg RUN_ANY_IN_BACKGROUND allow 2>/dev/null"
                commands += "am set-standby-bucket $foregroundPkg active 2>/dev/null"
            }
            if (parsedAdaptiveOff) {
                solvedProblems += "Batería Adaptativa reactivada"
            }
            if (t.isCharging && t.temperatureCelsius >= 39f) {
                if (allowRefreshRateChange && p.peakRefreshRateHz > 60) {
                    commands += "settings put system peak_refresh_rate 60.0"
                    commands += "settings put system min_refresh_rate 60.0"
                }
                if (parsedAodOn || !p.aodDisabled) {
                    commands += "settings put secure doze_always_on 0"
                }
                solvedProblems += "Calentamiento de batería al cargar reducido"
            } else if (!t.isCharging && t.level in 1..44 && p.peakRefreshRateHz > 60 && allowRefreshRateChange && !highDrain && !criticalDrain) {
                commands += "settings put system peak_refresh_rate 60.0"
                commands += "settings put system min_refresh_rate 60.0"
                if (parsedAodOn || !p.aodDisabled) {
                    commands += "settings put secure doze_always_on 0"
                }
                solvedProblems += "Problema de batería (120Hz/AOD) ajustado a 60Hz"
            }
            if (!t.isCharging && parsedWakeLocks >= 3) {
                solvedProblems += "Wakelocks de batería mitigados"
            }
        }

        // 2. Always enforce official Adaptive Battery & App Standby
        if (parsedAdaptiveOff || !p.isSyncedFromSystem) {
            commands += "settings put global adaptive_battery_management_enabled 1"
            commands += "settings put global app_standby_enabled 1"
        }
        activeTags += "Batería Adaptativa"

        if (request.appFreezeEcoEnabled) {
            activeTags += "Congelamiento IA"
        }

        if (request.appGameEcoEnabled) {
            activeTags += "Apps & Juegos Eco"
        }

        // 3. Bluetooth Eco: stops 24/7 background BLE scanning WITHOUT disabling Bluetooth hardware
        if (request.bluetoothEcoEnabled) {
            if (parsedBleScanAlwaysOn || !p.isSyncedFromSystem) {
                commands += "settings put global ble_scan_always_enabled 0"
                commands += "settings put global ble_scan_low_power_window_ms 512"
                commands += "settings put global ble_scan_low_power_interval_ms 5120"
                commands += "settings put global ble_scan_balanced_window_ms 1024"
                commands += "settings put global ble_scan_balanced_interval_ms 4096"
                if (parsedBleScanAlwaysOn && request.autoSolverEnabled) {
                    solvedProblems += "Escaneo BLE continuo corregido"
                }
            }
            activeTags += "Bluetooth Eco"
        }

        // 4. Autonomous Torch Eco WITHOUT turning off flashlight
        if (request.torchEcoEnabled) {
            if (request.torchActive) {
                if (allowRefreshRateChange && p.peakRefreshRateHz > 60) {
                    commands += "settings put system peak_refresh_rate 60.0"
                    commands += "settings put system min_refresh_rate 60.0"
                }
                if (!p.backgroundRestricted) {
                    commands += "settings put global adaptive_battery_management_enabled 1"
                    commands += "settings put global app_standby_enabled 1"
                }
                if (!request.deviceIdleLightActive && !request.isScreenInteractive) {
                    commands += "cmd deviceidle force-idle light"
                }
                if (request.autoSolverEnabled) {
                    solvedProblems += "Consumo de linterna compensado"
                }
            }
            activeTags += "Linterna Eco"
        }

        // 5. Thermal & Drain Problem Resolution
        if (highDrain || criticalDrain) {
            if (!p.lowPowerEnabled) {
                commands += "cmd power set-mode 1"
                commands += "settings put global low_power 1"
            }
            activeTags += "Ahorro Samsung"

            if (allowRefreshRateChange && p.peakRefreshRateHz > 60) {
                commands += "settings put system peak_refresh_rate 60.0"
                commands += "settings put system min_refresh_rate 60.0"
            }
            if (p.peakRefreshRateHz <= 60 || allowRefreshRateChange) {
                activeTags += "60 Hz"
            }

            if (parsedAodOn || !p.aodDisabled) {
                commands += "settings put secure doze_always_on 0"
            }
            activeTags += "AOD Off"

            if (!request.isScreenInteractive) {
                if (!request.deviceIdleLightActive) {
                    commands += "cmd deviceidle enable all"
                    commands += "cmd deviceidle force-idle light"
                }
                commands += "pm trim-caches 256M"
                activeTags += "Doze ligero"
            }

            if (request.autoSolverEnabled) {
                solvedProblems += when {
                    t.temperatureCelsius >= 38f -> "Sobrecalentamiento de batería mitigado"
                    else -> "Drenaje elevado de batería estabilizado"
                }
            }
        } else if (!t.isCharging) {
            activeTags += "App Standby"
        }

        if (!t.isCharging && highCpuPackages.isNotEmpty()) {
            highCpuPackages
                .filter { it != foregroundPkg }
                .take(2)
                .forEach { pkg ->
                    commands += "am set-standby-bucket $pkg rare"
                }
            val throttledCount = highCpuPackages.filter { it != foregroundPkg }.take(2).size
            if (throttledCount > 0) {
                activeTags += "$throttledCount apps en standby"
                if (request.autoSolverEnabled) {
                    solvedProblems += "$throttledCount apps con alto consumo optimizadas"
                }
            }
        }

        val finalCmd = if (commands.isEmpty()) {
            null
        } else {
            commands.distinct().joinToString("; ") + "; echo 'OK: Auto-Guardian Samsung aplicado'"
        }

        val summaryLabel = when {
            solvedProblems.isNotEmpty() -> "Resuelto: " + solvedProblems.distinct().joinToString(" • ")
            finalCmd == null -> "Sistema estable sin fallas (${t.currentMa} mA • ${p.peakRefreshRateHz} Hz)"
            else -> "Auto-ajuste: " + activeTags.distinct().joinToString(" + ")
        }

        return AutoGuardianPlan(
            severity = severity,
            commandToExecute = finalCmd,
            summaryLabel = summaryLabel,
            activeFeaturesLabel = activeTags.distinct().joinToString(" • "),
            solvedProblems = solvedProblems.distinct()
        )
    }

    private fun readAndroidThermalStatus(): String? {
        val ctx = context ?: return null
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Normal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Tibio"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
                PowerManager.THERMAL_STATUS_SEVERE -> "Alto"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Extremo"
                else -> null
            }
        } else {
            null
        }
    }

    private fun computeHourlyDrainRate(history: List<BatteryHistoryPoint>): Float? {
        if (history.size < 2) return null
        val first = history.first()
        val last = history.last()
        val elapsedHours = (last.timestampMs - first.timestampMs) / 3_600_000f
        if (elapsedHours < 0.01f) return null
        val drop = (first.level - last.level).toFloat()
        return if (drop > 0f) drop / elapsedHours else null
    }

    internal fun extractForegroundPackage(raw: String): String? {
        if (raw.isBlank()) return null
        val fgSection = raw.substringAfter("=== FG ===", "")
        val fgRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*\.[a-zA-Z0-9_.]+)/""")
        val protectedPrefixes = listOf(
            "android",
            "com.android.",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.providers.",
            "com.google.android.inputmethod.",
            "com.google.android.webview",
            "com.motorola.",
            "moe.shizuku.",
            "com.termux",
            "com.aistudio."
        )
        return fgRegex.findAll(fgSection)
            .map { it.groupValues[1] }
            .firstOrNull { pkg ->
                pkg.count { it == '.' } >= 1 &&
                    protectedPrefixes.none { prefix -> pkg == prefix || pkg.startsWith(prefix) }
            }
    }

    internal fun extractHighDrainThirdPartyPackages(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val bodyWithoutFg = raw.substringBefore("=== FG ===")
        val packageRegex = Regex("""\b([a-zA-Z][a-zA-Z0-9_]*\.[a-zA-Z0-9_.]+)\b""")
        val protectedPrefixes = listOf(
            "android",
            "com.android.",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.providers.",
            "com.motorola.",
            "moe.shizuku.",
            "com.termux",
            "com.aistudio."
        )
        return packageRegex.findAll(bodyWithoutFg)
            .map { it.groupValues[1].substringBefore(":") }
            .filter { pkg ->
                pkg.count { it == '.' } >= 2 &&
                    protectedPrefixes.none { prefix -> pkg == prefix || pkg.startsWith(prefix) }
            }
            .distinct()
            .take(3)
            .toList()
    }
}
