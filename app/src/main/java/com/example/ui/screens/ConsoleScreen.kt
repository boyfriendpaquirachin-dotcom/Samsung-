package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatteryTelemetry
import com.example.model.CommandEngine
import com.example.model.ConnectionHealth
import com.example.model.EngineExecutionResult
import com.example.model.ShizukuStatus
import com.example.model.TermuxStatus
import com.example.ui.components.EngineBadge
import com.example.ui.components.StatusPill
import com.example.ui.components.engineAccentColor
import com.example.ui.components.healthColor
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.DualTeal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleScreen(
    selectedEngine: CommandEngine,
    commandInput: String,
    isExecuting: Boolean,
    termuxBackground: Boolean,
    shizukuStatus: ShizukuStatus,
    termuxStatus: TermuxStatus,
    batteryTelemetry: BatteryTelemetry,
    logs: List<EngineExecutionResult>,
    onSelectEngine: (CommandEngine) -> Unit,
    onCommandInputChange: (String) -> Unit,
    onToggleTermuxBackground: (Boolean) -> Unit,
    onExecuteCommand: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onBindShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAuthorizeTermux: () -> Unit,
    onOpenBridgeGuide: () -> Unit,
    onOpenTermux: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val quickSnippets = when (selectedEngine) {
        CommandEngine.BOTH -> listOf(
            "id && uname -a",
            "uptime && df -h /data",
            "dumpsys battery | head -n 14",
            "echo \$PATH"
        )
        CommandEngine.SHIZUKU -> listOf(
            "cmd power set-mode 1 && settings put system peak_refresh_rate 60.0",
            "settings put system peak_refresh_rate 120.0 && settings put system min_refresh_rate 60.0",
            "dumpsys battery",
            "id",
            "pm list packages -3 | head -n 15",
            "dumpsys thermalservice | head -n 20"
        )
        CommandEngine.TERMUX -> listOf(
            "uname -a",
            "termux-battery-status",
            "pkg list-installed | head -n 15",
            "df -h \$PREFIX"
        )
        CommandEngine.LOCAL -> listOf(
            "getprop ro.product.model",
            "uptime",
            "cat /proc/meminfo | head -n 5",
            "ip -brief addr show"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 920.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Consola Dual",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Controla Shizuku y Termux juntos o por separado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Live compact battery pill in console header
                val batColor = when {
                    batteryTelemetry.isOverheating -> AlertRed
                    batteryTelemetry.isCharging -> TermuxEmerald
                    batteryTelemetry.level <= 20 -> BatteryAmber
                    else -> ShizukuCyan
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = batColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, batColor.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (batteryTelemetry.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                            contentDescription = "Batería en vivo",
                            tint = batColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%d%% • %.1f°C",
                                batteryTelemetry.level,
                                batteryTelemetry.temperatureCelsius
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = batColor
                        )
                    }
                }

                if (logs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Limpiar historial de consola",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Direct Shizuku + Termux quick control strip right inside Console
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactEngineControlPill(
                    name = "Shizuku",
                    accent = ShizukuCyan,
                    health = shizukuStatus.health,
                    detail = if (shizukuStatus.health == ConnectionHealth.CONNECTED) shizukuStatus.privilegeLabel else null,
                    actionLabel = when (shizukuStatus.health) {
                        ConnectionHealth.CONNECTED -> if (!shizukuStatus.isUserServiceBound) "Enlazar" else null
                        ConnectionHealth.PERMISSION_REQUIRED -> "Autorizar"
                        ConnectionHealth.INACTIVE, ConnectionHealth.NOT_INSTALLED -> "Abrir"
                    },
                    onAction = {
                        when (shizukuStatus.health) {
                            ConnectionHealth.CONNECTED -> onBindShizuku()
                            ConnectionHealth.PERMISSION_REQUIRED -> onAuthorizeShizuku()
                            ConnectionHealth.INACTIVE, ConnectionHealth.NOT_INSTALLED -> onOpenShizuku()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                CompactEngineControlPill(
                    name = "Termux",
                    accent = TermuxEmerald,
                    health = termuxStatus.health,
                    detail = when {
                        termuxStatus.health == ConnectionHealth.CONNECTED -> termuxStatus.modeLabel
                        termuxStatus.health == ConnectionHealth.PERMISSION_REQUIRED && !termuxStatus.supportsRunCommand -> "Usar Puente"
                        else -> null
                    },
                    actionLabel = when (termuxStatus.health) {
                        ConnectionHealth.CONNECTED -> "Abrir"
                        ConnectionHealth.PERMISSION_REQUIRED -> if (termuxStatus.supportsRunCommand) "Autorizar" else "Puente"
                        ConnectionHealth.INACTIVE, ConnectionHealth.NOT_INSTALLED -> "Abrir"
                    },
                    onAction = {
                        when (termuxStatus.health) {
                            ConnectionHealth.PERMISSION_REQUIRED -> {
                                if (termuxStatus.supportsRunCommand) onAuthorizeTermux() else onOpenBridgeGuide()
                            }
                            else -> onOpenTermux()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Engine Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandEngine.entries.forEach { engine ->
                    val accent = engineAccentColor(engine)
                    val dotColor = when (engine) {
                        CommandEngine.BOTH -> {
                            if (shizukuStatus.health == ConnectionHealth.CONNECTED &&
                                termuxStatus.health == ConnectionHealth.CONNECTED
                            ) TermuxEmerald else DualTeal
                        }
                        CommandEngine.SHIZUKU -> healthColor(shizukuStatus.health)
                        CommandEngine.TERMUX -> healthColor(termuxStatus.health)
                        CommandEngine.LOCAL -> TermuxEmerald
                    }
                    FilterChip(
                        selected = selectedEngine == engine,
                        onClick = { onSelectEngine(engine) },
                        leadingIcon = {
                            Surface(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape),
                                color = dotColor
                            ) {}
                        },
                        label = { Text("${engine.label} (${engine.shortLabel})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        ),
                        modifier = Modifier.testTag("engine_chip_${engine.name.lowercase()}")
                    )
                }
            }

            if (selectedEngine == CommandEngine.TERMUX || selectedEngine == CommandEngine.BOTH) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Termux en segundo plano",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (termuxBackground) "Captura stdout/stderr en esta consola" else "Abre sesión interactiva en Termux",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = termuxBackground,
                            onCheckedChange = onToggleTermuxBackground
                        )
                    }
                }
            }

            // Quick snippet chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickSnippets.forEach { snippet ->
                    AssistChip(
                        onClick = { onCommandInputChange(snippet) },
                        label = {
                            Text(
                                text = snippet,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // Command Input + Execute Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = onCommandInputChange,
                    label = { Text("Comando (${selectedEngine.label})") },
                    placeholder = { Text("Ej: id && uname -a (o usa @shizuku / @termux)") },
                    textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    maxLines = 4,
                    trailingIcon = {
                        if (commandInput.isNotEmpty()) {
                            IconButton(onClick = { onCommandInputChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Borrar comando"
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isExecuting) onExecuteCommand() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input")
                )

                val buttonAccent = engineAccentColor(selectedEngine)
                Button(
                    onClick = onExecuteCommand,
                    enabled = !isExecuting && commandInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonAccent,
                        contentColor = Color(0xFF06131A)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .padding(top = 6.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("run_command_button")
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF06131A)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Ejecutar comando"
                        )
                    }
                }
            }

            val engineWarning = when (selectedEngine) {
                CommandEngine.BOTH -> buildList {
                    if (shizukuStatus.health != ConnectionHealth.CONNECTED) {
                        add("Shizuku: ${if (shizukuStatus.isInstalled) "pulsa ${if (shizukuStatus.isBinderAlive) "Autorizar" else "Abrir"}" else "no instalado"}")
                    }
                    if (termuxStatus.health != ConnectionHealth.CONNECTED) {
                        add("Termux: ${if (termuxStatus.isInstalled) "activa Puente Local en Monitor o Autorizar" else "no instalado"}")
                    }
                }.takeIf { it.isNotEmpty() }?.joinToString(" • ")
                CommandEngine.SHIZUKU -> when (shizukuStatus.health) {
                    ConnectionHealth.NOT_INSTALLED -> "Shizuku no está instalado. Cambia a Shell Local o instala Shizuku."
                    ConnectionHealth.INACTIVE -> "Shizuku está inactivo. Pulsa 'Abrir' arriba para iniciar el servicio."
                    ConnectionHealth.PERMISSION_REQUIRED -> "Pulsa 'Autorizar' en la barra de Shizuku."
                    ConnectionHealth.CONNECTED -> null
                }
                CommandEngine.TERMUX -> when (termuxStatus.health) {
                    ConnectionHealth.NOT_INSTALLED -> "Termux no está instalado. Cambia a Shell Local o instala Termux."
                    ConnectionHealth.PERMISSION_REQUIRED -> if (!termuxStatus.supportsRunCommand) {
                        "Termux bloquea RUN_COMMAND. Pulsa 'Puente' arriba para activar el Puente Socket v2 (127.0.0.1:18765)."
                    } else {
                        "Pulsa 'Puente' arriba (127.0.0.1:18765) o 'Autorizar' para conectar Termux."
                    }
                    ConnectionHealth.INACTIVE -> "Termux inactivo."
                    ConnectionHealth.CONNECTED -> null
                }
                CommandEngine.LOCAL -> null
            }

            if (engineWarning != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = engineWarning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Output Stream
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 920.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Consola unificada lista",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Usa 'Ambos' para ejecutar en Shizuku y Termux simultáneamente, o prefijos @shizuku / @termux.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 920.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = logs,
                    key = { log ->
                        if (log.logId != 0) log.logId else "${log.engine.name}_${log.timestampMs}_${log.command.hashCode()}"
                    }
                ) { log ->
                    TerminalLogCard(log = log, onCopyNotice = onCopyNotice)
                }
            }
        }
    }
}

@Composable
private fun CompactEngineControlPill(
    name: String,
    accent: Color,
    health: ConnectionHealth,
    detail: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1
                )
                StatusPill(
                    health = health,
                    customText = detail,
                    modifier = Modifier
                )
            }
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLogCard(
    log: EngineExecutionResult,
    onCopyNotice: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val timeText = androidx.compose.runtime.remember(log.timestampMs) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestampMs))
    }
    val displayStdout = androidx.compose.runtime.remember(log.stdout) {
        if (log.stdout.length > 6000) log.stdout.take(6000) + "\n… [salida resumida en vista]" else log.stdout
    }
    val displayStderr = androidx.compose.runtime.remember(log.stderr) {
        if (log.stderr.length > 3000) log.stderr.take(3000) + "\n… [stderr resumido]" else log.stderr
    }
    val success = log.exitCode == 0
    val exitColor = if (success) TermuxEmerald else AlertRed

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF080B10),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    EngineBadge(engine = log.engine)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = exitColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "EXIT ${log.exitCode}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = exitColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "$timeText • ${log.durationMs}ms" + (log.uidLabel?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        val full = buildString {
                            appendLine("$ ${log.command}")
                            if (log.stdout.isNotBlank()) appendLine(log.stdout)
                            if (log.stderr.isNotBlank()) appendLine(log.stderr)
                        }.trim()
                        clipboard.setText(AnnotatedString(full))
                        onCopyNotice("Salida copiada al portapapeles.")
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copiar salida del comando",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "$ ${log.command}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = engineAccentColor(log.engine)
            )

            if (displayStdout.isNotBlank()) {
                Text(
                    text = displayStdout,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = Color(0xFFE2E8F0)
                )
            }

            if (displayStderr.isNotBlank()) {
                Text(
                    text = displayStderr,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = AlertRed
                )
            }

            if (displayStdout.isBlank() && displayStderr.isBlank()) {
                Text(
                    text = "(sin salida de texto)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
