package com.example.gameturbo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var fpsText: TextView
    private lateinit var tempText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var lastFpsTimestamp = 0L
    private var turboOn = false
    private var dndOn = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            if (lastFpsTimestamp == 0L) lastFpsTimestamp = frameTimeNanos
            val elapsedMs = (frameTimeNanos - lastFpsTimestamp) / 1_000_000
            if (elapsedMs >= 1000) {
                val fps = frameCount
                frameCount = 0
                lastFpsTimestamp = frameTimeNanos
                handler.post { fpsText.text = "FPS: $fps" }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val tempUpdater = object : Runnable {
        override fun run() {
            tempText.text = "Temp: ${readCpuTemperature()}"
            handler.postDelayed(this, 2000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlayView()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        handler.post(tempUpdater)
    }

    private fun startForegroundNotification() {
        val channelId = "gameturbo_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Game Turbo Overlay",
                NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Game Turbo activo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(1, notification)
    }

    private fun buildOverlayView() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#CC1E1E23"))
        }

        fpsText = TextView(this).apply {
            text = "FPS: --"
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        tempText = TextView(this).apply {
            text = "Temp: --"
            setTextColor(Color.WHITE)
            textSize = 14f
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val turboButton = Button(this).apply {
            text = "Turbo"
            textSize = 11f
            setOnClickListener {
                turboOn = !turboOn
                text = if (turboOn) "Turbo ON" else "Turbo"
                if (turboOn && ShizukuManager.hasPermission()) {
                    Thread {
                        PerformanceBooster.killBackgroundProcesses()
                        PerformanceBooster.setHighPerformanceMode()
                    }.start()
                }
            }
        }
        val dndButton = Button(this).apply {
            text = "DND"
            textSize = 11f
            setOnClickListener {
                dndOn = !dndOn
                text = if (dndOn) "DND ON" else "DND"
                if (dndOn) DoNotDisturbController.enable(this@OverlayService)
                else DoNotDisturbController.disable(this@OverlayService)
            }
        }
        val closeButton = Button(this).apply {
            text = "X"
            textSize = 11f
            setOnClickListener { stopSelf() }
        }

        buttonRow.addView(turboButton)
        buttonRow.addView(dndButton)
        buttonRow.addView(closeButton)

        panel.addView(fpsText)
        panel.addView(tempText)
        panel.addView(buttonRow)

        overlayView = panel

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 200

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(overlayView, params)
    }

    private fun readCpuTemperature(): String {
        return try {
            for (i in 0..9) {
                val f = File("/sys/class/thermal/thermal_zone$i/temp")
                if (f.exists()) {
                    val raw = f.readText().trim().toFloatOrNull() ?: continue
                    val celsius = if (raw > 1000) raw / 1000 else raw
                    if (celsius in 10f..120f) return "${celsius.toInt()}°C"
                }
            }
            "N/D"
        } catch (e: Exception) {
            "N/D"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        handler.removeCallbacksAndMessages(null)
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: Exception) {
            }
        }
    }
}
