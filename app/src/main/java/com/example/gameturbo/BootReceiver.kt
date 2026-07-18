package com.example.gameturbo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("gameturbo_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("auto_detect_enabled", false)
        if (!enabled) return

        if (!UsageAccessHelper.hasUsageAccess(context)) return

        val serviceIntent = Intent(context, AutoDetectService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
