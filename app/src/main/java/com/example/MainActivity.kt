package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.engine.TermuxController
import com.example.ui.MainViewModel
import com.example.ui.components.SetupGuideBottomSheet
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.FunctionsScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.theme.ShizukuCyan
import com.example.ui.theme.TermuZukuTheme
import kotlinx.serialization.Serializable

@Serializable
data object MonitorRoute

@Serializable
data object ConsoleRoute

@Serializable
data object FunctionsRoute

private data class NavDestinationItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermuZukuTheme {
                TermuZukuApp()
            }
        }
    }
}

@Composable
fun TermuZukuApp(
    viewModel: MainViewModel = viewModel()
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSetupGuide by remember { mutableStateOf(false) }

    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val termuxStatus by viewModel.termuxStatus.collectAsStateWithLifecycle()
    val batteryTelemetry by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
    val batteryHistory by viewModel.batteryHistory.collectAsStateWithLifecycle()
    val automationRule by viewModel.automationRule.collectAsStateWithLifecycle()
    val powerProfileStatus by viewModel.powerProfileStatus.collectAsStateWithLifecycle()
    val chargeProtection by viewModel.chargeProtection.collectAsStateWithLifecycle()
    val smartSavingState by viewModel.smartSavingState.collectAsStateWithLifecycle()
    val isOptimizingBattery by viewModel.isOptimizingBattery.collectAsStateWithLifecycle()
    val autoGuardianState by viewModel.autoGuardianState.collectAsStateWithLifecycle()
    val termuxBackground by viewModel.termuxRunInBackground.collectAsStateWithLifecycle()
    val selectedEngine by viewModel.selectedEngine.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val isExecuting by viewModel.isExecuting.collectAsStateWithLifecycle()
    val executionLogs by viewModel.executionLogs.collectAsStateWithLifecycle()
    val allFunctions by viewModel.allFunctions.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val uiNotice by viewModel.uiNotice.collectAsStateWithLifecycle()
    val pendingDangerousCommand by viewModel.pendingDangerousCommand.collectAsStateWithLifecycle()
    val termuxBridgeSetupCommand by viewModel.termuxBridgeSetupCommand.collectAsStateWithLifecycle()

    val termuxPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> viewModel.onTermuxPermissionResult(granted) }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiNotice) {
        val message = uiNotice
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(
                route = MonitorRoute,
                label = "Monitor",
                selectedIcon = Icons.Rounded.BatteryChargingFull,
                unselectedIcon = Icons.Outlined.BatteryChargingFull,
                testTag = "nav_monitor"
            ),
            NavDestinationItem(
                route = ConsoleRoute,
                label = "Consola",
                selectedIcon = Icons.Rounded.Terminal,
                unselectedIcon = Icons.Outlined.Terminal,
                testTag = "nav_console"
            ),
            NavDestinationItem(
                route = FunctionsRoute,
                label = "Funciones",
                selectedIcon = Icons.Rounded.Apps,
                unselectedIcon = Icons.Outlined.Apps,
                testTag = "nav_functions"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateToTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ShizukuCyan,
                                    selectedTextColor = ShizukuCyan,
                                    indicatorColor = ShizukuCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ShizukuCyan,
                                    selectedTextColor = ShizukuCyan,
                                    indicatorColor = ShizukuCyan.copy(alpha = 0.16f)
                                ),
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = MonitorRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<MonitorRoute> {
                            MonitorScreen(
                                shizukuStatus = shizukuStatus,
                                termuxStatus = termuxStatus,
                                telemetry = batteryTelemetry,
                                history = batteryHistory,
                                automationRule = automationRule,
                                powerStatus = powerProfileStatus,
                                chargeProtection = chargeProtection,
                                smartSavingState = smartSavingState,
                                isOptimizingBattery = isOptimizingBattery,
                                autoGuardianState = autoGuardianState,
                                onRefreshAll = viewModel::refreshAllStatuses,
                                onOpenGuide = { showSetupGuide = true },
                                onAuthorizeShizuku = viewModel::requestShizukuAuthorization,
                                onBindShizuku = viewModel::bindShizukuService,
                                onOpenShizuku = viewModel::openShizukuApp,
                                onAuthorizeTermux = {
                                    viewModel.authorizeTermuxOrRequest(
                                        onOpenBridgeGuide = { showSetupGuide = true },
                                        onLaunchRuntimeDialog = {
                                            termuxPermissionLauncher.launch(TermuxController.PERMISSION_RUN_COMMAND)
                                        }
                                    )
                                },
                                onOpenTermux = viewModel::openTermuxApp,
                                onRunQuickCommand = { engine, cmd ->
                                    viewModel.runQuickCommand(engine, cmd)
                                    navigateToTab(ConsoleRoute)
                                },
                                onSaveAutomationRule = viewModel::updateAutomationRule,
                                onOptimizePhoneBattery = viewModel::optimizePhoneBattery,
                                onToggleAutoGuardian = viewModel::toggleAutoGuardian,
                                onToggleAutoSolver = viewModel::toggleAutoSolver,
                                onToggleSmartSaving = viewModel::toggleSmartSaving,
                                onToggleSmartStandbySaving = viewModel::toggleSmartStandbySaving,
                                onToggleBluetoothEco = viewModel::toggleBluetoothEco,
                                onToggleTorchEco = viewModel::toggleTorchEco,
                                onToggleAppGameEco = viewModel::toggleAppGameEco,
                                onToggleAppFreezeEco = viewModel::toggleAppFreezeEco,
                                onApplyPowerProfile = viewModel::applySamsungPowerProfile,
                                onSetRefreshRate60Hz = viewModel::setRefreshRate60Hz,
                                onUpdateChargeProtection = viewModel::updateChargeProtection,
                                onSyncRealPowerStatus = viewModel::syncRealPowerProfileViaShizuku,
                                onOpenBatterySaverSettings = viewModel::openSystemBatterySaverSettings,
                                onAddQuickSettingsTile = viewModel::requestAddQuickSettingsTile,
                                onOpenAppSettings = viewModel::openAppPermissionSettings
                            )
                        }

                        composable<ConsoleRoute> {
                            ConsoleScreen(
                                selectedEngine = selectedEngine,
                                commandInput = commandInput,
                                isExecuting = isExecuting,
                                termuxBackground = termuxBackground,
                                shizukuStatus = shizukuStatus,
                                termuxStatus = termuxStatus,
                                batteryTelemetry = batteryTelemetry,
                                logs = executionLogs,
                                onSelectEngine = viewModel::selectEngine,
                                onCommandInputChange = viewModel::updateCommandInput,
                                onToggleTermuxBackground = viewModel::setTermuxBackground,
                                onExecuteCommand = viewModel::executeCurrentCommand,
                                onAuthorizeShizuku = viewModel::requestShizukuAuthorization,
                                onBindShizuku = viewModel::bindShizukuService,
                                onOpenShizuku = viewModel::openShizukuApp,
                                onAuthorizeTermux = {
                                    viewModel.authorizeTermuxOrRequest(
                                        onOpenBridgeGuide = { showSetupGuide = true },
                                        onLaunchRuntimeDialog = {
                                            termuxPermissionLauncher.launch(TermuxController.PERMISSION_RUN_COMMAND)
                                        }
                                    )
                                },
                                onOpenBridgeGuide = { showSetupGuide = true },
                                onOpenTermux = viewModel::openTermuxApp,
                                onClearLogs = viewModel::clearLogs,
                                onCopyNotice = viewModel::showNotice
                            )
                        }

                        composable<FunctionsRoute> {
                            FunctionsScreen(
                                functions = allFunctions,
                                selectedCategory = selectedCategory,
                                onSelectCategory = viewModel::selectCategory,
                                onRunPreset = { preset ->
                                    viewModel.executeFunctionPreset(preset)
                                    navigateToTab(ConsoleRoute)
                                },
                                onEditInConsole = { preset ->
                                    viewModel.loadInConsole(preset.command, preset.engine)
                                    navigateToTab(ConsoleRoute)
                                },
                                onSaveCustomCommand = viewModel::saveCustomCommand,
                                onDeleteCustomCommand = viewModel::deleteCustomCommand,
                                onToggleFavorite = viewModel::toggleFavoriteCommand
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSetupGuide) {
        SetupGuideBottomSheet(
            bridgeCommand = termuxBridgeSetupCommand,
            termuxStatus = termuxStatus,
            onDismiss = { showSetupGuide = false },
            onCopyCommand = viewModel::showNotice,
            onOpenShizuku = viewModel::openShizukuApp,
            onOpenTermux = viewModel::openTermuxApp,
            onVerifyBridge = viewModel::verifyTermuxLocalBridge
        )
    }

    pendingDangerousCommand?.let { (engine, command) ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDangerousCommand,
            title = { Text("Confirmar comando delicado") },
            text = {
                Text(
                    "Este comando puede modificar paquetes, reiniciar el teléfono o borrar archivos en ${engine.label}:\n\n$command\n\n¿Seguro que deseas ejecutarlo?"
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDangerousCommand,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Ejecutar bajo mi responsabilidad")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDangerousCommand) {
                    Text("Cancelar")
                }
            }
        )
    }
}
