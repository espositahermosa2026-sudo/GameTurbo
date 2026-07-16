package com.example.gameturbo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class AutoDetectService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var inGame = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        handler.post(pollRunnable)
    }

    private fun startForegroundNotification() {
        val channelId = "gameturbo_autodetect"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Deteccion automatica de juegos",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Game Turbo vigilando")
            .setContentText("Se activará solo al abrir un juego")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(3, notification)
    }

    private fun checkForegroundApp() {
        val pkg = getForegroundPackage() ?: return
        if (pkg == packageName) return
        if (pkg == lastForegroundPackage) return
        lastForegroundPackage = pkg

        val isGame = isGamePackage(pkg)

        if (isGame && !inGame) {
            inGame = true
            onEnterGame()
        } else if (!isGame && inGame) {
            inGame = false
            onExitGame()
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000
        val events = usm.queryEvents(begin, end)
        var result: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                result = event.packageName
            }
        }
        return result
    }

    private fun isGamePackage(pkg: String): Boolean {
        return try {
            val info: ApplicationInfo = packageManager.getApplicationInfo(pkg, 0)
            info.category == ApplicationInfo.CATEGORY_GAME
        } catch (e: Exception) {
            false
        }
    }

    private fun onEnterGame() {
        if (ShizukuManager.hasPermission()) {
            Thread {
                PerformanceBooster.killBackgroundProcesses()
                PerformanceBooster.setHighPerformanceMode()
            }.start()
        }
        if (DoNotDisturbController.hasPermission(this)) {
            DoNotDisturbController.enable(this)
        }
        startService(Intent(this, OverlayService::class.java))
    }

    private fun onExitGame() {
        if (DoNotDisturbController.hasPermission(this)) {
            DoNotDisturbController.disable(this)
        }
        stopService(Intent(this, OverlayService::class.java))
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
