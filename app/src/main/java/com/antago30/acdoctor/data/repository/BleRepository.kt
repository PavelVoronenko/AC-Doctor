package com.antago30.acdoctor.data.repository

import com.antago30.acdoctor.data.ble.BleManager
import com.antago30.acdoctor.domain.model.ConnectedDevice
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BleRepository(private val bleManager: BleManager) {

    private val _connectedDevices = androidx.lifecycle.MutableLiveData<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices

    private val messageMap = ConcurrentHashMap<String, String>()
    private val activeMap = ConcurrentHashMap<String, Boolean>()
    private val disposables = CompositeDisposable()

    fun startScan(onDeviceFound: (RxBleDevice) -> Unit) {
        val disposable = bleManager.scan()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { device -> onDeviceFound(device) },
                { it.printStackTrace() }
            )
        disposables.add(disposable)
    }

    fun stopScan() {
        disposables.clear()
    }

    fun connectToDevice(bleDevice: RxBleDevice) {
        if (_connectedDevices.value?.size == 2) return
        val addr = bleDevice.macAddress
        if (bleManager.isConnected(addr)) return

        bleManager.connect(bleDevice) { data ->
            val message = data.toString(Charsets.UTF_8)
            messageMap[addr] = message
            activeMap[addr] = true
            refreshDevices()

            AndroidSchedulers.mainThread().scheduleDirect({
                activeMap[addr] = false
                refreshDevices()
            }, 1000, TimeUnit.MILLISECONDS)
        }

        val newDevice = ConnectedDevice(
            deviceId = addr,
            name = bleDevice.name ?: "Unknown"
        )
        val list = _connectedDevices.value?.toMutableList() ?: mutableListOf()
        list.add(newDevice)
        _connectedDevices.postValue(list)
    }

    fun disconnectFromDevice(deviceId: String) {
        bleManager.disconnect(deviceId)
        val newList = _connectedDevices.value?.filter { it.deviceId != deviceId } ?: emptyList()
        _connectedDevices.postValue(newList)
        messageMap.remove(deviceId)
        activeMap.remove(deviceId)
    }

    private fun refreshDevices() {
        val updated = _connectedDevices.value?.map { dev ->
            dev.copy(
                latestMessage = messageMap[dev.deviceId] ?: "",
                isActive = activeMap[dev.deviceId] ?: false
            )
        } ?: emptyList()
        _connectedDevices.postValue(updated)
    }

    fun clear() {
        disposables.clear()
    }
}