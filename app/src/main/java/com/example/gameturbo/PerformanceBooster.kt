package com.example.gameturbo

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Ejecuta acciones de optimización usando el proceso "shell" que Shizuku
 * expone. Esto evita tener que declarar cada permiso del sistema en el
 * manifest: se ejecuta como si fuera un comando adb.
 */
object PerformanceBooster {

    /**
     * Ejecuta un comando de shell a través de Shizuku y devuelve su salida.
     * Requiere que ShizukuManager.hasPermission() sea true antes de llamarlo.
     */
    private fun runShizukuCommand(command: Array<String>): String {
        val process = Shizuku.newProcess(command, null, null)
        val output = BufferedReader(InputStreamReader(process.inputStream))
            .readText()
        process.waitFor()
        return output
    }

    /**
     * Cierra procesos en segundo plano de todas las apps de usuario
     * (equivalente a "am kill-all" pero más selectivo con "am kill").
     * Excluye la propia app y una lista de paquetes esenciales.
     */
    fun killBackgroundProcesses(excludedPackages: Set<String> = emptySet()) {
        // "pm list packages -3" lista apps de terceros instaladas
        val listOutput = runShizukuCommand(arrayOf("sh", "-c", "pm list packages -3"))
        val packages = listOutput.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in excludedPackages && it != "com.example.gameturbo" }

        for (pkg in packages) {
            runShizukuCommand(arrayOf("am", "kill", pkg))
        }
    }

    /**
     * Intenta activar el modo de máximo rendimiento del CPU/GPU escribiendo
     * en los nodos del kernel. ADVERTENCIA: las rutas varían mucho entre
     * fabricantes (MediaTek, Qualcomm, Samsung, etc.) y pueden no existir
     * o estar bloqueadas en tu dispositivo. Ajusta la ruta tras probar
     * en tu equipo concreto (ver README para cómo investigarla).
     */
    fun setHighPerformanceMode(governorPath: String = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") {
        runShizukuCommand(arrayOf("sh", "-c", "echo performance > $governorPath"))
    }
}
