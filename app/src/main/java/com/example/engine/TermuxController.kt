package com.example.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.CommandEngine
import com.example.model.EngineExecutionResult
import com.example.model.TermuxStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TermuxCallbackPayload(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val errorMessage: String
)

object TermuxResultBus {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxCallbackPayload>>()
    private val nextId = AtomicInteger(2000)

    fun allocate(deferred: CompletableDeferred<TermuxCallbackPayload>): Int {
        val id = nextId.incrementAndGet()
        pending[id] = deferred
        return id
    }

    fun complete(executionId: Int, payload: TermuxCallbackPayload) {
        pending.remove(executionId)?.complete(payload)
    }

    fun cancel(executionId: Int) {
        pending.remove(executionId)
    }
}

class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        if (executionId == -1) return

        val resultBundle = intent.getBundleExtra("result")
        val stdout = resultBundle?.getString("stdout").orEmpty()
        val stderr = resultBundle?.getString("stderr").orEmpty()
        val exitCode = resultBundle?.getInt("exitCode", 0) ?: 0
        val errmsg = resultBundle?.getString("errmsg").orEmpty()

        TermuxResultBus.complete(
            executionId = executionId,
            payload = TermuxCallbackPayload(
                stdout = stdout.trimEnd(),
                stderr = stderr.trimEnd(),
                exitCode = exitCode,
                errorMessage = errmsg.trim()
            )
        )
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "termuzuku_execution_id"
    }
}

class TermuxController(private val context: Context) {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(TermuxStatus())
    val status: StateFlow<TermuxStatus> = _status.asStateFlow()

    fun refreshStatus(runInBackground: Boolean = _status.value.runInBackground) {
        ioScope.launch {
            val installed = isPackageInstalled(TERMUX_PACKAGE)
            val apiInstalled = isPackageInstalled(TERMUX_API_PACKAGE)
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                PERMISSION_RUN_COMMAND
            ) == PackageManager.PERMISSION_GRANTED
            val caps = inspectTermuxManifestCapabilities()

            _status.value = _status.value.copy(
                isInstalled = installed,
                isTermuxApiInstalled = apiInstalled,
                hasRunCommandPermission = hasPerm,
                runInBackground = runInBackground,
                termuxVersionName = caps.versionName,
                runCommandDeclaredInTermux = caps.declaredPermission,
                runCommandIsRuntimeGrantable = caps.runtimeGrantable,
                runCommandServiceExported = caps.serviceExported
            )
        }
    }

    suspend fun refreshStatusAsync(
        runInBackground: Boolean = _status.value.runInBackground,
        bridgeToken: String,
        bridgePort: Int = DEFAULT_BRIDGE_PORT
    ) = withContext(Dispatchers.IO) {
        val installed = isPackageInstalled(TERMUX_PACKAGE)
        val apiInstalled = isPackageInstalled(TERMUX_API_PACKAGE)
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            PERMISSION_RUN_COMMAND
        ) == PackageManager.PERMISSION_GRANTED
        val caps = inspectTermuxManifestCapabilities()
        val bridgeAlive = if (bridgeToken.isNotBlank()) {
            checkLocalBridgeAlive(bridgePort, bridgeToken)
        } else {
            false
        }

        _status.value = TermuxStatus(
            isInstalled = installed,
            isTermuxApiInstalled = apiInstalled,
            hasRunCommandPermission = hasPerm,
            isLocalBridgeOnline = bridgeAlive,
            bridgePort = bridgePort,
            runInBackground = runInBackground,
            termuxVersionName = caps.versionName,
            runCommandDeclaredInTermux = caps.declaredPermission,
            runCommandIsRuntimeGrantable = caps.runtimeGrantable,
            runCommandServiceExported = caps.serviceExported
        )
    }

    fun openTermuxApp(): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return true
    }

    suspend fun executeCommand(
        command: String,
        runInBackground: Boolean,
        bridgeToken: String = "",
        bridgePort: Int = DEFAULT_BRIDGE_PORT
    ): EngineExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        refreshStatusAsync(runInBackground, bridgeToken, bridgePort)
        val current = _status.value

        // 1. Priority: Universal Local Bridge (127.0.0.1:8765) - works on Play Store, F-Droid & GitHub Termux
        if (current.isLocalBridgeOnline && bridgeToken.isNotBlank()) {
            val bridgeResult = executeViaLocalBridge(command, bridgeToken, bridgePort, startMs)
            if (bridgeResult != null) {
                return@withContext bridgeResult
            }
        }

        if (!current.isInstalled) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = "",
                stderr = "Termux no está instalado en este dispositivo (paquete: $TERMUX_PACKAGE).",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        // 2. Fallback: Termux RUN_COMMAND service (F-Droid / GitHub builds)
        if (!current.hasRunCommandPermission) {
            return@withContext EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = "",
                stderr = "Termux aún no está enlazado. Si usas Termux de Google Play, pulsa 'Conectar Puente' para activar el Puente Local 127.0.0.1:$bridgePort (no requiere permiso RUN_COMMAND).",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        val deferred = CompletableDeferred<TermuxCallbackPayload>()
        val executionId = TermuxResultBus.allocate(deferred)

        val callbackIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            putExtra(TermuxResultReceiver.EXTRA_EXECUTION_ID, executionId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, executionId, callbackIntent, flags)

        val serviceIntent = Intent().apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_RUN_SERVICE)
            action = ACTION_RUN_COMMAND
            putExtra(EXTRA_COMMAND_PATH, TERMUX_BASH_PATH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
            putExtra(EXTRA_WORKDIR, TERMUX_HOME_PATH)
            putExtra(EXTRA_BACKGROUND, runInBackground)
            putExtra(EXTRA_SESSION_ACTION, "0")
            putExtra(EXTRA_PENDING_INTENT, pendingIntent)
        }

        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runInBackground) {
                try {
                    context.startForegroundService(serviceIntent)
                } catch (_: Throwable) {
                    context.startService(serviceIntent)
                }
            } else {
                context.startService(serviceIntent)
            }
            true
        }

        if (started.isFailure) {
            TermuxResultBus.cancel(executionId)
            val err = started.exceptionOrNull()?.message ?: "Error al iniciar RunCommandService de Termux"
            return@withContext EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = "",
                stderr = "$err\nUsa el Puente Local Universal (127.0.0.1:$bridgePort) desde la Guía de conexión para compatibilidad total.",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        }

        if (!runInBackground) {
            TermuxResultBus.cancel(executionId)
            return@withContext EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = "Comando enviado a sesión interactiva de Termux.",
                stderr = "",
                exitCode = 0,
                durationMs = System.currentTimeMillis() - startMs,
                uidLabel = "Termux Session"
            )
        }

        val payload = withTimeoutOrNull(18_000L) {
            deferred.await()
        }

        if (payload == null) {
            TermuxResultBus.cancel(executionId)
            EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = "",
                stderr = "Termux no devolvió respuesta por RUN_COMMAND. Activa el Puente Local Universal (127.0.0.1:$bridgePort) en la Guía para que funcione en cualquier versión.",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs
            )
        } else {
            val combinedErr = buildString {
                if (payload.stderr.isNotBlank()) append(payload.stderr)
                if (payload.errorMessage.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(payload.errorMessage)
                }
            }
            EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = payload.stdout,
                stderr = combinedErr,
                exitCode = payload.exitCode,
                durationMs = System.currentTimeMillis() - startMs,
                uidLabel = "Termux"
            )
        }
    }

    suspend fun checkLocalBridgeAlive(
        port: Int = DEFAULT_BRIDGE_PORT,
        token: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                soTimeout = 900
                connect(InetSocketAddress("127.0.0.1", port), 700)
            }
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val pingJson = JSONObject()
                .put("t", token)
                .put("p", true)
                .toString()
            writer.write(pingJson)
            writer.write("\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val line = reader.readLine() ?: return@withContext false
            JSONObject(line).optBoolean("ok", false)
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { socket?.close() }
        }
    }

    private suspend fun executeViaLocalBridge(
        command: String,
        token: String,
        port: Int,
        startMs: Long
    ): EngineExecutionResult? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket().apply {
                soTimeout = 28_000
                connect(InetSocketAddress("127.0.0.1", port), 1500)
            }
            val writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val reqJson = JSONObject()
                .put("t", token)
                .put("c", command)
                .toString()
            writer.write(reqJson)
            writer.write("\n")
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val line = reader.readLine() ?: return@withContext null
            val json = JSONObject(line)
            val err = json.optString("err", "").trimEnd()
            val formattedErr = if (err == "auth") {
                "Token desactualizado en Termux. Pulsa 'Conectar Puente' para actualizar el puente."
            } else {
                err
            }
            EngineExecutionResult(
                engine = CommandEngine.TERMUX,
                command = command,
                stdout = json.optString("out", "").trimEnd(),
                stderr = formattedErr,
                exitCode = json.optInt("e", -1),
                durationMs = System.currentTimeMillis() - startMs,
                uidLabel = "Termux (Socket $port)"
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { socket?.close() }
        }
    }

    private data class TermuxManifestCapabilities(
        val versionName: String? = null,
        val declaredPermission: Boolean = false,
        val runtimeGrantable: Boolean = false,
        val serviceExported: Boolean = false
    )

    private fun inspectTermuxManifestCapabilities(): TermuxManifestCapabilities {
        return runCatching {
            val flags = (PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES).toLong()
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(flags)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    TERMUX_PACKAGE,
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
                )
            }
            val permInfo = pkgInfo.permissions?.firstOrNull { it.name == PERMISSION_RUN_COMMAND }
            val declared = permInfo != null
            val grantable = if (permInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    permInfo.protection == PermissionInfo.PROTECTION_DANGEROUS
                } else {
                    @Suppress("DEPRECATION")
                    (permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) == PermissionInfo.PROTECTION_DANGEROUS
                }
            } else {
                false
            }
            val serviceExported = pkgInfo.services?.firstOrNull {
                it.name == TERMUX_RUN_SERVICE
            }?.exported == true
            TermuxManifestCapabilities(
                versionName = pkgInfo.versionName,
                declaredPermission = declared,
                runtimeGrantable = grantable,
                serviceExported = serviceExported
            )
        }.getOrDefault(TermuxManifestCapabilities())
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_API_PACKAGE = "com.termux.api"
        const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
        const val TERMUX_RUN_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
        const val TERMUX_HOME_PATH = "/data/data/com.termux/files/home"
        const val DEFAULT_BRIDGE_PORT = 18765

        fun buildLocalBridgeSetupCommand(
            token: String,
            port: Int = DEFAULT_BRIDGE_PORT
        ): String {
            val pyLines = listOf(
                "import hmac,json,os,socket,subprocess,threading,time",
                "me=os.getpid()",
                "for p in os.listdir('/proc'):",
                " if p.isdigit() and int(p)!=me:",
                "  try:",
                "   d=open('/proc/'+p+'/cmdline','rb').read()",
                "   if b'tz_bridge.py' in d or b'termuzuku_bridge.py' in d: os.kill(int(p),9)",
                "  except: pass",
                "time.sleep(0.15)",
                "tp=os.path.expanduser('~/.tz_token')",
                "def rt():",
                " try: return open(tp).read().strip()",
                " except: return ''",
                "def h(c):",
                " with c,c.makefile() as f:",
                "  try:",
                "   r=json.loads(f.readline())",
                "   if not hmac.compare_digest(rt(),r.get('t','')): out={'e':-1,'err':'auth'}",
                "   elif r.get('p'): out={'ok':True}",
                "   else:",
                "    q=subprocess.run(['bash','-c',r.get('c','')],capture_output=True,text=True,timeout=25)",
                "    out={'out':q.stdout,'err':q.stderr,'e':q.returncode}",
                "  except Exception as x: out={'e':-1,'err':str(x)}",
                "  c.sendall((json.dumps(out)+chr(10)).encode())",
                "s=socket.socket()",
                "s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)",
                "s.bind(('127.0.0.1',$port))",
                "s.listen(8)",
                "while True:",
                " c,_=s.accept()",
                " threading.Thread(target=h,args=(c,),daemon=True).start()"
            )
            val printfArgs = pyLines.joinToString(" ") { "\"$it\"" }

            val autoStartLine =
                "python3 -c \"import socket;socket.create_connection(('127.0.0.1',$port),0.3).close()\" 2>/dev/null || nohup python3 ~/.tz_bridge.py </dev/null >/dev/null 2>&1 &"
            val escapedAutoStart = autoStartLine.replace("\"", "\\\"")

            val verifyPy = listOf(
                "import json,socket,time",
                "s=None",
                "for _ in range(15):",
                " try: s=socket.create_connection((\"127.0.0.1\",$port),2); break",
                " except: time.sleep(0.2)",
                "s.sendall((json.dumps({\"t\":\"$token\",\"p\":True})+chr(10)).encode())",
                "print(\"OK Puente Socket v2:\",s.makefile().readline().strip())",
                "s.close()"
            ).joinToString("\\n")

            return "(command -v python3 >/dev/null 2>&1 || (pkg update -y && pkg install -y python)) && " +
                "printf '%s\\n' $printfArgs > ~/.tz_bridge.py && " +
                "echo '$token' > ~/.tz_token && chmod 600 ~/.tz_token && " +
                "touch ~/.bashrc && sed -i '/termuzuku_bridge/d;/tz_bridge/d' ~/.bashrc && " +
                "printf '%s\\n' \"$escapedAutoStart\" >> ~/.bashrc && " +
                "(nohup python3 ~/.tz_bridge.py </dev/null >/dev/null 2>&1 &) && " +
                "python3 -c $'$verifyPy'"
        }
    }
}
