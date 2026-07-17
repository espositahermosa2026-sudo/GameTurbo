package com.example.gameturbo

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Switch
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
    private lateinit var turboDial: TextView
    private lateinit var dndSwitch: Switch
    private lateinit var overlaySwitch: Switch
    private lateinit var recordSwitch: Switch
    private lateinit var autoDetectSwitch: Switch

    private var turboActive = false
    private var overlayActive = false
    private var autoDetectActive = false

    private val accentRed = Color.parseColor("#FF3B30")

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
            setSwitchSilently(recordSwitch, true)
            Toast.makeText(this, "Grabación iniciada", Toast.LENGTH_SHORT).show()
        } else {
            setSwitchSilently(recordSwitch, false)
            Toast.makeText(this, "Permiso de grabación denegado", Toast.LENGTH_SHORT).show()
        }
        updateStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        turboDial = findViewById(R.id.turboDial)
        dndSwitch = findViewById(R.id.dndSwitch)
        overlaySwitch = findViewById(R.id.overlaySwitch)
        recordSwitch = findViewById(R.id.recordSwitch)
        autoDetectSwitch = findViewById(R.id.autoDetectSwitch)
        val floatingAppRow: LinearLayout = findViewById(R.id.floatingAppRow)

        Shizuku.addRequestPermissionResultListener(permissionListener)

        turboDial.setOnClickListener {
            if (!turboActive) {
                if (!ShizukuManager.isShizukuRunning()) {
                    Toast.makeText(this, "Shizuku no está corriendo. Ábrelo primero.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (!ShizukuManager.hasPermission()) {
                    ShizukuManager.requestPermission()
                    return@setOnClickListener
                }
                turboActive = true
                turboDial.setTextColor(accentRed)
                Thread {
                    PerformanceBooster.killBackgroundProcesses()
                    PerformanceBooster.setHighPerformanceMode()
                    runOnUiThread {
                        DoNotDisturbController.enable(this)
                        setSwitchSilently(dndSwitch, true)
                        updateStatus()
                        Toast.makeText(this, "Modo Turbo activado", Toast.LENGTH_SHORT).show()
                    }
                }.start()
            } else {
                turboActive = false
                turboDial.setTextColor(Color.WHITE)
                updateStatus()
                Toast.makeText(this, "Modo Turbo desactivado", Toast.LENGTH_SHORT).show()
            }
        }

        dndSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked && !DoNotDisturbController.hasPermission(this)) {
                setSwitchSilently(dndSwitch, false)
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(this, "Concede el permiso y vuelve a activar el switch", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            if (checked) DoNotDisturbController.enable(this) else DoNotDisturbController.disable(this)
            updateStatus()
        }

        overlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    setSwitchSilently(overlaySwitch, false)
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    Toast.makeText(this, "Activa el permiso y vuelve a activar el switch", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                startService(Intent(this, OverlayService::class.java))
                overlayActive = true
                Toast.makeText(this, "Panel flotante activado", Toast.LENGTH_SHORT).show()
            } else {
                stopService(Intent(this, OverlayService::class.java))
                overlayActive = false
            }
        }

        floatingAppRow.setOnClickListener {
            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(this, "Primero activa el dial de Turbo", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showAppPickerDialog()
        }

        recordSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (ScreenRecordService.isRecording) return@setOnCheckedChangeListener
                val projectionManager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                stopService(Intent(this, ScreenRecordService::class.java))
                Toast.makeText(this, "Grabación detenida", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }

        autoDetectSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!UsageAccessHelper.hasUsageAccess(this)) {
                    setSwitchSilently(autoDetectSwitch, false)
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    Toast.makeText(this, "Activa el acceso de uso y vuelve a activar el switch", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    setSwitchSilently(autoDetectSwitch, false)
                    Toast.makeText(this, "Primero activa el switch de Panel Flotante una vez", Toast.LENGTH_LONG).show()
                    return@setOnCheckedChangeListener
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(Intent(this, AutoDetectService::class.java))
                } else {
                    startService(Intent(this, AutoDetectService::class.java))
                }
                autoDetectActive = true
                Toast.makeText(this, "Se activará solo al abrir un juego", Toast.LENGTH_LONG).show()
            } else {
                stopService(Intent(this, AutoDetectService::class.java))
                autoDetectActive = false
            }
        }

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
            ACTION_START_RECORDING -> if (!recordSwitch.isChecked) recordSwitch.isChecked = true
            ACTION_PICK_FLOATING_APP -> {
                if (ShizukuManager.hasPermission()) showAppPickerDialog()
                else Toast.makeText(this, "Primero activa el dial de Turbo", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setSwitchSilently(switch: Switch, checked: Boolean) {
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = checked
        when (switch) {
            dndSwitch -> switch.setOnCheckedChangeListener { _, c ->
                if (c) DoNotDisturbController.enable(this) else DoNotDisturbController.disable(this)
                updateStatus()
            }
            recordSwitch -> switch.setOnCheckedChangeListener { _, c ->
                if (!c) {
                    stopService(Intent(this, ScreenRecordService::class.java))
                    updateStatus()
                }
            }
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
                Toast.makeText(this, "Abriendo ${chosen.label} en ventana flotante (si tu equipo lo soporta)", Toast.LENGTH_LONG).show()
            }
            .show()
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
