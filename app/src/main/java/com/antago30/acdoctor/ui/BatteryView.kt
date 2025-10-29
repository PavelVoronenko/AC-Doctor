package com.antago30.acdoctor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.antago30.acdoctor.R
import androidx.core.graphics.toColorInt

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bodyRect = RectF()
    private val chargeRect = RectF()
    private val terminalRect = RectF()

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = ContextCompat.getColor(context, R.color.battery_outline)
    }

    private val chargePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val terminalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.battery_outline)
    }

    var chargePercent: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Отступы
        val padding = 2f
        val terminalHeight = 4f
        val terminalWidth = w * 0.6f  // ширина контакта

        // Расположен ниже контакта
        val bodyTop = padding + terminalHeight
        val bodyBottom = h - padding

        bodyRect.set(padding, bodyTop, w - padding, bodyBottom)
        canvas.drawRoundRect(bodyRect, 3f, 3f, outlinePaint)

        terminalRect.set(
            (w - terminalWidth) / 2f,  // центрировать
            padding,
            (w + terminalWidth) / 2f,
            padding + terminalHeight
        )
        canvas.drawRect(terminalRect, terminalPaint)

        // Заряд
        if (chargePercent > 0f) {
            val usableHeight = bodyBottom - bodyTop - 4f // внутренняя высота
            val chargeHeight = (chargePercent / 100f) * usableHeight

            chargeRect.set(
                padding + 1.5f,
                bodyBottom - 1.5f - chargeHeight,
                w - padding - 1.5f,
                bodyBottom - 1.5f
            )

            // Цвет по зонам
            chargePaint.color = when {
                chargePercent > 66f -> "#4CAF50".toColorInt() // Зелёный
                chargePercent > 33f -> "#FFC107".toColorInt() // Жёлтый
                else -> "#F44336".toColorInt()               // Красный
            }

            canvas.drawRoundRect(chargeRect, 2f, 2f, chargePaint)
        }
    }
}