package com.antago30.acdoctor.ble

import android.os.ParcelUuid
import android.util.Log
import com.antago30.acdoctor.BleApplication
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.RxBleDeviceServices
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BleManager {

    private val rxBleClient = BleApplication.Companion.instance.rxBleClient

    private val serviceUuid = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val rxCharUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

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

    fun connectToDeviceAsObservable(bleDevice: RxBleDevice): Observable<ByteArray> {
        val mac = bleDevice.macAddress

        return bleDevice.establishConnection(false)
            //.timeout(12, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .flatMap { connection ->
                connection.discoverServices()
                    .toObservable()
                    .flatMap { servicesWrapper: RxBleDeviceServices ->
                        val service = servicesWrapper.bluetoothGattServices
                            .firstOrNull { it.uuid == serviceUuid }

                        val char = service?.characteristics?.firstOrNull { it.uuid == rxCharUuid }

                        if (service == null || char == null) {
                            Observable.error(RuntimeException("Incompatible device: service/char missing"))
                        } else {
                            connection.setupNotification(char).flatMap { it }
                        }
                    }
            }
            .doOnSubscribe { disposable ->
                activeConnections[mac] = disposable
            }
            .doFinally {
                activeConnections.remove(mac)
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