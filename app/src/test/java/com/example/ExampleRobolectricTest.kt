package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TermuZuku", appName)
  }

  @Test
  fun `built in catalog includes shizuku termux and local presets`() {
    val engines = com.example.model.BuiltInFunctionsCatalog.presets.map { it.engine }.toSet()
    assertTrue(engines.contains(com.example.model.CommandEngine.SHIZUKU))
    assertTrue(engines.contains(com.example.model.CommandEngine.TERMUX))
    assertTrue(engines.contains(com.example.model.CommandEngine.LOCAL))
    assertTrue(engines.contains(com.example.model.CommandEngine.BOTH))
  }

  @Test
  fun `built in presets contain no destructive system commands`() {
    val forbiddenTokens = listOf(
      "rm -rf", "mkfs", "dd if=", "reboot", "shutdown",
      "/dev/block", "mount -o rw", "setenforce 0", "pm uninstall", "pm clear"
    )
    val allCommands = com.example.model.BuiltInFunctionsCatalog.presets.map { it.command } +
      listOf(
        com.example.model.BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_SAMSUNG_ULTRA_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_OPTIMIZE_PHONE_BATTERY,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_ECO_60HZ,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_STANDBY_ON,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_STANDBY_OFF
      )
    allCommands.forEach { cmd ->
      val lower = cmd.lowercase()
      forbiddenTokens.forEach { token ->
        assertFalse("Unsafe token '$token' found in: $cmd", lower.contains(token))
      }
    }
  }

  @Test
  fun `on device battery intelligence detects drain causes and protects system packages`() =
    kotlinx.coroutines.test.runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
      val result = engine.analyzeBatteryDrain(
        com.example.ai.BatteryDrainAnalysisRequest(
          telemetry = com.example.model.BatteryTelemetry(
            isDetected = true,
            level = 32,
            temperatureCelsius = 41.5f,
            currentMa = -920,
            powerWatts = 3.5f
          ),
          history = emptyList(),
          powerStatus = com.example.model.PowerProfileStatus(
            peakRefreshRateHz = 120,
            lowPowerEnabled = false
          ),
          shizukuConnected = true,
          rawSystemEvidence = """
            Wake Locks: size=4
            com.android.systemui
            moe.shizuku.privileged.api
            com.example.heavyapp
          """.trimIndent()
        )
      )
      assertEquals(com.example.ai.DrainSeverity.CRITICAL, result.severity)
      assertTrue(result.recommendedFixes.any { it.command.contains("com.example.heavyapp") })
      assertFalse(result.recommendedFixes.any { it.command.contains("com.android.systemui") })
      assertFalse(result.recommendedFixes.any { it.command.contains("moe.shizuku") })

      val plan = engine.buildAutoGuardianPlan(
        request = com.example.ai.BatteryDrainAnalysisRequest(
          telemetry = com.example.model.BatteryTelemetry(
            isDetected = true,
            level = 32,
            temperatureCelsius = 41.5f,
            currentMa = -920,
            powerWatts = 3.5f
          ),
          history = emptyList(),
          powerStatus = com.example.model.PowerProfileStatus(
            peakRefreshRateHz = 120,
            lowPowerEnabled = false
          ),
          shizukuConnected = true,
          rawSystemEvidence = "Wake Locks: size=4\ncom.android.systemui\ncom.example.heavyapp"
        ),
        allowRefreshRateChange = true
      )
      val cmd = plan.commandToExecute.orEmpty()
      assertTrue(cmd.contains("peak_refresh_rate 60.0"))
      assertTrue(cmd.contains("cmd power set-mode 1"))
      assertTrue(cmd.contains("am set-standby-bucket com.example.heavyapp rare"))
      assertFalse(cmd.contains("com.android.systemui"))
      assertFalse(cmd.contains("netpolicy"))
      assertFalse(cmd.contains("restrict-background"))
      assertFalse(cmd.contains("set-mode 0"))
      assertFalse(cmd.contains("low_power 0"))
    }

  @Test
  fun `no presets or smart commands activate data saver or crash inducing system flags`() {
    val forbiddenTokens = listOf(
      "netpolicy",
      "restrict-background",
      "cmd power set-mode 0",
      "low_power 0",
      "bluetooth_manager disable",
      "svc bluetooth disable",
      "bluetooth_on 0",
      "use_freezer true",
      "cached_apps_freezer enabled",
      "forced_app_standby_enabled 1",
      "downscalefactor=0.8",
      "am kill-all"
    )
    val checkedCommands = com.example.model.BuiltInFunctionsCatalog.presets.map { it.command } +
      listOf(
        com.example.model.BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_SAMSUNG_ULTRA_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_MOTO_G82,
        com.example.model.BuiltInFunctionsCatalog.CMD_OPTIMIZE_PHONE_BATTERY,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_ECO_60HZ,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_STANDBY_ON,
        com.example.model.BuiltInFunctionsCatalog.CMD_SMART_STANDBY_OFF,
        com.example.model.BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_ON,
        com.example.model.BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_OFF,
        com.example.model.BuiltInFunctionsCatalog.CMD_TORCH_ECO_COMPENSATION,
        com.example.model.BuiltInFunctionsCatalog.CMD_STOP_BG_WAKEUPS,
        com.example.model.BuiltInFunctionsCatalog.CMD_ANTI_DRAIN_FULL,
        com.example.model.BuiltInFunctionsCatalog.CMD_QUERY_POWER_STATE,
        com.example.model.BuiltInFunctionsCatalog.CMD_INSPECT_POWER_FAST,
        com.example.model.BuiltInFunctionsCatalog.CMD_INSPECT_DRAIN_CAUSES
      )
    checkedCommands.forEach { cmd ->
      val lower = cmd.lowercase()
      forbiddenTokens.forEach { token ->
        assertFalse(
          "Prohibited token '$token' found in command: $cmd",
          lower.contains(token)
        )
      }
    }
  }

  @Test
  fun `quick settings tile service is declared with bind permission`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val component = android.content.ComponentName(
      context,
      com.example.tile.BatterySaverTileService::class.java
    )
    val serviceInfo = context.packageManager.getServiceInfo(
      component,
      android.content.pm.PackageManager.GET_META_DATA
    )
    assertEquals(android.Manifest.permission.BIND_QUICK_SETTINGS_TILE, serviceInfo.permission)
    assertTrue(serviceInfo.exported)
  }

  @Test
  fun `auto guardian applies bluetooth eco automatically without disabling bluetooth`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val plan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          level = 68,
          temperatureCelsius = 33f,
          currentMa = -260,
          powerWatts = 1.0f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 60,
          lowPowerEnabled = false,
          isSyncedFromSystem = true
        ),
        shizukuConnected = true,
        rawSystemEvidence = "BT_ON=1 BLE_SCAN=1",
        bluetoothEcoEnabled = true
      ),
      allowRefreshRateChange = false
    )
    val cmd = plan.commandToExecute.orEmpty()
    assertTrue(cmd.contains("ble_scan_always_enabled 0"))
    assertTrue(cmd.contains("ble_scan_low_power_window_ms 512"))
    assertFalse(cmd.contains("bluetooth_manager disable"))
    assertFalse(cmd.contains("svc bluetooth disable"))
    assertFalse(cmd.contains("bluetooth_on 0"))
    assertTrue(plan.activeFeaturesLabel.contains("Bluetooth Eco"))
  }

  @Test
  fun `termux local bridge marks connected even without run command permission`() {
    val bridgeOnly = com.example.model.TermuxStatus(
      isInstalled = true,
      hasRunCommandPermission = false,
      isLocalBridgeOnline = true,
      bridgePort = 18765
    )
    assertEquals(com.example.model.ConnectionHealth.CONNECTED, bridgeOnly.health)
    assertEquals("Puente Socket (18765)", bridgeOnly.modeLabel)

    val disconnected = com.example.model.TermuxStatus(
      isInstalled = true,
      hasRunCommandPermission = false,
      isLocalBridgeOnline = false,
      bridgePort = 18765
    )
    assertEquals(com.example.model.ConnectionHealth.PERMISSION_REQUIRED, disconnected.health)
    assertEquals("Requiere Puente", disconnected.modeLabel)
    assertEquals("Sin enlace", disconnected.copy(runCommandDeclaredInTermux = true, runCommandIsRuntimeGrantable = true, runCommandServiceExported = true).modeLabel)
  }

  @Test
  fun `termux local bridge setup command embeds token loopback port and autostart`() {
    val setupCmd = com.example.engine.TermuxController.buildLocalBridgeSetupCommand(
      token = "testtoken123",
      port = 18765
    )
    assertFalse(setupCmd.contains("\n"))
    assertFalse(setupCmd.contains("base64"))
    assertTrue(setupCmd.contains("testtoken123"))
    assertTrue(setupCmd.contains(".tz_bridge.py"))
    assertTrue(setupCmd.contains(".bashrc"))
    assertTrue(setupCmd.contains("18765"))
    assertTrue(setupCmd.contains("chmod 600 ~/.tz_token"))
    assertFalse(setupCmd.contains("pkill -f"))
  }

  @Test
  fun `termux status detects blocked run command on play store builds and bridge script is safe`() {
    val playStoreTermux = com.example.model.TermuxStatus(
      isInstalled = true,
      termuxVersionName = "googleplay.2026.10.09",
      runCommandDeclaredInTermux = false,
      runCommandIsRuntimeGrantable = false,
      runCommandServiceExported = false,
      hasRunCommandPermission = false,
      isLocalBridgeOnline = false
    )
    assertFalse(playStoreTermux.supportsRunCommand)
    assertEquals("Requiere Puente", playStoreTermux.modeLabel)

    val fdroidTermux = playStoreTermux.copy(
      termuxVersionName = "0.118.1",
      runCommandDeclaredInTermux = true,
      runCommandIsRuntimeGrantable = true,
      runCommandServiceExported = true
    )
    assertTrue(fdroidTermux.supportsRunCommand)
  }

  @Test
  fun `torch eco computes reduced level without ever turning off flashlight`() {
    assertEquals(2, com.example.engine.TorchEcoController.computeTargetEcoLevel(maxStrength = 5, highDrainOrHot = false))
    assertEquals(1, com.example.engine.TorchEcoController.computeTargetEcoLevel(maxStrength = 5, highDrainOrHot = true))
    assertEquals(40, com.example.engine.TorchEcoController.computeTargetEcoLevel(maxStrength = 100, highDrainOrHot = false))
    assertEquals(25, com.example.engine.TorchEcoController.computeTargetEcoLevel(maxStrength = 100, highDrainOrHot = true))
    assertTrue(com.example.engine.TorchEcoController.computeTargetEcoLevel(maxStrength = 1, highDrainOrHot = true) >= 1)
  }

  @Test
  fun `auto guardian compensates active flashlight without turning it off`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val plan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          level = 64,
          temperatureCelsius = 34f,
          currentMa = -450,
          powerWatts = 1.7f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 120,
          lowPowerEnabled = false,
          isSyncedFromSystem = true
        ),
        shizukuConnected = true,
        rawSystemEvidence = "TORCH=1",
        bluetoothEcoEnabled = true,
        torchEcoEnabled = true,
        torchActive = true,
        isScreenInteractive = false
      ),
      allowRefreshRateChange = true
    )
    val cmd = plan.commandToExecute.orEmpty()
    assertTrue(cmd.contains("peak_refresh_rate 60.0"))
    assertTrue(cmd.contains("app_standby_enabled 1"))
    assertTrue(cmd.contains("cmd deviceidle force-idle light"))
    assertFalse(cmd.contains("forced_app_standby_enabled 1"))
    assertFalse(cmd.lowercase().contains("torch 0"))
    assertFalse(cmd.lowercase().contains("flashlight 0"))
    assertTrue(plan.activeFeaturesLabel.contains("Linterna Eco"))

    val torchPresetCmd = com.example.model.BuiltInFunctionsCatalog.CMD_TORCH_ECO_COMPENSATION.lowercase()
    assertFalse(torchPresetCmd.contains("torch 0"))
    assertFalse(torchPresetCmd.contains("flashlight 0"))
    assertFalse(torchPresetCmd.contains("settorchmode"))
  }

  @Test
  fun `socket bridge v2 setup command launches cleanly and executes bash commands`() =
    kotlinx.coroutines.test.runTest {
      val tmpHome = java.nio.file.Files.createTempDirectory("tz_bridge_test").toFile()
      val probeSocket = java.net.ServerSocket(0)
      val testPort = probeSocket.localPort
      probeSocket.close()
      val testToken = "tok_e2e_9911"
      val setupCmd = com.example.engine.TermuxController.buildLocalBridgeSetupCommand(
        token = testToken,
        port = testPort
      )

      val proc = ProcessBuilder("bash", "-c", setupCmd)
        .apply {
          environment()["HOME"] = tmpHome.absolutePath
          redirectErrorStream(true)
        }
        .start()
      val setupOutput = proc.inputStream.bufferedReader().readText()
      val exit = proc.waitFor()
      assertEquals("Setup failed with output: $setupOutput", 0, exit)
      assertTrue("Expected OK verification in output: $setupOutput", setupOutput.contains("OK Puente Socket v2"))

      val context = ApplicationProvider.getApplicationContext<Context>()
      val controller = com.example.engine.TermuxController(context)
      try {
        assertTrue(controller.checkLocalBridgeAlive(port = testPort, token = testToken))
        assertFalse(controller.checkLocalBridgeAlive(port = testPort, token = "wrong_token"))
        val execRes = controller.executeCommand(
          command = "echo HOLA_SOCKET_V2",
          runInBackground = true,
          bridgeToken = testToken,
          bridgePort = testPort
        )
        assertEquals(0, execRes.exitCode)
        assertEquals("HOLA_SOCKET_V2", execRes.stdout)
      } finally {
        controller.executeCommand(
          command = "kill -9 \$(pgrep -f tz_bridge.py) 2>/dev/null || true",
          runInBackground = true,
          bridgeToken = testToken,
          bridgePort = testPort
        )
        tmpHome.deleteRecursively()
      }
    }

  @Test
  fun `app game optimizer parses apps games foreground and excludes protected packages`() {
    val rawDiscovery = """
      ===FG===
      topResumedActivity=ActivityRecord{1234 u0 com.spotify.music/.MainActivity t99}
      ===PKGS===
      com.android.systemui
      moe.shizuku.privileged.api
      com.termux
      com.aistudio.termuzuku.k7m2vx
      com.spotify.music
      com.whatsapp
      com.example.mygame
      com.funkin.fnfmobile
      ===GAMES===
      Package [com.example.mygame] (abc):
        flags=[ HAS_CODE ALLOW_BACKUP IS_GAME ]
      Package [com.whatsapp] (def):
        category=1
    """.trimIndent()

    val parsed = com.example.engine.AppGameOptimizerEngine.parseDiscoveredPackages(rawDiscovery)
    val pkgNames = parsed.map { it.packageName }

    assertFalse(pkgNames.contains("com.android.systemui"))
    assertFalse(pkgNames.contains("moe.shizuku.privileged.api"))
    assertFalse(pkgNames.contains("com.termux"))
    assertFalse(pkgNames.contains("com.aistudio.termuzuku.k7m2vx"))

    val spotify = parsed.first { it.packageName == "com.spotify.music" }
    val whatsapp = parsed.first { it.packageName == "com.whatsapp" }
    val manifestGame = parsed.first { it.packageName == "com.example.mygame" }
    val tokenGame = parsed.first { it.packageName == "com.funkin.fnfmobile" }

    assertTrue(spotify.isForeground)
    assertFalse(spotify.isGame)
    assertFalse(whatsapp.isForeground)
    assertFalse(whatsapp.isGame)
    assertTrue(manifestGame.isGame)
    assertTrue(tokenGame.isGame)
  }

  @Test
  fun `app game optimizer builds safe standby and game mode commands without duplicating work`() {
    val packages = listOf(
      com.example.engine.DetectedPackageInfo("com.spotify.music", isGame = false, isForeground = true),
      com.example.engine.DetectedPackageInfo("com.whatsapp", isGame = false, isForeground = false),
      com.example.engine.DetectedPackageInfo("com.funkin.fnfmobile", isGame = true, isForeground = false)
    )

    val firstPlan = com.example.engine.AppGameOptimizerEngine.buildAutoOptimizationPlan(
      packages = packages,
      appliedAppBuckets = emptyMap(),
      alreadyOptimizedGames = emptySet(),
      batteryLevel = 65,
      temperatureCelsius = 33f,
      isCharging = false
    )

    assertEquals(2, firstPlan.discoveredAppsCount)
    assertEquals(1, firstPlan.discoveredGamesCount)
    assertEquals(1, firstPlan.newlyOptimizedApps)
    assertEquals(1, firstPlan.newlyOptimizedGames)
    val cmd1 = firstPlan.commandToExecute.orEmpty()
    assertTrue(cmd1.contains("am set-standby-bucket com.funkin.fnfmobile rare"))
    assertTrue(cmd1.contains("am set-standby-bucket com.whatsapp rare"))
    assertFalse(cmd1.contains("downscaleFactor"))
    assertFalse(cmd1.contains("cmd game mode battery"))
    assertFalse(cmd1.contains("am set-standby-bucket com.spotify.music"))
    assertFalse(cmd1.contains("pm disable"))
    assertFalse(cmd1.contains("am force-stop"))

    // Second run with same conditions should skip redundant commands
    val secondPlan = com.example.engine.AppGameOptimizerEngine.buildAutoOptimizationPlan(
      packages = packages,
      appliedAppBuckets = firstPlan.targetedAppBuckets,
      alreadyOptimizedGames = firstPlan.targetedGames.toSet(),
      batteryLevel = 65,
      temperatureCelsius = 33f,
      isCharging = false
    )
    assertEquals(null, secondPlan.commandToExecute)

    // Low battery escalates background apps from rare -> restricted
    val escalatedPlan = com.example.engine.AppGameOptimizerEngine.buildAutoOptimizationPlan(
      packages = packages,
      appliedAppBuckets = firstPlan.targetedAppBuckets,
      alreadyOptimizedGames = firstPlan.targetedGames.toSet(),
      batteryLevel = 22,
      temperatureCelsius = 35f,
      isCharging = false
    )
    val cmdEsc = escalatedPlan.commandToExecute.orEmpty()
    assertTrue(cmdEsc.contains("am set-standby-bucket com.whatsapp restricted"))
    assertFalse(cmdEsc.contains("downscaleFactor"))
  }

  @Test
  fun `app game freezer gently restricts idle apps and games skips active media and unfreezes foreground`() {
    val rawDiscovery = """
      ===FG===
      topResumedActivity=ActivityRecord{111 u0 com.instagram.android/.MainActivity t10}
      ===MEDIA===
      package=com.spotify.music
        state=PlaybackState {state=3, position=1200}
      ===PKGS===
      com.instagram.android
      com.spotify.music
      com.whatsapp
      com.funkin.fnfmobile
      ===GAMES===
      Package [com.funkin.fnfmobile] (xyz):
        IS_GAME
    """.trimIndent()

    val discovered = com.example.engine.AppGameOptimizerEngine.parseDiscoveredPackages(rawDiscovery)
    val spotify = discovered.first { it.packageName == "com.spotify.music" }
    assertTrue(spotify.hasActiveMedia)

    val freezePlan = com.example.engine.AppGameOptimizerEngine.buildAutoOptimizationPlan(
      packages = discovered,
      appliedAppBuckets = emptyMap(),
      alreadyOptimizedGames = emptySet(),
      batteryLevel = 58,
      temperatureCelsius = 34f,
      isCharging = false,
      freezeAppsAndGames = true,
      alreadyFrozenPackages = emptySet(),
      freezerSystemEnabled = false,
      allowMakeUidIdle = false
    )

    val cmd = freezePlan.commandToExecute.orEmpty()
    assertTrue(cmd.contains("adaptive_battery_management_enabled 1"))
    assertTrue(cmd.contains("app_standby_enabled 1"))
    assertFalse(cmd.contains("use_freezer"))
    assertFalse(cmd.contains("cached_apps_freezer"))
    assertFalse(cmd.contains("RUN_IN_BACKGROUND ignore"))
    assertFalse(cmd.contains("am make-uid-idle"))
    assertTrue(freezePlan.newlyFrozenPackages.contains("com.whatsapp"))
    assertTrue(freezePlan.newlyFrozenPackages.contains("com.funkin.fnfmobile"))
    assertFalse(freezePlan.newlyFrozenPackages.contains("com.instagram.android"))
    assertFalse(freezePlan.newlyFrozenPackages.contains("com.spotify.music"))
    assertTrue(cmd.contains("appops set com.funkin.fnfmobile RUN_ANY_IN_BACKGROUND ignore"))
    assertTrue(cmd.contains("am set-standby-bucket com.whatsapp restricted"))
    assertFalse(cmd.contains("app_hibernation set-state com.whatsapp true"))
    assertFalse(cmd.contains("pm disable"))
    assertFalse(cmd.contains("am force-stop"))

    // Multi-window / floating window: both visible apps stay foreground-protected while idle apps freeze
    val multiWindowRaw = """
      ===FG===
      topResumedActivity=ActivityRecord{111 u0 com.funkin.fnfmobile/.MainActivity t10}
      mResumedActivity: ActivityRecord{222 u0 com.whatsapp/.HomeActivity t11}
      ===MEDIA===
      ===PKGS===
      com.funkin.fnfmobile
      com.whatsapp
      com.instagram.android
      ===GAMES===
      Package [com.funkin.fnfmobile] (xyz):
        IS_GAME
    """.trimIndent()
    val multiDiscovered = com.example.engine.AppGameOptimizerEngine.parseDiscoveredPackages(multiWindowRaw)
    assertTrue(multiDiscovered.first { it.packageName == "com.funkin.fnfmobile" }.isForeground)
    assertTrue(multiDiscovered.first { it.packageName == "com.whatsapp" }.isForeground)
    assertFalse(multiDiscovered.first { it.packageName == "com.instagram.android" }.isForeground)

    // Single package freeze on an open game keeps it open (no make-uid-idle, no game_overlay downscale)
    val openGameCmd = com.example.engine.AppGameOptimizerEngine.buildSinglePackageFreezeCommand(
      info = multiDiscovered.first { it.packageName == "com.funkin.fnfmobile" },
      freeze = true
    )
    assertTrue(openGameCmd.contains("appops set com.funkin.fnfmobile RUN_ANY_IN_BACKGROUND ignore"))
    assertTrue(openGameCmd.contains("am set-standby-bucket com.funkin.fnfmobile restricted"))
    assertFalse(openGameCmd.contains("am make-uid-idle"))
    assertFalse(openGameCmd.contains("am force-stop"))
    assertFalse(openGameCmd.contains("downscaleFactor"))

    // When user opens the previously frozen game in foreground, AI automatically restores it
    val switched = discovered.map { pkg ->
      pkg.copy(isForeground = pkg.packageName == "com.funkin.fnfmobile")
    }
    val unfreezePlan = com.example.engine.AppGameOptimizerEngine.buildAutoOptimizationPlan(
      packages = switched,
      appliedAppBuckets = freezePlan.targetedAppBuckets,
      alreadyOptimizedGames = freezePlan.targetedGames.toSet(),
      batteryLevel = 58,
      temperatureCelsius = 34f,
      isCharging = false,
      freezeAppsAndGames = true,
      alreadyFrozenPackages = freezePlan.newlyFrozenPackages.toSet(),
      freezerSystemEnabled = true
    )
    assertEquals("com.funkin.fnfmobile", unfreezePlan.unfrozenForegroundPackage)
    val unfreezeCmd = unfreezePlan.commandToExecute.orEmpty()
    assertTrue(unfreezeCmd.contains("appops set com.funkin.fnfmobile RUN_ANY_IN_BACKGROUND allow"))
    assertTrue(unfreezeCmd.contains("am set-standby-bucket com.funkin.fnfmobile active"))
  }

  @Test
  fun `auto guardian enables safe adaptive battery without kernel freezer or forced standby`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val plan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          level = 72,
          temperatureCelsius = 32f,
          currentMa = -250,
          powerWatts = 0.9f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 60,
          lowPowerEnabled = false,
          isSyncedFromSystem = false
        ),
        shizukuConnected = true,
        rawSystemEvidence = "",
        appGameEcoEnabled = true,
        appFreezeEcoEnabled = true
      ),
      allowRefreshRateChange = true
    )
    val cmd = plan.commandToExecute.orEmpty()
    assertTrue(cmd.contains("adaptive_battery_management_enabled 1"))
    assertTrue(cmd.contains("app_standby_enabled 1"))
    assertFalse(cmd.contains("use_freezer true"))
    assertFalse(cmd.contains("cached_apps_freezer enabled"))
    assertFalse(cmd.contains("forced_app_standby_enabled 1"))
    assertTrue(plan.activeFeaturesLabel.contains("Congelamiento IA"))
    assertTrue(plan.activeFeaturesLabel.contains("Apps & Juegos Eco"))
  }

  @Test
  fun `periodic polling commands avoid systemui locking dumpsys and repair command clears unstable flags`() {
    assertFalse(
      "DISCOVERY_COMMAND must not run full dumpsys package",
      com.example.engine.AppGameOptimizerEngine.DISCOVERY_COMMAND.contains("dumpsys package")
    )
    assertFalse(
      "CMD_INSPECT_POWER_FAST must not run dumpsys batterystats",
      com.example.model.BuiltInFunctionsCatalog.CMD_INSPECT_POWER_FAST.contains("dumpsys batterystats")
    )

    val repairCmd = com.example.model.BuiltInFunctionsCatalog.CMD_REPAIR_SYSTEM_STABILITY
    assertTrue(repairCmd.contains("device_config delete activity_manager_native_boot use_freezer"))
    assertTrue(repairCmd.contains("settings delete global cached_apps_freezer"))
    assertTrue(repairCmd.contains("settings put global forced_app_standby_enabled 0"))
    assertTrue(repairCmd.contains("RUN_IN_BACKGROUND allow"))
    assertTrue(repairCmd.contains("RUN_ANY_IN_BACKGROUND allow"))
    assertTrue(repairCmd.contains("device_config delete game_overlay"))
    assertTrue(repairCmd.contains("cmd deviceidle unforce"))

    assertTrue(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.motorola.launcher3"))
    assertTrue(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.google.android.inputmethod.latin"))
    assertTrue(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.touchtype.swiftkey.keyboard"))
    assertTrue(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.google.android.webview"))
    assertTrue(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.dolby.daxservice"))
    assertFalse(com.example.engine.AppGameOptimizerEngine.isProtectedPackage("com.whatsapp"))

    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val interactivePlan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          level = 30,
          temperatureCelsius = 39.5f,
          currentMa = -700,
          powerWatts = 2.6f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 120,
          lowPowerEnabled = false,
          isSyncedFromSystem = true
        ),
        shizukuConnected = true,
        rawSystemEvidence = "",
        isScreenInteractive = true,
        freezerSystemEnabled = true
      ),
      allowRefreshRateChange = true
    )
    val cmd = interactivePlan.commandToExecute.orEmpty()
    assertFalse("Interactive cycle must not run am kill-all", cmd.contains("am kill-all"))
    assertFalse("Interactive cycle must not run pm trim-caches", cmd.contains("pm trim-caches"))
    assertFalse("Interactive cycle must not spam force-idle while screen is on", cmd.contains("force-idle"))
  }

  @Test
  fun `auto solver automatically fixes unstable flags animations stuck doze and foreground appops`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val brokenEvidence = """
      PEAK_HZ=120 LOW_POWER=0 AOD=1 ADAPTIVE=0 FORCED_STANDBY=1 BT_ON=1 BLE_SCAN=1
      USE_FREEZER=true CACHED_FREEZER=enabled ANIM_WIN=0.0 ANIM_TRANS=0 ANIM_DUR=0.0
      DOZE_LIGHT=IDLE DOZE_DEEP=ACTIVE
      === FG ===
      topResumedActivity=ActivityRecord{123 u0 com.whatsapp/.MainActivity t12}
    """.trimIndent()

    val plan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          level = 24,
          temperatureCelsius = 40.5f,
          currentMa = -800,
          powerWatts = 3.1f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 120,
          lowPowerEnabled = false,
          isSyncedFromSystem = true
        ),
        shizukuConnected = true,
        rawSystemEvidence = brokenEvidence,
        bluetoothEcoEnabled = true,
        isScreenInteractive = true,
        autoSolverEnabled = true
      ),
      allowRefreshRateChange = true
    )

    val cmd = plan.commandToExecute.orEmpty()
    assertTrue(cmd.contains("device_config delete activity_manager_native_boot use_freezer"))
    assertTrue(cmd.contains("settings delete global cached_apps_freezer"))
    assertTrue(cmd.contains("settings put global forced_app_standby_enabled 0"))
    assertTrue(cmd.contains("window_animation_scale 1.0"))
    assertTrue(cmd.contains("transition_animation_scale 1.0"))
    assertTrue(cmd.contains("animator_duration_scale 1.0"))
    assertTrue(cmd.contains("cmd deviceidle unforce"))
    assertTrue(cmd.contains("appops set com.whatsapp RUN_IN_BACKGROUND allow"))
    assertTrue(cmd.contains("appops set com.whatsapp RUN_ANY_IN_BACKGROUND allow"))
    assertTrue(cmd.contains("am set-standby-bucket com.whatsapp active"))
    assertTrue(cmd.contains("adaptive_battery_management_enabled 1"))
    assertTrue(cmd.contains("ble_scan_always_enabled 0"))
    assertFalse(cmd.contains("forced_app_standby_enabled 1"))
    assertFalse(cmd.contains("use_freezer true"))
    assertTrue(plan.solvedProblems.isNotEmpty())
    assertTrue(plan.activeFeaturesLabel.contains("Auto-Solución"))

    val autoSolveAll = com.example.model.BuiltInFunctionsCatalog.CMD_AUTO_SOLVE_ALL
    assertTrue(autoSolveAll.contains("forced_app_standby_enabled 0"))
    assertTrue(autoSolveAll.contains("adaptive_battery_management_enabled 1"))
    assertTrue(autoSolveAll.contains("ble_scan_always_enabled 0"))
    assertFalse(autoSolveAll.contains("use_freezer true"))
  }

  @Test
  fun `smart saving card hides manual app freeze chips and defaults to automatic saving`() {
    val defaultState = com.example.model.SmartSavingState()
    assertTrue(defaultState.enabled)
    assertTrue(defaultState.standbyEnabled)
    assertTrue(defaultState.appGameEcoEnabled)
    assertTrue(defaultState.appFreezeEcoEnabled)

    composeTestRule.setContent {
      com.example.ui.theme.TermuZukuTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.SmartSavingCard(
            smartState = com.example.model.SmartSavingState(
              enabled = true,
              standbyEnabled = true,
              appGameEcoEnabled = true,
              appFreezeEcoEnabled = true,
              detectedAppsCount = 137,
              detectedGamesCount = 4,
              frozenPackagesCount = 30
            ),
            onToggleSmartSaving = {},
            onToggleSmartStandbySaving = {},
            onToggleBluetoothEco = {},
            onToggleTorchEco = {},
            onToggleAppGameEco = {},
            onToggleAppFreezeEco = {}
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("detected_apps_freeze_list").assertDoesNotExist()
    composeTestRule.onNodeWithText("Apps y Juegos detectados", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Toca para congelar", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Detectadas 137 apps y 4 juegos", substring = true)
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("IA hibernando 30 apps/juegos inactivos", substring = true)
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `battery ai doctor card highlights battery problem solving and mitigates hot charging`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = com.example.ai.OnDeviceBatteryIntelligenceEngine(context)
    val hotChargingPlan = engine.buildAutoGuardianPlan(
      request = com.example.ai.BatteryDrainAnalysisRequest(
        telemetry = com.example.model.BatteryTelemetry(
          isDetected = true,
          isCharging = true,
          level = 78,
          temperatureCelsius = 40.2f,
          currentMa = 1800,
          powerWatts = 7.4f
        ),
        history = emptyList(),
        powerStatus = com.example.model.PowerProfileStatus(
          peakRefreshRateHz = 120,
          lowPowerEnabled = false,
          isSyncedFromSystem = true
        ),
        shizukuConnected = true,
        rawSystemEvidence = "AOD=1 ADAPTIVE=1 FORCED_STANDBY=0 BLE_SCAN=0",
        autoSolverEnabled = true
      ),
      allowRefreshRateChange = true
    )
    val hotCmd = hotChargingPlan.commandToExecute.orEmpty()
    assertTrue(hotCmd.contains("peak_refresh_rate 60.0"))
    assertTrue(hotCmd.contains("doze_always_on 0"))
    assertTrue(hotChargingPlan.activeFeaturesLabel.contains("Problemas de Batería"))
    assertTrue(hotChargingPlan.solvedProblems.any { it.contains("Calentamiento de batería") })

    composeTestRule.setContent {
      com.example.ui.theme.TermuZukuTheme {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
          com.example.ui.components.BatteryAiDoctorCard(
            guardianState = com.example.ai.AutoGuardianState(),
            onToggleAutoGuardian = {},
            onToggleAutoSolver = {}
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("solve_all_now_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("repair_system_apps_button").assertDoesNotExist()
    composeTestRule.onNodeWithText("Resolver todo y activar funciones", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Reparar fallas de sistema y apps", substring = true).assertDoesNotExist()
    composeTestRule.onNodeWithText("Auto-Solución: Batería, Problemas y Errores", substring = false)
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("problemas de batería y drenaje", substring = true)
      .performScrollTo()
      .assertIsDisplayed()
  }
}
