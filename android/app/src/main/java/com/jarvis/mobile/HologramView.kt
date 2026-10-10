package com.jarvis.mobile

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class HologramView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
    }

    private val innerSegmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000F3FF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        pathEffect = DashPathEffect(floatArrayOf(40f, 25f), 0f)
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F3FF")
        style = Paint.Style.FILL
    }

    private val whiteCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var rotationAngle = 0f
    private var pulseFactor = 0f
    private var pulseDirection = 1

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) * 0.85f

        if (radius <= 0) return

        // Rotate Outer Ring
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        canvas.drawCircle(cx, cy, radius, outerPaint)
        canvas.restore()

        // Counter-Rotate Inner Segments
        canvas.save()
        canvas.rotate(-rotationAngle * 1.5f, cx, cy)
        canvas.drawCircle(cx, cy, radius * 0.72f, innerSegmentPaint)
        canvas.restore()

        // Core Pulse
        val coreRadius = radius * (0.32f + (pulseFactor * 0.08f))
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
        canvas.drawCircle(cx, cy, coreRadius * 0.5f, whiteCenterPaint)

        // Animate
        rotationAngle = (rotationAngle + 1.5f) % 360f
        pulseFactor += 0.03f * pulseDirection
        if (pulseFactor >= 1f) pulseDirection = -1
        else if (pulseFactor <= 0f) pulseDirection = 1

        postInvalidateOnAnimation()
    }
}
