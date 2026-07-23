package com.meapet.mobile.live2d

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

/**
 * 语音播放器。
 * 从 assets/voice/ 加载并播放 .wav 文件。
 * 同步 prepare — 由 GL 线程调用，阻塞时间在可接受范围。
 */
class VoicePlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    companion object {
        private const val TAG = "VoicePlayer"
        private const val VOICE_DIR = "voice"
    }

    fun play(filename: String) {
        try {
            stop()
            val afd = context.assets.openFd("$VOICE_DIR/$filename")
            player = MediaPlayer().apply {
                setDataSource(afd)
                setOnCompletionListener { release() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error: $what / $extra"); true
                }
                prepare()
                start()
            }
            Log.d(TAG, "Playing: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play $filename", e)
        }
    }

    fun stop() {
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
