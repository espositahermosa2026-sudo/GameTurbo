package com.example.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import java.util.LinkedList

class SparklineView(context: Context) : View(context) {

    private val history = LinkedList<Float>()
    private val maxSamples = 30

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#FF3B30")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33FF3B30")
    }

    fun addSample(value: Float) {
        history.addLast(value)
        if (history.size > maxSamples) history.removeFirst()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (history.size < 2) return

        val max = (history.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val stepX = width.toFloat() / (maxSamples - 1)
        val linePath = Path()
        val fillPath = Path()

        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value / max) * height
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, height.toFloat())
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((history.size - 1) * stepX, height.toFloat())
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
