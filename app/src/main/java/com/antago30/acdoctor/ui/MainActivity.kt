package com.antago30.acdoctor.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.antago30.acdoctor.R
import com.antago30.acdoctor.model.CardView
import com.antago30.acdoctor.permission.BlePermissionManager

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var btnConnectBle: Button
    private lateinit var progressBarConnected: ProgressBar
    private lateinit var cardManager: CardManager
    private lateinit var permissionManager: BlePermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        permissionManager = BlePermissionManager(this) {
            viewModel.disconnectAllAndConnect(maxDevices = 5)
        }

        observeDevices()
        setupConnectButton()
    }

    private fun initViews() {
        btnConnectBle = findViewById(R.id.btnConnectBLE)
        progressBarConnected = findViewById(R.id.progressBarConnected)

        val cardViews = listOf(
            CardView(findViewById(R.id.card1)),
            CardView(findViewById(R.id.card2)),
            CardView(findViewById(R.id.card3)),
            CardView(findViewById(R.id.card4)),
            CardView(findViewById(R.id.card5))
        )

        cardManager = CardManager(this, cardViews)
    }

    private fun setupConnectButton() {
        btnConnectBle.setOnClickListener {
            permissionManager.requestAllRequirements()
        }
    }

    private fun observeDevices() {
        viewModel.connectedDevices.observe(this) { devices ->
            cardManager.updateCards(devices)
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBarConnected.visibility = if (loading) View.VISIBLE else View.INVISIBLE
            btnConnectBle.isEnabled = !loading
            btnConnectBle.isClickable = !loading
            btnConnectBle.background = if (loading) {
                ContextCompat.getDrawable(this, R.drawable.button_background_with_ripple_disabled)
            } else {
                ContextCompat.getDrawable(this, R.drawable.button_background_with_ripple)
            }
        }

        viewModel.toastMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnectAll()
    }
}