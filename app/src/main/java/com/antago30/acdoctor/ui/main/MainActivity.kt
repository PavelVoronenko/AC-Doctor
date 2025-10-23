package com.antago30.acdoctor.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.antago30.acdoctor.R
import com.antago30.acdoctor.domain.model.ConnectedDevice

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var btnConnectBle: Button
    private lateinit var tvStatus: TextView
    private lateinit var container1: LinearLayout
    private lateinit var container2: LinearLayout
    private lateinit var tvDevice1: TextView
    private lateinit var tvDevice2: TextView
    private lateinit var indicator1: View
    private lateinit var indicator2: View

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            viewModel.disconnectAllAndConnect(maxDevices = 5)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        observeDevices()
        setupConnectButton()
    }

    private fun initViews() {
        btnConnectBle = findViewById(R.id.btnConnectBLE)
        tvStatus = findViewById(R.id.tvStatus)
        container1 = findViewById(R.id.containerDevice1)
        container2 = findViewById(R.id.containerDevice2)
        tvDevice1 = findViewById(R.id.tvDevice1)
        tvDevice2 = findViewById(R.id.tvDevice2)
        indicator1 = findViewById(R.id.indicator1)
        indicator2 = findViewById(R.id.indicator2)
    }

    private fun setupConnectButton() {
        btnConnectBle.setOnClickListener {
            requestBlePermissions()
        }
    }

    private fun requestBlePermissions() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
            viewModel.disconnectAllAndConnect(maxDevices = 5)
        } else {
            requestPermissionLauncher.launch(missing)
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
        textView.text = if (device.isError) "${device.name}: ERROR"
        else "${device.name}: ${device.latestMessage}"

        val indicatorColorRes = if (device.isError) {
            R.color.red
        } else if (device.isActive) {
            R.color.green
        } else {
            R.color.gray
        }
        indicator.setBackgroundColor(getColor(indicatorColorRes))
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}