package org.readium.r2.testapp.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import org.readium.r2.testapp.R

class ChapterMarkersView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.textColorPrimary)
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 0.8f, resources.displayMetrics
        )
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Список позиций маркеров от 0.0 до 1.0
    var markers: List<Float> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (markers.isEmpty() || height == 0) return

        // Учитываем отступы SeekBar (радиус thumb), чтобы линии совпадали с треком
        val thumbRadiusDp = 12f
        val thumbRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, thumbRadiusDp, resources.displayMetrics
        )

        val trackWidth = width - (2 * thumbRadius)
        if (trackWidth <= 0) return

        val paddingTop = 6f
        val paddingBottom = 6f
        val availableHeight = height - paddingTop - paddingBottom

        for (position in markers) {
            val x = thumbRadius + (position * trackWidth)
            canvas.drawLine(x, paddingTop, x, paddingTop + availableHeight, paint)
        }
    }
}