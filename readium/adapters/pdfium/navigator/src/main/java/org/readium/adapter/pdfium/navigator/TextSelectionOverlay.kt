@file:OptIn(InternalReadiumApi::class)

package org.readium.adapter.pdfium.navigator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import org.readium.r2.shared.InternalReadiumApi
import timber.log.Timber

public class TextSelectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    init {
        setWillNotDraw(false)
    }
    private val selectedRects = mutableListOf<RectF>()
    private var scaleFactor = 1f
    private var pageOffsetX = 0f
    private var pageOffsetY = 0f
    private var currentPageIndex = 0

    // Краски для рисования
    private val selectionPaint = Paint().apply {
        color = Color.parseColor("#4D2196F3")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FF2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }


    private var isSelecting = false
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private var selectionEndX = 0f
    private var selectionEndY = 0f


    private var passTouchToParent = true


    private var onTextSelected: ((String, List<RectF>, Int) -> Unit)? = null
    private var onSelectionCleared: (() -> Unit)? = null
    private var onSelectionFinishedListener: OnSelectionFinishedListener? = null



    public fun setOnTextSelectedListener(listener: (String, List<RectF>, Int) -> Unit) {
        onTextSelected = listener
    }

    public fun setOnSelectionClearedListener(listener: () -> Unit) {
        onSelectionCleared = listener
    }

    public interface OnSelectionFinishedListener {
        public fun onSelectionFinished(selectionRectInView: RectF, pageIndex: Int)
    }

    public fun setOnSelectionFinishedListener(listener: OnSelectionFinishedListener) {
        this.onSelectionFinishedListener = listener
    }


    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)


        for (annotation in permanentAnnotations) {
            val paint = Paint().apply {
                color = annotation.color
                alpha = if (annotation.type == AnnotationType.HIGHLIGHT) 100 else 255
                style = if (annotation.type == AnnotationType.UNDERLINE) Paint.Style.STROKE else Paint.Style.FILL
                if (annotation.type == AnnotationType.UNDERLINE) {
                    strokeWidth = 3f * scaleFactor
                }
            }

            for (rect in annotation.rects) {
                val left = rect.left * scaleFactor + pageOffsetX
                val top = rect.top * scaleFactor + pageOffsetY
                val right = rect.right * scaleFactor + pageOffsetX
                val bottom = rect.bottom * scaleFactor + pageOffsetY

                when (annotation.type) {
                    AnnotationType.HIGHLIGHT -> {
                        canvas.drawRect(left, top, right, bottom, paint)
                    }
                    AnnotationType.UNDERLINE -> {
                        canvas.drawLine(left, bottom, right, bottom, paint)
                    }
                    AnnotationType.NOTE -> {

                        val size = 15f * scaleFactor
                        canvas.drawRect(right, top, right + size, top + size, paint)
                    }
                }
            }
        }


        for (rect in selectedRects) {
            val left = rect.left * scaleFactor + pageOffsetX
            val top = rect.top * scaleFactor + pageOffsetY
            val right = rect.right * scaleFactor + pageOffsetX
            val bottom = rect.bottom * scaleFactor + pageOffsetY

            canvas.drawRect(left, top, right, bottom, selectionPaint)
            canvas.drawRect(left, top, right, bottom, borderPaint)
        }


        if (isSelecting) {
            val left = minOf(selectionStartX, selectionEndX)
            val top = minOf(selectionStartY, selectionEndY)
            val right = maxOf(selectionStartX, selectionEndX)
            val bottom = maxOf(selectionStartY, selectionEndY)
            canvas.drawRect(left, top, right, bottom, selectionPaint)
        }
    }


    public fun handleAnnotationClick(x: Float, y: Float): Boolean {
        for (annotation in permanentAnnotations.reversed()) {
            for (rect in annotation.rects) {
                val screenLeft = rect.left * scaleFactor + pageOffsetX
                val screenTop = rect.top * scaleFactor + pageOffsetY
                val screenRight = rect.right * scaleFactor + pageOffsetX
                val screenBottom = rect.bottom * scaleFactor + pageOffsetY


                val touchRect = RectF(screenLeft - 20f, screenTop - 20f, screenRight + 20f, screenBottom + 20f)

                if (touchRect.contains(x, y)) {
                    onAnnotationClicked?.invoke(annotation, RectF(screenLeft, screenTop, screenRight, screenBottom))
                    return true
                }
            }
        }
        return false
    }



    public data class PermanentHighlight(val rects: List<RectF>, val color: Int)
    private val permanentHighlights = mutableListOf<PermanentHighlight>()

    public fun setPermanentHighlights(highlights: List<PermanentHighlight>) {
        permanentHighlights.clear()
        permanentHighlights.addAll(highlights)
        invalidate()
    }

    public fun getCurrentSelectedRects(): List<RectF> = selectedRects.toList()

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(longPressCallback)
                selectionStartX = event.x
                selectionStartY = event.y
                selectionEndX = event.x
                selectionEndY = event.y
                isSelecting = false


                postDelayed(longPressCallback, ViewConfiguration.getLongPressTimeout().toLong())


                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = kotlin.math.abs(event.x - selectionStartX)
                val dy = kotlin.math.abs(event.y - selectionStartY)
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop


                if (dx > touchSlop || dy > touchSlop) {
                    removeCallbacks(longPressCallback)
                }


                return isSelecting
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressCallback)
                return false
            }
        }
        return false
    }


    public var onAnnotationClicked: ((PermanentAnnotation, RectF) -> Unit)? = null



    private var downX = 0f
    private var downY = 0f
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (isSelecting) return handleSelectionTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = kotlin.math.abs(event.x - downX)
                val dy = kotlin.math.abs(event.y - downY)
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop


                if (dx < touchSlop && dy < touchSlop) {
                    checkAnnotationClick(event.x, event.y)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleSelectionTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                selectionEndX = event.x
                selectionEndY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selectionEndX = event.x
                selectionEndY = event.y
                isSelecting = false
                passTouchToParent = true


                val rect = RectF(
                    Math.min(selectionStartX, selectionEndX),
                    Math.min(selectionStartY, selectionEndY),
                    Math.max(selectionStartX, selectionEndX),
                    Math.max(selectionStartY, selectionEndY)
                )

                // Отправляем рамку в PdfiumDocumentFragment для поиска текста
                onSelectionFinishedListener?.onSelectionFinished(rect, currentPageIndex)
                invalidate()
            }
        }
        return true
    }

    private fun checkAnnotationClick(x: Float, y: Float) {
        for (annotation in permanentAnnotations.reversed()) {
            for (rect in annotation.rects) {
                val screenLeft = rect.left * scaleFactor + pageOffsetX
                val screenTop = rect.top * scaleFactor + pageOffsetY
                val screenRight = rect.right * scaleFactor + pageOffsetX
                val screenBottom = rect.bottom * scaleFactor + pageOffsetY

                val touchRect = RectF(screenLeft - 15f, screenTop - 15f, screenRight + 15f, screenBottom + 15f)

                if (touchRect.contains(x, y)) {
                    onAnnotationClicked?.invoke(annotation, RectF(screenLeft, screenTop, screenRight, screenBottom))
                    return
                }
            }
        }
    }

    private val longPressCallback = Runnable {
        if (!isSelecting) {
            Timber.d("LongPress: Активация выделения!")
            isSelecting = true


            parent?.requestDisallowInterceptTouchEvent(true)

            selectionEndX = selectionStartX
            selectionEndY = selectionStartY
            invalidate()
        }
    }

    public enum class AnnotationType {
        HIGHLIGHT, UNDERLINE, NOTE
    }

    public data class PermanentAnnotation(
        val id: String,
        val rects: List<RectF>,
        val color: Int,
        val type: AnnotationType
    )

    private val permanentAnnotations = mutableListOf<PermanentAnnotation>()

    public fun setPermanentAnnotations(annotations: List<PermanentAnnotation>) {
        permanentAnnotations.clear()
        permanentAnnotations.addAll(annotations)
        invalidate()
    }


    public fun updateSelection(
        rects: List<RectF>,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        pageIndex: Int,
    ) {
        selectedRects.clear()
        selectedRects.addAll(rects)
        scaleFactor = scale
        pageOffsetX = offsetX
        pageOffsetY = offsetY
        currentPageIndex = pageIndex
        invalidate()
    }

    public fun clearSelection() {
        selectedRects.clear()
        isSelecting = false
        passTouchToParent = true
        invalidate()
        onSelectionCleared?.invoke()
    }

    public fun finalizeSelection(
        selectedText: String,
        selectedRectsInPdfCoords: List<RectF>,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        pageIndex: Int
    ) {
        currentPageIndex = pageIndex
        scaleFactor = scale
        pageOffsetX = offsetX
        pageOffsetY = offsetY



        selectedRects.clear()
        selectedRects.addAll(selectedRectsInPdfCoords)
        invalidate()

        onTextSelected?.invoke(selectedText, selectedRects, pageIndex)
    }

    public fun setPdfViewTransform(scale: Float, offsetX: Float, offsetY: Float) {
        scaleFactor = scale
        pageOffsetX = offsetX
        pageOffsetY = offsetY
    }

    public fun setCurrentPageIndex(index: Int) {
        currentPageIndex = index
    }


}