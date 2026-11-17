package com.example.model

enum class FunctionCategory(val label: String) {
    ALL("Todas"),
    SAMSUNG_MOTO("Ahorro Samsung"),
    BOTH("Ambos"),
    SHIZUKU("Shizuku"),
    TERMUX("Termux"),
    BATTERY("Batería"),
    SYSTEM("Sistema"),
    CUSTOM("Mis Comandos")
}

data class SystemFunctionPreset(
    val id: String,
    val title: String,
    val description: String,
    val command: String,
    val engine: CommandEngine,
    val category: FunctionCategory,
    val isCustom: Boolean = false,
    val customDbId: Int = 0,
    val isFavorite: Boolean = false
)

object BuiltInFunctionsCatalog {
    const val CMD_SAMSUNG_SAVING_MOTO_G82 =
        "cmd power set-mode 1 && settings put global low_power 1 && settings put system peak_refresh_rate 60.0 && settings put system min_refresh_rate 60.0; settings put secure doze_always_on 0; settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; echo 'OK: Ahorro Samsung (60Hz + Ahorro + Standby) activado'"

    const val CMD_SAMSUNG_ULTRA_MOTO_G82 =
        "cmd power set-mode 1 && settings put global low_power 1 && settings put system peak_refresh_rate 60.0 && settings put system min_refresh_rate 60.0; settings put secure doze_always_on 0; settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; settings put system screen_off_timeout 30000; echo 'OK: Ahorro Ultra Samsung activado'"

    const val CMD_RESTORE_NORMAL_120HZ_MOTO_G82 =
        "settings put system peak_refresh_rate 120.0; settings put system min_refresh_rate 60.0; settings put global forced_app_standby_enabled 0; settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; settings put global animator_duration_scale 1.0; settings put system screen_off_timeout 60000; cmd deviceidle unforce; echo 'OK: Pantalla 120Hz y animaciones restauradas (ahorro del dispositivo intacto)'"

    const val CMD_RESTORE_NORMAL_120HZ_WITH_POWER_OFF =
        "cmd power set-mode 0; settings put global low_power 0; $CMD_RESTORE_NORMAL_120HZ_MOTO_G82"

    const val CMD_QUERY_POWER_STATE =
        "echo \"MODEL=$(getprop ro.product.model)\" && echo \"DEVICE=$(getprop ro.product.device)\" && echo \"LOW_POWER=$(settings get global low_power)\" && echo \"PEAK_HZ=$(settings get system peak_refresh_rate)\" && echo \"AOD=$(settings get secure doze_always_on)\" && echo \"FORCED_STANDBY=$(settings get global forced_app_standby_enabled)\" && echo \"BT_ON=$(settings get global bluetooth_on)\" && echo \"BLE_SCAN=$(settings get global ble_scan_always_enabled)\""

    const val CMD_OPTIMIZE_PHONE_BATTERY =
        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; pm trim-caches 256M; dumpsys battery | head -n 10; echo 'OK: Batería del teléfono optimizada'"

    const val CMD_SMART_ECO_60HZ =
        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0; echo 'OK: Ahorro inteligente equilibrado (60Hz)'"

    const val CMD_SMART_STANDBY_ON =
        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; cmd deviceidle enable all; echo 'OK: Ahorro de energía inteligente en espera activado'"

    const val CMD_SMART_STANDBY_OFF =
        "cmd deviceidle unforce; settings put global forced_app_standby_enabled 0; echo 'OK: Ahorro inteligente en espera desactivado'"

    const val CMD_BLUETOOTH_ECO_ON =
        "settings put global ble_scan_always_enabled 0; settings put global ble_scan_low_power_window_ms 512; settings put global ble_scan_low_power_interval_ms 5120; settings put global ble_scan_balanced_window_ms 1024; settings put global ble_scan_balanced_interval_ms 4096; echo 'OK: Ahorro Bluetooth Samsung activo (Bluetooth encendido)'"

    const val CMD_BLUETOOTH_ECO_OFF =
        "settings put global ble_scan_always_enabled 1; settings delete global ble_scan_low_power_window_ms; settings delete global ble_scan_low_power_interval_ms; settings delete global ble_scan_balanced_window_ms; settings delete global ble_scan_balanced_interval_ms; echo 'OK: Escaneo Bluetooth normal restaurado (Bluetooth intacto)'"

    const val CMD_TORCH_ECO_COMPENSATION =
        "settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0; settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; echo 'OK: Compensación de energía con linterna activa (linterna intacta)'"

    const val CMD_INSPECT_TORCH_STATE =
        "dumpsys media.camera | grep -iE 'torch|flash' | head -n 15; dumpsys battery | grep -E 'level|temperature|current'; echo 'OK: Diagnóstico de linterna completado'"

    const val CMD_STOP_BG_WAKEUPS =
        "settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; settings put global forced_app_standby_enabled 0; echo 'OK: Apps en segundo plano restringidas (Standby seguro)'"

    const val CMD_ANTI_DRAIN_FULL =
        "cmd power set-mode 1; settings put global low_power 1; settings put system peak_refresh_rate 60.0; settings put system min_refresh_rate 60.0; settings put secure doze_always_on 0; settings put global adaptive_battery_management_enabled 1; settings put global app_standby_enabled 1; settings put global forced_app_standby_enabled 0; settings put global ble_scan_always_enabled 0; settings put global ble_scan_low_power_window_ms 512; settings put global ble_scan_low_power_interval_ms 5120; echo 'OK: Bloqueo anti-drenaje seguro aplicado'"

    const val CMD_REPAIR_SYSTEM_STABILITY =
        "device_config delete activity_manager_native_boot use_freezer 2>/dev/null; " +
            "settings delete global cached_apps_freezer 2>/dev/null; " +
            "settings put global forced_app_standby_enabled 0; " +
            "settings put global window_animation_scale 1.0; " +
            "settings put global transition_animation_scale 1.0; " +
            "settings put global animator_duration_scale 1.0; " +
            "cmd deviceidle unforce 2>/dev/null; " +
            "for p in \$(pm list packages -3 2>/dev/null | cut -d: -f2); do " +
            "appops set \$p RUN_IN_BACKGROUND allow 2>/dev/null; " +
            "appops set \$p RUN_ANY_IN_BACKGROUND allow 2>/dev/null; " +
            "device_config delete game_overlay \$p 2>/dev/null; " +
            "cmd game mode standard \$p 2>/dev/null; " +
            "am set-standby-bucket \$p active 2>/dev/null; " +
            "done; " +
            "echo 'OK: Estabilidad del sistema y aplicaciones restaurada'"

    const val CMD_AUTO_SOLVE_ALL =
        "device_config delete activity_manager_native_boot use_freezer 2>/dev/null; " +
            "settings delete global cached_apps_freezer 2>/dev/null; " +
            "settings put global forced_app_standby_enabled 0; " +
            "settings put global window_animation_scale 1.0; " +
            "settings put global transition_animation_scale 1.0; " +
            "settings put global animator_duration_scale 1.0; " +
            "settings put global adaptive_battery_management_enabled 1; " +
            "settings put global app_standby_enabled 1; " +
            "settings put global ble_scan_always_enabled 0; " +
            "settings put global ble_scan_low_power_window_ms 512; " +
            "settings put global ble_scan_low_power_interval_ms 5120; " +
            "cmd deviceidle unforce 2>/dev/null; " +
            "echo 'OK: Solución inteligente y ahorro total aplicados'"

    const val CMD_INSPECT_POWER_FAST =
        "echo '=== BATERIA ===' && dumpsys battery | head -n 12 && echo '=== PANTALLA Y AHORRO ===' && echo \"PEAK_HZ=$(settings get system peak_refresh_rate) LOW_POWER=$(settings get global low_power) AOD=$(settings get secure doze_always_on) ADAPTIVE=$(settings get global adaptive_battery_management_enabled) FORCED_STANDBY=$(settings get global forced_app_standby_enabled) BT_ON=$(settings get global bluetooth_on) BLE_SCAN=$(settings get global ble_scan_always_enabled) USE_FREEZER=$(device_config get activity_manager_native_boot use_freezer 2>/dev/null) CACHED_FREEZER=$(settings get global cached_apps_freezer 2>/dev/null) ANIM_WIN=$(settings get global window_animation_scale 2>/dev/null) ANIM_TRANS=$(settings get global transition_animation_scale 2>/dev/null) ANIM_DUR=$(settings get global animator_duration_scale 2>/dev/null)\" && echo '=== WAKELOCKS Y DOZE ===' && dumpsys power | grep -E 'mWakefulness=|Wake Locks:|Suspend Blockers:' | head -n 6 && echo \"DOZE_LIGHT=$(cmd deviceidle get light 2>/dev/null) DOZE_DEEP=$(cmd deviceidle get deep 2>/dev/null)\" && echo '=== FG ===' && (dumpsys activity activities 2>/dev/null | grep -E 'topResumedActivity=|mResumedActivity:' | head -n 3 || true)"

    const val CMD_INSPECT_DRAIN_CAUSES =
        "echo '=== BATERIA ===' && dumpsys battery | head -n 12 && echo '=== PANTALLA Y AHORRO ===' && echo \"PEAK_HZ=$(settings get system peak_refresh_rate) LOW_POWER=$(settings get global low_power) AOD=$(settings get secure doze_always_on) ADAPTIVE=$(settings get global adaptive_battery_management_enabled) FORCED_STANDBY=$(settings get global forced_app_standby_enabled) BT_ON=$(settings get global bluetooth_on) BLE_SCAN=$(settings get global ble_scan_always_enabled)\" && echo '=== WAKELOCKS Y DOZE ===' && dumpsys power | grep -E 'mWakefulness=|Wake Locks:|Suspend Blockers:' | head -n 8 && cmd deviceidle get light && echo '=== PROCESOS / CPU ===' && (top -b -n 1 -o %CPU,ARGS 2>/dev/null | head -n 10 || ps -A -o NAME,PID | head -n 12) && echo '=== ESTADISTICAS BATERIA ===' && dumpsys batterystats --charged | grep -E 'Capacity:|Screen on:|Estimated|Uid ' | head -n 15"

    val presets: List<SystemFunctionPreset> = listOf(
        SystemFunctionPreset(
            id = "ai_full_auto_solve",
            title = "Solución Inteligente Total (Auto-Reparación + Ahorro)",
            description = "Corrige flags conflictivos, restaura estabilidad del sistema/UI y activa ahorro adaptativo, standby y BLE Eco en un paso.",
            command = CMD_AUTO_SOLVE_ALL,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "repair_system_stability",
            title = "Reparar Fallas de Sistema y Apps",
            description = "Desactiva flags conflictivos (use_freezer, forced standby, game_overlay y appops) para eliminar bloqueos de UI y cierres de apps.",
            command = CMD_REPAIR_SYSTEM_STABILITY,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_anti_drain_full",
            title = "Bloqueo Anti-Drenaje Completo",
            description = "Aplica 60Hz, ahorro oficial, sin AOD, App Standby y optimización BLE segura (sin ahorro de datos).",
            command = CMD_ANTI_DRAIN_FULL,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_stop_bg_wakeups",
            title = "Frenar Apps en 2º Plano",
            description = "Activa App Standby adaptativo de Android sin bloquear procesos críticos del sistema.",
            command = CMD_STOP_BG_WAKEUPS,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "moto_inspect_drain",
            title = "Inspeccionar Causas de Drenaje",
            description = "Lee dumpsys battery, Hz reales, estado de ahorro y consumo reciente por UID.",
            command = CMD_INSPECT_DRAIN_CAUSES,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "moto_optimize_battery",
            title = "Optimizar la batería del teléfono",
            description = "Activa batería adaptativa, App Standby y libera cachés innecesarias sin cerrar apps activas.",
            command = CMD_OPTIMIZE_PHONE_BATTERY,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "smart_saving_standby",
            title = "Ahorro de energía inteligente en espera",
            description = "Activa App Standby y Doze inteligente cuando la pantalla entra en reposo.",
            command = CMD_SMART_STANDBY_ON,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "bluetooth_eco_samsung",
            title = "Ahorro Bluetooth (sin apagarlo)",
            description = "Detiene el escaneo BLE en segundo plano y reduce el ciclo de búsqueda estilo Samsung manteniendo tu Bluetooth y dispositivos conectados.",
            command = CMD_BLUETOOTH_ECO_ON,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "torch_eco_samsung",
            title = "Ahorro Linterna (sin apagarla)",
            description = "Compensa el consumo del LED con 60Hz y standby mientras la linterna sigue encendida (en Android 13+ atenúa el brillo automáticamente).",
            command = CMD_TORCH_ECO_COMPENSATION,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "torch_inspect_state",
            title = "Inspeccionar Consumo Linterna",
            description = "Consulta el estado real de la linterna/flash en media.camera junto con corriente y temperatura de batería.",
            command = CMD_INSPECT_TORCH_STATE,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "detect_apps_and_games",
            title = "Detectar Apps y Juegos (Standby + GameMode)",
            description = "Escanea todas las apps de terceros y juegos instalados junto con la app en primer plano.",
            command = com.example.engine.AppGameOptimizerEngine.DISCOVERY_COMMAND,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "kernel_app_freezer_on",
            title = "Congelamiento Seguro (App Standby + Batería)",
            description = "Habilita Batería Adaptativa y App Standby de Android sin modificar flags experimentales del kernel.",
            command = com.example.engine.AppGameOptimizerEngine.CMD_ENABLE_KERNEL_FREEZER +
                "; echo 'OK: Modo Standby seguro activado'",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "smart_saving_balanced",
            title = "Ahorro inteligente equilibrado",
            description = "Activa batería adaptativa del sistema y fija 60Hz pOLED respetando tu ahorro de batería actual.",
            command = CMD_SMART_ECO_60HZ,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "samsung_moto_saving",
            title = "Ahorro Samsung (Moto G82 5G)",
            description = "Activa el ahorro oficial Android + limita pOLED a 60Hz, apaga AOD y activa App Standby (sin ahorro de datos).",
            command = CMD_SAMSUNG_SAVING_MOTO_G82,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "samsung_moto_ultra",
            title = "Ahorro Ultra Máximo (Moto G82 5G)",
            description = "Perfil intenso seguro: ahorro Samsung 60Hz + AOD apagado + Batería Adaptativa + tiempo de pantalla 30s.",
            command = CMD_SAMSUNG_ULTRA_MOTO_G82,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "samsung_moto_restore_120",
            title = "Restaurar Normal 120Hz (Moto G82 5G)",
            description = "Restaura 120Hz y animaciones 1.0x sin apagar el ahorro de batería propio de tu dispositivo.",
            command = CMD_RESTORE_NORMAL_120HZ_MOTO_G82,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_g82_60hz",
            title = "Pantalla 60Hz (Ahorro pOLED)",
            description = "Fija la tasa de refresco máxima a 60Hz en el Moto G82 5G sin activar el resto del ahorro.",
            command = "settings put system peak_refresh_rate 60.0 && settings put system min_refresh_rate 60.0 && echo 'OK: 60Hz aplicado'",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_g82_120hz",
            title = "Pantalla 120Hz (Fluidez Total)",
            description = "Restaura la tasa máxima a 120Hz adaptativos en el Moto G82 5G.",
            command = "settings put system peak_refresh_rate 120.0 && settings put system min_refresh_rate 60.0 && echo 'OK: 120Hz aplicado'",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_g82_power_inspect",
            title = "Verificar Estado Real de Ahorro",
            description = "Lee del sistema el estado real de low_power, tasa de refresco Hz, AOD y App Standby forzado.",
            command = CMD_QUERY_POWER_STATE,
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "moto_g82_doze_force",
            title = "Forzar Doze Profundo (Standby)",
            description = "Pone las apps en reposo energético profundo inmediatamente mediante deviceidle.",
            command = "cmd deviceidle force-idle",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SAMSUNG_MOTO
        ),
        SystemFunctionPreset(
            id = "dual_audit_both",
            title = "Auditoría Dual (Shizuku + Termux)",
            description = "Ejecuta simultáneamente en Shizuku y Termux para verificar identidad, kernel y entorno.",
            command = "id && uname -a",
            engine = CommandEngine.BOTH,
            category = FunctionCategory.BOTH
        ),
        SystemFunctionPreset(
            id = "dual_storage_uptime",
            title = "Disco y Uptime en Ambos",
            description = "Compara espacio en /data y tiempo de encendido desde Shizuku y Termux a la vez.",
            command = "uptime && df -h /data",
            engine = CommandEngine.BOTH,
            category = FunctionCategory.BOTH
        ),
        SystemFunctionPreset(
            id = "shizuku_battery_dump",
            title = "Estado de Batería (Dumpsys)",
            description = "Lee voltaje, ciclos, nivel y estado del controlador de energía del kernel.",
            command = "dumpsys battery",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "shizuku_thermal_status",
            title = "Sensores Térmicos",
            description = "Consulta temperaturas de CPU, GPU y batería mediante thermalservice.",
            command = "dumpsys thermalservice",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "shizuku_battery_stats",
            title = "Estadísticas de Consumo",
            description = "Resumen de wakelocks y consumo energético reciente del sistema.",
            command = "dumpsys batterystats --charged | head -n 45",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.BATTERY
        ),
        SystemFunctionPreset(
            id = "shizuku_packages_3p",
            title = "Listar Apps de Usuario",
            description = "Lista todos los paquetes de terceros instalados usando PackageManager.",
            command = "pm list packages -3 | sort",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SHIZUKU
        ),
        SystemFunctionPreset(
            id = "shizuku_display_info",
            title = "Resolución y Densidad",
            description = "Muestra el tamaño físico y densidad DPI actual del panel.",
            command = "wm size && wm density",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SHIZUKU
        ),
        SystemFunctionPreset(
            id = "shizuku_trim_cache",
            title = "Liberar Caché de Apps",
            description = "Solicita al sistema recortar cachés de almacenamiento innecesarias.",
            command = "pm trim-caches 512M",
            engine = CommandEngine.SHIZUKU,
            category = FunctionCategory.SHIZUKU
        ),
        SystemFunctionPreset(
            id = "termux_battery_api",
            title = "Batería Termux:API",
            description = "Obtiene JSON detallado de batería si Termux:API está instalado.",
            command = "termux-battery-status",
            engine = CommandEngine.TERMUX,
            category = FunctionCategory.TERMUX
        ),
        SystemFunctionPreset(
            id = "termux_sys_info",
            title = "Entorno Linux & Paquetes",
            description = "Verifica kernel, arquitectura, espacio en \$PREFIX y versión de bash.",
            command = "uname -a && df -h \$PREFIX && bash --version | head -n 1",
            engine = CommandEngine.TERMUX,
            category = FunctionCategory.TERMUX
        ),
        SystemFunctionPreset(
            id = "termux_pkg_update",
            title = "Actualizar Repositorios Pkg",
            description = "Sincroniza índices de paquetes APT/pkg dentro de Termux.",
            command = "pkg update -y",
            engine = CommandEngine.TERMUX,
            category = FunctionCategory.TERMUX
        ),
        SystemFunctionPreset(
            id = "termux_enable_external",
            title = "Habilitar Apps Externas",
            description = "Configura allow-external-apps = true en termux.properties.",
            command = "mkdir -p ~/.termux && grep -q 'allow-external-apps' ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps = true' >> ~/.termux/termux.properties && termux-reload-settings && echo 'OK: external apps enabled'",
            engine = CommandEngine.TERMUX,
            category = FunctionCategory.TERMUX
        ),
        SystemFunctionPreset(
            id = "local_device_info",
            title = "Propiedades del Dispositivo",
            description = "Lee modelo, versión Android, SDK y tiempo encendido sin privilegios extra.",
            command = "echo \"Modelo: $(getprop ro.product.model)\" && echo \"Android: $(getprop ro.build.version.release) (SDK $(getprop ro.build.version.sdk))\" && uptime",
            engine = CommandEngine.LOCAL,
            category = FunctionCategory.SYSTEM
        ),
        SystemFunctionPreset(
            id = "local_memory_disk",
            title = "Memoria RAM y Almacenamiento",
            description = "Consulta el estado actual de memoria libre y particiones montadas.",
            command = "cat /proc/meminfo | head -n 5 && df -h /data",
            engine = CommandEngine.LOCAL,
            category = FunctionCategory.SYSTEM
        ),
        SystemFunctionPreset(
            id = "local_network_ifaces",
            title = "Interfaces de Red",
            description = "Muestra direcciones IP locales e interfaces activas del dispositivo.",
            command = "ip -brief addr show 2>/dev/null || ifconfig",
            engine = CommandEngine.LOCAL,
            category = FunctionCategory.SYSTEM
        )
    )
}
