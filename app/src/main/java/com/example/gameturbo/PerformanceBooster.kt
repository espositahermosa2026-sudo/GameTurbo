package com.example.gameturbo

object PerformanceBooster {

    fun killBackgroundProcesses(excludedPackages: Set<String> = emptySet()) {
        val listOutput = ShizukuShell.run(arrayOf("sh", "-c", "pm list packages -3"))
        val packages = listOutput.lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it !in excludedPackages && it != "com.example.gameturbo" }

        for (pkg in packages) {
            ShizukuShell.run(arrayOf("am", "kill", pkg))
        }
    }

    fun setHighPerformanceMode(governorPath: String = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") {
        ShizukuShell.run(arrayOf("sh", "-c", "echo performance > $governorPath"))
    }
}
