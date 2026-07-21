package com.llz121517.meapet.live2d

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Touch input manager — tracks single / multi-touch gestures.
 */
class TouchManager {
    fun touchesBegan(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY
        startX = deviceX
        startY = deviceY
        lastTouchDistance = -1.0f
        isFlipAvailable = true
        isTouchSingle = true
    }

    fun touchesMoved(deviceX: Float, deviceY: Float) {
        lastX = deviceX
        lastY = deviceY
        lastTouchDistance = -1.0f
        isTouchSingle = true
    }

    fun touchesMoved(
        deviceX1: Float, deviceY1: Float,
        deviceX2: Float, deviceY2: Float
    ) {
        val distance = calculateDistance(deviceX1, deviceY1, deviceX2, deviceY2)
        val centerX = (deviceX1 + deviceX2) * 0.5f
        val centerY = (deviceY1 + deviceY2) * 0.5f

        if (lastTouchDistance > 0.0f) {
            scale = (distance / lastTouchDistance).toDouble().let { Math.pow(it, 0.75) }.toFloat()
            deltaX = calculateMovingAmount(deviceX1 - lastX1, deviceX2 - lastX2)
            deltaY = calculateMovingAmount(deviceY1 - lastY1, deviceY2 - lastY2)
        } else {
            scale = 1.0f
            deltaX = 0.0f
            deltaY = 0.0f
        }

        lastX = centerX
        lastY = centerY
        lastX1 = deviceX1
        lastY1 = deviceY1
        lastX2 = deviceX2
        lastY2 = deviceY2
        lastTouchDistance = distance
        isTouchSingle = false
    }

    fun calculateFlickDistance(): Float {
        return calculateDistance(startX, startY, lastX, lastY)
    }

    // --- getters ---
    fun getStartX(): Float = startX
    fun getStartY(): Float = startY
    fun getLastX(): Float = lastX
    fun getLastY(): Float = lastY
    fun getDeltaX(): Float = deltaX
    fun getDeltaY(): Float = deltaY
    fun getScale(): Float = scale
    fun isTouchSingle(): Boolean = isTouchSingle
    fun isFlipAvailable(): Boolean = isFlipAvailable

    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt(((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).toDouble()).toFloat()
    }

    private fun calculateMovingAmount(v1: Float, v2: Float): Float {
        if ((v1 > 0.0f) != (v2 > 0.0f)) return 0.0f
        val sign = if (v1 > 0.0f) 1.0f else -1.0f
        return sign * minOf(abs(v1), abs(v2))
    }

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastX1 = 0f
    private var lastY1 = 0f
    private var lastX2 = 0f
    private var lastY2 = 0f
    private var lastTouchDistance = -1.0f
    private var deltaX = 0f
    private var deltaY = 0f
    private var scale = 1.0f
    private var isTouchSingle = false
    private var isFlipAvailable = false
}
