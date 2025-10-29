package com.antago30.acdoctor.model

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.antago30.acdoctor.R
import com.antago30.acdoctor.ui.BatteryView

data class CardView(val root: View) {
    val indicator: View = root.findViewById(R.id.indicator)
    val label: TextView = root.findViewById(R.id.label)
    val value: TextView = root.findViewById(R.id.value)
    val icon: ImageView = root.findViewById(R.id.icon)
    val battery: BatteryView = root.findViewById(R.id.battery)
    val batteryPercent: TextView = root.findViewById(R.id.batteryPercent)

}