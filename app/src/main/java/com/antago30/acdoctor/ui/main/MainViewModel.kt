package com.antago30.acdoctor.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.antago30.acdoctor.data.ble.BleManager
import com.antago30.acdoctor.data.repository.BleRepository
import com.polidea.rxandroidble2.RxBleDevice

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BleRepository by lazy {
        BleRepository(BleManager())
    }

    val connectedDevices = repository.connectedDevices
    fun disconnectFromDevice(deviceId: String) = repository.disconnectFromDevice(deviceId)
    fun connectToAllCompatibleDevices() = repository.connectToAllCompatibleDevices(maxDevices = 5)
    fun disconnectAllAndConnect(maxDevices: Int = 5) = repository.disconnectAllAndConnect(maxDevices)


    override fun onCleared() {
        repository.clear()
        super.onCleared()
    }
}