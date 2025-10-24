package com.antago30.acdoctor.domain.model

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.antago30.acdoctor.R

data class CardView(val root: View) {
    val indicator: View = root.findViewById(R.id.indicator)
    val label: TextView = root.findViewById(R.id.label)
    val value: TextView = root.findViewById(R.id.value)
    val icon: ImageView = root.findViewById(R.id.icon)
}