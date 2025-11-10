package com.antago30.acdoctor.ui

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.antago30.acdoctor.R
import com.antago30.acdoctor.model.CardView
import com.antago30.acdoctor.model.ConnectedDevice
import com.antago30.acdoctor.model.SensorType
import com.antago30.acdoctor.utils.BatteryUtils

class CardManager(
    private val context: Context,
    private val cardViews: List<CardView>
) {

    init {
        configureCard(
            cardViews[0],
            R.drawable.pressure_high,
            "Press High",
            R.drawable.bg_icon_circle_red
        )
        configureCard(
            cardViews[1],
            R.drawable.pressure_low,
            "Press Low",
            R.drawable.bg_icon_circle_blue
        )
        configureCard(
            cardViews[2],
            R.drawable.term_all,
            "Temp High",
            R.drawable.bg_icon_circle_red
        )
        configureCard(
            cardViews[3],
            R.drawable.term_all,
            "Temp Low",
            R.drawable.bg_icon_circle_blue
        )
        configureCard(
            cardViews[4],
            R.drawable.term_all,
            "Temp Air",
            R.drawable.bg_icon_circle_green
        )
    }

    fun updateCards(devices: List<ConnectedDevice>) {
        cardViews.forEach { it.renderInactive() }

        for (device in devices) {
            if (device.isError) continue

            val type = getSensorType(device)
            val index = sensorTypeToCardIndex[type] ?: continue
            cardViews[index].renderActive(device.latestMessage, device.batteryVoltage)
        }
    }

    private fun configureCard(card: CardView, icon: Int, label: String, iconColorRes: Int) {
        card.icon.setBackgroundResource(iconColorRes)
        card.label.text = label
        card.icon.setImageResource(icon)
    }

    private fun getSensorType(device: ConnectedDevice): SensorType {
        return when {
            device.name.startsWith("ESP32-Pressure-High") -> SensorType.HIGH_PRESSURE
            device.name.startsWith("ESP32-Pressure-Low") -> SensorType.LOW_PRESSURE
            device.name.startsWith("ESP32-Temp-High") -> SensorType.HIGH_TEMP
            device.name.startsWith("ESP32-Temp-Low") -> SensorType.LOW_TEMP
            device.name.startsWith("ESP32-Temp-All") -> SensorType.ALL_TEMP

            else -> SensorType.UNKNOWN
        }
    }

    private val sensorTypeToCardIndex = mapOf(
        SensorType.HIGH_PRESSURE to 0,
        SensorType.LOW_PRESSURE to 1,
        SensorType.HIGH_TEMP to 2,
        SensorType.LOW_TEMP to 3,
        SensorType.ALL_TEMP to 4
    )

    private fun CardView.renderInactive() {
        value.text = ""
        battery.visibility = View.INVISIBLE
        batteryPercent.visibility = View.INVISIBLE
        indicator.setBackgroundResource(R.drawable.ic_indicator_circle_red)
        setTextColor(R.color.gray_inactive)
    }

    private fun CardView.renderActive(message: String, batteryVoltage: String = "") {
        value.text = message

        // Показываем батарейку и проценты
        battery.visibility = View.VISIBLE
        batteryPercent.visibility = View.VISIBLE

        val percent = if (batteryVoltage.isNotBlank()) {
            BatteryUtils.voltageToPercent(batteryVoltage)
        } else {
            0f
        }

        battery.chargePercent = percent
        batteryPercent.text = context.getString(R.string.battery_percent, percent.toInt())

        indicator.setBackgroundResource(R.drawable.ic_indicator_circle_green)
        setTextColor(R.color.text_light)
    }

    private fun CardView.setTextColor(colorRes: Int) {
        val color = ContextCompat.getColor(context, colorRes)
        value.setTextColor(color)
        label.setTextColor(color)
    }
}