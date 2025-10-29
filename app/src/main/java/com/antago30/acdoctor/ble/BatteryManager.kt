package com.antago30.acdoctor.ble

import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDeviceServices
import io.reactivex.Observable
import java.util.UUID

class BatteryManager(
    private val serviceUuid: UUID = BleConstants.SERVICE_UUID,
    private val batteryCharUuid: UUID = BleConstants.BATTERY_CHAR_UUID
) {

    fun observeBattery(connection: RxBleConnection): Observable<String> {
        return connection.discoverServices()
            .toObservable()
            .flatMap { services: RxBleDeviceServices ->
                val service = services.bluetoothGattServices.firstOrNull { it.uuid == serviceUuid }
                val char = service?.characteristics?.firstOrNull { it.uuid == batteryCharUuid }

                if (char == null) {
                    Observable.error(RuntimeException("Battery characteristic not found"))
                } else {
                    connection.setupNotification(char)
                        .flatMap { it }
                        .map { it.toString(Charsets.UTF_8) }
                }
            }
    }
}