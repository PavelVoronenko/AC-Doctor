package com.antago30.acdoctor.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.antago30.acdoctor.R
import com.antago30.acdoctor.domain.model.CardView
import com.antago30.acdoctor.domain.model.ConnectedDevice
import com.antago30.acdoctor.domain.model.SensorType

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var btnConnectBle: Button
    private lateinit var progressBarConnected: ProgressBar
    private val cardViews = mutableListOf<CardView>()

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
        progressBarConnected = findViewById(R.id.progressBarConnected)

        cardViews.add(CardView(findViewById(R.id.card1)))
        cardViews.add(CardView(findViewById(R.id.card2)))
        cardViews.add(CardView(findViewById(R.id.card3)))
        cardViews.add(CardView(findViewById(R.id.card4)))
        cardViews.add(CardView(findViewById(R.id.card5)))

        configureCard(
            cardViews[0],
            R.drawable.pressure_high,
            "High Pressure",
            R.drawable.bg_icon_circle_red
        )
        configureCard(
            cardViews[1],
            R.drawable.pressure_low,
            "Low Pressure",
            R.drawable.bg_icon_circle_blue
        )
        configureCard(
            cardViews[2],
            R.drawable.term_high,
            "High Temp",
            R.drawable.bg_icon_circle_green
        )
        configureCard(
            cardViews[3],
            R.drawable.term_low,
            "Low Temp",
            R.drawable.bg_icon_circle_orange
        )
        configureCard(
            cardViews[4],
            R.drawable.term_all,
            "All Temp",
            R.drawable.bg_icon_circle_purple
        )
    }

    private fun configureCard(card: CardView, icon: Int, label: String, iconColorRes: Int) {
        card.icon.setBackgroundResource(iconColorRes)
        card.label.text = label
        card.icon.setImageResource(icon)
    }

    private fun setupConnectButton() {
        btnConnectBle.setOnClickListener {
            requestBlePermissions()
        }
    }

    private fun requestBlePermissions() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
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

        viewModel.isLoading.observe(this) { loading ->
            progressBarConnected.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        }

        viewModel.toastMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI(devices: List<ConnectedDevice>) {
        for (card in cardViews) {
            card.render(null)
        }

        for (device in devices) {
            if (device.isError) continue

            val type = getSensorType(device)
            val index = sensorTypeToCardIndex[type] ?: continue
            cardViews[index].render(device)
        }
    }

    private fun CardView.render(device: ConnectedDevice?) {
        when {
            device == null || device.isError -> {
                value.text = ""
                indicator.setBackgroundResource(R.drawable.ic_indicator_circle_red)
                setTextColor(R.color.gray_inactive)
            }

            else -> {
                value.text = device.latestMessage
                indicator.setBackgroundResource(R.drawable.ic_indicator_circle_green)
                setTextColor(R.color.text_light)
            }
        }
    }

    private fun CardView.setTextColor(colorRes: Int) {
        val color = getColor(colorRes)
        value.setTextColor(color)
        label.setTextColor(color)
    }

    private fun getSensorType(device: ConnectedDevice): SensorType {
        val deviceName = device.name
        return when {
            deviceName.startsWith("ESP32-C3-Pressure") -> SensorType.HIGH_PRESSURE
            deviceName.startsWith("ESP32-C3-Temperature") -> SensorType.HIGH_TEMP

            else -> SensorType.UNKNOWN
        }
    }

    private val sensorTypeToCardIndex = mapOf(
        SensorType.HIGH_PRESSURE to 0,
        SensorType.LOW_PRESSURE to 1,
        SensorType.HIGH_TEMP to 2,
        SensorType.LOW_TEMP to 3,
        SensorType.BOILER_TEMP to 4
    )
}