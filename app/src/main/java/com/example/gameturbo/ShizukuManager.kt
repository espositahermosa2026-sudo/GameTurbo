package com.example.gameturbo

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Encapsula la comprobación de disponibilidad y permisos de Shizuku.
 * Shizuku debe estar instalado y su servicio corriendo en el dispositivo
 * (activado por el usuario vía ADB o root una sola vez).
 */
object ShizukuManager {

    private const val REQUEST_CODE = 1001

    /** True si el servicio Shizuku está corriendo en el dispositivo. */
    fun isShizukuRunning(): Boolean = Shizuku.pingBinder()

    /** True si nuestra app ya tiene el permiso concedido. */
    fun hasPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Pide el permiso al usuario. El resultado llega al listener registrado
     * con Shizuku.addRequestPermissionResultListener (normalmente en la Activity).
     */
    fun requestPermission() {
        if (isShizukuRunning() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }
}
