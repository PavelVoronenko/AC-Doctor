package com.antago30.acdoctor.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.antago30.acdoctor.R
import com.antago30.acdoctor.domain.model.ConnectedDevice
import com.polidea.rxandroidble2.RxBleDevice

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var container1: LinearLayout
    private lateinit var container2: LinearLayout
    private lateinit var tvDevice1: TextView
    private lateinit var tvDevice2: TextView
    private lateinit var indicator1: View
    private lateinit var indicator2: View

    private val devicesFound = mutableSetOf<RxBleDevice>()
    private val handler = Handler(Looper.getMainLooper())
    private var scanTimeoutRunnable: Runnable? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startScanning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        observeDevices()
        setupScanButton()
    }

    private fun initViews() {
        btnScan = findViewById(R.id.btnScan)
        tvStatus = findViewById(R.id.tvStatus)
        container1 = findViewById(R.id.containerDevice1)
        container2 = findViewById(R.id.containerDevice2)
        tvDevice1 = findViewById(R.id.tvDevice1)
        tvDevice2 = findViewById(R.id.tvDevice2)
        indicator1 = findViewById(R.id.indicator1)
        indicator2 = findViewById(R.id.indicator2)
    }

    private fun setupScanButton() {
        btnScan.setOnClickListener {
            requestBlePermissions()
        }
    }

    private fun requestBlePermissions() {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missing.isEmpty()) {
            startScanning()
        } else {
            requestPermissionLauncher.launch(missing)
        }
    }

    private fun startScanning() {
        devicesFound.clear()
        viewModel.startScan { device ->
            devicesFound.add(device)
        }

        btnScan.text = "Сканирование..."
        btnScan.isEnabled = false

        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = Runnable {
            viewModel.stopScan()
            if (!isFinishing && !isDestroyed) {
                showDeviceSelectionDialog()
            }
        }
        handler.postDelayed(scanTimeoutRunnable!!, 10000)
    }

    private fun showDeviceSelectionDialog() {
        if (isFinishing || isDestroyed) return

        if (devicesFound.isEmpty()) {
            Toast.makeText(this, "Устройства не найдены", Toast.LENGTH_SHORT).show()
            resetScanButton()
            return
        }

        val deviceNames = devicesFound.map { it.name ?: it.macAddress }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите устройство")
            .setItems(deviceNames) { _, which ->
                val device = devicesFound.elementAt(which)
                viewModel.connectToDevice(device)
            }
            .setOnDismissListener { resetScanButton() }
            .show()
    }

    private fun resetScanButton() {
        if (!isFinishing && !isDestroyed) {
            btnScan.text = getString(R.string.scan)
            btnScan.isEnabled = true
        }
    }

    private fun observeDevices() {
        viewModel.connectedDevices.observe(this) { devices ->
            updateUI(devices)
        }
    }

    private fun updateUI(devices: List<ConnectedDevice>) {
        when (devices.size) {
            0 -> {
                tvStatus.visibility = View.VISIBLE
                container1.visibility = View.GONE
                container2.visibility = View.GONE
            }
            1 -> {
                tvStatus.visibility = View.GONE
                container1.visibility = View.VISIBLE
                container2.visibility = View.GONE
                renderDevice(devices[0], tvDevice1, indicator1)
            }
            2 -> {
                tvStatus.visibility = View.GONE
                container1.visibility = View.VISIBLE
                container2.visibility = View.VISIBLE
                renderDevice(devices[0], tvDevice1, indicator1)
                renderDevice(devices[1], tvDevice2, indicator2)
            }
        }
    }

    private fun renderDevice(device: ConnectedDevice, textView: TextView, indicator: View) {
        textView.text = "${device.name}: ${device.latestMessage}"
        indicator.setBackgroundColor(
            if (device.isActive) getColor(R.color.green) else getColor(R.color.gray)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
    }
}