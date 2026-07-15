package com.example.gameturbo

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object PerformanceBooster {

    private fun runShizukuCommand(command: Array<String>): String {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, command, null, null) as Process
        val output = BufferedReader(InputStreamReader(process.inputStream))
            .readText()
        process.waitFor()
        return output
    }

    fun killBackgroundProcesses(excludedPackages: Set<String> = emptySet()) {
        val listOutput = runShizukuCommand(arrayOf("sh", "-c", "pm list packages -3"))
        val packages = listOutput.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in excludedPackages && it != "com.example.gameturbo" }

        for (pkg in packages) {
            runShizukuCommand(arrayOf("am", "kill", pkg))
        }
    }

    fun setHighPerformanceMode(governorPath: String = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") {
        runShizukuCommand(arrayOf("sh", "-c", "echo performance > $governorPath"))
    }
}
