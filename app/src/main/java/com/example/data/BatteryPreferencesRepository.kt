package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.BatteryAutomationRule
import com.example.model.ChargeProtectionConfig
import com.example.model.CommandEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "termuzuku_prefs")

class BatteryPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        val LOW_BATTERY_PERCENT = intPreferencesKey("low_battery_percent")
        val HIGH_TEMP_CELSIUS = floatPreferencesKey("high_temp_celsius")
        val AUTOMATION_ENGINE = stringPreferencesKey("automation_engine")
        val AUTOMATION_COMMAND = stringPreferencesKey("automation_command")
        val TERMUX_BACKGROUND = booleanPreferencesKey("termux_background")
        val CHARGE_PROTECTION_ENABLED = booleanPreferencesKey("charge_protection_enabled")
        val CHARGE_LIMIT_PERCENT = intPreferencesKey("charge_limit_percent")
        val SMART_SAVING_ENABLED = booleanPreferencesKey("smart_saving_enabled")
        val STANDBY_SMART_SAVING_ENABLED = booleanPreferencesKey("standby_smart_saving_enabled")
        val AUTO_GUARDIAN_ENABLED = booleanPreferencesKey("auto_guardian_enabled")
        val QS_TILE_ACTIVE = booleanPreferencesKey("qs_tile_active")
        val QS_TILE_ENABLED_LOW_POWER = booleanPreferencesKey("qs_tile_enabled_low_power")
        val BLUETOOTH_ECO_ENABLED = booleanPreferencesKey("bluetooth_eco_enabled")
        val TORCH_ECO_ENABLED = booleanPreferencesKey("torch_eco_enabled")
        val APP_GAME_ECO_ENABLED = booleanPreferencesKey("app_game_eco_enabled")
        val APP_FREEZE_ECO_ENABLED = booleanPreferencesKey("app_freeze_eco_enabled")
        val TERMUX_BRIDGE_TOKEN = stringPreferencesKey("termux_bridge_token")
        val STABILITY_REPAIR_V1_DONE = booleanPreferencesKey("stability_repair_v1_done")
        val AUTO_SOLVER_ENABLED = booleanPreferencesKey("auto_solver_enabled")
    }

    val automationRule: Flow<BatteryAutomationRule> = context.dataStore.data.map { prefs ->
        val engineName = prefs[Keys.AUTOMATION_ENGINE] ?: CommandEngine.SHIZUKU.name
        val engine = runCatching { CommandEngine.valueOf(engineName) }
            .getOrDefault(CommandEngine.SHIZUKU)
        BatteryAutomationRule(
            enabled = prefs[Keys.AUTOMATION_ENABLED] ?: false,
            lowBatteryPercent = prefs[Keys.LOW_BATTERY_PERCENT] ?: 20,
            highTempCelsius = prefs[Keys.HIGH_TEMP_CELSIUS] ?: 42f,
            targetEngine = engine,
            command = prefs[Keys.AUTOMATION_COMMAND]
                ?: "cmd power set-mode 1 && settings put system peak_refresh_rate 60.0"
        )
    }

    val termuxRunInBackground: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.TERMUX_BACKGROUND] ?: true
    }

    val chargeProtection: Flow<ChargeProtectionConfig> = context.dataStore.data.map { prefs ->
        ChargeProtectionConfig(
            enabled = prefs[Keys.CHARGE_PROTECTION_ENABLED] ?: false,
            limitPercent = prefs[Keys.CHARGE_LIMIT_PERCENT] ?: 85
        )
    }

    val smartSavingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SMART_SAVING_ENABLED] ?: true
    }

    val smartStandbySavingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.STANDBY_SMART_SAVING_ENABLED] ?: true
    }

    val autoGuardianEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_GUARDIAN_ENABLED] ?: true
    }

    val autoSolverEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_SOLVER_ENABLED] ?: true
    }

    val qsTileActive: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.QS_TILE_ACTIVE] ?: false
    }

    val qsTileEnabledLowPower: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.QS_TILE_ENABLED_LOW_POWER] ?: false
    }

    val bluetoothEcoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BLUETOOTH_ECO_ENABLED] ?: true
    }

    val torchEcoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.TORCH_ECO_ENABLED] ?: true
    }

    val appGameEcoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_GAME_ECO_ENABLED] ?: true
    }

    val appFreezeEcoEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_FREEZE_ECO_ENABLED] ?: true
    }

    val termuxBridgeToken: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.TERMUX_BRIDGE_TOKEN].orEmpty()
    }

    suspend fun isStabilityRepairV1Done(): Boolean {
        return context.dataStore.data.first()[Keys.STABILITY_REPAIR_V1_DONE] ?: false
    }

    suspend fun markStabilityRepairV1Done() {
        context.dataStore.edit { prefs ->
            prefs[Keys.STABILITY_REPAIR_V1_DONE] = true
        }
    }

    suspend fun updateAutomationRule(rule: BatteryAutomationRule) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTOMATION_ENABLED] = rule.enabled
            prefs[Keys.LOW_BATTERY_PERCENT] = rule.lowBatteryPercent.coerceIn(5, 95)
            prefs[Keys.HIGH_TEMP_CELSIUS] = rule.highTempCelsius.coerceIn(30f, 60f)
            prefs[Keys.AUTOMATION_ENGINE] = rule.targetEngine.name
            prefs[Keys.AUTOMATION_COMMAND] = rule.command
        }
    }

    suspend fun setTermuxRunInBackground(background: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TERMUX_BACKGROUND] = background
        }
    }

    suspend fun updateChargeProtection(enabled: Boolean, limitPercent: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CHARGE_PROTECTION_ENABLED] = enabled
            prefs[Keys.CHARGE_LIMIT_PERCENT] = limitPercent.coerceIn(75, 95)
        }
    }

    suspend fun setSmartSavingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SMART_SAVING_ENABLED] = enabled
        }
    }

    suspend fun setSmartStandbySavingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STANDBY_SMART_SAVING_ENABLED] = enabled
        }
    }

    suspend fun setAutoGuardianEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_GUARDIAN_ENABLED] = enabled
        }
    }

    suspend fun setAutoSolverEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_SOLVER_ENABLED] = enabled
        }
    }

    suspend fun setQsTileState(active: Boolean, enabledLowPower: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.QS_TILE_ACTIVE] = active
            prefs[Keys.QS_TILE_ENABLED_LOW_POWER] = enabledLowPower
        }
    }

    suspend fun setBluetoothEcoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLUETOOTH_ECO_ENABLED] = enabled
        }
    }

    suspend fun setTorchEcoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TORCH_ECO_ENABLED] = enabled
        }
    }

    suspend fun setAppGameEcoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_GAME_ECO_ENABLED] = enabled
        }
    }

    suspend fun setAppFreezeEcoEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_FREEZE_ECO_ENABLED] = enabled
        }
    }

    suspend fun getOrCreateTermuxBridgeToken(): String {
        val existing = context.dataStore.data.first()[Keys.TERMUX_BRIDGE_TOKEN]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString().replace("-", "")
        context.dataStore.edit { prefs ->
            prefs[Keys.TERMUX_BRIDGE_TOKEN] = generated
        }
        return generated
    }
}
