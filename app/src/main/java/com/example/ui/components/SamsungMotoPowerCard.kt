package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatteryTelemetry
import com.example.model.ChargeProtectionConfig
import com.example.model.ConnectionHealth
import com.example.model.PowerProfileStatus
import com.example.model.SamsungPowerProfile
import com.example.model.ShizukuStatus
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald

@Composable
fun SamsungMotoPowerCard(
    powerStatus: PowerProfileStatus,
    chargeProtection: ChargeProtectionConfig,
    telemetry: BatteryTelemetry,
    shizukuStatus: ShizukuStatus,
    isOptimizing: Boolean,
    onOptimizePhoneBattery: () -> Unit,
    onApplyProfile: (SamsungPowerProfile) -> Unit,
    onSetRefreshRate60Hz: (Boolean) -> Unit,
    onUpdateChargeProtection: (enabled: Boolean, limitPercent: Int) -> Unit,
    onSyncRealStatus: () -> Unit,
    onOpenBatterySaverSettings: () -> Unit,
    onAddQuickSettingsTile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shizukuReady = shizukuStatus.health == ConnectionHealth.CONNECTED
    val activeAccent = when (powerStatus.activeProfile) {
        SamsungPowerProfile.SAMSUNG_SAVING -> TermuxEmerald
        SamsungPowerProfile.SAMSUNG_ULTRA -> BatteryAmber
        SamsungPowerProfile.NORMAL_120HZ -> ShizukuCyan
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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
                        color = activeAccent.copy(alpha = 0.16f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BatterySaver,
                            contentDescription = "Perfil de ahorro Samsung",
                            tint = activeAccent,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ahorro Samsung • Moto G82 5G",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = powerStatus.deviceLabel + if (powerStatus.isMotoG82) " • Perfil exacto 120Hz pOLED" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Real System State Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StateChipBadge(
                    label = "Ahorro: ${if (powerStatus.lowPowerEnabled) "ON" else "OFF"}",
                    color = if (powerStatus.lowPowerEnabled) TermuxEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                StateChipBadge(
                    label = "Pantalla: ${powerStatus.peakRefreshRateHz} Hz",
                    color = if (powerStatus.peakRefreshRateHz <= 60) TermuxEmerald else ShizukuCyan
                )
                if (powerStatus.isSyncedFromSystem) {
                    StateChipBadge(
                        label = "Standby: ${if (powerStatus.backgroundRestricted) "Activo" else "Normal"}",
                        color = if (powerStatus.backgroundRestricted) TermuxEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!shizukuReady) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, BatteryAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Conecta y autoriza Shizuku arriba para aplicar los comandos reales de ahorro Samsung (60Hz, ahorro, AOD off, Doze y Standby) en tu Moto G82 5G.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // 1-Tap Official Profiles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Perfiles de energía reales (Shizuku)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileActionButton(
                        label = "Ahorro Samsung",
                        selected = powerStatus.activeProfile == SamsungPowerProfile.SAMSUNG_SAVING,
                        accent = TermuxEmerald,
                        onClick = { onApplyProfile(SamsungPowerProfile.SAMSUNG_SAVING) },
                        modifier = Modifier.testTag("samsung_saving_button")
                    )
                    ProfileActionButton(
                        label = "Ahorro Ultra",
                        selected = powerStatus.activeProfile == SamsungPowerProfile.SAMSUNG_ULTRA,
                        accent = BatteryAmber,
                        onClick = { onApplyProfile(SamsungPowerProfile.SAMSUNG_ULTRA) },
                        modifier = Modifier.testTag("samsung_ultra_button")
                    )
                    ProfileActionButton(
                        label = "Normal 120Hz",
                        selected = powerStatus.activeProfile == SamsungPowerProfile.NORMAL_120HZ,
                        accent = ShizukuCyan,
                        onClick = { onApplyProfile(SamsungPowerProfile.NORMAL_120HZ) },
                        modifier = Modifier.testTag("moto_normal_120hz_button")
                    )
                }
            }

            // Dedicated 1-tap Phone Battery Optimization Button
            Button(
                onClick = onOptimizePhoneBattery,
                enabled = !isOptimizing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TermuxEmerald,
                    contentColor = Color(0xFF002110)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("optimize_phone_battery_button")
            ) {
                if (isOptimizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF002110)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.BatteryChargingFull,
                        contentDescription = "Optimizar la batería del teléfono",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isOptimizing) "Optimizando batería..." else "Optimizar la batería del teléfono",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Refresh Rate Switch for Moto G82 5G pOLED
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = "Tasa de refresco",
                        tint = ShizukuCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Frecuencia pOLED",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = powerStatus.peakRefreshRateHz <= 60,
                        onClick = { onSetRefreshRate60Hz(true) },
                        label = { Text("60 Hz") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TermuxEmerald.copy(alpha = 0.18f),
                            selectedLabelColor = TermuxEmerald
                        )
                    )
                    FilterChip(
                        selected = powerStatus.peakRefreshRateHz > 60,
                        onClick = { onSetRefreshRate60Hz(false) },
                        label = { Text("120 Hz") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShizukuCyan.copy(alpha = 0.18f),
                            selectedLabelColor = ShizukuCyan
                        )
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            // Samsung-style Battery Charge Protection (80% / 85% / 90%)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Protección de batería",
                            tint = TermuxEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Protección de Batería (${chargeProtection.limitPercent}%)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Alerta al alcanzar el límite de carga estilo Samsung",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = chargeProtection.enabled,
                        onCheckedChange = { onUpdateChargeProtection(it, chargeProtection.limitPercent) }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(80, 85, 90).forEach { limit ->
                        FilterChip(
                            selected = chargeProtection.limitPercent == limit,
                            onClick = { onUpdateChargeProtection(chargeProtection.enabled, limit) },
                            label = { Text("Máx $limit%") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TermuxEmerald.copy(alpha = 0.18f),
                                selectedLabelColor = TermuxEmerald
                            )
                        )
                    }
                }

                if (chargeProtection.alertActive) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AlertRed.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "¡Límite de protección alcanzado (${telemetry.level}%)! Desconecta el cargador de tu Moto G82 5G para cuidar la vida útil.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AlertRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSyncRealStatus,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Leer estado", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onOpenBatterySaverSettings,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajustes Batería", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            OutlinedButton(
                onClick = onAddQuickSettingsTile,
                border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("add_qs_tile_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DashboardCustomize,
                    contentDescription = "Añadir a Ajustes Rápidos",
                    tint = ShizukuCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Añadir a Ajustes Rápidos (Quick Settings Tile)",
                    color = ShizukuCyan,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StateChipBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileActionButton(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = Color(0xFF06131A)
            ),
            modifier = modifier.minimumInteractiveComponentSize()
        ) {
            Text(label, maxLines = 1, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
            modifier = modifier.minimumInteractiveComponentSize()
        ) {
            Text(label, color = accent, maxLines = 1)
        }
    }
}
