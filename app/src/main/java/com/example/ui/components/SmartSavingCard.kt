package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SmartSavingStage
import com.example.model.SmartSavingState
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.DualTeal
import com.example.ui.theme.LocalViolet
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald

@Composable
fun SmartSavingCard(
    smartState: SmartSavingState,
    onToggleSmartSaving: () -> Unit,
    onToggleSmartStandbySaving: () -> Unit,
    onToggleBluetoothEco: () -> Unit,
    onToggleTorchEco: () -> Unit,
    onToggleAppGameEco: () -> Unit,
    onToggleAppFreezeEco: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stageAccent = when (smartState.stage) {
        SmartSavingStage.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        SmartSavingStage.CHARGING_NORMAL -> ShizukuCyan
        SmartSavingStage.BALANCED_120HZ -> DualTeal
        SmartSavingStage.ECO_60HZ -> TermuxEmerald
        SmartSavingStage.CRITICAL_SAVER -> BatteryAmber
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_saving_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (smartState.enabled || smartState.standbyEnabled || smartState.appGameEcoEnabled || smartState.appFreezeEcoEnabled) {
                DualTeal.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DualTeal.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Ahorro inteligente",
                            tint = DualTeal,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ahorro Inteligente",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ajusta automáticamente 120Hz, 60Hz y Ahorro según batería y calor",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, stageAccent.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estado actual",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = smartState.stage.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = stageAccent
                        )
                    }
                    Text(
                        text = "• ≥45% o cargando: 120Hz adaptable\n• 21%–44%: 60Hz pOLED + batería adaptativa\n• ≤20% o ≥41°C: Ahorro Samsung completo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (smartState.lastActionLabel != null) {
                        Text(
                            text = "Último ajuste: ${smartState.lastActionLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DualTeal
                        )
                    }
                }
            }

            if (smartState.enabled) {
                OutlinedButton(
                    onClick = onToggleSmartSaving,
                    border = BorderStroke(1.dp, DualTeal.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("smart_saving_button")
                ) {
                    Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = DualTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Desactivar ahorro inteligente", color = DualTeal, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onToggleSmartSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DualTeal,
                        contentColor = Color(0xFF041D1B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("smart_saving_button")
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activar ahorro inteligente", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            SmartEcoOptionBlock(
                icon = Icons.Rounded.Bedtime,
                iconContentDescription = "Ahorro de energía inteligente en espera",
                title = "Ahorro en espera (Standby)",
                subtitle = if (smartState.standbyEnabled) {
                    "Activo: activa Doze y App Standby al apagar la pantalla"
                } else {
                    "Reduce el drenaje en reposo y de noche al bloquear el teléfono"
                },
                lastActionText = smartState.standbyLastEventLabel?.let { "Standby: $it" },
                enabled = smartState.standbyEnabled,
                activeButtonText = "Desactivar ahorro de energía inteligente en espera",
                inactiveButtonText = "Ahorro de energía inteligente en espera",
                accent = LocalViolet,
                inactiveButtonContentColor = Color(0xFF1A1033),
                testTag = "smart_standby_saving_button",
                onToggle = onToggleSmartStandbySaving
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            SmartEcoOptionBlock(
                icon = Icons.Rounded.Bluetooth,
                iconContentDescription = "Ahorro Bluetooth sin apagarlo",
                title = "Ahorro Bluetooth (sin apagarlo)",
                subtitle = if (smartState.bluetoothEcoEnabled) {
                    val hwState = if (smartState.bluetoothHardwareOn) "Bluetooth encendido" else "Bluetooth en espera"
                    "$hwState • Reduce escaneo BLE automático estilo Samsung"
                } else {
                    "Escaneo BLE estándar activo (Bluetooth intacto)"
                },
                lastActionText = smartState.bluetoothEcoLastActionLabel?.let { "Bluetooth Eco: $it" },
                enabled = smartState.bluetoothEcoEnabled,
                activeButtonText = "Ahorro Bluetooth activo (sin apagar Bluetooth)",
                inactiveButtonText = "Activar ahorro Bluetooth (sin apagarlo)",
                accent = ShizukuCyan,
                inactiveButtonContentColor = Color(0xFF06131A),
                testTag = "bluetooth_eco_button",
                onToggle = onToggleBluetoothEco
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            val torchSummary = when {
                smartState.torchEcoEnabled && smartState.torchHardwareOn -> {
                    val lvl = smartState.torchLevelLabel ?: "Activa"
                    "Linterna encendida ($lvl) • Ahorro automático activo"
                }
                smartState.torchEcoEnabled && smartState.torchSupportsDimming ->
                    "En automático • atenúa el brillo LED al encender sin apagarla"
                smartState.torchEcoEnabled ->
                    "En automático • compensa 60Hz y 2º plano cuando uses linterna"
                else -> "Control estándar de linterna (sin atenuación automática)"
            }
            SmartEcoOptionBlock(
                icon = Icons.Rounded.FlashlightOn,
                iconContentDescription = "Ahorro Linterna sin apagarla",
                title = "Ahorro Linterna (sin apagarla)",
                subtitle = torchSummary,
                lastActionText = smartState.torchEcoLastActionLabel?.let { "Linterna Eco: $it" },
                enabled = smartState.torchEcoEnabled,
                activeButtonText = "Ahorro Linterna activo (sin apagarla)",
                inactiveButtonText = "Activar ahorro Linterna (sin apagarla)",
                accent = BatteryAmber,
                inactiveButtonContentColor = Color(0xFF1F1200),
                testTag = "torch_eco_button",
                onToggle = onToggleTorchEco
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            val appGameSubtitle = when {
                smartState.appGameEcoEnabled && (smartState.detectedAppsCount > 0 || smartState.detectedGamesCount > 0) -> {
                    val fgPart = smartState.foregroundPackageLabel?.let { " • En uso: $it" }.orEmpty()
                    "Detectadas ${smartState.detectedAppsCount} apps y ${smartState.detectedGamesCount} juegos (Standby + GameMode)$fgPart"
                }
                smartState.appGameEcoEnabled ->
                    "Detecta automáticamente cualquier app y juego instalado (Standby + Game Mode sin cerrar nada)"
                else -> "Optimización por app y juego pausada (modo estándar)"
            }
            SmartEcoOptionBlock(
                icon = Icons.Rounded.Apps,
                iconContentDescription = "Ahorro automático para apps y juegos",
                title = "Ahorro Apps y Juegos (Automático)",
                subtitle = appGameSubtitle,
                lastActionText = smartState.appGameEcoLastActionLabel?.let { "Apps/Juegos: $it" },
                enabled = smartState.appGameEcoEnabled,
                activeButtonText = "Ahorro Apps y Juegos activo (automático)",
                inactiveButtonText = "Activar ahorro Apps y Juegos",
                accent = TermuxEmerald,
                inactiveButtonContentColor = Color(0xFF002110),
                testTag = "app_game_eco_button",
                onToggle = onToggleAppGameEco
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            val freezeSubtitle = when {
                smartState.appFreezeEcoEnabled && smartState.frozenPackagesCount > 0 ->
                    "IA hibernando ${smartState.frozenPackagesCount} apps/juegos inactivos en automático (se reactivan solos al abrirlos)"
                smartState.appFreezeEcoEnabled ->
                    "Hiberna en automático cualquier app o juego inactivo en 2º plano (100% automático)"
                else -> "Congelamiento de apps y juegos desactivado"
            }
            SmartEcoOptionBlock(
                icon = Icons.Rounded.AcUnit,
                iconContentDescription = "Congelamiento de apps y juegos",
                title = "Ahorro Congelamiento IA (Apps y Juegos)",
                subtitle = freezeSubtitle,
                lastActionText = smartState.appFreezeLastActionLabel?.let { "Congelamiento: $it" },
                enabled = smartState.appFreezeEcoEnabled,
                activeButtonText = if (smartState.frozenPackagesCount > 0) {
                    "Congelamiento IA activo (${smartState.frozenPackagesCount} congeladas)"
                } else {
                    "Congelamiento IA activo (automático)"
                },
                inactiveButtonText = "Activar congelamiento de apps y juegos",
                accent = ShizukuCyan,
                inactiveButtonContentColor = Color(0xFF06131A),
                testTag = "app_freeze_eco_button",
                onToggle = onToggleAppFreezeEco
            )
        }
    }
}

@Composable
private fun SmartEcoOptionBlock(
    icon: ImageVector,
    iconContentDescription: String,
    title: String,
    subtitle: String,
    lastActionText: String?,
    enabled: Boolean,
    activeButtonText: String,
    inactiveButtonText: String,
    accent: Color,
    inactiveButtonContentColor: Color,
    testTag: String,
    onToggle: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (lastActionText != null) {
            Text(
                text = lastActionText,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }

        if (enabled) {
            OutlinedButton(
                onClick = onToggle,
                border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = activeButtonText,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = inactiveButtonContentColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = inactiveButtonText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
