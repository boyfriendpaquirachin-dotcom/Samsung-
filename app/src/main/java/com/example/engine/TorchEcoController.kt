package com.example.engine

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class TorchEcoStatus(
    val torchCameraId: String? = null,
    val isTorchOn: Boolean = false,
    val supportsVariableBrightness: Boolean = false,
    val maxStrengthLevel: Int = 1,
    val defaultStrengthLevel: Int = 1,
    val currentStrengthLevel: Int = 1,
    val lastActionLabel: String? = null
) {
    val levelLabel: String?
        get() = when {
            !isTorchOn -> null
            supportsVariableBrightness && maxStrengthLevel > 1 -> {
                val pct = ((currentStrengthLevel.toFloat() / maxStrengthLevel.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(1, 100)
                "$currentStrengthLevel/$maxStrengthLevel ($pct%)"
            }
            else -> "Brillo fijo"
        }
}

/**
 * Controlador autónomo de ahorro energético para la linterna (Torch Eco).
 *
 * REGLA ESTRICTA DE DISEÑO:
 * Este controlador NUNCA llama a setTorchMode(..., false) ni apaga la linterna del usuario.
 * En Android 13+ (API 33+) con hardware compatible, atenúa automáticamente la intensidad
 * del LED mediante turnOnTorchWithStrengthLevel(...) manteniendo la linterna encendida.
 * En dispositivos sin control de intensidad, coordina la compensación energética del sistema
 * sin apagar la linterna.
 */
class TorchEcoController(private val context: Context) {

    private val cameraManager: CameraManager? = runCatching {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }.getOrNull()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow(inspectTorchCapabilities(baseState = TorchEcoStatus()))
    val status: StateFlow<TorchEcoStatus> = _status.asStateFlow()

    @Volatile
    private var ecoModeEnabled: Boolean = true

    @Volatile
    private var highDrainOrHot: Boolean = false

    @Volatile
    private var lastDimmedByApp: Boolean = false

    private var registered = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == _status.value.torchCameraId) {
                _status.value = _status.value.copy(isTorchOn = false)
            }
        }

        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            val prev = _status.value
            val targetId = prev.torchCameraId ?: findFlashCameraId()
            if (targetId != null && cameraId != targetId) return

            val currentCaps = inspectTorchCapabilities(targetId, prev)
            val currentLevel = if (enabled) {
                readStrengthLevelCompat(cameraId).coerceAtLeast(1)
            } else {
                lastDimmedByApp = false
                currentCaps.defaultStrengthLevel
            }

            _status.value = currentCaps.copy(
                torchCameraId = cameraId,
                isTorchOn = enabled,
                currentStrengthLevel = currentLevel,
                lastActionLabel = prev.lastActionLabel
            )

            if (enabled && ecoModeEnabled) {
                applyTorchEcoLocked()
            }
        }

        override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
            val prev = _status.value
            val targetId = prev.torchCameraId
            if (targetId != null && cameraId != targetId) return

            _status.value = prev.copy(
                currentStrengthLevel = newStrengthLevel
            )

            // Only step brightness down if eco mode is enabled and current level exceeds target eco level
            if (_status.value.isTorchOn && ecoModeEnabled) {
                applyTorchEcoLocked()
            }
        }
    }

    fun register() {
        if (registered) return
        val cm = cameraManager ?: return
        runCatching {
            cm.registerTorchCallback(torchCallback, mainHandler)
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        val cm = cameraManager ?: return
        runCatching {
            cm.unregisterTorchCallback(torchCallback)
        }
        registered = false
    }

    fun updatePolicy(enabled: Boolean, highDrainOrHot: Boolean): TorchEcoStatus {
        this.ecoModeEnabled = enabled
        this.highDrainOrHot = highDrainOrHot
        val prev = _status.value
        val refreshed = inspectTorchCapabilities(prev.torchCameraId, prev)
        _status.value = refreshed.copy(
            isTorchOn = prev.isTorchOn,
            currentStrengthLevel = if (prev.isTorchOn) {
                readStrengthLevelCompat(refreshed.torchCameraId).coerceAtLeast(1)
            } else {
                refreshed.defaultStrengthLevel
            },
            lastActionLabel = prev.lastActionLabel
        )

        if (enabled) {
            applyTorchEcoLocked()
        } else if (lastDimmedByApp && _status.value.isTorchOn) {
            restoreDefaultStrengthWithoutTurningOff()
        } else {
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            _status.value = _status.value.copy(
                lastActionLabel = "Brillo normal • linterna intacta ($timeFmt)"
            )
        }
        return _status.value
    }

    private fun applyTorchEcoLocked() {
        val current = _status.value
        val cameraId = current.torchCameraId ?: return
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (!current.isTorchOn) {
            _status.value = current.copy(
                lastActionLabel = "En espera • lista al encender linterna ($timeFmt)"
            )
            return
        }

        if (!current.supportsVariableBrightness || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _status.value = current.copy(
                lastActionLabel = "Linterna ON • compensando energía del sistema ($timeFmt)"
            )
            return
        }

        val targetLevel = computeTargetEcoLevel(
            maxStrength = current.maxStrengthLevel,
            highDrainOrHot = highDrainOrHot
        )

        // Never increase brightness beyond current setting and NEVER turn off torch (level >= 1).
        if (current.currentStrengthLevel > targetLevel) {
            val applied = setTorchStrengthLevelApi33(cameraId, targetLevel)
            if (applied) {
                lastDimmedByApp = true
                val pct = ((targetLevel.toFloat() / current.maxStrengthLevel.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(1, 100)
                _status.value = _status.value.copy(
                    currentStrengthLevel = targetLevel,
                    lastActionLabel = "Brillo eco $targetLevel/${current.maxStrengthLevel} ($pct%) • ON ($timeFmt)"
                )
            }
        } else {
            val pct = ((current.currentStrengthLevel.toFloat() / current.maxStrengthLevel.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(1, 100)
            _status.value = current.copy(
                lastActionLabel = "Eco activo $pct% • linterna ON ($timeFmt)"
            )
        }
    }

    private fun restoreDefaultStrengthWithoutTurningOff() {
        val current = _status.value
        val cameraId = current.torchCameraId ?: return
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        if (current.supportsVariableBrightness && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val targetDefault = current.defaultStrengthLevel.coerceIn(1, current.maxStrengthLevel)
            if (setTorchStrengthLevelApi33(cameraId, targetDefault)) {
                lastDimmedByApp = false
                _status.value = current.copy(
                    currentStrengthLevel = targetDefault,
                    lastActionLabel = "Brillo estándar restaurado ($timeFmt)"
                )
                return
            }
        }
        lastDimmedByApp = false
        _status.value = current.copy(
            lastActionLabel = "Modo normal • linterna intacta ($timeFmt)"
        )
    }

    private fun inspectTorchCapabilities(
        preferredId: String? = null,
        baseState: TorchEcoStatus = TorchEcoStatus()
    ): TorchEcoStatus {
        val id = preferredId ?: findFlashCameraId() ?: return baseState
        val cm = cameraManager ?: return baseState.copy(torchCameraId = id)
        return runCatching {
            val chars = cm.getCameraCharacteristics(id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val maxLevel = (chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1)
                    .coerceAtLeast(1)
                val defaultLevel = (chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 1)
                    .coerceIn(1, maxLevel)
                TorchEcoStatus(
                    torchCameraId = id,
                    isTorchOn = baseState.isTorchOn,
                    supportsVariableBrightness = maxLevel > 1,
                    maxStrengthLevel = maxLevel,
                    defaultStrengthLevel = defaultLevel,
                    currentStrengthLevel = baseState.currentStrengthLevel.coerceIn(1, maxLevel),
                    lastActionLabel = baseState.lastActionLabel
                )
            } else {
                TorchEcoStatus(
                    torchCameraId = id,
                    isTorchOn = baseState.isTorchOn,
                    supportsVariableBrightness = false,
                    maxStrengthLevel = 1,
                    defaultStrengthLevel = 1,
                    currentStrengthLevel = 1,
                    lastActionLabel = baseState.lastActionLabel
                )
            }
        }.getOrDefault(baseState.copy(torchCameraId = id))
    }

    private fun findFlashCameraId(): String? {
        val cm = cameraManager ?: return null
        return runCatching {
            val ids = cm.cameraIdList
            // Prefer back camera with flash unit
            val backFlash = ids.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = c.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            }
            backFlash ?: ids.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private fun readStrengthLevelCompat(cameraId: String?): Int {
        val cm = cameraManager ?: return 1
        if (cameraId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 1
        return runCatching {
            cm.getTorchStrengthLevel(cameraId)
        }.getOrDefault(1)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setTorchStrengthLevelApi33(cameraId: String, strengthLevel: Int): Boolean {
        val cm = cameraManager ?: return false
        val safeLevel = strengthLevel.coerceAtLeast(1)
        return runCatching {
            cm.turnOnTorchWithStrengthLevel(cameraId, safeLevel)
            true
        }.getOrDefault(false)
    }

    companion object {
        fun computeTargetEcoLevel(maxStrength: Int, highDrainOrHot: Boolean): Int {
            if (maxStrength <= 1) return 1
            val ratio = if (highDrainOrHot) 0.25f else 0.40f
            return (maxStrength * ratio).roundToInt().coerceIn(1, maxStrength - 1)
        }
    }
}
