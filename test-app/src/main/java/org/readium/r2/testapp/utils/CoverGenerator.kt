package org.readium.r2.testapp.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

object CoverGenerator {

    fun generate(
        title: String,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val safeWidth = widthPx.coerceAtLeast(1)
        val safeHeight = heightPx.coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(
            safeWidth,
            safeHeight,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)

        // Белый фон
        canvas.drawColor(Color.WHITE)

        // Тонкая рамка
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        canvas.drawRect(
            4f,
            4f,
            safeWidth - 4f,
            safeHeight - 4f,
            borderPaint
        )

        val textPaint = Paint().apply {
            color = Color.parseColor("#1A1C1E")
            textSize = calculateTextSize(title, safeWidth, safeHeight)
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.BOLD
            )
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val lines = wrapText(
            text = title,
            paint = textPaint,
            maxWidth = (safeWidth * 0.8f).toInt()
        )

        val lineHeight = textPaint.fontSpacing
        val totalTextHeight = lines.size * lineHeight
        val startY = (safeHeight - totalTextHeight) / 2f + lineHeight * 0.7f

        lines.forEachIndexed { index, line ->
            canvas.drawText(
                line,
                safeWidth / 2f,
                startY + index * lineHeight,
                textPaint
            )
        }

        return bitmap
    }

    private fun calculateTextSize(
        title: String,
        widthPx: Int,
        heightPx: Int
    ): Float {
        if (widthPx <= 10 || heightPx <= 10) return 10f

        var textSize = 28f
        val maxWidth = widthPx * 0.8f
        val maxHeight = heightPx * 0.5f

        val paint = Paint().apply {
            typeface = Typeface.create(
                Typeface.SANS_SERIF,
                Typeface.BOLD
            )
        }

        while (textSize > 10f) {
            paint.textSize = textSize

            val lines = wrapText(
                text = title,
                paint = paint,
                maxWidth = maxWidth.toInt()
            )

            val totalHeight = lines.size * paint.fontSpacing
            val maxLineWidth = lines.maxOfOrNull { paint.measureText(it) } ?: 0f

            if (totalHeight <= maxHeight && maxLineWidth <= maxWidth) {
                break
            }

            textSize -= 1f
        }

        return textSize
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Int
    ): List<String> {
        if (text.isBlank()) return listOf("")
        if (maxWidth <= 0) return listOf(text.take(10).ifBlank { "" })

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }

                if (paint.measureText(word) > maxWidth) {
                    var truncated = word

                    while (paint.measureText("$truncated…") > maxWidth && truncated.length > 1) {
                        truncated = truncated.dropLast(1)
                    }

                    lines.add("$truncated…")
                } else {
                    currentLine = word
                    continue
                }

                currentLine = ""
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        return if (lines.size > 6) {
            lines.take(5) + (lines[5].take(20) + "…")
        } else {
            lines.ifEmpty { listOf("") }
        }
    }
}