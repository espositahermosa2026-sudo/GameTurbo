package com.example.gameturbo

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private var turboActive = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de Shizuku concedido", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permiso de Shizuku denegado", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        val turboButton: Button = findViewById(R.id.turboButton)
        val dndPermissionButton: Button = findViewById(R.id.dndPermissionButton)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        turboButton.setOnClickListener {
            if (!ShizukuManager.isShizukuRunning()) {
                Toast.makeText(this, "Shizuku no está corriendo. Ábrelo primero.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!ShizukuManager.hasPermission()) {
                ShizukuManager.requestPermission()
                return@setOnClickListener
            }
            toggleTurbo()
        }

        dndPermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }

        updateStatus()
    }

    private fun toggleTurbo() {
        turboActive = !turboActive
        if (turboActive) {
            // Ejecutar en un hilo aparte: las llamadas a Shizuku no deben ir en el hilo principal
            Thread {
                PerformanceBooster.killBackgroundProcesses()
                PerformanceBooster.setHighPerformanceMode()
                runOnUiThread {
                    DoNotDisturbController.enable(this)
                    updateStatus()
                    Toast.makeText(this, "Modo Turbo activado", Toast.LENGTH_SHORT).show()
                }
            }.start()
        } else {
            DoNotDisturbController.disable(this)
            updateStatus()
            Toast.makeText(this, "Modo Turbo desactivado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus() {
        val shizukuOk = ShizukuManager.isShizukuRunning() && ShizukuManager.hasPermission()
        val dndOk = DoNotDisturbController.hasPermission(this)
        statusText.text = "Shizuku: ${if (shizukuOk) "OK" else "pendiente"} | " +
                "No Molestar: ${if (dndOk) "OK" else "pendiente"} | " +
                "Turbo: ${if (turboActive) "ACTIVO" else "inactivo"}"
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
