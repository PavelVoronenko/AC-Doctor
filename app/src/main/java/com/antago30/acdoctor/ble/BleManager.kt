package com.antago30.acdoctor.ble

import android.os.ParcelUuid
import android.util.Log
import com.antago30.acdoctor.BleApplication
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ConcurrentHashMap

class BleManager {

    private val rxBleClient = BleApplication.instance.rxBleClient

    private val serviceUuid = BleConstants.SERVICE_UUID
    private val rxCharUuid = BleConstants.RX_CHAR_UUID

    private val activeConnections = ConcurrentHashMap<String, Disposable>()

    fun scanForCompatibleDevices(): Observable<RxBleDevice> {
        val tag = "BLE1"
        Log.d(tag, "Starting BLE scan for service: $serviceUuid")

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        return rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanFilter
        )
            .doOnSubscribe { Log.d(tag, "BLE scan subscribed — scanning started") }
            .doOnDispose { Log.d(tag, "BLE scan disposed — scanning stopped") }
            .map { scanResult -> scanResult.bleDevice }
            .distinct { it.macAddress }
            .doOnNext { device ->
                Log.d(tag, "Device emitted (within take limit): ${device.macAddress}")
            }
    }

    fun connectToDeviceAsObservable(bleDevice: RxBleDevice): Observable<BleMessage> {
        val mac = bleDevice.macAddress

        return bleDevice.establishConnection(false)
            .subscribeOn(Schedulers.io())
            .doOnSubscribe { activeConnections[mac] = it }
            .doFinally { activeConnections.remove(mac) }
            .flatMap { connection ->
                val dataStream: Observable<BleMessage> = connection.discoverServices()
                    .toObservable()
                    .flatMap { services ->
                        val service = services.bluetoothGattServices
                            .firstOrNull { it.uuid == serviceUuid }
                        val char = service?.characteristics
                            ?.firstOrNull { it.uuid == rxCharUuid }

                        if (char == null) {
                            Observable.error(
                                RuntimeException("Main characteristic missing for ${bleDevice.macAddress}")
                            )
                        } else {
                            connection.setupNotification(char)
                                .flatMap { it }
                                .map { BleMessage.Data(it) }
                        }
                    }

                val batteryStream: Observable<BleMessage> = BatteryManager()
                    .observeBattery(connection)
                    .map { BleMessage.Battery(it) }

                Observable.merge(dataStream, batteryStream)
            }
    }

    fun disconnect(deviceAddress: String) {
        activeConnections[deviceAddress]?.dispose()
        activeConnections.remove(deviceAddress)
    }

    fun isConnected(deviceAddress: String): Boolean {
        return activeConnections.containsKey(deviceAddress)
    }
}