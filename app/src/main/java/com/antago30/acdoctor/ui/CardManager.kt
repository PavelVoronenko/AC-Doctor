package com.antago30.acdoctor.ui

import android.content.Context
import androidx.core.content.ContextCompat
import com.antago30.acdoctor.R
import com.antago30.acdoctor.model.CardView
import com.antago30.acdoctor.model.ConnectedDevice
import com.antago30.acdoctor.model.SensorType

class CardManager(
    private val context: Context,
    private val cardViews: List<CardView>
) {

    init {
        configureCard(
            cardViews[0],
            R.drawable.pressure_high,
            "High Pressure",
            R.drawable.bg_icon_circle_red
        )
        configureCard(
            cardViews[1],
            R.drawable.pressure_low,
            "Low Pressure",
            R.drawable.bg_icon_circle_blue
        )
        configureCard(
            cardViews[2],
            R.drawable.term_high,
            "High Temp",
            R.drawable.bg_icon_circle_green
        )
        configureCard(
            cardViews[3],
            R.drawable.term_low,
            "Low Temp",
            R.drawable.bg_icon_circle_orange
        )
        configureCard(
            cardViews[4],
            R.drawable.term_all,
            "All Temp",
            R.drawable.bg_icon_circle_purple
        )
    }

    fun updateCards(devices: List<ConnectedDevice>) {
        cardViews.forEach { it.renderInactive() }

        for (device in devices) {
            if (device.isError) continue

            val type = getSensorType(device)
            val index = sensorTypeToCardIndex[type] ?: continue
            cardViews[index].renderActive(device.latestMessage)
        }
    }

    private fun configureCard(card: CardView, icon: Int, label: String, iconColorRes: Int) {
        card.icon.setBackgroundResource(iconColorRes)
        card.label.text = label
        card.icon.setImageResource(icon)
    }

    private fun getSensorType(device: ConnectedDevice): SensorType {
        return when {
            device.name.startsWith("ESP32-C3-Pressure") -> SensorType.HIGH_PRESSURE
            device.name.startsWith("ESP32-C3-Temperature") -> SensorType.HIGH_TEMP

            else -> SensorType.UNKNOWN
        }
    }

    private val sensorTypeToCardIndex = mapOf(
        SensorType.HIGH_PRESSURE to 0,
        SensorType.LOW_PRESSURE to 1,
        SensorType.HIGH_TEMP to 2,
        SensorType.LOW_TEMP to 3,
        SensorType.BOILER_TEMP to 4
    )

    private fun CardView.renderInactive() {
        value.text = ""
        indicator.setBackgroundResource(R.drawable.ic_indicator_circle_red)
        setTextColor(R.color.gray_inactive)
    }

    private fun CardView.renderActive(message: String) {
        value.text = message
        indicator.setBackgroundResource(R.drawable.ic_indicator_circle_green)
        setTextColor(R.color.text_light)
    }

    private fun CardView.setTextColor(colorRes: Int) {
        val color = ContextCompat.getColor(context, colorRes)
        value.setTextColor(color)
        label.setTextColor(color)
    }
}