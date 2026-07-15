package com.example.gameturbo

import android.app.NotificationManager
import android.content.Context

/**
 * Controla el modo No Molestar de Android para bloquear notificaciones
 * y llamadas mientras el modo turbo está activo. Requiere el permiso
 * ACCESS_NOTIFICATION_POLICY, que el usuario debe conceder manualmente
 * en Ajustes > Apps > Acceso especial > No molestar.
 */
object DoNotDisturbController {

    fun hasPermission(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun enable(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
    }

    fun disable(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}
