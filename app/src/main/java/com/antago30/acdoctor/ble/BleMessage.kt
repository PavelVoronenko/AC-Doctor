package com.antago30.acdoctor.ble

sealed class BleMessage {

    data class Battery(val voltage: String) : BleMessage()

    class Data(val payload: ByteArray) : BleMessage() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Data) return false
            return payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            return payload.contentHashCode()
        }

        override fun toString(): String {
            return "Data(payload=${payload.contentToString()})"
        }
    }
}