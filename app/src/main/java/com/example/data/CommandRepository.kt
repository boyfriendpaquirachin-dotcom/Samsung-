package com.example.data

import com.example.model.CommandEngine
import com.example.model.EngineExecutionResult
import com.example.model.FunctionCategory
import com.example.model.SystemFunctionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CommandRepository(private val commandDao: CommandDao) {

    val customFunctions: Flow<List<SystemFunctionPreset>> =
        commandDao.observeSavedCommands().map { list ->
            list.map { entity ->
                val engine = runCatching { CommandEngine.valueOf(entity.engineName) }
                    .getOrDefault(CommandEngine.LOCAL)
                SystemFunctionPreset(
                    id = "custom_${entity.id}",
                    title = entity.title,
                    description = entity.description,
                    command = entity.command,
                    engine = engine,
                    category = FunctionCategory.CUSTOM,
                    isCustom = true,
                    customDbId = entity.id,
                    isFavorite = entity.isFavorite
                )
            }
        }

    val recentLogs: Flow<List<EngineExecutionResult>> =
        commandDao.observeRecentLogs().map { list ->
            list.map { log ->
                val engine = runCatching { CommandEngine.valueOf(log.engineName) }
                    .getOrDefault(CommandEngine.LOCAL)
                EngineExecutionResult(
                    logId = log.id,
                    engine = engine,
                    command = log.command,
                    stdout = log.stdout,
                    stderr = log.stderr,
                    exitCode = log.exitCode,
                    durationMs = log.durationMs,
                    uidLabel = log.uidLabel,
                    timestampMs = log.timestampMs
                )
            }
        }

    suspend fun saveCustomCommand(
        title: String,
        description: String,
        command: String,
        engine: CommandEngine
    ) {
        commandDao.insertSavedCommand(
            SavedCommandEntity(
                title = title.trim(),
                description = description.trim().ifEmpty { "Comando personalizado (${engine.label})" },
                command = command.trim(),
                engineName = engine.name
            )
        )
    }

    suspend fun deleteCustomCommand(dbId: Int) {
        commandDao.deleteSavedCommandById(dbId)
    }

    suspend fun toggleFavorite(dbId: Int, favorite: Boolean) {
        commandDao.setFavorite(dbId, favorite)
    }

    suspend fun recordExecution(result: EngineExecutionResult) {
        val safeStdout = if (result.stdout.length > 12_000) {
            result.stdout.take(12_000) + "\n… [salida recortada para mantener fluida la consola]"
        } else {
            result.stdout
        }
        val safeStderr = if (result.stderr.length > 6_000) {
            result.stderr.take(6_000) + "\n… [stderr recortado]"
        } else {
            result.stderr
        }
        commandDao.insertLog(
            CommandLogEntity(
                engineName = result.engine.name,
                command = result.command.take(2_000),
                stdout = safeStdout,
                stderr = safeStderr,
                exitCode = result.exitCode,
                durationMs = result.durationMs,
                uidLabel = result.uidLabel,
                timestampMs = result.timestampMs
            )
        )
    }

    suspend fun clearHistory() {
        commandDao.clearLogs()
    }
}
