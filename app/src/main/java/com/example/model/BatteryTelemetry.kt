package com.example.model

enum class SamsungPowerProfile(val label: String, val subtitle: String) {
    NORMAL_120HZ("Normal 120Hz", "Rendimiento completo Moto G82 5G"),
    SAMSUNG_SAVING("Ahorro Samsung", "Ahorro + 60Hz + Sin AOD + Standby"),
    SAMSUNG_ULTRA("Ahorro Ultra", "Ahorro + 60Hz + Sin AOD + Pantalla 30s")
}

enum class SmartSavingStage(val label: String) {
    OFF("Desactivado"),
    CHARGING_NORMAL("Cargando • 120Hz Normal"),
    BALANCED_120HZ("Batería alta • Adaptativa"),
    ECO_60HZ("Equilibrado (≤44%) • 60Hz"),
    CRITICAL_SAVER("Crítico (≤20% / ≥41°C) • Ahorro total")
}

data class SmartSavingState(
    val enabled: Boolean = true,
    val stage: SmartSavingStage = SmartSavingStage.OFF,
    val lastActionLabel: String? = null,
    val standbyEnabled: Boolean = true,
    val standbyLastEventLabel: String? = null,
    val bluetoothEcoEnabled: Boolean = true,
    val bluetoothHardwareOn: Boolean = false,
    val bluetoothEcoLastActionLabel: String? = null,
    val torchEcoEnabled: Boolean = true,
    val torchHardwareOn: Boolean = false,
    val torchSupportsDimming: Boolean = false,
    val torchLevelLabel: String? = null,
    val torchEcoLastActionLabel: String? = null,
    val appGameEcoEnabled: Boolean = true,
    val detectedAppsCount: Int = 0,
    val detectedGamesCount: Int = 0,
    val foregroundPackageLabel: String? = null,
    val appGameEcoLastActionLabel: String? = null,
    val appFreezeEcoEnabled: Boolean = true,
    val frozenPackagesCount: Int = 0,
    val appFreezeLastActionLabel: String? = null
)

data class PowerProfileStatus(
    val deviceLabel: String = "Android",
    val isMotoG82: Boolean = false,
    val lowPowerEnabled: Boolean = false,
    val peakRefreshRateHz: Int = 120,
    val backgroundRestricted: Boolean = false,
    val aodDisabled: Boolean = false,
    val isSyncedFromSystem: Boolean = false,
    val activeProfile: SamsungPowerProfile = SamsungPowerProfile.NORMAL_120HZ
)

data class ChargeProtectionConfig(
    val enabled: Boolean = false,
    val limitPercent: Int = 85,
    val alertActive: Boolean = false
)

data class BatteryTelemetry(
    val isDetected: Boolean = false,
    val level: Int = 0,
    val scale: Int = 100,
    val statusCode: Int = 0,
    val statusLabel: String = "Detectando...",
    val pluggedLabel: String = "Batería",
    val isCharging: Boolean = false,
    val healthLabel: String = "Desconocida",
    val isOverheating: Boolean = false,
    val temperatureCelsius: Float = 0f,
    val voltageMv: Int = 0,
    val currentMa: Int = 0,
    val powerWatts: Float = 0f,
    val chargeCounterMah: Int = 0,
    val chargeTimeRemainingMin: Int? = null,
    val technology: String = "Li-ion",
    val timestampMs: Long = System.currentTimeMillis()
)

data class BatteryHistoryPoint(
    val timestampMs: Long,
    val level: Int,
    val temperatureCelsius: Float,
    val currentMa: Int
)

data class BatteryAutomationRule(
    val enabled: Boolean = false,
    val lowBatteryPercent: Int = 20,
    val highTempCelsius: Float = 42f,
    val targetEngine: CommandEngine = CommandEngine.SHIZUKU,
    val command: String = "cmd power set-mode 1 && settings put system peak_refresh_rate 60.0",
    val lastTriggeredLabel: String? = null
)
