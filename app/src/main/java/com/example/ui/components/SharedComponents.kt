package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.CommandEngine
import com.example.model.ConnectionHealth
import com.example.model.TermuxStatus
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.DualTeal
import com.example.ui.theme.LocalViolet
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald

@Composable
fun engineAccentColor(engine: CommandEngine): Color = when (engine) {
    CommandEngine.BOTH -> DualTeal
    CommandEngine.SHIZUKU -> ShizukuCyan
    CommandEngine.TERMUX -> TermuxEmerald
    CommandEngine.LOCAL -> LocalViolet
}

@Composable
fun healthColor(health: ConnectionHealth): Color = when (health) {
    ConnectionHealth.CONNECTED -> TermuxEmerald
    ConnectionHealth.PERMISSION_REQUIRED -> BatteryAmber
    ConnectionHealth.INACTIVE -> BatteryAmber
    ConnectionHealth.NOT_INSTALLED -> MaterialTheme.colorScheme.error
}

@Composable
fun healthLabel(health: ConnectionHealth): String = when (health) {
    ConnectionHealth.CONNECTED -> "Conectado"
    ConnectionHealth.PERMISSION_REQUIRED -> "Sin permiso"
    ConnectionHealth.INACTIVE -> "Inactivo"
    ConnectionHealth.NOT_INSTALLED -> "No instalado"
}

@Composable
fun EngineBadge(
    engine: CommandEngine,
    modifier: Modifier = Modifier
) {
    val accent = engineAccentColor(engine)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Text(
            text = engine.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun StatusPill(
    health: ConnectionHealth,
    customText: String? = null,
    modifier: Modifier = Modifier
) {
    val color = healthColor(health)
    val label = customText ?: healthLabel(health)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideBottomSheet(
    bridgeCommand: String,
    termuxStatus: TermuxStatus,
    onDismiss: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenTermux: () -> Unit,
    onVerifyBridge: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Guía de Activación Shizuku & Termux",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Shizuku",
                            tint = ShizukuCyan
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "1. Activar Shizuku (ADB / Root)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Abre la app Shizuku e inicia el servicio mediante Depuración Inalámbrica (Android 11+) o Root. Luego regresa aquí y pulsa 'Autorizar'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onOpenShizuku,
                        border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.5f))
                    ) {
                        Text("Abrir Shizuku", color = ShizukuCyan)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = "Termux",
                                tint = TermuxEmerald
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "2. Puente Socket v2 (Termux)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        StatusPill(
                            health = termuxStatus.health,
                            customText = when {
                                termuxStatus.isLocalBridgeOnline -> "Puente activo"
                                termuxStatus.health == ConnectionHealth.PERMISSION_REQUIRED && !termuxStatus.supportsRunCommand -> "Usar Puente"
                                else -> null
                            }
                        )
                    }
                    Text(
                        text = buildString {
                            append("Termux detectado: ")
                            append(termuxStatus.termuxVersionName ?: if (termuxStatus.isInstalled) "Instalado" else "No instalado")
                            append(" • RUN_COMMAND: ")
                            append(if (termuxStatus.supportsRunCommand) "Permitido por esta compilación" else "Bloqueado por esta versión (Play Store)")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = BatteryAmber
                    )
                    Text(
                        text = "Nuevo puente TCP directo con token en 127.0.0.1:18765 (limpia puentes viejos y sin HTTP 403 ni base64):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bridgeCommand.ifBlank { "Generando token..." },
                                style = MaterialTheme.typography.labelSmall,
                                color = TermuxEmerald,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (bridgeCommand.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(bridgeCommand))
                                        onCopyCommand("Script de Puente Local copiado. Pégalo en Termux y presiona Enter.")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copiar script de puente Termux",
                                    tint = TermuxEmerald
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (bridgeCommand.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(bridgeCommand))
                                    onCopyCommand("Script copiado. Pégalo en Termux y pulsa Enter.")
                                }
                                onOpenTermux()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TermuxEmerald,
                                contentColor = Color(0xFF002110)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copiar y abrir Termux", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onVerifyBridge,
                            border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.55f))
                        ) {
                            Text("Verificar", color = TermuxEmerald)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
