package com.example.shizuku

import android.os.Bundle
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class ShellUserService : IShellUserService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun getUid(): Int {
        return Process.myUid()
    }

    override fun executeCommand(command: String): Bundle {
        val result = Bundle()
        result.putInt("uid", Process.myUid())
        return try {
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
                        if (stdoutBuilder.length < MAX_OUTPUT_CHARS) {
                            stdoutBuilder.appendLine(line)
                        }
                    }
                }
            }
            val errThread = Thread {
                stderrReader.useLines { lines ->
                    lines.forEach { line ->
                        if (stderrBuilder.length < MAX_OUTPUT_CHARS) {
                            stderrBuilder.appendLine(line)
                        }
                    }
                }
            }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                result.putInt("exitCode", -1)
                result.putString("stdout", stdoutBuilder.toString().trimEnd())
                result.putString(
                    "stderr",
                    (stderrBuilder.toString() + "\n[Tiempo de espera agotado tras ${COMMAND_TIMEOUT_SECONDS}s]").trim()
                )
            } else {
                outThread.join(1000)
                errThread.join(1000)
                result.putInt("exitCode", process.exitValue())
                result.putString("stdout", stdoutBuilder.toString().trimEnd())
                result.putString("stderr", stderrBuilder.toString().trimEnd())
            }
            result
        } catch (t: Throwable) {
            result.putInt("exitCode", -1)
            result.putString("stdout", "")
            result.putString("stderr", t.message ?: "Error al ejecutar comando en Shizuku")
            result
        }
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 25L
        const val MAX_OUTPUT_CHARS = 64_000
    }
}
