package com.antago30.acdoctor.data.repository

import android.util.Log
import com.antago30.acdoctor.data.ble.BleManager
import com.antago30.acdoctor.domain.model.ConnectedDevice
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class BleRepository(private val bleManager: BleManager) {

    private val _connectedDevices =
        androidx.lifecycle.MutableLiveData<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices

    private val deviceInfoMap = ConcurrentHashMap<String, ConnectedDevice>()

    private val scanAndConnectDisposable = CompositeDisposable()

    fun disconnectAllAndConnect(maxDevices: Int = 5) {
        disconnectAll()
        _connectedDevices.postValue(emptyList())
        deviceInfoMap.clear()
        scanAndConnectDisposable.clear()
        connectToAllCompatibleDevices(maxDevices)
    }

    private fun disconnectAll() {
        val currentDeviceIds = _connectedDevices.value?.map { it.deviceId } ?: emptyList()
        currentDeviceIds.forEach { deviceId ->
            bleManager.disconnect(deviceId)
        }
    }

    fun connectToAllCompatibleDevices(maxDevices: Int = 5) {
        if ((connectedDevices.value?.size ?: 0) >= maxDevices) return

        scanAndConnectDisposable.clear()

        val scanWithTimeout = bleManager.scanForCompatibleDevices()
            .takeUntil(Observable.timer(3, TimeUnit.SECONDS, Schedulers.io()))

        val job = scanWithTimeout
            .toList()
            .map { list -> list.take(maxDevices) }
            .onErrorReturn { emptyList() }
            .flatMapObservable { devices ->
                Log.d(
                    "BLE1",
                    "Scan finished. Found ${devices.size} devices: ${devices.map { it.macAddress }}"
                )
                Observable.fromIterable(devices)
                    .filter { device ->
                        val alreadyInList =
                            connectedDevices.value?.any { it.deviceId == device.macAddress } == true
                        val alreadyConnected = bleManager.isConnected(device.macAddress)
                        !alreadyInList && !alreadyConnected
                    }
                    .flatMap(
                        { device ->
                            Log.d("BLE1", "Attempting to connect to ${device.macAddress}")
                            bleManager.connectToDeviceAsObservable(device)
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnNext { data ->
                                    Log.d("BLE1", "Data received from ${device.macAddress}")
                                    handleIncomingMessage(device, data)
                                }
                                .doOnError { error ->
                                    Log.w(
                                        "BLE1",
                                        "Failed to connect or receive data from ${device.macAddress}",
                                        error
                                    )
                                    handleDeviceError(device, error)
                                }
                                .onErrorResumeNext(Observable.empty())
                        },
                        false,
                        maxDevices
                    )
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {},
                { error -> Log.w("BLE1", "Pipeline error", error) },
                { Log.d("BLE1", "Scan+connect pipeline completed") }
            )

        scanAndConnectDisposable.add(job)
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