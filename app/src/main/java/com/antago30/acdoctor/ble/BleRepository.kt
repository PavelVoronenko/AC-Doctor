package com.antago30.acdoctor.ble

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.antago30.acdoctor.model.ConnectedDevice
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BleRepository(private val bleManager: BleManager) {

    private val _connectedDevices =
        MutableLiveData<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices

    private val deviceInfoMap = ConcurrentHashMap<String, ConnectedDevice>()

    private val scanAndConnectDisposable = CompositeDisposable()
    var onConnectionProcessFinished: ((foundAny: Boolean) -> Unit)? = null

    fun disconnectAllAndConnect(maxDevices: Int = 5) {
        disconnectAll()
        _connectedDevices.postValue(emptyList())
        deviceInfoMap.clear()
        scanAndConnectDisposable.clear()
        connectToAllCompatibleDevices(maxDevices)
    }

    fun disconnectAll() {
        val currentDeviceIds = _connectedDevices.value?.map { it.deviceId } ?: emptyList()

        currentDeviceIds.forEach { deviceId ->
            bleManager.disconnect(deviceId)
        }

        _connectedDevices.postValue(emptyList())
        deviceInfoMap.clear()
        scanAndConnectDisposable.clear()
    }

    fun connectToAllCompatibleDevices(maxDevices: Int = 5) {
        if ((connectedDevices.value?.size ?: 0) >= maxDevices) {
            onConnectionProcessFinished?.invoke(false)
            return
        }

        scanAndConnectDisposable.clear()
        _connectedDevices.postValue(emptyList())
        deviceInfoMap.clear()

        val scanJob = bleManager.scanForCompatibleDevices()
            .takeUntil(Observable.timer(3, TimeUnit.SECONDS, Schedulers.io()))
            .toList()
            .map { it.take(maxDevices) }
            .onErrorReturn { emptyList() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ devices ->
                Log.d("BLE1", "Scan finished. Found ${devices.size} devices")

                val foundAny = devices.isNotEmpty()
                for (device in devices) {
                    val mac = device.macAddress
                    if (connectedDevices.value?.any { it.deviceId == mac } == true) continue
                    if (bleManager.isConnected(mac)) continue

                    startListeningToDevice(device)
                }

                onConnectionProcessFinished?.invoke(foundAny)

            }, { error ->
                onConnectionProcessFinished?.invoke(false)
            })

        scanAndConnectDisposable.add(scanJob)
    }

    private fun startListeningToDevice(device: RxBleDevice) {
        val mac = device.macAddress
        Log.d("BLE1", "Starting listener for $mac")

        val listener = bleManager.connectToDeviceAsObservable(device)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { data -> handleIncomingMessage(device, data) },
                { error ->
                    Log.w("BLE1", "Listener error for $mac", error)
                    handleDeviceError(device, error)
                }
            )

        scanAndConnectDisposable.add(listener)
    }

    private fun handleIncomingMessage(device: RxBleDevice, data: ByteArray) {
        val mac = device.macAddress
        val message = data.toString(Charsets.UTF_8)

        var currentDevice = deviceInfoMap[mac]
        if (currentDevice == null) {
            currentDevice = ConnectedDevice(
                deviceId = mac,
                name = device.name ?: "Device $mac",
                latestMessage = message,
                isActive = true,
                isError = false
            )
            val list = connectedDevices.value?.toMutableList() ?: mutableListOf()
            list.add(currentDevice)
            _connectedDevices.postValue(list)
        } else {

            currentDevice = currentDevice.copy(
                latestMessage = message,
                isActive = true,
                isError = false
            )
        }
        deviceInfoMap[mac] = currentDevice

        AndroidSchedulers.mainThread().scheduleDirect({
            val updatedDevice = deviceInfoMap[mac]?.copy(isActive = false)
            if (updatedDevice != null) {
                deviceInfoMap[mac] = updatedDevice
                refreshDevices()
            }
        }, 1000, TimeUnit.MILLISECONDS)

        refreshDevices()
    }

    private fun handleDeviceError(device: RxBleDevice, error: Throwable) {
        val mac = device.macAddress

        var currentDevice = deviceInfoMap[mac]
        if (currentDevice != null) {
            currentDevice = currentDevice.copy(
                isError = true,
                isActive = false
            )
            deviceInfoMap[mac] = currentDevice
            refreshDevices()
        } else {
            Log.d(
                "BLE1",
                "Device $mac had an error but was not in the active list: ${error.message}"
            )
        }
    }

    private fun refreshDevices() {
        val updatedList = deviceInfoMap.values.toList()
        _connectedDevices.postValue(updatedList)
    }

    fun clear() {
        scanAndConnectDisposable.clear()
    }
}