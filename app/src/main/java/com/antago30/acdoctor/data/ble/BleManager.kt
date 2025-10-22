package com.antago30.acdoctor.data.ble

import android.annotation.SuppressLint
import com.antago30.acdoctor.BleApplication
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import java.util.*
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattCharacteristic
import com.polidea.rxandroidble2.RxBleDeviceServices

class BleManager {

    private val rxBleClient = BleApplication.instance.rxBleClient

    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val RX_CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private val activeConnections = mutableMapOf<String, Disposable>()

    @SuppressLint("MissingPermission")
    fun scan(): Observable<RxBleDevice> {
        return rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        ).map { it.bleDevice }
    }

    @SuppressLint("MissingPermission")
    fun connect(bleDevice: RxBleDevice, onMessage: (ByteArray) -> Unit) {
        val address = bleDevice.macAddress
        if (activeConnections.containsKey(address)) return

        val disposable = bleDevice.establishConnection(false)
            .flatMap { connection ->
                connection.discoverServices()
                    .toObservable()
                    .flatMap { rxBleServices: RxBleDeviceServices ->
                        val servicesList = rxBleServices.bluetoothGattServices

                        var targetService: BluetoothGattService? = null
                        for (i in 0 until servicesList.size) {
                            val service = servicesList[i]
                            if (service.uuid == SERVICE_UUID) {
                                targetService = service
                                break
                            }
                        }

                        if (targetService == null) {
                            Observable.error(RuntimeException("Service not found"))
                        } else {
                            var targetChar: BluetoothGattCharacteristic? = null
                            for (j in 0 until targetService.characteristics.size) {
                                val char = targetService.characteristics[j]
                                if (char.uuid == RX_CHAR_UUID) {
                                    targetChar = char
                                    break
                                }
                            }

                            if (targetChar == null) {
                                Observable.error(RuntimeException("Char not found"))
                            } else {
                                connection.setupNotification(targetChar)
                                    .flatMap { it }
                            }
                        }
                    }
            }
            .subscribe(
                { data -> onMessage(data) },
                { error ->
                    activeConnections.remove(address)
                    error.printStackTrace()
                }
            )

        activeConnections[address] = disposable
    }

    fun disconnect(deviceAddress: String) {
        activeConnections[deviceAddress]?.dispose()
        activeConnections.remove(deviceAddress)
    }

    fun isConnected(deviceAddress: String): Boolean {
        return activeConnections.containsKey(deviceAddress)
    }
}