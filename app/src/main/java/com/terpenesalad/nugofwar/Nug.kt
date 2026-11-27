package com.terpenesalad.nugofwar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How a nug looks: a colour plus a lumpy body shape built from overlapping circles. */
class Look(val color: Int, val shape: Int, seed: Int) {
    val bumps: FloatArray   // triples of (dx, dy, radius) in units of body radius
    val dots: FloatArray    // trichome sparkle positions
    val outline = Gfx.shade(color, 0.45f)
    val light = Gfx.mix(color, Color.WHITE, 0.35f)

    init {
        val r = Random(seed * 31 + shape)
        val b = ArrayList<Float>()
        fun add(x: Float, y: Float, rad: Float) {
            b.add(x + (r.nextFloat() - 0.5f) * 0.08f); b.add(y + (r.nextFloat() - 0.5f) * 0.08f)
            b.add(rad * (0.92f + r.nextFloat() * 0.16f))
        }
        when (shape) {
            0 -> { // round
                add(0f, 0f, 0.78f)
                for (i in 0 until 6) { val a = i * 1.047f + 0.3f; add(cos(a) * 0.45f, sin(a) * 0.42f, 0.42f) }
            }
            1 -> { // tall cone
                add(0f, 0.28f, 0.66f); add(0f, -0.15f, 0.58f); add(0f, -0.55f, 0.42f); add(0f, -0.82f, 0.26f)
                add(-0.42f, 0.2f, 0.36f); add(0.42f, 0.2f, 0.36f); add(-0.3f, -0.35f, 0.3f); add(0.3f, -0.35f, 0.3f)
            }
            2 -> { // chunky
                add(-0.36f, 0.08f, 0.6f); add(0.36f, 0.08f, 0.6f); add(0f, -0.28f, 0.6f); add(0f, 0.3f, 0.55f)
                add(-0.6f, -0.2f, 0.32f); add(0.6f, -0.2f, 0.32f)
            }
            else -> { // spiky popcorn
                add(0f, 0f, 0.68f)
                for (i in 0 until 9) { val a = i * 0.698f; add(cos(a) * 0.62f, sin(a) * 0.6f, 0.26f) }
            }
        }
        bumps = b.toFloatArray()
        dots = FloatArray(12) { (r.nextFloat() - 0.5f) * 1.2f }
    }
}

object Gfx {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
    fun alpha(c: Int, a: Float) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER, shadow: Boolean = true) {
        text.textSize = size
        text.textAlign = align
        if (shadow) {
            text.color = Color.argb(160, 0, 0, 0)
            c.drawText(s, x + size * 0.06f, y + size * 0.06f, text)
        }
        text.color = color
        c.drawText(s, x, y, text)
    }

    fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int) {
        fill.color = Color.argb(255, 32, 34, 33)
        c.drawRoundRect(x - 3, y - 3, x + w + 3, y + h + 3, h / 2 + 3, h / 2 + 3, fill)
        fill.color = color
        val f = frac.coerceIn(0f, 1f)
        if (f > 0) c.drawRoundRect(x, y, x + w * f, y + h, h / 2, h / 2, fill)
    }

    fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int = Color.argb(235, 20, 22, 21)) {
        fill.color = color
        c.drawRoundRect(l, t, r, b, 28f, 28f, fill)
        stroke.color = Color.argb(40, 255, 255, 255); stroke.strokeWidth = 2f
        c.drawRoundRect(l, t, r, b, 28f, 28f, stroke)
    }

    /**
     * Draws a nug with little legs.
     * @param fx facing direction x (-1..1), used for eyes and leg lean
     * @param mood 0 = friendly, 1 = angry (bad nug), 2 = knocked out
     */
    fun nug(c: Canvas, x: Float, y: Float, r: Float, look: Look, fx: Float, fy: Float, phase: Float,
            moving: Boolean, flash: Float, mood: Int, alphaMul: Float = 1f, hat: Boolean = false) {
        val a = (alphaMul * 255).toInt().coerceIn(0, 255)
        // shadow
        fill.color = Color.argb((70 * alphaMul).toInt(), 0, 0, 0)
        c.drawOval(x - r * 0.85f, y + r * 0.95f, x + r * 0.85f, y + r * 1.25f, fill)
        // legs
        val swing = if (moving) sin(phase) * r * 0.28f else 0f
        stroke.color = Color.argb(a, 30, 34, 26); stroke.strokeWidth = r * 0.15f
        val hipY = y + r * 0.5f; val footY = y + r * 1.08f
        val lx = x - r * 0.32f; val rx = x + r * 0.32f
        c.drawLine(lx, hipY, lx + swing, footY, stroke)
        c.drawLine(rx, hipY, rx - swing, footY, stroke)
        fill.color = Color.argb(a, 30, 34, 26)
        c.drawOval(lx + swing - r * 0.16f + fx * r * 0.06f, footY - r * 0.07f, lx + swing + r * 0.16f + fx * r * 0.06f, footY + r * 0.09f, fill)
        c.drawOval(rx - swing - r * 0.16f + fx * r * 0.06f, footY - r * 0.07f, rx - swing + r * 0.16f + fx * r * 0.06f, footY + r * 0.09f, fill)

        val bob = if (moving) -kotlin.math.abs(sin(phase)) * r * 0.06f else 0f
        val cy = y + bob
        val bm = look.bumps
        // outline pass
        fill.color = Gfx.alpha(look.outline, alphaMul)
        var i = 0
        while (i < bm.size) { c.drawCircle(x + bm[i] * r, cy + bm[i + 1] * r, bm[i + 2] * r + r * 0.07f, fill); i += 3 }
        // body
        fill.color = Gfx.alpha(look.color, alphaMul)
        i = 0
        while (i < bm.size) { c.drawCircle(x + bm[i] * r, cy + bm[i + 1] * r, bm[i + 2] * r, fill); i += 3 }
        // highlight
        fill.color = Gfx.alpha(look.light, 0.3f * alphaMul)
        c.drawCircle(x - r * 0.3f, cy - r * 0.35f, r * 0.24f, fill)
        // trichome sparkles
        fill.color = Color.argb((45 * alphaMul).toInt(), 255, 255, 255)
        val d = look.dots
        i = 0
        while (i < d.size) { c.drawCircle(x + d[i] * r, cy + d[i + 1] * r, r * 0.045f, fill); i += 2 }
        // eyes
        val ex = x + fx * r * 0.18f; val ey = cy - r * 0.12f + fy * r * 0.06f
        val es = r * 0.2f
        if (mood == 2) {
            stroke.color = Color.argb(a, 20, 20, 20); stroke.strokeWidth = r * 0.08f
            for (s in intArrayOf(-1, 1)) {
                val cx = ex + s * r * 0.27f
                c.drawLine(cx - es * 0.6f, ey - es * 0.6f, cx + es * 0.6f, ey + es * 0.6f, stroke)
                c.drawLine(cx - es * 0.6f, ey + es * 0.6f, cx + es * 0.6f, ey - es * 0.6f, stroke)
            }
        } else {
            for (s in intArrayOf(-1, 1)) {
                val cx = ex + s * r * 0.27f
                fill.color = Color.argb(a, 255, 255, 255); c.drawCircle(cx, ey, es, fill)
                fill.color = Color.argb(a, 15, 15, 15); c.drawCircle(cx + fx * es * 0.4f, ey + fy * es * 0.4f, es * 0.5f, fill)
            }
            if (mood == 1) {
                stroke.color = Color.argb(a, 25, 15, 5); stroke.strokeWidth = r * 0.09f
                c.drawLine(ex - r * 0.48f, ey - es * 1.5f, ex - r * 0.1f, ey - es * 0.9f, stroke)
                c.drawLine(ex + r * 0.48f, ey - es * 1.5f, ex + r * 0.1f, ey - es * 0.9f, stroke)
            }
        }
        if (hat) {
            val top = cy - r * 0.62f
            val hp = android.graphics.Path()
            hp.moveTo(x - r * 0.5f, top); hp.lineTo(x + r * 0.5f, top)
            hp.quadTo(x + r * 0.2f, top - r * 0.5f, x - fx * r * 0.1f + r * 0.55f, top - r * 0.62f)
            hp.quadTo(x - r * 0.1f, top - r * 0.6f, x - r * 0.5f, top)
            hp.close()
            fill.color = Color.argb(a, 168, 72, 72); c.drawPath(hp, fill)
            fill.color = Color.argb(a, 236, 236, 232)
            c.drawRoundRect(x - r * 0.58f, top - r * 0.1f, x + r * 0.58f, top + r * 0.12f, r * 0.1f, r * 0.1f, fill)
            c.drawCircle(x - fx * r * 0.1f + r * 0.55f, top - r * 0.62f, r * 0.12f, fill)
        }
        if (flash > 0f) {
            fill.color = Color.argb((flash.coerceIn(0f, 1f) * 200 * alphaMul).toInt(), 255, 255, 255)
            i = 0
            while (i < bm.size) { c.drawCircle(x + bm[i] * r, cy + bm[i + 1] * r, bm[i + 2] * r, fill); i += 3 }
        }
    }
}
