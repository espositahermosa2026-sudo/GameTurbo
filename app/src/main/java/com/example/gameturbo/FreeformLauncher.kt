package com.example.gameturbo

import android.content.Context

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

        Thread {
            ShizukuShell.run(
                arrayOf(
                    "sh", "-c",
                    "am start -n $componentStr --windowingMode 5"
                )
            )
        }.start()
    }
}
