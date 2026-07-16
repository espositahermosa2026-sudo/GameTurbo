package com.example.gameturbo

import android.content.Intent
import android.net.Uri
import android.os.Build
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
        val overlayButton: Button = findViewById(R.id.overlayButton)

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

        overlayButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Activa el permiso y vuelve a tocar este botón",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Panel flotante activado", Toast.LENGTH_SHORT).show()
        }

        updateStatus()
    }

    private fun toggleTurbo() {
        turboActive = !turboActive
        if (turboActive) {
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
