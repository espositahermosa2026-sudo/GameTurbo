package com.example.gameturbo

import android.app.ActivityManager
import android.content.Context
import java.io.RandomAccessFile

object SystemStats {

    private var lastTotal: Long = -1
    private var lastIdle: Long = -1

    fun readBatteryPercent(context: Context): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (level in 0..100) level else null
        } catch (e: Exception) {
            null
        }
    }

    fun readWifiStatus(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return "Sin red"
            val caps = cm.getNetworkCapabilities(network) ?: return "Sin red"
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos"
                else -> "Sin red"
            }
        } catch (e: Exception) {
            "N/D"
        }
    }

    fun readRamUsageGb(context: Context): Pair<Float, Float> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem / 1_073_741_824f
        val usedGb = (info.totalMem - info.availMem) / 1_073_741_824f
        return Pair(usedGb, totalGb)
    }

    fun readCpuPercent(): Int? {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            val parts = line.split(" ").filter { it.isNotBlank() && it != "cpu" }
            val values = parts.map { it.toLong() }
            val idle = values[3]
            val total = values.sum()

            if (lastTotal < 0) {
                lastTotal = total
                lastIdle = idle
                return null
            }

            val totalDelta = total - lastTotal
            val idleDelta = idle - lastIdle
            lastTotal = total
            lastIdle = idle

            if (totalDelta <= 0) return null
            (((totalDelta - idleDelta).toFloat() / totalDelta) * 100).toInt().coerceIn(0, 100)
        } catch (e: Exception) {
            null
        }
    }
}
