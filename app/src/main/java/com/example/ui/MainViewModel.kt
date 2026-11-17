package com.example.ui

import android.app.Application
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.tile.BatterySaverTileService
import com.example.ai.AutoGuardianState
import com.example.ai.BatteryAiRepository
import com.example.ai.BatteryDrainAnalysisRequest
import com.example.ai.OnDeviceBatteryIntelligenceEngine
import com.example.data.AppDatabase
import com.example.data.BatteryPreferencesRepository
import com.example.data.CommandRepository
import com.example.engine.AppGameOptimizerEngine
import com.example.engine.BatteryMonitorManager
import com.example.engine.DetectedPackageInfo
import com.example.engine.LocalShellExecutor
import com.example.engine.ShizukuController
import com.example.engine.TermuxController
import com.example.engine.TorchEcoController
import com.example.model.BatteryAutomationRule
import com.example.model.BatteryHistoryPoint
import com.example.model.BatteryTelemetry
import com.example.model.BuiltInFunctionsCatalog
import com.example.model.ChargeProtectionConfig
import com.example.model.CommandEngine
import com.example.model.ConnectionHealth
import com.example.model.EngineExecutionResult
import com.example.model.FunctionCategory
import com.example.model.PowerProfileStatus
import com.example.model.SamsungPowerProfile
import com.example.model.ShizukuStatus
import com.example.model.SmartSavingStage
import com.example.model.SmartSavingState
import com.example.model.SystemFunctionPreset
import com.example.model.TermuxStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val commandRepository = CommandRepository(database.commandDao())
    private val prefsRepository = BatteryPreferencesRepository(application)

    private val batteryMonitor = BatteryMonitorManager(application, viewModelScope)
    private val shizukuController = ShizukuController(application)
    private val termuxController = TermuxController(application)
    private val localShellExecutor = LocalShellExecutor()
    private val torchEcoController = TorchEcoController(application)
    private val batteryAiRepository: BatteryAiRepository = OnDeviceBatteryIntelligenceEngine(application)

    val batteryTelemetry: StateFlow<BatteryTelemetry> = batteryMonitor.telemetry
    val batteryHistory: StateFlow<List<BatteryHistoryPoint>> = batteryMonitor.history
    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuController.status
    val termuxStatus: StateFlow<TermuxStatus> = termuxController.status

    val termuxRunInBackground: StateFlow<Boolean> = prefsRepository.termuxRunInBackground
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _lastAutomationTriggerLabel = MutableStateFlow<String?>(null)
    val automationRule: StateFlow<BatteryAutomationRule> = combine(
        prefsRepository.automationRule,
        _lastAutomationTriggerLabel
    ) { rule, triggerLabel ->
        rule.copy(lastTriggeredLabel = triggerLabel)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BatteryAutomationRule())

    val chargeProtection: StateFlow<ChargeProtectionConfig> = combine(
        prefsRepository.chargeProtection,
        batteryTelemetry
    ) { config, telemetry ->
        val limitHit = config.enabled && telemetry.isCharging && telemetry.level >= config.limitPercent
        config.copy(alertActive = limitHit)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChargeProtectionConfig())

    private val _powerProfileStatus = MutableStateFlow(PowerProfileStatus())
    val powerProfileStatus: StateFlow<PowerProfileStatus> = _powerProfileStatus.asStateFlow()

    val customFunctions: StateFlow<List<SystemFunctionPreset>> = commandRepository.customFunctions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFunctions: StateFlow<List<SystemFunctionPreset>> = combine(
        customFunctions,
        MutableStateFlow(BuiltInFunctionsCatalog.presets)
    ) { custom, builtIn ->
        custom + builtIn
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BuiltInFunctionsCatalog.presets)

    val executionLogs: StateFlow<List<EngineExecutionResult>> = commandRepository.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedEngine = MutableStateFlow(CommandEngine.BOTH)
    val selectedEngine: StateFlow<CommandEngine> = _selectedEngine.asStateFlow()

    private val _commandInput = MutableStateFlow("id && uname -a")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FunctionCategory.ALL)
    val selectedCategory: StateFlow<FunctionCategory> = _selectedCategory.asStateFlow()

    private val _uiNotice = MutableStateFlow<String?>(null)
    val uiNotice: StateFlow<String?> = _uiNotice.asStateFlow()

    private val _pendingDangerousCommand = MutableStateFlow<Pair<CommandEngine, String>?>(null)
    val pendingDangerousCommand: StateFlow<Pair<CommandEngine, String>?> =
        _pendingDangerousCommand.asStateFlow()

    private val _termuxBridgeToken = MutableStateFlow("")
    val termuxBridgeSetupCommand: StateFlow<String> = _termuxBridgeToken
        .map { token ->
            if (token.isBlank()) "" else TermuxController.buildLocalBridgeSetupCommand(token)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _isOptimizingBattery = MutableStateFlow(false)
    val isOptimizingBattery: StateFlow<Boolean> = _isOptimizingBattery.asStateFlow()

    private val _autoGuardianState = MutableStateFlow(AutoGuardianState())
    val autoGuardianState: StateFlow<AutoGuardianState> = _autoGuardianState.asStateFlow()

    private val _smartSavingStage = MutableStateFlow(SmartSavingStage.OFF)
    private val _smartSavingLastAction = MutableStateFlow<String?>(null)
    private val _standbyLastEvent = MutableStateFlow<String?>(null)
    private val _bluetoothHardwareOn = MutableStateFlow(false)
    private val _bluetoothEcoLastAction = MutableStateFlow<String?>(null)
    private val smartCoreFlow = combine(
        prefsRepository.smartSavingEnabled,
        _smartSavingStage,
        _smartSavingLastAction,
        prefsRepository.smartStandbySavingEnabled,
        _standbyLastEvent
    ) { enabled, stage, lastAction, standbyEnabled, standbyEvent ->
        SmartSavingState(
            enabled = enabled,
            stage = if (enabled) stage else SmartSavingStage.OFF,
            lastActionLabel = lastAction,
            standbyEnabled = standbyEnabled,
            standbyLastEventLabel = standbyEvent
        )
    }
    private val smartWithBluetoothFlow = combine(
        smartCoreFlow,
        prefsRepository.bluetoothEcoEnabled,
        _bluetoothHardwareOn,
        _bluetoothEcoLastAction
    ) { core, btEcoEnabled, btHwOn, btLastAction ->
        core.copy(
            bluetoothEcoEnabled = btEcoEnabled,
            bluetoothHardwareOn = btHwOn,
            bluetoothEcoLastActionLabel = btLastAction
        )
    }
    private data class AppGameEcoSummary(
        val detectedAppsCount: Int = 0,
        val detectedGamesCount: Int = 0,
        val foregroundPackage: String? = null,
        val lastActionLabel: String? = null,
        val frozenCount: Int = 0,
        val freezeLastActionLabel: String? = null
    )

    private val _appGameEcoSummary = MutableStateFlow(AppGameEcoSummary())

    private val smartWithTorchFlow = combine(
        smartWithBluetoothFlow,
        prefsRepository.torchEcoEnabled,
        torchEcoController.status
    ) { state, torchEcoEnabled, torchStatus ->
        state.copy(
            torchEcoEnabled = torchEcoEnabled,
            torchHardwareOn = torchStatus.isTorchOn,
            torchSupportsDimming = torchStatus.supportsVariableBrightness,
            torchLevelLabel = torchStatus.levelLabel,
            torchEcoLastActionLabel = torchStatus.lastActionLabel
        )
    }

    val smartSavingState: StateFlow<SmartSavingState> = combine(
        smartWithTorchFlow,
        prefsRepository.appGameEcoEnabled,
        prefsRepository.appFreezeEcoEnabled,
        _appGameEcoSummary
    ) { state, appGameEcoEnabled, appFreezeEcoEnabled, summary ->
        state.copy(
            appGameEcoEnabled = appGameEcoEnabled,
            detectedAppsCount = summary.detectedAppsCount,
            detectedGamesCount = summary.detectedGamesCount,
            foregroundPackageLabel = summary.foregroundPackage,
            appGameEcoLastActionLabel = summary.lastActionLabel,
            appFreezeEcoEnabled = appFreezeEcoEnabled,
            frozenPackagesCount = summary.frozenCount,
            appFreezeLastActionLabel = summary.freezeLastActionLabel
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SmartSavingState())

    private var lastAutomationTriggeredMs: Long = 0L
    private var lastChargeAlertLevel: Int = -1
    private var lastAppliedSmartStage: SmartSavingStage = SmartSavingStage.OFF
    private var lastAutoGuardianRunMs: Long = 0L
    private var manualNormalOverrideUntilMs: Long = 0L
    private var appEnabledLowPower: Boolean = false
    private var standbyReceiverRegistered = false
    private var lastDetectedPackages: List<DetectedPackageInfo> = emptyList()
    private val appliedAppBuckets = mutableMapOf<String, String>()
    private val optimizedGamePackages = mutableSetOf<String>()
    private val frozenPackages = mutableSetOf<String>()
    private var freezerSystemEnabled: Boolean = false
    private var sessionStabilityRepairDone: Boolean = false
    private var lastAppGameScanMs: Long = 0L
    private val autoCycleMutex = Mutex()
    private val packageLabelCache = ConcurrentHashMap<String, String>()
    private val packageGameCache = ConcurrentHashMap<String, Boolean>()

    private val standbyScreenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val standbyOn = smartSavingState.value.standbyEnabled
            val btEcoOn = smartSavingState.value.bluetoothEcoEnabled
            val freezeOn = smartSavingState.value.appFreezeEcoEnabled
            val aiOn = _autoGuardianState.value.enabled
            if (!standbyOn && !btEcoOn && !freezeOn && !aiOn) return
            if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    viewModelScope.launch(Dispatchers.IO) {
                        val cmds = buildList {
                            if (standbyOn || aiOn) {
                                add("cmd deviceidle force-idle light")
                                if (standbyOn) {
                                    add("settings put system peak_refresh_rate 60.0")
                                }
                            }
                            if (btEcoOn) {
                                add("settings put global ble_scan_always_enabled 0")
                            }
                        }
                        if (cmds.isNotEmpty()) {
                            val res = shizukuController.executeCommand(cmds.joinToString("; "))
                            if (res.exitCode == 0) {
                                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                if (standbyOn) {
                                    _standbyLastEvent.value = "Doze ligero en espera ($timeFmt)"
                                }
                                if (btEcoOn) {
                                    _bluetoothEcoLastAction.value = "BLE reducido en reposo ($timeFmt)"
                                }
                            }
                        }
                        if (freezeOn || aiOn) {
                            runAppGameEcoCycle(force = false, allowMakeUidIdle = true)
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    viewModelScope.launch(Dispatchers.IO) {
                        if (standbyOn) {
                            val targetHz = if (smartSavingState.value.stage == SmartSavingStage.ECO_60HZ ||
                                smartSavingState.value.stage == SmartSavingStage.CRITICAL_SAVER ||
                                powerProfileStatus.value.lowPowerEnabled
                            ) "60.0" else "120.0"
                            val res = shizukuController.executeCommand(
                                "cmd deviceidle unforce; settings put system peak_refresh_rate $targetHz"
                            )
                            if (res.exitCode == 0) {
                                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                                _standbyLastEvent.value = "Despierto • $targetHz Hz ($timeFmt)"
                            }
                        } else if (aiOn) {
                            shizukuController.executeCommand("cmd deviceidle unforce")
                        }
                    }
                }
            }
        }
    }

    init {
        batteryMonitor.start()
        shizukuController.register()
        torchEcoController.register()
        termuxController.refreshStatus(true)
        refreshPowerProfileFromDevice()
        registerStandbyReceiver()

        viewModelScope.launch {
            combine(
                prefsRepository.torchEcoEnabled,
                batteryTelemetry.map { t -> !t.isCharging && (t.level in 1..20 || t.temperatureCelsius >= 39f) }.distinctUntilChanged()
            ) { torchEcoOn, hotOrLow ->
                torchEcoOn to hotOrLow
            }.collect { (torchEcoOn, hotOrLow) ->
                torchEcoController.updatePolicy(
                    enabled = torchEcoOn,
                    highDrainOrHot = hotOrLow
                )
            }
        }

        viewModelScope.launch {
            torchEcoController.status
                .map { it.isTorchOn }
                .distinctUntilChanged()
                .collect { torchOn ->
                    if (torchOn &&
                        smartSavingState.value.torchEcoEnabled &&
                        shizukuStatus.value.health == ConnectionHealth.CONNECTED
                    ) {
                        shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_TORCH_ECO_COMPENSATION)
                    }
                }
        }

        viewModelScope.launch {
            val token = prefsRepository.getOrCreateTermuxBridgeToken()
            _termuxBridgeToken.value = token
            termuxController.refreshStatusAsync(
                runInBackground = termuxRunInBackground.value,
                bridgeToken = token
            )
        }

        viewModelScope.launch {
            termuxRunInBackground.collect { bg ->
                termuxController.refreshStatusAsync(
                    runInBackground = bg,
                    bridgeToken = _termuxBridgeToken.value
                )
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            shizukuStatus
                .map { it.health == ConnectionHealth.CONNECTED }
                .distinctUntilChanged()
                .collect { connected ->
                    if (connected) {
                        if (!prefsRepository.isStabilityRepairV1Done() ||
                            (!sessionStabilityRepairDone && _autoGuardianState.value.autoSolverEnabled)
                        ) {
                            val repairRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_REPAIR_SYSTEM_STABILITY)
                            if (repairRes.exitCode == 0) {
                                sessionStabilityRepairDone = true
                                prefsRepository.markStabilityRepairV1Done()
                                appliedAppBuckets.clear()
                                optimizedGamePackages.clear()
                                frozenPackages.clear()
                                freezerSystemEnabled = false
                                commandRepository.recordExecution(repairRes)
                                _autoGuardianState.value = _autoGuardianState.value.copy(
                                    frozenCount = 0,
                                    solvedIssuesCount = _autoGuardianState.value.solvedIssuesCount + 1,
                                    lastProblemSolved = "Sistema, batería y apps estabilizados en automático"
                                )
                            }
                        }
                        syncRealPowerProfileViaShizuku()
                        shizukuController.executeCommand(
                            "cmd deviceidle whitelist +${getApplication<Application>().packageName} 2>/dev/null"
                        )
                        if (termuxStatus.value.isInstalled && !termuxStatus.value.hasRunCommandPermission) {
                            grantTermuxPermissionViaShizuku(silent = true)
                        }
                        if (smartSavingState.value.bluetoothEcoEnabled) {
                            applyBluetoothEcoInBackground()
                        }
                        if (!_autoGuardianState.value.enabled &&
                            (smartSavingState.value.appGameEcoEnabled || smartSavingState.value.appFreezeEcoEnabled)
                        ) {
                            runAppGameEcoCycle(force = true)
                        }
                    } else {
                        sessionStabilityRepairDone = false
                        refreshPowerProfileFromDevice()
                    }
                }
        }

        viewModelScope.launch {
            combine(batteryTelemetry, automationRule) { telemetry, rule ->
                telemetry to rule
            }.collect { (telemetry, rule) ->
                evaluateAutomationTrigger(telemetry, rule)
            }
        }

        viewModelScope.launch {
            combine(batteryTelemetry, prefsRepository.chargeProtection) { telemetry, protection ->
                telemetry to protection
            }.collect { (telemetry, protection) ->
                if (protection.enabled && telemetry.isCharging && telemetry.level >= protection.limitPercent) {
                    if (lastChargeAlertLevel != telemetry.level) {
                        lastChargeAlertLevel = telemetry.level
                        _uiNotice.value =
                            "Protección de batería (${protection.limitPercent}%): desconecta el cargador (${telemetry.level}%)."
                    }
                } else {
                    lastChargeAlertLevel = -1
                }
            }
        }

        viewModelScope.launch {
            combine(batteryTelemetry, prefsRepository.smartSavingEnabled) { telemetry, enabled ->
                telemetry to enabled
            }.collect { (telemetry, enabled) ->
                evaluateSmartSaving(telemetry, enabled)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(
                prefsRepository.autoGuardianEnabled,
                prefsRepository.autoSolverEnabled,
                shizukuStatus.map { it.health == ConnectionHealth.CONNECTED }.distinctUntilChanged()
            ) { enabled, autoSolver, shizukuReady ->
                Triple(enabled, autoSolver, shizukuReady)
            }.collect { (enabled, autoSolver, shizukuReady) ->
                _autoGuardianState.value = _autoGuardianState.value.copy(
                    enabled = enabled,
                    autoSolverEnabled = autoSolver,
                    shizukuReady = shizukuReady
                )
                if ((enabled || autoSolver) && shizukuReady) {
                    runAutoGuardianCycle(force = true)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(60_000L)
                val status = shizukuStatus.value
                val guardian = _autoGuardianState.value
                if (guardian.autoSolverEnabled &&
                    status.isInstalled &&
                    status.health == ConnectionHealth.CONNECTED &&
                    !status.isUserServiceBound
                ) {
                    shizukuController.bindServiceIfNeeded()
                }
                if ((guardian.enabled || guardian.autoSolverEnabled) &&
                    shizukuStatus.value.health == ConnectionHealth.CONNECTED
                ) {
                    runAutoGuardianCycle(force = false)
                }
            }
        }
    }

    private fun buildLocalPowerStatus(): PowerProfileStatus {
        val app = getApplication<Application>()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val fullLabel = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Dispositivo Android" }
        val isMotoG82 = model.contains("g82", ignoreCase = true) ||
            device.equals("rhodep", ignoreCase = true)

        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lowPower = powerManager?.isPowerSaveMode ?: false

        val displayManager = app.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val refreshRate = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            ?.refreshRate?.roundToInt() ?: 60

        val profile = if (lowPower) {
            SamsungPowerProfile.SAMSUNG_SAVING
        } else {
            SamsungPowerProfile.NORMAL_120HZ
        }

        return PowerProfileStatus(
            deviceLabel = if (device.isNotBlank()) "$fullLabel ($device)" else fullLabel,
            isMotoG82 = isMotoG82,
            lowPowerEnabled = lowPower,
            peakRefreshRateHz = refreshRate,
            backgroundRestricted = false,
            aodDisabled = false,
            isSyncedFromSystem = false,
            activeProfile = profile
        )
    }

    private fun isScreenInteractive(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive ?: true
    }

    private fun readBluetoothHardwareOn(): Boolean {
        val app = getApplication<Application>()
        return runCatching {
            Settings.Global.getInt(app.contentResolver, Settings.Global.BLUETOOTH_ON, 0) == 1
        }.getOrDefault(false)
    }

    fun refreshPowerProfileFromDevice() {
        viewModelScope.launch(Dispatchers.IO) {
            val base = buildLocalPowerStatus()
            _powerProfileStatus.value = base
            _bluetoothHardwareOn.value = readBluetoothHardwareOn()
        }
    }

    fun syncRealPowerProfileViaShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) {
                val base = buildLocalPowerStatus()
                _powerProfileStatus.value = base
                _bluetoothHardwareOn.value = readBluetoothHardwareOn()
                return@launch
            }
            val res = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_QUERY_POWER_STATE)
            if (res.exitCode == 0) {
                applyParsedPowerOutput(res.stdout)
            }
        }
    }

    private fun applyParsedPowerOutput(rawOutput: String) {
        val tokens = rawOutput.replace('\n', ' ').split(Regex("\\s+")).filter { it.contains("=") }
        fun readFlag(key: String): String? =
            tokens.firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()
                ?: rawOutput.lines().firstOrNull { it.startsWith("$key=") }?.substringAfter("=")?.trim()

        val lowPower = readFlag("LOW_POWER") == "1"
        val base = buildLocalPowerStatus()
        val peakHz = readFlag("PEAK_HZ")?.toFloatOrNull()?.roundToInt() ?: base.peakRefreshRateHz
        val aodOff = readFlag("AOD") == "0"
        val forcedStandby = readFlag("FORCED_STANDBY") == "1"
        val btOn = readFlag("BT_ON") == "1"
        _bluetoothHardwareOn.value = btOn
        if (!lowPower) {
            appEnabledLowPower = false
        }

        val profile = when {
            lowPower && _powerProfileStatus.value.activeProfile == SamsungPowerProfile.SAMSUNG_ULTRA ->
                SamsungPowerProfile.SAMSUNG_ULTRA
            lowPower -> SamsungPowerProfile.SAMSUNG_SAVING
            else -> SamsungPowerProfile.NORMAL_120HZ
        }
        _powerProfileStatus.value = base.copy(
            lowPowerEnabled = lowPower,
            peakRefreshRateHz = peakHz,
            backgroundRestricted = forcedStandby,
            aodDisabled = aodOff,
            isSyncedFromSystem = true,
            activeProfile = profile
        )
    }

    fun optimizePhoneBattery() {
        viewModelScope.launch {
            if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) {
                _uiNotice.value =
                    "Autoriza Shizuku para optimizar procesos y batería del sistema, o usa Ajustes de Batería."
                return@launch
            }
            _isOptimizingBattery.value = true
            try {
                val res = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_OPTIMIZE_PHONE_BATTERY)
                commandRepository.recordExecution(res)
                batteryMonitor.refreshNow()
                _uiNotice.value = if (res.exitCode == 0) {
                    "Batería del teléfono optimizada (caché y procesos limpiados)."
                } else {
                    "Optimización finalizada con advertencias; revisa la Consola."
                }
            } finally {
                _isOptimizingBattery.value = false
            }
        }
    }

    fun toggleAutoGuardian() {
        val next = !_autoGuardianState.value.enabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setAutoGuardianEnabled(next)
            _autoGuardianState.value = _autoGuardianState.value.copy(enabled = next)
            if (next) {
                runAutonomousFullActivationAndRepair()
            } else {
                _uiNotice.value = "IA Solución Total pausada."
            }
        }
    }

    fun toggleAutoSolver() {
        val next = !_autoGuardianState.value.autoSolverEnabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setAutoSolverEnabled(next)
            _autoGuardianState.value = _autoGuardianState.value.copy(autoSolverEnabled = next)
            if (next) {
                runAutonomousFullActivationAndRepair()
                _uiNotice.value = "Auto-Solución de batería, problemas y errores activa en automático."
            } else {
                _uiNotice.value = "Auto-Solución automática pausada."
            }
        }
    }

    private suspend fun runAutonomousFullActivationAndRepair() {
        prefsRepository.setSmartSavingEnabled(true)
        prefsRepository.setSmartStandbySavingEnabled(true)
        prefsRepository.setBluetoothEcoEnabled(true)
        prefsRepository.setTorchEcoEnabled(true)
        prefsRepository.setAppGameEcoEnabled(true)

        val t = batteryTelemetry.value
        torchEcoController.updatePolicy(
            enabled = true,
            highDrainOrHot = !t.isCharging && (t.level in 1..20 || t.temperatureCelsius >= 39f)
        )
        evaluateSmartSaving(t, enabled = true)

        if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) return

        val solveRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_AUTO_SOLVE_ALL)
        commandRepository.recordExecution(solveRes)
        if (!sessionStabilityRepairDone) {
            val repairRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_REPAIR_SYSTEM_STABILITY)
            if (repairRes.exitCode == 0) {
                sessionStabilityRepairDone = true
                prefsRepository.markStabilityRepairV1Done()
                appliedAppBuckets.clear()
                optimizedGamePackages.clear()
                frozenPackages.clear()
                freezerSystemEnabled = false
                commandRepository.recordExecution(repairRes)
            }
        }
        if (termuxStatus.value.isInstalled && !termuxStatus.value.hasRunCommandPermission) {
            grantTermuxPermissionViaShizuku(silent = true)
        }
        applyBluetoothEcoInBackground()
        lastAppGameScanMs = 0L
        runAppGameEcoCycle(force = true, allowMakeUidIdle = !isScreenInteractive())
        runAutoGuardianCycle(force = true)
        syncRealPowerProfileViaShizuku()
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _standbyLastEvent.value = "Vigilando reposo ($timeFmt)"
    }

    private suspend fun runAutoGuardianCycle(force: Boolean) = autoCycleMutex.withLock {
        val state = _autoGuardianState.value
        if ((!state.enabled && !state.autoSolverEnabled) || state.isWorking) return@withLock
        if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) return@withLock

        val now = System.currentTimeMillis()
        if (!force && now - lastAutoGuardianRunMs < 45_000L) return@withLock
        lastAutoGuardianRunMs = now

        _autoGuardianState.value = _autoGuardianState.value.copy(isWorking = true)
        try {
            var recoveredErrorText: String? = _autoGuardianState.value.lastErrorRecovered
            var solvedCount = _autoGuardianState.value.solvedIssuesCount
            var lastSolved = _autoGuardianState.value.lastProblemSolved

            var inspectRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_INSPECT_POWER_FAST)
            if (inspectRes.exitCode != 0 && state.autoSolverEnabled) {
                // Auto-recover Shizuku binder/service errors before retrying once
                shizukuController.bindServiceIfNeeded()
                shizukuController.refreshStatusAsync()
                inspectRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_INSPECT_POWER_FAST)
                if (inspectRes.exitCode == 0) {
                    solvedCount += 1
                    recoveredErrorText = "Servicio Shizuku re-enlazado automáticamente"
                }
            }

            val evidence = listOf(inspectRes.stdout, inspectRes.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            if (inspectRes.exitCode == 0 && inspectRes.stdout.isNotBlank()) {
                applyParsedPowerOutput(inspectRes.stdout)
            }

            if (state.autoSolverEnabled && termuxStatus.value.isInstalled && !termuxStatus.value.hasRunCommandPermission) {
                if (grantTermuxPermissionViaShizuku(silent = true)) {
                    solvedCount += 1
                    recoveredErrorText = "Permisos de Termux reparados en automático"
                }
            }

            val allowHzChange = now >= manualNormalOverrideUntilMs
            val interactive = isScreenInteractive()
            val lightIdleActive = evidence.contains("LIGHT_IDLE") || evidence.contains("DOZE_LIGHT=IDLE")
            val currentSmart = smartSavingState.value
            val plan = batteryAiRepository.buildAutoGuardianPlan(
                request = BatteryDrainAnalysisRequest(
                    telemetry = batteryTelemetry.value,
                    history = batteryHistory.value,
                    powerStatus = powerProfileStatus.value,
                    shizukuConnected = true,
                    rawSystemEvidence = evidence,
                    bluetoothEcoEnabled = currentSmart.bluetoothEcoEnabled,
                    torchEcoEnabled = currentSmart.torchEcoEnabled,
                    torchActive = torchEcoController.status.value.isTorchOn,
                    appGameEcoEnabled = currentSmart.appGameEcoEnabled,
                    appFreezeEcoEnabled = currentSmart.appFreezeEcoEnabled,
                    isScreenInteractive = interactive,
                    freezerSystemEnabled = freezerSystemEnabled,
                    deviceIdleLightActive = lightIdleActive,
                    autoSolverEnabled = state.autoSolverEnabled
                ),
                allowRefreshRateChange = allowHzChange
            )

            var newCount = _autoGuardianState.value.actionsAppliedCount
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val cmd = plan.commandToExecute
            if (cmd != null) {
                val hadSystemLowPower = withContext(Dispatchers.IO) { buildLocalPowerStatus().lowPowerEnabled }
                val execRes = shizukuController.executeCommand(cmd)
                if (execRes.exitCode == 0) {
                    if (!hadSystemLowPower && cmd.contains("cmd power set-mode 1")) {
                        appEnabledLowPower = true
                    }
                    newCount += 1
                    if (plan.solvedProblems.isNotEmpty()) {
                        solvedCount += plan.solvedProblems.size
                        lastSolved = plan.solvedProblems.joinToString(" • ") + " ($timeFmt)"
                    }
                    commandRepository.recordExecution(execRes)
                } else if (state.autoSolverEnabled) {
                    // Self-heal failed optimization batch with safe baseline recovery command
                    val fallbackRes = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_AUTO_SOLVE_ALL)
                    if (fallbackRes.exitCode == 0) {
                        solvedCount += 1
                        recoveredErrorText = "Error de comando recuperado automáticamente ($timeFmt)"
                        commandRepository.recordExecution(fallbackRes)
                    } else {
                        recoveredErrorText = "Error detectado y contenido sin bloquear la UI ($timeFmt)"
                    }
                }
            }

            if (currentSmart.appGameEcoEnabled || currentSmart.appFreezeEcoEnabled) {
                runAppGameEcoCycle(
                    force = false,
                    highDrainPackages = (batteryAiRepository as? OnDeviceBatteryIntelligenceEngine)
                        ?.extractHighDrainThirdPartyPackages(evidence)
                        .orEmpty(),
                    allowMakeUidIdle = !interactive
                )
            }

            val latestSummary = _appGameEcoSummary.value
            _autoGuardianState.value = _autoGuardianState.value.copy(
                isWorking = false,
                shizukuReady = true,
                severity = plan.severity,
                activeFunctionsSummary = plan.activeFeaturesLabel,
                lastActionSummary = "${plan.summaryLabel} ($timeFmt)",
                lastProblemSolved = lastSolved,
                lastErrorRecovered = recoveredErrorText,
                actionsAppliedCount = newCount,
                solvedIssuesCount = solvedCount,
                detectedProblemsCount = plan.solvedProblems.size,
                frozenCount = frozenPackages.size,
                detectedAppsAndGamesCount = latestSummary.detectedAppsCount + latestSummary.detectedGamesCount
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _autoGuardianState.value = _autoGuardianState.value.copy(
                isWorking = false,
                lastErrorRecovered = "Excepción controlada: ${t.message?.take(48) ?: "recuperada"} ($timeFmt)"
            )
        }
    }

    fun toggleSmartSaving() {
        val next = !smartSavingState.value.enabled
        viewModelScope.launch {
            prefsRepository.setSmartSavingEnabled(next)
            if (!next) {
                lastAppliedSmartStage = SmartSavingStage.OFF
                _smartSavingStage.value = SmartSavingStage.OFF
                _uiNotice.value = "Ahorro inteligente desactivado."
            } else {
                _uiNotice.value = "Ahorro inteligente activado."
                evaluateSmartSaving(batteryTelemetry.value, enabled = true)
            }
        }
    }

    fun toggleSmartStandbySaving() {
        val next = !smartSavingState.value.standbyEnabled
        viewModelScope.launch {
            prefsRepository.setSmartStandbySavingEnabled(next)
            if (shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                val cmd = if (next) {
                    BuiltInFunctionsCatalog.CMD_SMART_STANDBY_ON
                } else {
                    BuiltInFunctionsCatalog.CMD_SMART_STANDBY_OFF
                }
                val res = shizukuController.executeCommand(cmd)
                commandRepository.recordExecution(res)
            }
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _standbyLastEvent.value = if (next) {
                "Vigilando reposo ($timeFmt)"
            } else {
                "Desactivado ($timeFmt)"
            }
            _uiNotice.value = if (next) {
                "Ahorro de energía inteligente en espera activado."
            } else {
                "Ahorro en espera desactivado."
            }
        }
    }

    fun toggleBluetoothEco() {
        val next = !smartSavingState.value.bluetoothEcoEnabled
        viewModelScope.launch {
            prefsRepository.setBluetoothEcoEnabled(next)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            if (shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                val cmd = if (next) {
                    BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_ON
                } else {
                    BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_OFF
                }
                val res = shizukuController.executeCommand(cmd)
                commandRepository.recordExecution(res)
                if (res.exitCode == 0) {
                    _bluetoothEcoLastAction.value = if (next) {
                        "BLE limitado • Bluetooth ON ($timeFmt)"
                    } else {
                        "Escaneo normal • Bluetooth ON ($timeFmt)"
                    }
                }
            } else {
                _bluetoothEcoLastAction.value = if (next) {
                    "Listo en automático ($timeFmt)"
                } else {
                    "Desactivado ($timeFmt)"
                }
            }
            _uiNotice.value = if (next) {
                "Ahorro Bluetooth activado: reduce escaneo BLE sin apagar tu Bluetooth."
            } else {
                "Ahorro Bluetooth desactivado (tu Bluetooth sigue intacto)."
            }
        }
    }

    private suspend fun applyBluetoothEcoInBackground() {
        val res = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_BLUETOOTH_ECO_ON)
        if (res.exitCode == 0) {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _bluetoothEcoLastAction.value = "BLE reducido en auto ($timeFmt)"
            _bluetoothHardwareOn.value = readBluetoothHardwareOn()
        }
    }

    fun toggleTorchEco() {
        val next = !smartSavingState.value.torchEcoEnabled
        val t = batteryTelemetry.value
        val hotOrLow = !t.isCharging && (t.level in 1..20 || t.temperatureCelsius >= 39f)
        viewModelScope.launch {
            prefsRepository.setTorchEcoEnabled(next)
            val status = torchEcoController.updatePolicy(
                enabled = next,
                highDrainOrHot = hotOrLow
            )
            if (next && status.isTorchOn && shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                val res = shizukuController.executeCommand(BuiltInFunctionsCatalog.CMD_TORCH_ECO_COMPENSATION)
                commandRepository.recordExecution(res)
            }
            _uiNotice.value = if (next) {
                "Ahorro Linterna activado: atenúa/compensa el LED automáticamente sin apagar tu linterna."
            } else {
                "Ahorro Linterna desactivado (tu linterna sigue intacta)."
            }
        }
    }

    fun toggleAppGameEco() {
        val next = !smartSavingState.value.appGameEcoEnabled
        viewModelScope.launch {
            prefsRepository.setAppGameEcoEnabled(next)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            if (next) {
                lastAppGameScanMs = 0L
                _uiNotice.value = "Ahorro automático para apps y juegos activado."
                runAppGameEcoCycle(force = true)
            } else {
                if (shizukuStatus.value.health == ConnectionHealth.CONNECTED && lastDetectedPackages.isNotEmpty()) {
                    val restoreCmd = AppGameOptimizerEngine.buildRestoreAllCommand(lastDetectedPackages)
                    val res = shizukuController.executeCommand(restoreCmd)
                    commandRepository.recordExecution(res)
                }
                appliedAppBuckets.clear()
                optimizedGamePackages.clear()
                if (!smartSavingState.value.appFreezeEcoEnabled) {
                    frozenPackages.clear()
                }
                _appGameEcoSummary.value = _appGameEcoSummary.value.copy(
                    lastActionLabel = "Modo estándar restaurado ($timeFmt)",
                    frozenCount = frozenPackages.size
                )
                _uiNotice.value = "Ahorro de apps y juegos pausado (ajustes restaurados)."
            }
        }
    }

    fun toggleAppFreezeEco() {
        val next = !smartSavingState.value.appFreezeEcoEnabled
        viewModelScope.launch(Dispatchers.IO) {
            prefsRepository.setAppFreezeEcoEnabled(next)
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            if (next) {
                lastAppGameScanMs = 0L
                _uiNotice.value = "Congelamiento IA activado para todas las apps y juegos."
                runAppGameEcoCycle(force = true, allowMakeUidIdle = false)
            } else {
                if (shizukuStatus.value.health == ConnectionHealth.CONNECTED && lastDetectedPackages.isNotEmpty()) {
                    val unfreezeCmd = AppGameOptimizerEngine.buildUnfreezeAllCommand(lastDetectedPackages)
                    val res = shizukuController.executeCommand(unfreezeCmd)
                    commandRepository.recordExecution(res)
                }
                frozenPackages.clear()
                freezerSystemEnabled = false
                _appGameEcoSummary.value = _appGameEcoSummary.value.copy(
                    frozenCount = 0,
                    freezeLastActionLabel = "Descongeladas a modo activo ($timeFmt)"
                )
                _autoGuardianState.value = _autoGuardianState.value.copy(frozenCount = 0)
                _uiNotice.value = "Congelamiento IA pausado (todas las apps descongeladas)."
            }
        }
    }

    private suspend fun runAppGameEcoCycle(
        force: Boolean,
        highDrainPackages: List<String> = emptyList(),
        allowMakeUidIdle: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        val freezeOn = smartSavingState.value.appFreezeEcoEnabled
        val appGameOn = smartSavingState.value.appGameEcoEnabled
        if (!appGameOn && !freezeOn) return@withContext
        if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) return@withContext

        val now = System.currentTimeMillis()
        if (!force && now - lastAppGameScanMs < 45_000L) return@withContext
        if (force && now - lastAppGameScanMs < 8_000L) return@withContext
        lastAppGameScanMs = now

        val discoverRes = shizukuController.executeCommand(AppGameOptimizerEngine.DISCOVERY_COMMAND)
        if (discoverRes.exitCode != 0 && discoverRes.stdout.isBlank()) return@withContext

        val rawDiscovered = AppGameOptimizerEngine.parseDiscoveredPackages(discoverRes.stdout)
        val discovered = enrichGameFlagsViaPackageManager(rawDiscovered)
        if (discovered.isNotEmpty()) {
            lastDetectedPackages = discovered
        }

        val telemetry = batteryTelemetry.value
        val plan = AppGameOptimizerEngine.buildAutoOptimizationPlan(
            packages = discovered,
            appliedAppBuckets = appliedAppBuckets,
            alreadyOptimizedGames = optimizedGamePackages,
            batteryLevel = telemetry.level,
            temperatureCelsius = telemetry.temperatureCelsius,
            isCharging = telemetry.isCharging,
            highDrainPackages = highDrainPackages,
            freezeAppsAndGames = freezeOn,
            alreadyFrozenPackages = frozenPackages,
            freezerSystemEnabled = freezerSystemEnabled,
            allowMakeUidIdle = allowMakeUidIdle ?: !isScreenInteractive()
        )

        val cmd = plan.commandToExecute
        if (cmd != null) {
            val execRes = shizukuController.executeCommand(cmd)
            if (execRes.exitCode == 0) {
                appliedAppBuckets.putAll(plan.targetedAppBuckets)
                optimizedGamePackages.addAll(plan.targetedGames)
                if (freezeOn) {
                    freezerSystemEnabled = true
                    plan.unfrozenForegroundPackage?.let { frozenPackages.remove(it) }
                    frozenPackages.addAll(plan.newlyFrozenPackages)
                }
                commandRepository.recordExecution(execRes)
            }
        }

        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val previousLabel = _appGameEcoSummary.value.lastActionLabel
        val nextLabel = when {
            plan.newlyOptimizedApps > 0 || plan.newlyOptimizedGames > 0 ->
                "${plan.summaryLabel} ($timeFmt)"
            previousLabel == null || previousLabel.startsWith("Modo estándar") ->
                "${plan.discoveredAppsCount} apps • ${plan.discoveredGamesCount} juegos protegidos ($timeFmt)"
            else -> previousLabel
        }
        val prevFreezeLabel = _appGameEcoSummary.value.freezeLastActionLabel
        val nextFreezeLabel = when {
            !freezeOn -> prevFreezeLabel
            plan.newlyFrozenPackages.isNotEmpty() ->
                "IA congeló +${plan.newlyFrozenPackages.size} (${frozenPackages.size} total • $timeFmt)"
            plan.unfrozenForegroundPackage != null ->
                "En uso abierta: ${resolvePackageLabelBlocking(plan.unfrozenForegroundPackage)} ($timeFmt)"
            prevFreezeLabel == null || prevFreezeLabel.startsWith("Descongeladas") ->
                "${frozenPackages.size} apps/juegos congelados ($timeFmt)"
            else -> prevFreezeLabel
        }

        val fgLabel = plan.foregroundPackage?.let { resolvePackageLabelBlocking(it) }
        _appGameEcoSummary.value = AppGameEcoSummary(
            detectedAppsCount = plan.discoveredAppsCount,
            detectedGamesCount = plan.discoveredGamesCount,
            foregroundPackage = fgLabel,
            lastActionLabel = nextLabel,
            frozenCount = frozenPackages.size,
            freezeLastActionLabel = nextFreezeLabel
        )
        _autoGuardianState.value = _autoGuardianState.value.copy(
            frozenCount = frozenPackages.size,
            detectedAppsAndGamesCount = plan.discoveredAppsCount + plan.discoveredGamesCount
        )
    }

    private fun enrichGameFlagsViaPackageManager(
        packages: List<DetectedPackageInfo>
    ): List<DetectedPackageInfo> {
        val pm = getApplication<Application>().packageManager
        return packages.map { info ->
            if (info.isGame) return@map info
            val cached = packageGameCache[info.packageName]
            val detectedGame = cached ?: runCatching {
                val appInfo = pm.getApplicationInfo(info.packageName, 0)
                val byCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    false
                }
                @Suppress("DEPRECATION")
                val byFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                byCategory || byFlag
            }.getOrDefault(false).also { result ->
                packageGameCache[info.packageName] = result
            }
            if (detectedGame) info.copy(isGame = true) else info
        }
    }

    private fun resolvePackageLabelBlocking(packageName: String): String {
        packageLabelCache[packageName]?.let { return it }
        val pm = getApplication<Application>().packageManager
        return runCatching {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        }.getOrElse {
            AppGameOptimizerEngine.formatPackageDisplayName(packageName)
        }.also { label ->
            packageLabelCache[packageName] = label
        }
    }

    private fun registerStandbyReceiver() {
        if (!standbyReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                getApplication(),
                standbyScreenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            standbyReceiverRegistered = true
        }
    }

    private suspend fun evaluateSmartSaving(telemetry: BatteryTelemetry, enabled: Boolean) {
        if (!enabled) {
            _smartSavingStage.value = SmartSavingStage.OFF
            lastAppliedSmartStage = SmartSavingStage.OFF
            return
        }

        val targetStage = when {
            telemetry.isCharging -> SmartSavingStage.CHARGING_NORMAL
            telemetry.level > 0 && (telemetry.level <= 20 || telemetry.temperatureCelsius >= 41f) ->
                SmartSavingStage.CRITICAL_SAVER
            telemetry.level in 1..44 -> SmartSavingStage.ECO_60HZ
            else -> SmartSavingStage.BALANCED_120HZ
        }
        _smartSavingStage.value = targetStage

        if (targetStage == lastAppliedSmartStage) return
        if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) return

        withContext(Dispatchers.IO) {
            val deviceLowPowerActive = buildLocalPowerStatus().lowPowerEnabled || powerProfileStatus.value.lowPowerEnabled
            val command = when (targetStage) {
                SmartSavingStage.CHARGING_NORMAL, SmartSavingStage.BALANCED_120HZ -> {
                    if (deviceLowPowerActive) {
                        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1"
                    } else {
                        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; settings put system peak_refresh_rate 120.0; settings put system min_refresh_rate 60.0"
                    }
                }
                SmartSavingStage.ECO_60HZ ->
                    BuiltInFunctionsCatalog.CMD_SMART_ECO_60HZ
                SmartSavingStage.CRITICAL_SAVER -> {
                    if (!deviceLowPowerActive) {
                        appEnabledLowPower = true
                    }
                    BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82
                }
                SmartSavingStage.OFF -> return@withContext
            }

            val result = shizukuController.executeCommand(command)
            if (result.exitCode == 0) {
                lastAppliedSmartStage = targetStage
                val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _smartSavingLastAction.value = "${targetStage.label} ($timeFmt)"
                syncRealPowerProfileViaShizuku()
            }
        }
    }

    fun applySamsungPowerProfile(profile: SamsungPowerProfile) {
        if (profile == SamsungPowerProfile.NORMAL_120HZ) {
            manualNormalOverrideUntilMs = System.currentTimeMillis() + 5 * 60_000L
        } else {
            manualNormalOverrideUntilMs = 0L
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) {
                _uiNotice.value =
                    "Inicia y autoriza Shizuku para aplicar ${profile.label} en el sistema, o usa Ajustes de Batería."
                return@launch
            }
            val wasSystemLowPowerActive = buildLocalPowerStatus().lowPowerEnabled
            val turnOffAppLowPower = profile == SamsungPowerProfile.NORMAL_120HZ && appEnabledLowPower
            val cmd = when (profile) {
                SamsungPowerProfile.SAMSUNG_SAVING -> BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82
                SamsungPowerProfile.SAMSUNG_ULTRA -> BuiltInFunctionsCatalog.CMD_SAMSUNG_ULTRA_MOTO_G82
                SamsungPowerProfile.NORMAL_120HZ -> {
                    if (turnOffAppLowPower) {
                        BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_WITH_POWER_OFF
                    } else {
                        BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_MOTO_G82
                    }
                }
            }
            _isExecuting.value = true
            try {
                val result = shizukuController.executeCommand(cmd)
                commandRepository.recordExecution(result)
                if (result.exitCode == 0) {
                    if (profile == SamsungPowerProfile.NORMAL_120HZ) {
                        if (turnOffAppLowPower) {
                            appEnabledLowPower = false
                        }
                    } else if (!wasSystemLowPowerActive) {
                        appEnabledLowPower = true
                    }
                    val preservedLowPower = if (profile != SamsungPowerProfile.NORMAL_120HZ) {
                        true
                    } else if (turnOffAppLowPower) {
                        false
                    } else {
                        wasSystemLowPowerActive
                    }
                    _powerProfileStatus.value = _powerProfileStatus.value.copy(
                        lowPowerEnabled = preservedLowPower,
                        peakRefreshRateHz = if (profile == SamsungPowerProfile.NORMAL_120HZ) 120 else 60,
                        backgroundRestricted = profile != SamsungPowerProfile.NORMAL_120HZ,
                        aodDisabled = profile != SamsungPowerProfile.NORMAL_120HZ,
                        isSyncedFromSystem = true,
                        activeProfile = profile
                    )
                    prefsRepository.setQsTileState(
                        active = profile != SamsungPowerProfile.NORMAL_120HZ,
                        enabledLowPower = appEnabledLowPower
                    )
                    BatterySaverTileService.requestTileRefresh(getApplication())
                    _uiNotice.value = if (profile == SamsungPowerProfile.NORMAL_120HZ && preservedLowPower) {
                        "120Hz restaurado sin desactivar el ahorro de batería de tu dispositivo."
                    } else {
                        "${profile.label} aplicado correctamente."
                    }
                } else {
                    _uiNotice.value = "Error al aplicar ${profile.label}: revisa la Consola."
                }
            } finally {
                _isExecuting.value = false
            }
        }
    }

    fun setRefreshRate60Hz(enable60Hz: Boolean) {
        if (!enable60Hz) {
            manualNormalOverrideUntilMs = System.currentTimeMillis() + 5 * 60_000L
        } else {
            manualNormalOverrideUntilMs = 0L
        }
        val cmd = if (enable60Hz) {
            "settings put system peak_refresh_rate 60.0 && settings put system min_refresh_rate 60.0 && echo 'OK: 60Hz'"
        } else {
            "settings put system peak_refresh_rate 120.0 && settings put system min_refresh_rate 60.0 && echo 'OK: 120Hz'"
        }
        viewModelScope.launch {
            if (shizukuStatus.value.health != ConnectionHealth.CONNECTED) {
                _uiNotice.value = "Autoriza Shizuku para cambiar entre 60Hz y 120Hz."
                return@launch
            }
            val result = shizukuController.executeCommand(cmd)
            commandRepository.recordExecution(result)
            if (result.exitCode == 0) {
                _powerProfileStatus.value = _powerProfileStatus.value.copy(
                    peakRefreshRateHz = if (enable60Hz) 60 else 120
                )
                _uiNotice.value = if (enable60Hz) {
                    "Pantalla fijada en 60Hz (ahorro pOLED)."
                } else {
                    "Pantalla restaurada a 120Hz."
                }
            } else {
                _uiNotice.value = "No se pudo cambiar la tasa de refresco."
            }
        }
    }

    fun updateChargeProtection(enabled: Boolean, limitPercent: Int) {
        viewModelScope.launch {
            prefsRepository.updateChargeProtection(enabled, limitPercent)
            _uiNotice.value = if (enabled) {
                "Protección de carga Samsung activada al $limitPercent%."
            } else {
                "Protección de carga desactivada."
            }
        }
    }

    fun openSystemBatterySaverSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            app.startActivity(intent)
            true
        }.getOrDefault(false)
        if (!launched) {
            _uiNotice.value = "No se pudo abrir los ajustes de ahorro del sistema."
        }
    }

    private suspend fun evaluateAutomationTrigger(
        telemetry: BatteryTelemetry,
        rule: BatteryAutomationRule
    ) {
        if (!rule.enabled || rule.command.isBlank() || telemetry.level <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastAutomationTriggeredMs < 60_000L) return

        val lowBatteryHit = telemetry.level <= rule.lowBatteryPercent && !telemetry.isCharging
        val highTempHit = telemetry.temperatureCelsius >= rule.highTempCelsius
        if (!lowBatteryHit && !highTempHit) return

        lastAutomationTriggeredMs = now
        val reason = if (highTempHit) {
            "Temp ${String.format(Locale.US, "%.1f", telemetry.temperatureCelsius)}°C"
        } else {
            "Batería ${telemetry.level}%"
        }
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        _lastAutomationTriggerLabel.value = "$reason ($timeFmt)"
        runCommandInternal(rule.targetEngine, rule.command)
    }

    fun refreshAllStatuses() {
        viewModelScope.launch(Dispatchers.IO) {
            batteryMonitor.refreshNow()
            shizukuController.refreshStatusAsync()
            val token = _termuxBridgeToken.value.ifBlank {
                prefsRepository.getOrCreateTermuxBridgeToken().also { _termuxBridgeToken.value = it }
            }
            termuxController.refreshStatusAsync(
                runInBackground = termuxRunInBackground.value,
                bridgeToken = token
            )
            if (shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                syncRealPowerProfileViaShizuku()
            } else {
                refreshPowerProfileFromDevice()
            }
        }
    }

    fun verifyTermuxLocalBridge() {
        viewModelScope.launch {
            val token = _termuxBridgeToken.value.ifBlank {
                prefsRepository.getOrCreateTermuxBridgeToken().also { _termuxBridgeToken.value = it }
            }
            termuxController.refreshStatusAsync(
                runInBackground = termuxRunInBackground.value,
                bridgeToken = token
            )
            _uiNotice.value = if (termuxStatus.value.isLocalBridgeOnline) {
                "Puente Socket v2 conectado (127.0.0.1:${termuxStatus.value.bridgePort})."
            } else {
                "El Puente Socket v2 aún no responde en 127.0.0.1:18765. Pega el comando en Termux y pulsa Enter."
            }
        }
    }

    fun selectEngine(engine: CommandEngine) {
        _selectedEngine.value = engine
    }

    fun updateCommandInput(value: String) {
        _commandInput.value = value
    }

    fun selectCategory(category: FunctionCategory) {
        _selectedCategory.value = category
    }

    fun loadInConsole(command: String, engine: CommandEngine) {
        _commandInput.value = command
        _selectedEngine.value = engine
    }

    fun executeCurrentCommand() {
        val raw = _commandInput.value.trim()
        if (raw.isEmpty()) {
            _uiNotice.value = "Escribe un comando antes de ejecutar."
            return
        }
        val (resolvedEngine, resolvedCommand) = when {
            raw.startsWith("@shizuku ", ignoreCase = true) ->
                CommandEngine.SHIZUKU to raw.substringAfter(" ").trim()
            raw.startsWith("@termux ", ignoreCase = true) ->
                CommandEngine.TERMUX to raw.substringAfter(" ").trim()
            raw.startsWith("@ambos ", ignoreCase = true) ->
                CommandEngine.BOTH to raw.substringAfter(" ").trim()
            raw.startsWith("@local ", ignoreCase = true) ->
                CommandEngine.LOCAL to raw.substringAfter(" ").trim()
            else -> _selectedEngine.value to raw
        }
        if (resolvedCommand.isEmpty()) {
            _uiNotice.value = "Escribe el comando después del prefijo."
            return
        }
        if (isHighRiskCommand(resolvedCommand)) {
            _pendingDangerousCommand.value = resolvedEngine to resolvedCommand
            return
        }
        viewModelScope.launch {
            runCommandInternal(resolvedEngine, resolvedCommand)
        }
    }

    fun confirmDangerousCommand() {
        val pending = _pendingDangerousCommand.value ?: return
        _pendingDangerousCommand.value = null
        viewModelScope.launch {
            runCommandInternal(pending.first, pending.second)
        }
    }

    fun dismissDangerousCommand() {
        _pendingDangerousCommand.value = null
    }

    private fun isHighRiskCommand(command: String): Boolean {
        val lower = command.lowercase(Locale.ROOT)
        val riskyTokens = listOf(
            "rm -rf",
            "rm -r ",
            "mkfs",
            "dd if=",
            "reboot",
            "shutdown",
            "pm uninstall",
            "pm disable",
            "pm clear",
            "dumpsys battery set",
            ">/dev/"
        )
        return riskyTokens.any { lower.contains(it) }
    }

    fun executeFunctionPreset(preset: SystemFunctionPreset) {
        _commandInput.value = preset.command
        _selectedEngine.value = preset.engine
        viewModelScope.launch {
            runCommandInternal(preset.engine, preset.command)
        }
    }

    fun runQuickCommand(engine: CommandEngine, command: String) {
        _commandInput.value = command
        _selectedEngine.value = engine
        viewModelScope.launch {
            runCommandInternal(engine, command)
        }
    }

    private suspend fun runCommandInternal(engine: CommandEngine, command: String) {
        _isExecuting.value = true
        try {
            when (engine) {
                CommandEngine.BOTH -> {
                    coroutineScope {
                        val shizukuDeferred = async { shizukuController.executeCommand(command) }
                        val termuxDeferred = async {
                            termuxController.executeCommand(
                                command = command,
                                runInBackground = termuxRunInBackground.value,
                                bridgeToken = _termuxBridgeToken.value
                            )
                        }
                        val shizukuRes = shizukuDeferred.await()
                        val termuxRes = termuxDeferred.await()
                        commandRepository.recordExecution(shizukuRes)
                        commandRepository.recordExecution(termuxRes)
                    }
                }
                CommandEngine.SHIZUKU -> {
                    commandRepository.recordExecution(shizukuController.executeCommand(command))
                }
                CommandEngine.TERMUX -> {
                    commandRepository.recordExecution(
                        termuxController.executeCommand(
                            command = command,
                            runInBackground = termuxRunInBackground.value,
                            bridgeToken = _termuxBridgeToken.value
                        )
                    )
                }
                CommandEngine.LOCAL -> {
                    commandRepository.recordExecution(localShellExecutor.executeCommand(command))
                }
            }
        } finally {
            _isExecuting.value = false
        }
    }

    fun requestShizukuAuthorization() {
        val ok = shizukuController.requestPermission()
        if (!ok) {
            _uiNotice.value = if (!shizukuStatus.value.isInstalled) {
                "Shizuku no está instalado."
            } else {
                "Inicia primero el servicio Shizuku desde la app Shizuku."
            }
        }
    }

    fun bindShizukuService() {
        shizukuController.bindServiceIfNeeded()
        shizukuController.refreshStatus()
    }

    fun openShizukuApp() {
        if (!shizukuController.openShizukuApp()) {
            _uiNotice.value = "No se encontró la app Shizuku instalada."
        }
    }

    fun openTermuxApp() {
        if (!termuxController.openTermuxApp()) {
            _uiNotice.value = "No se encontró Termux instalado."
        }
    }

    fun authorizeTermuxOrRequest(
        onOpenBridgeGuide: () -> Unit = {},
        onLaunchRuntimeDialog: () -> Unit
    ) {
        viewModelScope.launch {
            val token = _termuxBridgeToken.value.ifBlank {
                prefsRepository.getOrCreateTermuxBridgeToken().also { _termuxBridgeToken.value = it }
            }
            termuxController.refreshStatusAsync(termuxRunInBackground.value, token)
            val status = termuxStatus.value
            if (!status.isInstalled) {
                openTermuxApp()
                return@launch
            }
            if (status.isLocalBridgeOnline) {
                _uiNotice.value = "Termux ya está conectado mediante el Puente Socket v2 (127.0.0.1:${status.bridgePort})."
                return@launch
            }
            if (shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                val granted = grantTermuxPermissionViaShizuku(silent = false)
                if (granted) return@launch
            }
            if (!status.supportsRunCommand) {
                val ver = status.termuxVersionName ?: "Play Store"
                _uiNotice.value =
                    "Tu Termux ($ver) no expone el permiso RUN_COMMAND otorgable. Usa 'Copiar y abrir Termux' para activar el Puente Local."
                onOpenBridgeGuide()
            } else {
                onLaunchRuntimeDialog()
            }
        }
    }

    private suspend fun grantTermuxPermissionViaShizuku(silent: Boolean): Boolean {
        val app = getApplication<Application>()
        val supportsRunCmd = termuxStatus.value.supportsRunCommand
        val grantPart = if (supportsRunCmd) {
            "pm grant --user 0 ${app.packageName} ${TermuxController.PERMISSION_RUN_COMMAND} 2>/dev/null; "
        } else {
            ""
        }
        val cmd = grantPart +
            "appops set --user 0 ${TermuxController.TERMUX_PACKAGE} SYSTEM_ALERT_WINDOW allow 2>/dev/null; " +
            "appops set --user 0 ${TermuxController.TERMUX_PACKAGE} RUN_ANY_IN_BACKGROUND allow 2>/dev/null; " +
            "appops set --user 0 ${TermuxController.TERMUX_PACKAGE} WAKE_LOCK allow 2>/dev/null; " +
            "cmd deviceidle whitelist +${TermuxController.TERMUX_PACKAGE} 2>/dev/null; " +
            "echo 'OK: TERMUX_CONFIG'"
        val res = shizukuController.executeCommand(cmd)
        if (!silent) {
            commandRepository.recordExecution(res)
        }
        termuxController.refreshStatusAsync(termuxRunInBackground.value, _termuxBridgeToken.value)
        val nowGranted = termuxStatus.value.hasRunCommandPermission || termuxStatus.value.isLocalBridgeOnline
        if (nowGranted && !silent) {
            _uiNotice.value = "Termux configurado y conectado correctamente."
        }
        return nowGranted
    }

    fun openAppPermissionSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { app.startActivity(intent) }
    }

    fun onTermuxPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            termuxController.refreshStatusAsync(termuxRunInBackground.value, _termuxBridgeToken.value)
            if (granted || termuxStatus.value.health == ConnectionHealth.CONNECTED) {
                _uiNotice.value = "Termux conectado correctamente."
                return@launch
            }
            if (shizukuStatus.value.health == ConnectionHealth.CONNECTED) {
                val fixed = grantTermuxPermissionViaShizuku(silent = false)
                if (fixed) return@launch
            }
            val ver = termuxStatus.value.termuxVersionName ?: "actual"
            _uiNotice.value = if (!termuxStatus.value.supportsRunCommand) {
                "Android no puede otorgar RUN_COMMAND en Termux ($ver). Usa 'Conectar Puente (Universal)'."
            } else {
                "Android bloqueó el diálogo de permiso (Ajustes restringidos). Actívalo en Ajustes o usa 'Conectar Puente'."
            }
        }
    }

    fun setTermuxBackground(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setTermuxRunInBackground(enabled)
        }
    }

    fun updateAutomationRule(rule: BatteryAutomationRule) {
        viewModelScope.launch {
            prefsRepository.updateAutomationRule(rule)
            _uiNotice.value = if (rule.enabled) {
                "Regla de batería activada (${rule.targetEngine.label})."
            } else {
                "Automatización de batería pausada."
            }
        }
    }

    fun saveCustomCommand(
        title: String,
        description: String,
        command: String,
        engine: CommandEngine
    ) {
        if (title.isBlank() || command.isBlank()) {
            _uiNotice.value = "El título y el comando son obligatorios."
            return
        }
        viewModelScope.launch {
            commandRepository.saveCustomCommand(title, description, command, engine)
            _uiNotice.value = "Comando guardado en Mis Comandos."
        }
    }

    fun deleteCustomCommand(dbId: Int) {
        viewModelScope.launch {
            commandRepository.deleteCustomCommand(dbId)
            _uiNotice.value = "Comando eliminado."
        }
    }

    fun toggleFavoriteCommand(dbId: Int, currentFavorite: Boolean) {
        viewModelScope.launch {
            commandRepository.toggleFavorite(dbId, !currentFavorite)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            commandRepository.clearHistory()
            _uiNotice.value = "Historial de consola limpiado."
        }
    }

    fun consumeNotice() {
        _uiNotice.value = null
    }

    fun showNotice(message: String) {
        _uiNotice.value = message
    }

    fun requestAddQuickSettingsTile() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = app.getSystemService(StatusBarManager::class.java)
            if (statusBarManager == null) {
                _uiNotice.value = "Desliza la barra de notificaciones, pulsa Editar y agrega 'Ahorro Samsung'."
                return
            }
            statusBarManager.requestAddTileService(
                ComponentName(app, BatterySaverTileService::class.java),
                app.getString(R.string.qs_tile_label),
                Icon.createWithResource(app, R.drawable.ic_qs_battery_saver),
                ContextCompat.getMainExecutor(app)
            ) { resultCode ->
                _uiNotice.value = when (resultCode) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "Tile 'Ahorro Samsung' agregado a Ajustes Rápidos."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "El tile 'Ahorro Samsung' ya está en tus Ajustes Rápidos."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        "Solicitud cancelada. Puedes añadirlo manualmente desde el botón Editar de Ajustes Rápidos."
                    else ->
                        "No se pudo fijar automáticamente; agrégalo desde Editar en Ajustes Rápidos."
                }
            }
        } else {
            _uiNotice.value =
                "Desliza la barra superior dos veces, pulsa Editar (lápiz) y arrastra 'Ahorro Samsung'."
        }
    }

    override fun onCleared() {
        super.onCleared()
        batteryMonitor.stop()
        shizukuController.unregister()
        torchEcoController.unregister()
        if (standbyReceiverRegistered) {
            runCatching { getApplication<Application>().unregisterReceiver(standbyScreenReceiver) }
            standbyReceiverRegistered = false
        }
    }
}
