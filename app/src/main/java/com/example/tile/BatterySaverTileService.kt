package com.example.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Display
import com.example.MainActivity
import com.example.R
import com.example.data.BatteryPreferencesRepository
import com.example.engine.ShizukuController
import com.example.model.BuiltInFunctionsCatalog
import com.example.model.ConnectionHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class BatterySaverTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var shizukuController: ShizukuController
    private lateinit var prefsRepository: BatteryPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        shizukuController = ShizukuController(applicationContext)
        prefsRepository = BatteryPreferencesRepository(applicationContext)
        shizukuController.register()
    }

    override fun onDestroy() {
        shizukuController.unregister()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            updateTileAppearance()
        }
    }

    override fun onClick() {
        super.onClick()
        unlockAndRun {
            serviceScope.launch {
                handleTileToggle()
            }
        }
    }

    private suspend fun handleTileToggle() {
        val shizukuReady = withContext(Dispatchers.IO) {
            shizukuController.refreshStatusAsync()
            shizukuController.status.value.health == ConnectionHealth.CONNECTED
        }
        if (!shizukuReady) {
            qsTile?.let { tile ->
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.qs_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.qs_tile_subtitle_shizuku_needed)
                }
                tile.updateTile()
            }
            launchMainActivityAndCollapse()
            return
        }

        val currentTileActive = withContext(Dispatchers.IO) { prefsRepository.qsTileActive.first() }
        val systemLowPower = withContext(Dispatchers.IO) { readSystemLowPower() }

        // Mark busy state while executing Shizuku command
        qsTile?.let { tile ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.qs_tile_subtitle_working)
            }
            tile.updateTile()
        }

        withContext(Dispatchers.IO) {
            if (!currentTileActive) {
                val appTurnedOnLowPower = !systemLowPower
                val result = shizukuController.executeCommand(
                    BuiltInFunctionsCatalog.CMD_SAMSUNG_SAVING_MOTO_G82
                )
                if (result.exitCode == 0) {
                    prefsRepository.setQsTileState(
                        active = true,
                        enabledLowPower = appTurnedOnLowPower
                    )
                }
            } else {
                val shouldDisableLowPower = prefsRepository.qsTileEnabledLowPower.first()
                val restoreCmd = if (shouldDisableLowPower) {
                    BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_WITH_POWER_OFF
                } else {
                    BuiltInFunctionsCatalog.CMD_RESTORE_NORMAL_120HZ_MOTO_G82
                }
                val result = shizukuController.executeCommand(restoreCmd)
                if (result.exitCode == 0) {
                    prefsRepository.setQsTileState(
                        active = false,
                        enabledLowPower = false
                    )
                }
            }
        }

        updateTileAppearance()
    }

    private suspend fun updateTileAppearance() {
        val snapshot = withContext(Dispatchers.IO) {
            shizukuController.refreshStatusAsync()
            val shizukuReady = shizukuController.status.value.health == ConnectionHealth.CONNECTED
            val savedActive = prefsRepository.qsTileActive.first()
            val refreshHz = readCurrentRefreshRateHz()
            val batteryLevel = readBatteryLevel()
            val isSavingVisual = savedActive || (refreshHz <= 60 && readSystemLowPower())
            TileSnapshot(shizukuReady, refreshHz, batteryLevel, isSavingVisual)
        }
        val tile = qsTile ?: return

        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_battery_saver)
        tile.label = getString(R.string.qs_tile_label)
        tile.state = if (snapshot.isSavingVisual) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = if (snapshot.isSavingVisual) {
            "Ahorro Samsung activo, ${snapshot.refreshHz} Hz, batería ${snapshot.batteryLevel}%"
        } else {
            "Ahorro Samsung inactivo, ${snapshot.refreshHz} Hz, batería ${snapshot.batteryLevel}%"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !snapshot.shizukuReady -> getString(R.string.qs_tile_subtitle_shizuku_needed)
                snapshot.isSavingVisual -> "60Hz • Standby • ${snapshot.batteryLevel}%"
                else -> "Normal ${snapshot.refreshHz}Hz • ${snapshot.batteryLevel}%"
            }
        }
        tile.updateTile()
    }

    private data class TileSnapshot(
        val shizukuReady: Boolean,
        val refreshHz: Int,
        val batteryLevel: Int,
        val isSavingVisual: Boolean
    )

    private fun readSystemLowPower(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode ?: false
    }

    private fun readCurrentRefreshRateHz(): Int {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60
    }

    private fun readBatteryLevel(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun launchMainActivityAndCollapse() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                QS_LAUNCH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private const val QS_LAUNCH_REQUEST_CODE = 3102

        fun requestTileRefresh(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, BatterySaverTileService::class.java)
                )
            }
        }
    }
}
