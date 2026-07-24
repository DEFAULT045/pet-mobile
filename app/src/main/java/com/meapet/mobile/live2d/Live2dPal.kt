package com.meapet.mobile.live2d

import android.util.Log
import com.live2d.sdk.cubism.core.ICubismLogger
import com.live2d.sdk.cubism.framework.ICubismLoadFileFunction
import java.io.IOException

/**
 * Platform abstraction layer — file loading, timing, logging.
 */
object Live2dPal {
    private const val TAG = "Live2d"

    class PrintLogFunction : ICubismLogger {
        override fun print(message: String) {
            Log.d(TAG, message)
        }
    }

    class LoadFileFunction : ICubismLoadFileFunction {
        override fun load(path: String): ByteArray {
            return loadFileAsBytes(path)
        }
    }

    fun updateTime() {
        val now = System.nanoTime()
        deltaNanoTime = now - lastNanoTime
        lastNanoTime = now
    }

    fun loadFileAsBytes(path: String): ByteArray {
        return try {
            // 用 application context（不随 Activity 销毁失效），Activity 不可用时回退
            val ctx = Live2dDelegate.getInstance().appContext
                ?: Live2dDelegate.getInstance().activity
                ?: return ByteArray(0)
            val stream = ctx.assets.open(path)
            stream.use { s ->
                val buffer = ByteArray(s.available())
                s.read(buffer)
                buffer
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load file: $path", e)
            ByteArray(0)
        }
    }

    fun getDeltaTime(): Float {
        return (deltaNanoTime / 1_000_000_000.0).toFloat()
    }

    fun printLog(message: String) {
        Log.d(TAG, message)
    }

    private var lastNanoTime = System.nanoTime()
    private var deltaNanoTime = 0L
}
