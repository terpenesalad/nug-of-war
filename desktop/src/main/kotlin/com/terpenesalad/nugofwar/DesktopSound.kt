package com.terpenesalad.nugofwar

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.random.Random

/** Desktop sound: the same synthesized effects as Android, mixed in software and streamed to the speakers. */
class Sound(enabledOutput: Boolean = true) {
    companion object {
        const val SHOOT = 0; const val HIT = 1; const val POP = 2; const val THUD = 3
        const val PICKUP = 4; const val WAVE = 5; const val DASH = 6; const val OUCH = 7
        const val SELECT = 8; const val WIN = 9; const val LOSE = 10; const val JINGLE = 11
        const val BOOM = 12; const val CLANG = 13; const val SWISH = 14; const val JUMP = 15
        const val UI_OK = 16
        private const val CHUNK = 256
        private const val MAX_VOICES = 16
    }

    @Volatile var uiEnabled = true
    @Volatile var gameEnabled = true

    private class Voice(val buf: FloatArray, val vol: Float, val step: Float) { var pos = 0f }

    private val bufs = SoundSynth.all()
    private val voices = ArrayList<Voice>()
    private val lastPlay = LongArray(SoundSynth.COUNT)
    @Volatile private var running = true
    private val line: SourceDataLine? = if (!enabledOutput) null else try {
        val fmt = AudioFormat(SoundSynth.RATE.toFloat(), 16, 1, true, false)
        AudioSystem.getSourceDataLine(fmt).apply { open(fmt, CHUNK * 2 * 8); start() }
    } catch (t: Throwable) {
        System.err.println("No audio output: $t"); null
    }

    init {
        val l = line
        if (l != null) Thread({
            val mix = FloatArray(CHUNK)
            val out = ByteArray(CHUNK * 2)
            while (running) {
                java.util.Arrays.fill(mix, 0f)
                synchronized(voices) {
                    val it = voices.iterator()
                    while (it.hasNext()) {
                        val v = it.next()
                        var i = 0
                        while (i < CHUNK) {
                            val p = v.pos.toInt()
                            if (p >= v.buf.size - 1) break
                            val f = v.pos - p
                            mix[i] += (v.buf[p] * (1f - f) + v.buf[p + 1] * f) * v.vol
                            v.pos += v.step; i++
                        }
                        if (v.pos.toInt() >= v.buf.size - 1) it.remove()
                    }
                }
                for (i in 0 until CHUNK) {
                    var s = mix[i]
                    if (s > 0.9f) s = 0.9f + (s - 0.9f) * 0.25f else if (s < -0.9f) s = -0.9f + (s + 0.9f) * 0.25f
                    val v = (s.coerceIn(-1f, 1f) * 32000).toInt()
                    out[i * 2] = (v and 0xFF).toByte()
                    out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                l.write(out, 0, out.size)
            }
        }, "nug-audio").apply { isDaemon = true; start() }
    }

    fun play(id: Int, vol: Float = 1f) {
        if (line == null) return
        val ui = SoundSynth.isUi(id)
        if (if (ui) !uiEnabled else !gameEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastPlay[id] < 40) return
        lastPlay[id] = now
        val pitch = if (ui) 1f else 0.94f + Random.nextFloat() * 0.12f
        synchronized(voices) {
            if (voices.size >= MAX_VOICES) voices.removeAt(0)
            voices.add(Voice(bufs[id], vol, pitch))
        }
    }

    fun release() {
        running = false
        try { line?.stop(); line?.close() } catch (_: Throwable) {}
    }
}
