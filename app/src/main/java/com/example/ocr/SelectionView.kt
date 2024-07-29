package com.example.ocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class SelectionView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private var rect: Rect? = null
    private var selectionEnabled = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        rect?.let {
            canvas.drawRect(it, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!selectionEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                rect = Rect(event.x.toInt(), event.y.toInt(), event.x.toInt(), event.y.toInt())
            }
            MotionEvent.ACTION_MOVE -> {
                rect?.right = event.x.toInt()
                rect?.bottom = event.y.toInt()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                // Finaliza la selección
            }
        }
        return true
    }

    fun getSelectionRect(): Rect? {
        return rect
    }

    fun setSelectionEnabled(enabled: Boolean) {
        selectionEnabled = enabled
    }
}
