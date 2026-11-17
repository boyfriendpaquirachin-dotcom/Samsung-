package com.example.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.example.model.CommandEngine
import com.example.model.EngineExecutionResult
import com.example.model.ShizukuStatus
import com.example.shizuku.IShellUserService
import com.example.shizuku.ShellUserService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShizukuController(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    private var userService: IShellUserService? = null
    @Volatile
    private var pendingBindDeferred: CompletableDeferred<IShellUserService?>? = null
    private val bindMutex = Mutex()
    private var listenersRegistered = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, ShellUserService::class.java))
            .daemon(false)
            .processNameSuffix("termuzuku_shell")
            .debuggable(false)
            .version(1)
            .tag("TermuZukuShellService")
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val stub = IShellUserService.Stub.asInterface(service)
            userService = stub
            pendingBindDeferred?.complete(stub)
            pendingBindDeferred = null
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            pendingBindDeferred?.complete(null)
            pendingBindDeferred = null
            refreshStatus()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        ioScope.launch {
            refreshStatusBlocking()
            bindServiceIfNeededBlocking()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        refreshStatus()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                ioScope.launch {
                    refreshStatusBlocking()
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
                    }
                }
            }
        }

    fun register() {
        if (!listenersRegistered) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            listenersRegistered = true
        }
        refreshStatus()
    }

    fun unregister() {
        if (listenersRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
            listenersRegistered = false
        }
        unbindService()
        ioScope.cancel()
    }

    fun refreshStatus() {
        ioScope.launch {
            refreshStatusBlocking()
        }
    }

    suspend fun refreshStatusAsync() {
        withContext(Dispatchers.IO) {
            refreshStatusBlocking()
        }
    }

    private fun refreshStatusBlocking() {
        val isInstalled = isPackageInstalled(SHIZUKU_PACKAGE)
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val hasPerm = if (binderAlive) {
            runCatching {
                !Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            false
        }
        val boundService = userService
        val uid = if (binderAlive && hasPerm) {
            runCatching { boundService?.uid ?: Shizuku.getUid() }.getOrDefault(-1)
        } else {
            -1
        }
        val apiVer = if (binderAlive) {
            runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        } else {
            -1
        }

        _status.value = ShizukuStatus(
            isInstalled = isInstalled,
            isBinderAlive = binderAlive,
            hasPermission = hasPerm,
            isUserServiceBound = boundService != null,
            uid = uid,
            apiVersion = apiVer
        )
    }

    fun requestPermission(): Boolean {
        val current = _status.value
        if (!current.isBinderAlive) {
            refreshStatus()
            return false
        }
        if (current.hasPermission) {
            bindServiceIfNeeded()
            return true
        }
        return runCatching {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                true
            }
        }.getOrDefault(false)
    }

    fun bindServiceIfNeeded() {
        ioScope.launch {
            refreshStatusBlocking()
            bindServiceIfNeededBlocking()
        }
    }

    private fun bindServiceIfNeededBlocking() {
        val current = _status.value
        if (current.isBinderAlive && current.hasPermission && userService == null) {
            runCatching {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        }
    }

    fun unbindService() {
        val bound = userService ?: return
        userService = null
        runCatching {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        }
        if (bound != null) {
            _status.value = _status.value.copy(isUserServiceBound = false)
        }
    }

    fun openShizukuApp(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    suspend fun executeCommand(command: String): EngineExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        refreshStatusBlocking()
        val current = _status.value

        if (!current.isInstalled) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = "",
                stderr = "Shizuku no está instalado en este dispositivo (paquete: $SHIZUKU_PACKAGE).",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }
        if (!current.isBinderAlive) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = "",
                stderr = "El servicio Shizuku está inactivo. Ábrelo e inicia el servicio por Depuración Inalámbrica o Root.",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }
        if (!current.hasPermission) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = "",
                stderr = "Falta permiso de Shizuku. Pulsa 'Autorizar' en la tarjeta de Shizuku.",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        val service = ensureUserService()
        if (service == null) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = "",
                stderr = "No se pudo enlazar UserService de Shizuku. Verifica que Shizuku siga activo.",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        try {
            val bundle = service.executeCommand(command)
            val stdout = bundle.getString("stdout").orEmpty()
            val stderr = bundle.getString("stderr").orEmpty()
            val exitCode = bundle.getInt("exitCode", -1)
            val uid = bundle.getInt("uid", current.uid)
            val uidLabel = when (uid) {
                0 -> "Root (UID 0)"
                2000 -> "ADB (UID 2000)"
                else -> "UID $uid"
            }
            EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                durationMs = System.currentTimeMillis() - startMs,
                uidLabel = uidLabel
            )
        } catch (t: Throwable) {
            userService = null
            refreshStatusBlocking()
            EngineExecutionResult(
                engine = CommandEngine.SHIZUKU,
                command = command,
                stdout = "",
                stderr = t.message ?: "Error IPC con Shizuku UserService",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }
    }

    private suspend fun ensureUserService(): IShellUserService? = bindMutex.withLock {
        userService?.let { return it }
        val deferred = CompletableDeferred<IShellUserService?>()
        pendingBindDeferred = deferred
        val requested = runCatching {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
            true
        }.getOrDefault(false)
        if (!requested) {
            pendingBindDeferred = null
            return null
        }
        return withTimeoutOrNull(5000L) {
            deferred.await()
        }.also {
            if (it == null) pendingBindDeferred = null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1042
    }
}
