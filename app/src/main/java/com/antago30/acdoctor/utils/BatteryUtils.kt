package com.antago30.acdoctor.utils

object BatteryUtils {
    fun voltageToPercent(voltageStr: String): Float {
        return try {
            val v = voltageStr.toFloat()
            when {
                v >= 4.2f -> 100f
                v <= 3.69f -> 0f
                else -> ((v - 3.69f) / (4.2f - 3.69f) * 100f).coerceIn(0f, 100f)
            }
        } catch (_: NumberFormatException) {
            0f
        }
    }
}