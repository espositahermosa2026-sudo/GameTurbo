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
