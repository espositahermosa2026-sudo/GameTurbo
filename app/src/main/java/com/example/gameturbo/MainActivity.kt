package com.example.gameturbo

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_START_RECORDING = "com.example.gameturbo.START_RECORDING"
        const val ACTION_PICK_FLOATING_APP = "com.example.gameturbo.PICK_FLOATING_APP"
    }

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

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenRecordService::class.java)
            intent.putExtra(ScreenRecordService.EXTRA_RESULT_CODE, result.resultCode)
            intent.putExtra(ScreenRecordService.EXTRA_RESULT_DATA, result.data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permiso de grabación denegado", Toast.LENGTH_SHORT).show()
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
        val floatingAppButton: Button = findViewById(R.id.floatingAppButton)
        val recordButton: Button = findViewById(R.id.recordButton)

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

        floatingAppButton.setOnClickListener {
            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(
                    this,
                    "Primero activa el permiso de Shizuku (botón Modo Turbo)",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            showAppPickerDialog()
        }

        recordButton.setOnClickListener { toggleRecording() }

        updateStatus()
        handleIntentAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    private fun handleIntentAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_START_RECORDING -> toggleRecording()
            ACTION_PICK_FLOATING_APP -> {
                if (ShizukuManager.hasPermission()) showAppPickerDialog()
                else Toast.makeText(
                    this,
                    "Primero activa el permiso de Shizuku (botón Modo Turbo)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun toggleRecording() {
        if (ScreenRecordService.isRecording) {
            stopService(Intent(this, ScreenRecordService::class.java))
            Toast.makeText(this, "Grabación detenida", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    private fun showAppPickerDialog() {
        val apps = FreeformLauncher.listLaunchableApps(this)
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Elige una app para abrir flotando")
            .setItems(labels) { _, index ->
                val chosen = apps[index]
                FreeformLauncher.launchFloating(this, chosen.packageName)
                Toast.makeText(
                    this,
                    "Abriendo ${chosen.label} en ventana flotante (si tu equipo lo soporta)",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
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
        val recOk = ScreenRecordService.isRecording
        statusText.text = "Shizuku: ${if (shizukuOk) "OK" else "pendiente"} | " +
                "No Molestar: ${if (dndOk) "OK" else "pendiente"} | " +
                "Turbo: ${if (turboActive) "ACTIVO" else "inactivo"} | " +
                "Grabando: ${if (recOk) "SI" else "no"}"
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
