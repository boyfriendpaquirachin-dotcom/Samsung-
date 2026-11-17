package com.example.engine

import android.os.Process
import com.example.model.CommandEngine
import com.example.model.EngineExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class LocalShellExecutor {
    suspend fun executeCommand(command: String): EngineExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(false)
                .start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val outThread = Thread {
                stdoutReader.useLines { lines ->
                    lines.forEach { line ->
                        if (stdoutBuilder.length < 48_000) stdoutBuilder.appendLine(line)
                    }
                }
            }
            val errThread = Thread {
                stderrReader.useLines { lines ->
                    lines.forEach { line ->
                        if (stderrBuilder.length < 48_000) stderrBuilder.appendLine(line)
                    }
                }
            }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(15L, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                EngineExecutionResult(
                    engine = CommandEngine.LOCAL,
                    command = command,
                    stdout = stdoutBuilder.toString().trimEnd(),
                    stderr = (stderrBuilder.toString() + "\n[Tiempo de espera agotado tras 15s]").trim(),
                    exitCode = -1,
                    durationMs = System.currentTimeMillis() - startMs,
                    uidLabel = "App UID ${Process.myUid()}"
                )
            } else {
                outThread.join(1000)
                errThread.join(1000)
                EngineExecutionResult(
                    engine = CommandEngine.LOCAL,
                    command = command,
                    stdout = stdoutBuilder.toString().trimEnd(),
                    stderr = stderrBuilder.toString().trimEnd(),
                    exitCode = process.exitValue(),
                    durationMs = System.currentTimeMillis() - startMs,
                    uidLabel = "App UID ${Process.myUid()}"
                )
            }
        } catch (t: Throwable) {
            EngineExecutionResult(
                engine = CommandEngine.LOCAL,
                command = command,
                stdout = "",
                stderr = t.message ?: "Error al ejecutar shell local",
                exitCode = -1,
                durationMs = System.currentTimeMillis() - startMs,
                uidLabel = "App UID ${Process.myUid()}"
            )
        }
    }
}
