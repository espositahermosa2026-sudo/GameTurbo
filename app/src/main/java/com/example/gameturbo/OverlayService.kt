package com.example.gameturbo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootView: FrameLayout
    private lateinit var collapsedTab: TextView
    private lateinit var expandedPanel: LinearLayout
    private lateinit var fpsValueText: TextView
    private lateinit var cpuValueText: TextView
    private lateinit var tempValueText: TextView
    private lateinit var ramValueText: TextView
    private lateinit var battValueText: TextView
    private lateinit var wifiValueText: TextView
    private lateinit var sparkline: SparklineView
    private lateinit var params: WindowManager.LayoutParams

    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var lastFpsTimestamp = 0L
    private var lastFps = 0
    private var turboOn = false
    private var dndOn = false
    private var isExpanded = false

    private val accentCyan = Color.parseColor("#00E5FF")
    private val accentMagenta = Color.parseColor("#FF2D95")
    private val accentGreen = Color.parseColor("#30D158")
    private val textSecondary = Color.parseColor("#8C8C96")

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            if (lastFpsTimestamp == 0L) lastFpsTimestamp = frameTimeNanos
            val elapsedMs = (frameTimeNanos - lastFpsTimestamp) / 1_000_000
            if (elapsedMs >= 1000) {
                lastFps = frameCount
                frameCount = 0
                lastFpsTimestamp = frameTimeNanos
                handler.post {
                    fpsValueText.text = "$lastFps"
                    sparkline.addSample(lastFps.toFloat())
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val statsUpdater = object : Runnable {
        override fun run() {
            tempValueText.text = readCpuTemperature()
            val cpu = SystemStats.readCpuPercent()
            cpuValueText.text = cpu?.let { "$it%" } ?: "N/D"
            val (used, total) = SystemStats.readRamUsageGb(this@OverlayService)
            ramValueText.text = "%.1f/%.1fGB".format(used, total)
            battValueText.text = SystemStats.readBatteryPercent(this@OverlayService)?.let { "$it%" } ?: "N/D"
            wifiValueText.text = SystemStats.readWifiStatus(this@OverlayService)
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
        handler.post(statsUpdater)
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

    private fun statBlock(label: String, initialValue: String): Pair<LinearLayout, TextView> {
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val value = TextView(this).apply {
            text = initialValue
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(textSecondary)
            textSize = 9f
            gravity = Gravity.CENTER
        }
        block.addView(value)
        block.addView(lbl)
        return Pair(block, value)
    }

    private fun circularButton(icon: String, label: String): Pair<LinearLayout, TextView> {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val circleSize = 88
        val iconText = TextView(this).apply {
            text = icon
            textSize = 16f
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_overlay_circle)
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize)
        }
        val lbl = TextView(this).apply {
            text = label
            setTextColor(textSecondary)
            textSize = 8f
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }
        wrapper.addView(iconText)
        wrapper.addView(lbl)
        return Pair(wrapper, iconText)
    }

    private fun expand() {
        collapsedTab.visibility = View.GONE
        expandedPanel.visibility = View.VISIBLE
        isExpanded = true
        windowManager.updateViewLayout(rootView, params)
    }

    private fun collapse() {
        expandedPanel.visibility = View.GONE
        collapsedTab.visibility = View.VISIBLE
        isExpanded = false
        windowManager.updateViewLayout(rootView, params)
    }

    private fun buildOverlayView() {
        rootView = FrameLayout(this)

        collapsedTab = TextView(this).apply {
            text = "⚡"
            textSize = 18f
            setTextColor(accentCyan)
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_collapsed_tab)
            layoutParams = FrameLayout.LayoutParams(90, 90)
            visibility = View.VISIBLE
            setOnClickListener { expand() }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            background = getDrawable(R.drawable.bg_overlay_panel)
            visibility = View.GONE
        }
        expandedPanel = panel

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(this).apply {
            text = "🛡 TURBO HUD"
            setTextColor(accentCyan)
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = 0.05f
        }
        val headerStatus = TextView(this).apply {
            text = "  •  ACTIVO"
            setTextColor(accentGreen)
            textSize = 10f
        }
        val minimizeBtn = TextView(this).apply {
            text = "—"
            setTextColor(accentMagenta)
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(24, 0, 0, 0)
            setOnClickListener { collapse() }
        }
        val headerSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)
        headerRow.addView(headerStatus)
        headerRow.addView(headerSpacer)
        headerRow.addView(minimizeBtn)

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 10)
        }
        val (fpsBlock, fpsValue) = statBlock("FPS", "--")
        val (cpuBlock, cpuValue) = statBlock("CPU", "--")
        val (tempBlock, tempValue) = statBlock("TEMP", "--")
        val (ramBlock, ramValue) = statBlock("RAM", "--")
        fpsValueText = fpsValue
        cpuValueText = cpuValue
        tempValueText = tempValue
        ramValueText = ramValue

        val statParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        statsRow.addView(fpsBlock, statParams)
        statsRow.addView(cpuBlock, statParams)
        statsRow.addView(tempBlock, statParams)
        statsRow.addView(ramBlock, statParams)

        val statsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 10)
        }
        val (battBlock, battValue) = statBlock("BATERIA", "--")
        val (wifiBlock, wifiValue) = statBlock("WIFI", "--")
        battValueText = battValue
        wifiValueText = wifiValue
        statsRow2.addView(battBlock, statParams)
        statsRow2.addView(wifiBlock, statParams)

        sparkline = SparklineView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 140)
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val (turboWrap, turboIcon) = circularButton("⚡", "TURBO")
        turboIcon.setOnClickListener {
            turboOn = !turboOn
            turboIcon.setTextColor(if (turboOn) accentMagenta else Color.WHITE)
            if (turboOn && ShizukuManager.hasPermission()) {
                Thread {
                    PerformanceBooster.killBackgroundProcesses()
                    PerformanceBooster.setHighPerformanceMode()
                }.start()
            }
        }

        val (dndWrap, dndIcon) = circularButton("🔕", "DND")
        dndIcon.setOnClickListener {
            dndOn = !dndOn
            dndIcon.setTextColor(if (dndOn) accentMagenta else Color.WHITE)
            if (dndOn) DoNotDisturbController.enable(this@OverlayService)
            else DoNotDisturbController.disable(this@OverlayService)
        }

        val (winWrap, winIcon) = circularButton("🪟", "VENTANA")
        winIcon.setOnClickListener {
            val i = Intent(this@OverlayService, MainActivity::class.java)
            i.action = MainActivity.ACTION_PICK_FLOATING_APP
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }

        val (recWrap, recIcon) = circularButton("⏺", "GRABAR")
        recIcon.setOnClickListener {
            val i = Intent(this@OverlayService, MainActivity::class.java)
            i.action = MainActivity.ACTION_START_RECORDING
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }

        val (closeWrap, closeIcon) = circularButton("✕", "CERRAR")
        closeIcon.setOnClickListener { stopSelf() }

        val btnParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        buttonsRow.addView(turboWrap, btnParams)
        buttonsRow.addView(dndWrap, btnParams)
        buttonsRow.addView(winWrap, btnParams)
        buttonsRow.addView(recWrap, btnParams)
        buttonsRow.addView(closeWrap, btnParams)

        panel.addView(headerRow)
        panel.addView(statsRow)
        panel.addView(statsRow2)
        panel.addView(sparkline)
        panel.addView(buttonsRow)

        rootView.addView(collapsedTab)
        rootView.addView(expandedPanel)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        val dragListener = View.OnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                else -> false
            }
        }
        collapsedTab.setOnTouchListener { v, event ->
            dragListener.onTouch(v, event)
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            true
        }
        headerRow.setOnTouchListener(dragListener)

        windowManager.addView(rootView, params)
    }

    private fun readCpuTemperature(): String {
        return try {
            for (i in 0..9) {
                val f = File("/sys/class/thermal/thermal_zone$i/temp")
                if (f.exists()) {
                    val raw = f.readText().trim().toFloatOrNull() ?: continue
                    val celsius = if (raw > 1000) raw / 1000 else raw
                    if (celsius in 10f..120f) return "${celsius.toInt()}°"
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
        if (::rootView.isInitialized) {
            try {
                windowManager.removeView(rootView)
            } catch (e: Exception) {
            }
        }
    }
}
