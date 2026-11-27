package com.terpenesalad.nugofwar

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Tiny synthesizer: sound effects are generated on first launch, so the app ships no audio files. */
class Sound(context: Context) {
    companion object {
        const val SHOOT = 0; const val HIT = 1; const val POP = 2; const val THUD = 3
        const val PICKUP = 4; const val WAVE = 5; const val DASH = 6; const val OUCH = 7
        const val SELECT = 8; const val WIN = 9; const val LOSE = 10; const val JINGLE = 11
        const val BOOM = 12; const val CLANG = 13; const val SWISH = 14; const val JUMP = 15
        const val UI_OK = 16
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
    private val ids = IntArray(17)
    private val lastPlay = LongArray(17)
    /** Menu and editor sounds (soft chimes). */
    @Volatile var uiEnabled = true
    /** Everything that happens during a fight. */
    @Volatile var gameEnabled = true

    private fun isUi(id: Int) = id == SELECT || id == UI_OK

    init {
        val dir = File(context.cacheDir, "sfx_v4").apply { mkdirs() }
        val rnd = Random(7)
        /**
         * [lp] is a one-pole low-pass (0..1, lower = darker) to keep everything soft;
         * [echo] adds a few quiet repeats for a little room / ambience.
         */
        fun make(id: Int, dur: Float, lp: Float = 0.35f, echo: Float = 0f, fn: (Float, Float) -> Float) {
            val f = File(dir, "s$id.wav")
            if (!f.exists()) writeWav(f, dur, lp, echo, fn)
            ids[id] = pool.load(f.path, 1)
        }
        fun sn(t: Float, hz: Float) = sin(2 * PI * hz * t).toFloat()
        fun noise() = rnd.nextFloat() * 2f - 1f
        // soft attack, smooth exponential release
        fun env(t: Float, att: Float, decay: Float) = min(1f, t / att) * exp(-t / decay)
        // a mellow bell-ish tone: fundamental plus a quiet upper partial
        fun tone(t: Float, hz: Float) = sn(t, hz) * 0.8f + sn(t, hz * 2f) * 0.12f + sn(t, hz * 3.01f) * 0.05f

        // ---- interface: quiet, rounded, a touch of room
        make(SELECT, 0.45f, lp = 0.5f, echo = 0.22f) { t, _ -> tone(t, 784f) * env(t, 0.006f, 0.06f) * 0.16f }
        make(UI_OK, 1.1f, lp = 0.45f, echo = 0.3f) { t, _ ->
            (tone(t, 587f) * env(t, 0.008f, 0.18f) + tone(max(0f, t - 0.07f), 880f) * env(max(0f, t - 0.07f), 0.01f, 0.25f) * (if (t > 0.07f) 1f else 0f)) * 0.16f
        }

        // ---- game: soft and low, nothing buzzy
        make(SHOOT, 0.14f, lp = 0.3f) { t, p -> sn(t, 520f - 260f * p) * env(t, 0.003f, 0.04f) * 0.35f + noise() * env(t, 0.001f, 0.02f) * 0.15f }
        make(HIT, 0.16f, lp = 0.18f) { t, _ -> (noise() * 0.6f + sn(t, 140f) * 0.6f) * env(t, 0.002f, 0.04f) * 0.5f }
        make(POP, 0.25f, lp = 0.3f) { t, p -> sn(t, 420f - 250f * p) * env(t, 0.004f, 0.06f) * 0.45f }
        make(THUD, 0.25f, lp = 0.2f) { t, p -> (sn(t, 85f - 35f * p) * 0.9f + noise() * 0.1f) * env(t, 0.004f, 0.07f) * 0.7f }
        make(PICKUP, 0.6f, lp = 0.45f, echo = 0.2f) { t, _ ->
            val n2 = if (t > 0.06f) tone(t - 0.06f, 784f) * env(t - 0.06f, 0.006f, 0.12f) else 0f
            (tone(t, 523f) * env(t, 0.006f, 0.1f) + n2) * 0.18f
        }
        make(WAVE, 1.4f, lp = 0.3f, echo = 0.3f) { t, _ -> (sn(t, 220f) + sn(t, 330f) * 0.7f + sn(t, 440f) * 0.3f) * min(1f, t / 0.25f) * exp(-t / 0.45f) * 0.22f }
        make(DASH, 0.2f, lp = 0.12f) { _, p -> noise() * sin(PI * p).toFloat() * 0.35f }
        make(OUCH, 0.2f, lp = 0.3f) { t, p -> sn(t, 300f - 120f * p) * env(t, 0.004f, 0.06f) * 0.35f }
        make(WIN, 1.6f, lp = 0.45f, echo = 0.3f) { t, _ ->
            var v = 0f
            val notes = floatArrayOf(523f, 659f, 784f, 1046f)
            for ((i, hz) in notes.withIndex()) { val tt = t - i * 0.13f; if (tt > 0f) v += tone(tt, hz) * env(tt, 0.01f, 0.35f) }
            v * 0.13f
        }
        make(JINGLE, 1.6f, lp = 0.5f, echo = 0.3f) { t, _ ->
            var v = 0f
            val notes = floatArrayOf(1319f, 1319f, 1319f, 1568f)
            for ((i, hz) in notes.withIndex()) { val tt = t - i * 0.16f; if (tt > 0f) v += tone(tt, hz) * env(tt, 0.004f, 0.2f) }
            v * 0.1f
        }
        make(BOOM, 0.9f, lp = 0.08f, echo = 0.15f) { t, p -> (noise() * 0.8f + sn(t, 55f - 20f * p) * 0.6f) * env(t, 0.005f, 0.2f) * 0.8f }
        make(CLANG, 0.6f, lp = 0.4f) { t, _ -> (sn(t, 1250f) * 0.4f + sn(t, 1870f) * 0.25f + sn(t, 2630f) * 0.1f) * env(t, 0.002f, 0.1f) * 0.45f }
        make(SWISH, 0.16f, lp = 0.15f) { _, p -> noise() * sin(PI * p).toFloat() * 0.3f }
        make(JUMP, 0.14f, lp = 0.3f) { t, p -> sn(t, 240f + 180f * p) * env(t, 0.006f, 0.04f) * 0.25f }
        make(LOSE, 1.4f, lp = 0.35f, echo = 0.3f) { t, _ ->
            var v = 0f
            val notes = floatArrayOf(392f, 330f, 262f)
            for ((i, hz) in notes.withIndex()) { val tt = t - i * 0.2f; if (tt > 0f) v += tone(tt, hz) * env(tt, 0.01f, 0.35f) }
            v * 0.15f
        }
    }

    fun play(id: Int, vol: Float = 1f) {
        if (if (isUi(id)) !uiEnabled else !gameEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastPlay[id] < 40) return
        lastPlay[id] = now
        // UI chimes stay at one pitch so they feel calm; game sounds vary a little
        val pitch = if (isUi(id)) 1f else 0.94f + Random.nextFloat() * 0.12f
        pool.play(ids[id], vol, vol, 1, 0, pitch)
    }

    fun release() = pool.release()

    private fun writeWav(f: File, dur: Float, lp: Float, echo: Float, fn: (Float, Float) -> Float) {
        val n = (RATE * dur).toInt()
        val buf = FloatArray(n)
        var y = 0f
        for (i in 0 until n) {
            val v = fn(i.toFloat() / RATE, i.toFloat() / n)
            y += lp * (v - y)
            buf[i] = y
        }
        if (echo > 0f) {
            val taps = intArrayOf((0.083f * RATE).toInt(), (0.157f * RATE).toInt(), (0.241f * RATE).toInt(), (0.33f * RATE).toInt())
            val gains = floatArrayOf(echo, echo * 0.6f, echo * 0.35f, echo * 0.2f)
            val dry = buf.copyOf()
            for (k in taps.indices) for (i in taps[k] until n) buf[i] += dry[i - taps[k]] * gains[k]
        }
        // gentle fade out so nothing clicks
        val fade = min(n, RATE / 50)
        for (i in 0 until fade) buf[n - 1 - i] *= i.toFloat() / fade
        val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()); data.putInt(36 + n * 2); data.put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()); data.putInt(16); data.putShort(1); data.putShort(1)
        data.putInt(RATE); data.putInt(RATE * 2); data.putShort(2); data.putShort(16)
        data.put("data".toByteArray()); data.putInt(n * 2)
        for (v in buf) data.putShort((v.coerceIn(-1f, 1f) * 32000).toInt().toShort())
        FileOutputStream(f).use { it.write(data.array()) }
    }
}
