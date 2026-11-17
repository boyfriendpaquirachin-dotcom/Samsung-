package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ai.AutoGuardianState
import com.example.model.BatteryAutomationRule
import com.example.model.BatteryHistoryPoint
import com.example.model.BatteryTelemetry
import com.example.model.ChargeProtectionConfig
import com.example.model.CommandEngine
import com.example.model.ConnectionHealth
import com.example.model.PowerProfileStatus
import com.example.model.SamsungPowerProfile
import com.example.model.ShizukuStatus
import com.example.model.SmartSavingState
import com.example.model.TermuxStatus
import com.example.ui.components.BatteryAiDoctorCard
import com.example.ui.components.SamsungMotoPowerCard
import com.example.ui.components.SmartSavingCard
import com.example.ui.components.StatusPill
import com.example.ui.components.engineAccentColor
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MonitorScreen(
    shizukuStatus: ShizukuStatus,
    termuxStatus: TermuxStatus,
    telemetry: BatteryTelemetry,
    history: List<BatteryHistoryPoint>,
    automationRule: BatteryAutomationRule,
    powerStatus: PowerProfileStatus,
    chargeProtection: ChargeProtectionConfig,
    smartSavingState: SmartSavingState,
    isOptimizingBattery: Boolean,
    autoGuardianState: AutoGuardianState,
    onRefreshAll: () -> Unit,
    onOpenGuide: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    onBindShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAuthorizeTermux: () -> Unit,
    onOpenTermux: () -> Unit,
    onRunQuickCommand: (CommandEngine, String) -> Unit,
    onSaveAutomationRule: (BatteryAutomationRule) -> Unit,
    onOptimizePhoneBattery: () -> Unit,
    onToggleAutoGuardian: () -> Unit,
    onToggleAutoSolver: () -> Unit,
    onToggleSmartSaving: () -> Unit,
    onToggleSmartStandbySaving: () -> Unit,
    onToggleBluetoothEco: () -> Unit,
    onToggleTorchEco: () -> Unit,
    onToggleAppGameEco: () -> Unit,
    onToggleAppFreezeEco: () -> Unit,
    onApplyPowerProfile: (SamsungPowerProfile) -> Unit,
    onSetRefreshRate60Hz: (Boolean) -> Unit,
    onUpdateChargeProtection: (Boolean, Int) -> Unit,
    onSyncRealPowerStatus: () -> Unit,
    onOpenBatterySaverSettings: () -> Unit,
    onAddQuickSettingsTile: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                MonitorHeader(
                    onRefreshAll = onRefreshAll,
                    onOpenGuide = onOpenGuide,
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            item {
                if (isWide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 920.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShizukuControlCard(
                            status = shizukuStatus,
                            onAuthorize = onAuthorizeShizuku,
                            onBind = onBindShizuku,
                            onOpenApp = onOpenShizuku,
                            onQuickTest = { onRunQuickCommand(CommandEngine.SHIZUKU, "id && getprop ro.product.model") },
                            modifier = Modifier.weight(1f)
                        )
                        TermuxControlCard(
                            status = termuxStatus,
                            onAuthorize = onAuthorizeTermux,
                            onConnectBridge = onOpenGuide,
                            onOpenApp = onOpenTermux,
                            onOpenAppSettings = onOpenAppSettings,
                            onQuickTest = { onRunQuickCommand(CommandEngine.TERMUX, "uname -a && whoami") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ShizukuControlCard(
                            status = shizukuStatus,
                            onAuthorize = onAuthorizeShizuku,
                            onBind = onBindShizuku,
                            onOpenApp = onOpenShizuku,
                            onQuickTest = { onRunQuickCommand(CommandEngine.SHIZUKU, "id && getprop ro.product.model") }
                        )
                        TermuxControlCard(
                            status = termuxStatus,
                            onAuthorize = onAuthorizeTermux,
                            onConnectBridge = onOpenGuide,
                            onOpenApp = onOpenTermux,
                            onOpenAppSettings = onOpenAppSettings,
                            onQuickTest = { onRunQuickCommand(CommandEngine.TERMUX, "uname -a && whoami") }
                        )
                    }
                }
            }

            item {
                BatteryTelemetryHeroCard(
                    telemetry = telemetry,
                    history = history,
                    onDumpShizukuBattery = {
                        onRunQuickCommand(CommandEngine.SHIZUKU, "dumpsys battery")
                    },
                    onTermuxBattery = {
                        onRunQuickCommand(CommandEngine.TERMUX, "termux-battery-status")
                    },
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            item {
                BatteryAiDoctorCard(
                    guardianState = autoGuardianState,
                    onToggleAutoGuardian = onToggleAutoGuardian,
                    onToggleAutoSolver = onToggleAutoSolver,
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            item {
                SamsungMotoPowerCard(
                    powerStatus = powerStatus,
                    chargeProtection = chargeProtection,
                    telemetry = telemetry,
                    shizukuStatus = shizukuStatus,
                    isOptimizing = isOptimizingBattery,
                    onOptimizePhoneBattery = onOptimizePhoneBattery,
                    onApplyProfile = onApplyPowerProfile,
                    onSetRefreshRate60Hz = onSetRefreshRate60Hz,
                    onUpdateChargeProtection = onUpdateChargeProtection,
                    onSyncRealStatus = onSyncRealPowerStatus,
                    onOpenBatterySaverSettings = onOpenBatterySaverSettings,
                    onAddQuickSettingsTile = onAddQuickSettingsTile,
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            item {
                SmartSavingCard(
                    smartState = smartSavingState,
                    onToggleSmartSaving = onToggleSmartSaving,
                    onToggleSmartStandbySaving = onToggleSmartStandbySaving,
                    onToggleBluetoothEco = onToggleBluetoothEco,
                    onToggleTorchEco = onToggleTorchEco,
                    onToggleAppGameEco = onToggleAppGameEco,
                    onToggleAppFreezeEco = onToggleAppFreezeEco,
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }

            item {
                BatteryAutomationCard(
                    rule = automationRule,
                    onSaveRule = onSaveAutomationRule,
                    modifier = Modifier.widthIn(max = 920.dp)
                )
            }
        }
    }
}

@Composable
private fun MonitorHeader(
    onRefreshAll: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TermuZuku",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Puente de control Shizuku • Termux • Batería",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onOpenGuide,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("setup_guide_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = "Guía de configuración",
                    tint = ShizukuCyan
                )
            }
            IconButton(
                onClick = onRefreshAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_status_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Actualizar estado de servicios",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun ShizukuControlCard(
    status: ShizukuStatus,
    onAuthorize: () -> Unit,
    onBind: () -> Unit,
    onOpenApp: () -> Unit,
    onQuickTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        color = ShizukuCyan.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Motor Shizuku",
                            tint = ShizukuCyan,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Shizuku",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (status.health == ConnectionHealth.CONNECTED) {
                                "${status.privilegeLabel} • UserService ${if (status.isUserServiceBound) "activo" else "listo"}"
                            } else {
                                "Control privilegiado ADB / Root"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusPill(health = status.health)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (status.health) {
                    ConnectionHealth.CONNECTED -> {
                        Button(
                            onClick = onQuickTest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ShizukuCyan,
                                contentColor = Color(0xFF001F29)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("shizuku_test_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Probar id", maxLines = 1)
                        }
                        if (!status.isUserServiceBound) {
                            OutlinedButton(
                                onClick = onBind,
                                border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Enlazar", color = ShizukuCyan)
                            }
                        }
                    }

                    ConnectionHealth.PERMISSION_REQUIRED -> {
                        Button(
                            onClick = onAuthorize,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BatteryAmber,
                                contentColor = Color(0xFF281B00)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("shizuku_authorize_button")
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Autorizar Shizuku", maxLines = 1)
                        }
                    }

                    ConnectionHealth.INACTIVE, ConnectionHealth.NOT_INSTALLED -> {
                        OutlinedButton(
                            onClick = onOpenApp,
                            border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("shizuku_open_button")
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = ShizukuCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (status.isInstalled) "Abrir Shizuku" else "Requiere Shizuku",
                                color = ShizukuCyan,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxControlCard(
    status: TermuxStatus,
    onAuthorize: () -> Unit,
    onConnectBridge: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onQuickTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        color = TermuxEmerald.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = "Motor Termux",
                            tint = TermuxEmerald,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Termux",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                status.isLocalBridgeOnline -> "Puente Local 127.0.0.1:${status.bridgePort} listo"
                                status.hasRunCommandPermission && status.isInstalled ->
                                    if (status.isTermuxApiInstalled) "Bash + Termux:API listos" else "RUN_COMMAND listo"
                                status.isInstalled && !status.supportsRunCommand ->
                                    "Termux ${status.termuxVersionName ?: ""} bloquea RUN_COMMAND • Usa Puente"
                                status.isInstalled -> "Usa Puente Local o autoriza RUN_COMMAND"
                                else -> "Paquete com.termux no detectado"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                StatusPill(
                    health = status.health,
                    customText = when {
                        status.isLocalBridgeOnline -> "Puente activo"
                        status.health == ConnectionHealth.PERMISSION_REQUIRED && !status.supportsRunCommand -> "Usar Puente"
                        else -> null
                    }
                )
            }

            if (status.health == ConnectionHealth.PERMISSION_REQUIRED) {
                Text(
                    text = if (!status.supportsRunCommand) {
                        "Tu versión de Termux (${status.termuxVersionName ?: "Play Store"}) no permite dar RUN_COMMAND en Android. Usa 'Conectar Puente (Universal)'."
                    } else {
                        "Puedes activar el Puente Local (127.0.0.1:${status.bridgePort}) o autorizar RUN_COMMAND."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = BatteryAmber
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (status.health) {
                    ConnectionHealth.CONNECTED -> {
                        Button(
                            onClick = onQuickTest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TermuxEmerald,
                                contentColor = Color(0xFF002110)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("termux_test_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Probar Bash", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onOpenApp,
                            border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Abrir", color = TermuxEmerald)
                        }
                    }

                    ConnectionHealth.PERMISSION_REQUIRED -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onConnectBridge,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TermuxEmerald,
                                    contentColor = Color(0xFF002110)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("termux_bridge_button")
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Conectar Puente (Universal)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (status.supportsRunCommand) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onAuthorize,
                                        border = BorderStroke(1.dp, BatteryAmber.copy(alpha = 0.65f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("termux_authorize_button")
                                    ) {
                                        Text("Autorizar RUN_COMMAND", color = BatteryAmber, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton(
                                        onClick = onOpenAppSettings,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("termux_settings_button")
                                    ) {
                                        Text("Ajustes", maxLines = 1)
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onOpenApp,
                                    border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.5f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = TermuxEmerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Abrir Termux", color = TermuxEmerald, maxLines = 1)
                                }
                            }
                        }
                    }

                    ConnectionHealth.INACTIVE, ConnectionHealth.NOT_INSTALLED -> {
                        OutlinedButton(
                            onClick = onConnectBridge,
                            border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("termux_open_button")
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = TermuxEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (status.isInstalled) "Conectar Puente" else "Puente / Instalar",
                                color = TermuxEmerald,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryTelemetryHeroCard(
    telemetry: BatteryTelemetry,
    history: List<BatteryHistoryPoint>,
    onDumpShizukuBattery: () -> Unit,
    onTermuxBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when {
        telemetry.isOverheating -> AlertRed
        telemetry.level <= 20 && !telemetry.isCharging -> BatteryAmber
        telemetry.isCharging -> TermuxEmerald
        else -> ShizukuCyan
    }

    val animatedProgress by animateFloatAsState(
        targetValue = (telemetry.level / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 550),
        label = "batteryArc"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 10.dp.toPx()
                            val diameter = size.minDimension - strokeWidth
                            val topLeft = Offset(
                                (size.width - diameter) / 2f,
                                (size.height - diameter) / 2f
                            )
                            drawArc(
                                color = levelColor.copy(alpha = 0.16f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(levelColor.copy(alpha = 0.7f), levelColor)
                                ),
                                startAngle = 135f,
                                sweepAngle = 270f * animatedProgress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = Size(diameter, diameter),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (telemetry.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                                contentDescription = "Icono de batería",
                                tint = levelColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "${telemetry.level}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Monitor de Batería",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${telemetry.statusLabel} • ${telemetry.pluggedLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = levelColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        val remainingText = telemetry.chargeTimeRemainingMin?.let { " • ~$it min para 100%" }.orEmpty()
                        Text(
                            text = String.format(
                                Locale.US,
                                "Potencia: %.2f W • %s%s",
                                telemetry.powerWatts,
                                telemetry.technology,
                                remainingText
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6-tile real-time telemetry grid
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        icon = Icons.Rounded.Thermostat,
                        label = "Temperatura",
                        value = String.format(Locale.US, "%.1f °C", telemetry.temperatureCelsius),
                        accent = if (telemetry.temperatureCelsius >= 41f) AlertRed else BatteryAmber,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        icon = Icons.Rounded.Bolt,
                        label = "Voltaje",
                        value = String.format(Locale.US, "%.2f V", telemetry.voltageMv / 1000f),
                        accent = ShizukuCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        icon = Icons.Rounded.ElectricMeter,
                        label = "Corriente",
                        value = "${telemetry.currentMa} mA",
                        accent = TermuxEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        icon = Icons.Rounded.Favorite,
                        label = "Salud",
                        value = telemetry.healthLabel,
                        accent = if (telemetry.isOverheating) AlertRed else TermuxEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        icon = Icons.Rounded.Memory,
                        label = "Carga",
                        value = if (telemetry.chargeCounterMah > 0) "${telemetry.chargeCounterMah} mAh" else "${telemetry.voltageMv} mV",
                        accent = ShizukuCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        icon = Icons.Rounded.Power,
                        label = "Fuente",
                        value = telemetry.pluggedLabel,
                        accent = BatteryAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Real-time sparkline graph
            BatterySparklineChart(history = history, accentColor = levelColor)

            // Quick cross-engine battery inspection buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDumpShizukuBattery,
                    border = BorderStroke(1.dp, ShizukuCyan.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("battery_dumpsys_button")
                ) {
                    Text(
                        text = "Dumpsys (Shizuku)",
                        color = ShizukuCyan,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onTermuxBattery,
                    border = BorderStroke(1.dp, TermuxEmerald.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("battery_termux_button")
                ) {
                    Text(
                        text = "Termux:API",
                        color = TermuxEmerald,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatterySparklineChart(
    history: List<BatteryHistoryPoint>,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historial en vivo (Nivel % / Temp °C)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${history.size} muestras",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // Grid guidelines
                    for (i in 1..3) {
                        val y = h * (i / 4f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (history.size >= 2) {
                        val levelPath = Path()
                        val tempPath = Path()
                        val count = history.size
                        history.forEachIndexed { idx, pt ->
                            val x = (idx.toFloat() / (count - 1).coerceAtLeast(1)) * w
                            val yLevel = h - ((pt.level.coerceIn(0, 100) / 100f) * (h - 10f)) - 5f
                            val normalizedTemp = ((pt.temperatureCelsius - 15f) / 40f).coerceIn(0f, 1f)
                            val yTemp = h - (normalizedTemp * (h - 10f)) - 5f
                            if (idx == 0) {
                                levelPath.moveTo(x, yLevel)
                                tempPath.moveTo(x, yTemp)
                            } else {
                                levelPath.lineTo(x, yLevel)
                                tempPath.lineTo(x, yTemp)
                            }
                        }

                        drawPath(
                            path = tempPath,
                            color = BatteryAmber.copy(alpha = 0.75f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = levelPath,
                            color = accentColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    } else if (history.size == 1) {
                        val pt = history.first()
                        val yLevel = h - ((pt.level.coerceIn(0, 100) / 100f) * (h - 10f)) - 5f
                        drawCircle(
                            color = accentColor,
                            radius = 4.dp.toPx(),
                            center = Offset(w / 2f, yLevel)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryAutomationCard(
    rule: BatteryAutomationRule,
    onSaveRule: (BatteryAutomationRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var enabled by remember(rule.enabled) { mutableStateOf(rule.enabled) }
    var lowPercent by remember(rule.lowBatteryPercent) { mutableIntStateOf(rule.lowBatteryPercent) }
    var highTemp by remember(rule.highTempCelsius) { mutableFloatStateOf(rule.highTempCelsius) }
    var selectedEngine by remember(rule.targetEngine) { mutableStateOf(rule.targetEngine) }
    var commandText by remember(rule.command) { mutableStateOf(rule.command) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Automatización por batería",
                        tint = BatteryAmber
                    )
                    Column {
                        Text(
                            text = "Automatización por Batería",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ejecuta un comando al superar un umbral",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        onSaveRule(
                            BatteryAutomationRule(
                                enabled = enabled,
                                lowBatteryPercent = lowPercent,
                                highTempCelsius = highTemp,
                                targetEngine = selectedEngine,
                                command = commandText
                            )
                        )
                    },
                    modifier = Modifier.testTag("automation_switch")
                )
            }

            if (rule.lastTriggeredLabel != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BatteryAmber.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = "Último disparo automático: ${rule.lastTriggeredLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = BatteryAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

            Column {
                Text(
                    text = "Batería baja (al descargar): ≤ $lowPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = lowPercent.toFloat(),
                    onValueChange = { lowPercent = it.roundToInt() },
                    valueRange = 5f..60f,
                    colors = SliderDefaults.colors(thumbColor = BatteryAmber, activeTrackColor = BatteryAmber)
                )
            }

            Column {
                Text(
                    text = String.format(Locale.US, "Alerta térmica: ≥ %.0f °C", highTemp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = highTemp,
                    onValueChange = { highTemp = it.roundToInt().toFloat() },
                    valueRange = 34f..55f,
                    colors = SliderDefaults.colors(thumbColor = AlertRed, activeTrackColor = AlertRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandEngine.entries.forEach { engine ->
                    val accent = engineAccentColor(engine)
                    FilterChip(
                        selected = selectedEngine == engine,
                        onClick = { selectedEngine = engine },
                        label = { Text(engine.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent
                        )
                    )
                }
            }

            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("Comando automático a ejecutar") },
                textStyle = MaterialTheme.typography.labelMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    onSaveRule(
                        BatteryAutomationRule(
                            enabled = enabled,
                            lowBatteryPercent = lowPercent,
                            highTempCelsius = highTemp,
                            targetEngine = selectedEngine,
                            command = commandText
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("save_automation_button")
            ) {
                Text("Guardar regla de batería")
            }
        }
    }
}
