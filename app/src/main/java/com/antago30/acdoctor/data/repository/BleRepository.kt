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

    private val _connectedDevices = androidx.lifecycle.MutableLiveData<List<ConnectedDevice>>(emptyList())
    val connectedDevices = _connectedDevices

    private val messageMap = ConcurrentHashMap<String, String>()
    private val activeMap = ConcurrentHashMap<String, Boolean>()

    private val scanAndConnectDisposable = CompositeDisposable()
    private val connectionDisposables = CompositeDisposable()

    fun connectToAllCompatibleDevices(maxDevices: Int = 5) {
        if ((connectedDevices.value?.size ?: 0) >= maxDevices) return

        scanAndConnectDisposable.clear()

        val scanWithTimeout = bleManager.scanForCompatibleDevices()
            .takeUntil(Observable.timer(2, TimeUnit.SECONDS, Schedulers.io()))

        val job = scanWithTimeout
            .toList()
            .map { list -> list.take(maxDevices) }
            .onErrorReturn { emptyList() }
            .flatMapObservable { devices ->
                Log.d("BLE1", "Scan finished. Found ${devices.size} devices: ${devices.map { it.macAddress }}")
                Observable.fromIterable(devices)
                    .filter { device ->
                        val alreadyInList = connectedDevices.value?.any { it.deviceId == device.macAddress } == true
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
                                    Log.w("BLE1", "Failed to connect to ${device.macAddress}", error)
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

        messageMap[mac] = message
        activeMap[mac] = true
        refreshDevices()

        AndroidSchedulers.mainThread().scheduleDirect({
            activeMap[mac] = false
            refreshDevices()
        }, 1000, TimeUnit.MILLISECONDS)

        if (connectedDevices.value?.any { it.deviceId == mac } == false) {
            val newDevice = ConnectedDevice(
                deviceId = mac,
                name = device.name ?: "Device $mac",
                latestMessage = message,
                isActive = true
            )
            val list = connectedDevices.value?.toMutableList() ?: mutableListOf()
            list.add(newDevice)
            _connectedDevices.postValue(list)
            Log.d("BLE1", "Connected to: $mac")
        }
    }

    // Метод для отключения всех устройств и нового подключения
    fun disconnectAllAndConnect(maxDevices: Int = 5) {
        disconnectAll()

        _connectedDevices.postValue(emptyList())
        messageMap.clear()
        activeMap.clear()

        scanAndConnectDisposable.clear()
        connectToAllCompatibleDevices(maxDevices)
    }

    // Метод для отключения всех устройств
    private fun disconnectAll() {
        val currentDeviceIds = _connectedDevices.value?.map { it.deviceId } ?: emptyList()
        currentDeviceIds.forEach { deviceId ->
            bleManager.disconnect(deviceId)
        }
        connectionDisposables.clear()
    }

    fun disconnectFromDevice(deviceId: String) {
        bleManager.disconnect(deviceId)
        messageMap.remove(deviceId)
        activeMap.remove(deviceId)
        val newList = _connectedDevices.value?.filter { it.deviceId != deviceId } ?: emptyList()
        _connectedDevices.postValue(newList)
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
        scanAndConnectDisposable.clear()
        connectionDisposables.clear()
    }
}