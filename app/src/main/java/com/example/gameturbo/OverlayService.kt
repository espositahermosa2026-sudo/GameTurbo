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
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    private lateinit var collapsedView: TextView
    private lateinit var collapsedParams: WindowManager.LayoutParams
    private var collapsedAdded = false

    private lateinit var expandedView: LinearLayout
    private lateinit var expandedParams: WindowManager.LayoutParams
    private var expandedAdded = false

    private lateinit var fpsValueText: TextView
    private lateinit var cpuValueText: TextView
    private lateinit var tempValueText: TextView
    private lateinit var ramValueText: TextView
    private lateinit var battValueText: TextView
    private lateinit var wifiValueText: TextView
    private lateinit var sparkline: SparklineView

    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var lastFpsTimestamp = 0L
    private var lastFps = 0
    private var turboOn = false
    private var dndOn = false

    private var lastX = 0
    private var lastY = 100

    private val accentCyan = Color.parseColor("#00E5FF")
    private val accentMagenta = Color.parseColor("#FF2D95")
    private val accentGreen = Color.parseColor("#30D158")
    private val textSecondary = Color.parseColor("#8C8C96")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        buildCollapsedView()
        buildExpandedView()
        showCollapsed()
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

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun newParams(): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = lastX
        p.y = lastY
        return p
    }

    private fun makeDragListener(paramsProvider: () -> WindowManager.LayoutParams, view: View): View.OnTouchListener {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        return View.OnTouchListener { v, event ->
            val params = paramsProvider()
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
                    lastX = params.x
                    lastY = params.y
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun buildCollapsedView() {
        collapsedView = TextView(this).apply {
            text = "⚡"
            textSize = 18f
            setTextColor(accentCyan)
            gravity = Gravity.CENTER
            background = getDrawable(R.drawable.bg_collapsed_tab)
        }
        collapsedParams = newParams()
        collapsedParams.width = dp(48)
        collapsedParams.height = dp(48)
        collapsedView.setOnTouchListener(makeDragListener({ collapsedParams }, collapsedView))
        collapsedView.setOnClickListener { showExpanded() }
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
        val circleSize = dp(44)
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
            setPadding(0, dp(3), 0, 0)
        }
        wrapper.addView(iconText)
        wrapper.addView(lbl)
        return Pair(wrapper, iconText)
    }

    private fun buildExpandedView() {
        try {
            buildExpandedViewInner()
        } catch (e: Throwable) {
            android.widget.Toast.makeText(
                this,
                "ERROR panel: ${e.javaClass.simpleName}: ${e.message}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun buildExpandedViewInner() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = getDrawable(R.drawable.bg_overlay_panel)
        }

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
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { showCollapsed() }
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
            setPadding(0, dp(7), 0, dp(5))
            setBackgroundColor(Color.parseColor("#FFFF00"))
        }
        val (fpsBlock, fpsValue) = statBlock("FPS", "--")
        val (cpuBlock, cpuValue) = statBlock("CPU", "--")
        val (tempBlock, tempValue) = statBlock("TEMP", "--")
        val (ramBlock, ramValue) = statBlock("RAM", "--")
        fpsValueText = fpsValue
        cpuValueText = cpuValue
        tempValueText = tempValue
        ramValueText = ramValue

        fun statMargin() = LinearLayout.LayoutParams(dp(66), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) }
        statsRow.addView(fpsBlock, statMargin())
        statsRow.addView(cpuBlock, statMargin())
        statsRow.addView(tempBlock, statMargin())
        statsRow.addView(ramBlock, statMargin())

        val statsRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(5))
            setBackgroundColor(Color.parseColor("#00FF00"))
        }
        val (battBlock, battValue) = statBlock("BATERIA", "--")
        val (wifiBlock, wifiValue) = statBlock("WIFI", "--")
        battValueText = battValue
        wifiValueText = wifiValue
        statsRow2.addView(battBlock, statMargin())
        statsRow2.addView(wifiBlock, statMargin())

        sparkline = SparklineView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(220), dp(50))
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            setBackgroundColor(Color.parseColor("#0000FF"))
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

        fun btnMargin() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(5) }
        buttonsRow.addView(turboWrap, btnMargin())
        buttonsRow.addView(dndWrap, btnMargin())
        buttonsRow.addView(winWrap, btnMargin())
        buttonsRow.addView(recWrap, btnMargin())
        buttonsRow.addView(closeWrap)

        panel.addView(headerRow)
        panel.addView(statsRow)
        panel.addView(statsRow2)
        panel.addView(sparkline)
        panel.addView(buttonsRow)

        expandedView = panel
        expandedParams = newParams()
        expandedParams.width = dp(300)
        expandedParams.height = dp(260)
        headerRow.setOnTouchListener(makeDragListener({ expandedParams }, expandedView))
    }

    private fun showCollapsed() {
        if (expandedAdded) {
            try { windowManager.removeView(expandedView) } catch (e: Exception) {}
            expandedAdded = false
        }
        if (!collapsedAdded) {
            collapsedParams.x = lastX
            collapsedParams.y = lastY
            windowManager.addView(collapsedView, collapsedParams)
            collapsedAdded = true
        }
    }

    private fun showExpanded() {
        if (collapsedAdded) {
            try { windowManager.removeView(collapsedView) } catch (e: Exception) {}
            collapsedAdded = false
        }
        if (!expandedAdded) {
            expandedParams.x = lastX
            expandedParams.y = lastY
            windowManager.addView(expandedView, expandedParams)
            expandedAdded = true
            expandedView.post {
                android.widget.Toast.makeText(
                    this,
                    "Panel: ${expandedView.width}x${expandedView.height}px | statsRow visible: ${expandedView.getChildAt(1)?.visibility} h=${expandedView.getChildAt(1)?.height}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
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
        try { if (collapsedAdded) windowManager.removeView(collapsedView) } catch (e: Exception) {}
        try { if (expandedAdded) windowManager.removeView(expandedView) } catch (e: Exception) {}
    }
}
