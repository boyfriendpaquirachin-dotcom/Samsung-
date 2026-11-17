package com.example.model

enum class CommandEngine(val label: String, val shortLabel: String) {
    BOTH("Ambos", "Shizuku + Termux"),
    SHIZUKU("Shizuku", "ADB/Root"),
    TERMUX("Termux", "Linux"),
    LOCAL("Shell Local", "Local")
}

data class EngineExecutionResult(
    val logId: Int = 0,
    val engine: CommandEngine,
    val command: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val uidLabel: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
    val isPending: Boolean = false
)

enum class ConnectionHealth {
    CONNECTED,
    PERMISSION_REQUIRED,
    INACTIVE,
    NOT_INSTALLED
}

data class ShizukuStatus(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val hasPermission: Boolean = false,
    val isUserServiceBound: Boolean = false,
    val uid: Int = -1,
    val apiVersion: Int = -1
) {
    val health: ConnectionHealth
        get() = when {
            !isInstalled -> ConnectionHealth.NOT_INSTALLED
            !isBinderAlive -> ConnectionHealth.INACTIVE
            !hasPermission -> ConnectionHealth.PERMISSION_REQUIRED
            else -> ConnectionHealth.CONNECTED
        }

    val privilegeLabel: String
        get() = when (uid) {
            0 -> "Root (UID 0)"
            2000 -> "ADB Shell (UID 2000)"
            in 1..Int.MAX_VALUE -> "UID $uid"
            else -> if (isBinderAlive) "Enlazado" else "Sin enlace"
        }
}

data class TermuxStatus(
    val isInstalled: Boolean = false,
    val isTermuxApiInstalled: Boolean = false,
    val hasRunCommandPermission: Boolean = false,
    val isLocalBridgeOnline: Boolean = false,
    val bridgePort: Int = 18765,
    val runInBackground: Boolean = true,
    val termuxVersionName: String? = null,
    val runCommandDeclaredInTermux: Boolean = false,
    val runCommandIsRuntimeGrantable: Boolean = false,
    val runCommandServiceExported: Boolean = false
) {
    val supportsRunCommand: Boolean
        get() = isInstalled &&
            runCommandDeclaredInTermux &&
            runCommandIsRuntimeGrantable &&
            runCommandServiceExported

    val health: ConnectionHealth
        get() = when {
            isLocalBridgeOnline -> ConnectionHealth.CONNECTED
            !isInstalled -> ConnectionHealth.NOT_INSTALLED
            !hasRunCommandPermission -> ConnectionHealth.PERMISSION_REQUIRED
            else -> ConnectionHealth.CONNECTED
        }

    val modeLabel: String
        get() = when {
            isLocalBridgeOnline -> "Puente Socket ($bridgePort)"
            hasRunCommandPermission -> "RUN_COMMAND"
            isInstalled && !supportsRunCommand -> "Requiere Puente"
            else -> "Sin enlace"
        }
}
