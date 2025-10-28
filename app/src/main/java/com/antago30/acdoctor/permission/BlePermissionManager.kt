package com.antago30.acdoctor.permission

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

class BlePermissionManager(
    private val activity: ComponentActivity,
    private val onReadyToScan: () -> Unit
) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            activity.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
    }


    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            requestBluetoothEnableIfNeeded()
        } else {
            val permanentlyDenied = permissions.entries.any { (perm, granted) ->
                !granted && !activity.shouldShowRequestPermissionRationale(perm)
            }

            if (permanentlyDenied) {
                Toast.makeText(
                    activity,
                    "Предоставьте разрешения в настройках приложения",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private val bluetoothLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkIfReady()
    }

    fun requestAllRequirements() {
        if (bluetoothAdapter == null) {
            Toast.makeText(activity, "Bluetooth не поддерживается", Toast.LENGTH_SHORT).show()
            return
        }

        val permissions = getRequiredPermissions()
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PermissionChecker.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            requestBluetoothEnableIfNeeded()
        }
    }

    private fun requestBluetoothEnableIfNeeded() {
        if (bluetoothAdapter?.isEnabled == true) {
            onReadyToScan()
        } else {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothLauncher.launch(enableIntent)
        }
    }

    private fun checkIfReady() {
        if (hasAllPermissions() && bluetoothAdapter?.isEnabled == true) {
            onReadyToScan()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(activity, it) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}