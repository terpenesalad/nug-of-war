package com.terpenesalad.nugofwar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Tiny synthesizer: sound effects are generated on first launch, so the app ships no audio files. */
class Sound(context: Context) {
    companion object {
        const val SHOOT = 0; const val HIT = 1; const val POP = 2; const val THUD = 3
        const val PICKUP = 4; const val WAVE = 5; const val DASH = 6; const val OUCH = 7
        const val SELECT = 8; const val WIN = 9; const val LOSE = 10; const val JINGLE = 11
        private const val RATE = 22050
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = IntArray(12)
    private val lastPlay = LongArray(12)
    @Volatile var enabled = true

    init {
        val dir = File(context.cacheDir, "sfx_v2").apply { mkdirs() }
        val rnd = Random(7)
        fun make(id: Int, dur: Float, fn: (Float, Float) -> Float) {
            val f = File(dir, "s$id.wav")
            if (!f.exists()) writeWav(f, dur, fn)
            ids[id] = pool.load(f.path, 1)
        }
        fun sq(t: Float, hz: Float) = if (sin(2 * PI * hz * t) >= 0) 1f else -1f
        fun sn(t: Float, hz: Float) = sin(2 * PI * hz * t).toFloat()
        fun noise() = rnd.nextFloat() * 2f - 1f

        make(SHOOT, 0.09f) { t, p -> sq(t, 900f - 500f * p) * 0.25f * (1 - p) }
        make(HIT, 0.08f) { _, p -> noise() * 0.35f * (1 - p) }
        make(POP, 0.18f) { t, p -> (sn(t, 520f - 400f * p) * 0.5f + noise() * 0.2f) * (1 - p) }
        make(THUD, 0.2f) { t, p -> (sn(t, 90f - 40f * p) * 0.8f + noise() * 0.15f) * (1 - p) }
        make(PICKUP, 0.25f) { t, p -> sq(t, if (p < 0.33f) 660f else if (p < 0.66f) 880f else 1320f) * 0.2f * (1 - p * 0.5f) }
        make(WAVE, 0.5f) { t, p -> sn(t, if (p < 0.5f) 330f else 495f) * 0.45f * (1 - p) }
        make(DASH, 0.15f) { _, p -> noise() * 0.3f * sin(PI * p).toFloat() }
        make(OUCH, 0.15f) { t, p -> sq(t, 220f - 100f * p) * 0.25f * (1 - p) }
        make(SELECT, 0.06f) { t, p -> sq(t, 740f) * 0.18f * (1 - p) }
        make(WIN, 0.8f) { t, p -> sq(t, if (p < 0.25f) 523f else if (p < 0.5f) 659f else if (p < 0.75f) 784f else 1046f) * 0.2f * (1 - p * 0.6f) }
        make(JINGLE, 0.9f) { t, p ->
            val notes = floatArrayOf(1319f, 1319f, 1319f, 1319f, 1319f, 1319f, 1319f, 1568f)
            val seg = (p * notes.size).toInt().coerceIn(0, notes.size - 1)
            val local = p * notes.size - seg
            (sn(t, notes[seg]) * 0.5f + sn(t, notes[seg] * 2.01f) * 0.25f) * 0.35f * (1 - local) * (if (seg == 2 || seg == 5) 0.3f else 1f)
        }
        make(LOSE, 0.8f) { t, p -> sq(t, 330f - 200f * p) * 0.22f * (1 - p) }
    }

    fun play(id: Int, vol: Float = 1f) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastPlay[id] < 40) return
        lastPlay[id] = now
        val pitch = 0.92f + Random.nextFloat() * 0.16f
        pool.play(ids[id], vol, vol, 1, 0, pitch)
    }

    fun release() = pool.release()

    private fun writeWav(f: File, dur: Float, fn: (Float, Float) -> Float) {
        val n = (RATE * dur).toInt()
        val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + n * 2); data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()); data.putInt(16); data.putShort(1); data.putShort(1)
        data.putInt(RATE); data.putInt(RATE * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(n * 2)
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val v = fn(t, i.toFloat() / n).coerceIn(-1f, 1f)
            data.putShort((v * 32000).toInt().toShort())
        }
        FileOutputStream(f).use { it.write(data.array()) }
    }
}
