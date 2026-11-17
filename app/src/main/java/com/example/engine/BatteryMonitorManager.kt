package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.BatteryHistoryPoint
import com.example.model.BatteryTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class BatteryMonitorManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    private val _telemetry = MutableStateFlow(BatteryTelemetry())
    val telemetry: StateFlow<BatteryTelemetry> = _telemetry.asStateFlow()

    private val _history = MutableStateFlow<List<BatteryHistoryPoint>>(emptyList())
    val history: StateFlow<List<BatteryHistoryPoint>> = _history.asStateFlow()

    private var lastIntent: Intent? = null
    private var pollingJob: Job? = null
    private var isRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                lastIntent = intent
                updateFromIntent(intent, recordPoint = true)
            }
        }
    }

    fun start() {
        if (!isRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isRegistered = true
            if (sticky != null) {
                lastIntent = sticky
                updateFromIntent(sticky, recordPoint = true)
            } else {
                refreshProperties(recordPoint = true)
            }
        }
        if (pollingJob == null) {
            pollingJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(5000L)
                    refreshProperties(recordPoint = true)
                }
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        if (isRegistered) {
            runCatching { context.unregisterReceiver(batteryReceiver) }
            isRegistered = false
        }
    }

    fun refreshNow() {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            lastIntent = sticky
            updateFromIntent(sticky, recordPoint = true)
        } else {
            refreshProperties(recordPoint = true)
        }
    }

    private fun refreshProperties(recordPoint: Boolean) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: lastIntent
        if (intent != null) {
            lastIntent = intent
            updateFromIntent(intent, recordPoint)
        }
    }

    private fun updateFromIntent(intent: Intent, recordPoint: Boolean) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val rawScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val level = if (rawLevel >= 0) {
            ((rawLevel.toFloat() / rawScale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val voltageMv = if (rawVoltage in 1..99) rawVoltage * 1000 else rawVoltage
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).orEmpty().ifBlank { "Li-ion" }

        val rawCurrentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val rawCurrentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val rawCurrent = when {
            rawCurrentNow != Int.MIN_VALUE && rawCurrentNow != 0 -> rawCurrentNow
            rawCurrentAvg != Int.MIN_VALUE && rawCurrentAvg != 0 -> rawCurrentAvg
            else -> 0
        }
        val currentMa = if (rawCurrent == 0) {
            0
        } else if (abs(rawCurrent) > 10_000) {
            rawCurrent / 1000
        } else {
            rawCurrent
        }

        val chargeCounterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeCounterMah = if (chargeCounterUah > 0) chargeCounterUah / 1000 else 0

        val tempCelsius = tempTenths / 10f
        val voltageV = voltageMv / 1000f
        val powerWatts = abs(currentMa / 1000f) * voltageV

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0 ||
            batteryManager.isCharging

        val chargeRemainingMin = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isCharging && level < 100) {
            val ms = runCatching { batteryManager.computeChargeTimeRemaining() }.getOrDefault(-1L)
            if (ms > 0L) (ms / 60_000L).toInt().coerceAtLeast(1) else null
        } else {
            null
        }

        val statusLabel = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
            BatteryManager.BATTERY_STATUS_FULL -> "Completa"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sin carga"
            else -> if (isCharging) "Cargando" else "Descargando"
        }

        val pluggedLabel = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "Adaptador CA"
            BatteryManager.BATTERY_PLUGGED_USB -> "Puerto USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrica"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "Base / Dock"
            else -> if (isCharging) "Corriente externa" else "Batería"
        }

        val healthLabel = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Óptima"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dañada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensión"
            BatteryManager.BATTERY_HEALTH_COLD -> "Muy fría"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Fallo interno"
            else -> "Desconocida"
        }

        val nowMs = System.currentTimeMillis()
        val snapshot = BatteryTelemetry(
            isDetected = rawLevel >= 0 || level > 0,
            level = level,
            scale = rawScale,
            statusCode = status,
            statusLabel = statusLabel,
            pluggedLabel = pluggedLabel,
            isCharging = isCharging,
            healthLabel = healthLabel,
            isOverheating = health == BatteryManager.BATTERY_HEALTH_OVERHEAT || tempCelsius >= 43f,
            temperatureCelsius = tempCelsius,
            voltageMv = voltageMv,
            currentMa = currentMa,
            powerWatts = powerWatts,
            chargeCounterMah = chargeCounterMah,
            chargeTimeRemainingMin = chargeRemainingMin,
            technology = tech,
            timestampMs = nowMs
        )
        _telemetry.value = snapshot

        if (recordPoint) {
            val prevPoint = _history.value.lastOrNull()
            val shouldRecord = prevPoint == null ||
                (nowMs - prevPoint.timestampMs) >= 4_500L ||
                prevPoint.level != snapshot.level ||
                abs(prevPoint.temperatureCelsius - snapshot.temperatureCelsius) >= 0.4f
            if (shouldRecord) {
                val point = BatteryHistoryPoint(
                    timestampMs = snapshot.timestampMs,
                    level = snapshot.level,
                    temperatureCelsius = snapshot.temperatureCelsius,
                    currentMa = snapshot.currentMa
                )
                _history.value = (_history.value + point).takeLast(60)
            }
        }
    }
}
