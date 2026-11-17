package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AutoGuardianState
import com.example.ai.DrainSeverity
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BatteryAmber
import com.example.ui.theme.DualTeal
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuxEmerald

@Composable
fun BatteryAiDoctorCard(
    guardianState: AutoGuardianState,
    onToggleAutoGuardian: () -> Unit,
    onToggleAutoSolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAny = guardianState.enabled || guardianState.autoSolverEnabled
    val accentColor = when {
        !activeAny -> MaterialTheme.colorScheme.onSurfaceVariant
        !guardianState.shizukuReady -> BatteryAmber
        guardianState.severity == DrainSeverity.CRITICAL -> AlertRed
        guardianState.severity == DrainSeverity.HIGH -> BatteryAmber
        else -> TermuxEmerald
    }

    val statusText = when {
        !activeAny -> "Pausado"
        guardianState.isWorking -> "Diagnosticando y resolviendo..."
        !guardianState.shizukuReady -> "En espera de Shizuku"
        guardianState.autoSolverEnabled -> "Auto-Solución y Ahorro activos"
        else -> "Automático activo"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_ai_doctor_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
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
                        if (guardianState.isWorking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(20.dp),
                                strokeWidth = 2.dp,
                                color = DualTeal
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "IA Solución Total Samsung",
                                tint = DualTeal,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "IA Solución Total • Automático",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Resuelve problemas de batería, fallas, errores y ejecuta todas las funciones de ahorro en automático",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = guardianState.enabled,
                    onCheckedChange = { onToggleAutoGuardian() },
                    modifier = Modifier.testTag("auto_guardian_switch")
                )
            }

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
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = "Auto-Solución de batería, problemas y errores",
                        tint = TermuxEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "Auto-Solución: Batería, Problemas y Errores",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Resuelve problemas de batería y drenaje, flags rotos, Doze trabado, Binder Shizuku/Termux y sobrecalentamiento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = guardianState.autoSolverEnabled,
                    onCheckedChange = { onToggleAutoSolver() },
                    modifier = Modifier.testTag("auto_solver_switch")
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
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
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                        Text(
                            text = "${guardianState.solvedIssuesCount} resueltos • ${guardianState.actionsAppliedCount} ajustes",
                            style = MaterialTheme.typography.labelSmall,
                            color = ShizukuCyan
                        )
                    }
                    Text(
                        text = guardianState.activeFunctionsSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (guardianState.lastProblemSolved != null) {
                        Text(
                            text = "Última solución: ${guardianState.lastProblemSolved}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TermuxEmerald
                        )
                    }
                    if (guardianState.lastErrorRecovered != null) {
                        Text(
                            text = "Recuperación de error: ${guardianState.lastErrorRecovered}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BatteryAmber
                        )
                    }
                    if (guardianState.detectedAppsAndGamesCount > 0 || guardianState.frozenCount > 0) {
                        Text(
                            text = "Apps y Juegos: ${guardianState.detectedAppsAndGamesCount} detectados • ${guardianState.frozenCount} en hibernación segura",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = ShizukuCyan
                        )
                    }
                    if (guardianState.lastActionSummary != null) {
                        Text(
                            text = guardianState.lastActionSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = DualTeal
                        )
                    }
                }
            }
        }
    }
}
