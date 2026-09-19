package com.get.detail.rentdesk.ui.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.get.detail.rentdesk.R
import kotlin.math.hypot

class PatternLockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val selected = mutableListOf<Int>()
    private val centers = Array(9) { FloatArray(2) }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline_soft)
        style = Paint.Style.FILL
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_primary)
        style = Paint.Style.STROKE
        strokeWidth = dp(6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var pointerX = 0f
    private var pointerY = 0f
    private var drawing = false
    private var feedback = Feedback.NORMAL

    var onPatternComplete: ((List<Int>) -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = dp(280f).toInt()
        val height = resolveSize(desired, heightMeasureSpec)
        setMeasuredDimension(width, minOf(width, height))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val cellW = w / 3f
        val cellH = h / 3f
        for (index in centers.indices) {
            centers[index][0] = (index % 3 + 0.5f) * cellW
            centers[index][1] = (index / 3 + 0.5f) * cellH
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val feedbackColor = when (feedback) {
            Feedback.SUCCESS -> ContextCompat.getColor(context, R.color.status_paid)
            Feedback.ERROR -> ContextCompat.getColor(context, R.color.status_due)
            Feedback.NORMAL -> ContextCompat.getColor(context, R.color.brand_primary)
        }
        linePaint.color = feedbackColor
        selectedPaint.color = feedbackColor
        if (selected.isNotEmpty()) {
            val path = Path()
            selected.forEachIndexed { index, node ->
                val center = centers[node]
                if (index == 0) path.moveTo(center[0], center[1]) else path.lineTo(center[0], center[1])
            }
            if (drawing) path.lineTo(pointerX, pointerY)
            canvas.drawPath(path, linePaint)
        }

        centers.forEachIndexed { index, center ->
            val isSelected = index in selected
            canvas.drawCircle(
                center[0],
                center[1],
                if (isSelected) dp(14f) else dp(10f),
                if (isSelected) selectedPaint else dotPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selected.clear()
                feedback = Feedback.NORMAL
                drawing = true
                updatePointer(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updatePointer(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                updatePointer(event.x, event.y)
                drawing = false
                parent?.requestDisallowInterceptTouchEvent(false)
                val completed = selected.toList()
                invalidate()
                performClick()
                if (completed.isNotEmpty()) onPatternComplete?.invoke(completed)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                clearPattern()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun clearPattern() {
        selected.clear()
        drawing = false
        feedback = Feedback.NORMAL
        invalidate()
    }

    fun showSuccess() {
        feedback = Feedback.SUCCESS
        invalidate()
    }

    fun showError() {
        feedback = Feedback.ERROR
        invalidate()
    }

    private fun updatePointer(x: Float, y: Float) {
        pointerX = x
        pointerY = y
        hitNode(x, y)?.let(::addNode)
        invalidate()
    }

    private fun hitNode(x: Float, y: Float): Int? {
        val hitRadius = width / 9f * 0.55f
        return centers.indices.firstOrNull { index ->
            val center = centers[index]
            hypot(x - center[0], y - center[1]) <= hitRadius
        }
    }

    private fun addNode(node: Int) {
        if (node in selected) return
        selected.lastOrNull()?.let { previous ->
            val previousRow = previous / 3
            val previousColumn = previous % 3
            val row = node / 3
            val column = node % 3
            if ((previousRow + row) % 2 == 0 && (previousColumn + column) % 2 == 0) {
                val middle = ((previousRow + row) / 2) * 3 + (previousColumn + column) / 2
                if (middle != previous && middle != node && middle !in selected) selected += middle
            }
        }
        selected += node
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private enum class Feedback {
        NORMAL,
        SUCCESS,
        ERROR
    }
}
