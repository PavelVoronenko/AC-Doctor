package com.antago30.acdoctor.ble

import java.util.UUID

object BleConstants {
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    val RX_CHAR_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    val BATTERY_CHAR_UUID: UUID = UUID.fromString("e1f00324-70ea-4fff-b0b1-94f33872dff9")
}