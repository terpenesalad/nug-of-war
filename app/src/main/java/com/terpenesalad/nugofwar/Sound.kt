package com.terpenesalad.nugofwar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/** Sound effects come from [SoundSynth], written once to WAV files in the cache, so the app ships no audio. */
class Sound(context: Context) {
    companion object {
        const val SHOOT = 0; const val HIT = 1; const val POP = 2; const val THUD = 3
        const val PICKUP = 4; const val WAVE = 5; const val DASH = 6; const val OUCH = 7
        const val SELECT = 8; const val WIN = 9; const val LOSE = 10; const val JINGLE = 11
        const val BOOM = 12; const val CLANG = 13; const val SWISH = 14; const val JUMP = 15
        const val UI_OK = 16
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = IntArray(SoundSynth.COUNT)
    private val lastPlay = LongArray(SoundSynth.COUNT)
    /** Menu and editor sounds (soft chimes). */
    @Volatile var uiEnabled = true
    /** Everything that happens during a fight. */
    @Volatile var gameEnabled = true

    init {
        val dir = File(context.cacheDir, "sfx_v4").apply { mkdirs() }
        var bufs: Array<FloatArray>? = null
        for (id in 0 until SoundSynth.COUNT) {
            val f = File(dir, "s$id.wav")
            if (!f.exists()) {
                val b = bufs ?: SoundSynth.all().also { bufs = it }
                writeWav(f, b[id])
            }
            ids[id] = pool.load(f.path, 1)
        }
    }

    fun play(id: Int, vol: Float = 1f) {
        val ui = SoundSynth.isUi(id)
        if (if (ui) !uiEnabled else !gameEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastPlay[id] < 40) return
        lastPlay[id] = now
        // UI chimes stay at one pitch so they feel calm; game sounds vary a little
        val pitch = if (ui) 1f else 0.94f + Random.nextFloat() * 0.12f
        pool.play(ids[id], vol, vol, 1, 0, pitch)
    }

    fun release() = pool.release()

    private fun writeWav(f: File, buf: FloatArray) {
        val n = buf.size
        val rate = SoundSynth.RATE
        val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + n * 2); data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()); data.putInt(16); data.putShort(1); data.putShort(1)
        data.putInt(rate); data.putInt(rate * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(n * 2)
        for (v in buf) data.putShort((v * 32000).toInt().toShort())
        FileOutputStream(f).use { it.write(data.array()) }
    }
}
