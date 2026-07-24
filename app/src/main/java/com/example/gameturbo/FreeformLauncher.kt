package com.example.gameturbo

import android.content.Context
import android.content.pm.PackageManager

object FreeformLauncher {

    data class LaunchableApp(val label: String, val packageName: String)

    fun listLaunchableApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        return resolveInfos
            .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launchFloating(context: Context, packageName: String) {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        val component = launchIntent.component ?: return
        val componentStr = "${component.packageName}/${component.className}"

        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val winW = (screenW * 0.45).toInt()
        val winH = (screenH * 0.45).toInt()
        val left = screenW - winW
        val top = screenH - winH
        val right = screenW
        val bottom = screenH

        Thread {
            runShell("am start -n $componentStr --windowingMode 5")
            Thread.sleep(700)
            val taskId = findTaskId(componentStr)
            if (taskId != null) {
                runShell("am task resize $taskId $left $top $right $bottom")
            }
        }.start()
    }

    private fun findTaskId(componentStr: String): String? {
        val output = runShellForOutput("dumpsys activity activities") ?: return null
        val regex = Regex(Regex.escape(componentStr) + ".*?\\bt(\\d+)\\}")
        return regex.find(output)?.groupValues?.get(1)
    }

    private fun newShizukuProcess(cmd: Array<String>): Process? {
        return try {
            val method = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        } catch (e: Exception) {
            null
        }
    }

    private fun runShell(command: String) {
        try {
            val process = newShizukuProcess(arrayOf("sh", "-c", command)) ?: return
            process.waitFor()
        } catch (e: Exception) {
        }
    }

    private fun runShellForOutput(command: String): String? {
        return try {
            val process = newShizukuProcess(arrayOf("sh", "-c", command)) ?: return null
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            null
        }
    }
}
