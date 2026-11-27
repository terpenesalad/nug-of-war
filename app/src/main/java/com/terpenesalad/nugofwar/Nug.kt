package com.terpenesalad.nugofwar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * How a nug looks. The bud body (layered calyxes with darker shading flicks, sugar leaves,
 * pistils and frost) is painted once into a bitmap, then drawn each frame with legs and eyes on top.
 */
class Look private constructor(val color: Int, val shape: Int, val seed: Int) {
    val outline = Gfx.shade(color, 0.42f)
    val light = Gfx.mix(color, Color.WHITE, 0.35f)
    /** Top of the bud in units of body radius (negative = above centre). Used to place hats. */
    var top = -0.8f
        private set
    private var bmp: Bitmap? = null

    companion object {
        const val REF = 90f
        const val BW = 240
        const val BH = 290
        const val OX = 120f
        const val OY = 190f
        private val cache = LinkedHashMap<Long, Look>()

        fun of(color: Int, shape: Int, seed: Int): Look {
            val key = (color.toLong() shl 24) xor (shape.toLong() shl 16) xor seed.toLong()
            cache[key]?.let { return it }
            if (cache.size > 48) cache.remove(cache.keys.first())
            return Look(color, shape, seed).also { cache[key] = it }
        }
    }

    private class Bract(val x: Float, val y: Float, val w: Float, val h: Float, val ang: Float, val shade: Float)

    init {
        top = when (shape) { 1 -> -1.45f; 3 -> -1.3f; 2 -> -0.72f; else -> -0.82f }
    }

    fun bitmap(): Bitmap {
        bmp?.let { return it }
        val b = Bitmap.createBitmap(BW, BH, Bitmap.Config.ARGB_8888)
        paint(Canvas(b))
        bmp = b
        return b
    }

    private fun paint(c: Canvas) {
        c.translate(OX, OY)
        c.scale(REF, REF) // draw in units of body radius
        val rnd = Random(seed * 31 + shape * 7 + 3)
        val bracts = ArrayList<Bract>()

        fun row(y: Float, w: Float, size: Float) {
            val n = max(1, (2f * w / (0.34f * size)).roundToInt())
            for (i in 0 until n) {
                val dx = -w + (i + 0.5f) * (2f * w / n) + (rnd.nextFloat() - 0.5f) * 0.08f
                val s = size * (0.85f + rnd.nextFloat() * 0.3f)
                bracts.add(Bract(dx, y + (rnd.nextFloat() - 0.5f) * 0.06f, 0.4f * s, 0.55f * s,
                    dx * 26f + (rnd.nextFloat() - 0.5f) * 14f, rnd.nextFloat()))
            }
        }
        fun body(yTop: Float, yBot: Float, cy: Float, ry: Float, maxW: Float) {
            var y = yBot
            while (y >= yTop) {
                val t = (y - cy) / ry
                val w = maxW * sqrt(max(0f, 1f - t * t))
                row(y, max(0.1f, w), 0.75f + 0.35f * (w / maxW))
                y -= 0.2f
            }
        }
        when (shape) {
            0 -> body(-0.72f, 0.55f, -0.1f, 0.75f, 0.8f)           // dense round bud
            1 -> body(-1.38f, 0.55f, -0.42f, 1.05f, 0.58f)         // tall cola
            2 -> body(-0.62f, 0.6f, -0.02f, 0.7f, 0.98f)           // chunky
            else -> {                                              // foxtails: spires growing off the top
                body(-0.45f, 0.55f, 0f, 0.62f, 0.72f)
                for (sx in floatArrayOf(-0.34f, 0.08f, 0.42f)) {
                    var x = sx; var y = -0.42f
                    val h = 4 + rnd.nextInt(3)
                    for (k in 0 until h) {
                        bracts.add(Bract(x, y, 0.2f, 0.3f, (rnd.nextFloat() - 0.5f) * 30f + sx * 20f, rnd.nextFloat()))
                        y -= 0.15f; x += (rnd.nextFloat() - 0.5f) * 0.08f + sx * 0.05f
                    }
                }
            }
        }
        bracts.sortBy { it.y }

        val leafCol = Gfx.mix(Gfx.shade(color, 0.62f), 0xFF2E4A2C.toInt(), if (isBrown()) 0.15f else 0.4f)
        val pistilCol = if (isBrown()) 0xFF5E4026.toInt() else 0xFFC98A4A.toInt()
        val dark = Gfx.shade(color, 0.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()

        // sugar leaves behind the bud
        val leaves = 2 + rnd.nextInt(2)
        for (i in 0 until leaves) {
            val side = if (i % 2 == 0) -1f else 1f
            val ly = 0.1f + rnd.nextFloat() * 0.35f
            val ang = 10f + rnd.nextFloat() * 35f
            leaf(c, p, path, side * 0.45f, ly, 0.75f + rnd.nextFloat() * 0.3f, if (side > 0) -ang else 180f + ang, leafCol)
        }

        // dark under-layer gives the bud a soft outline
        p.style = Paint.Style.FILL
        p.color = outline
        for (b in bracts) { bract(path, b, 1.18f); c.drawPath(path, p) }

        // calyxes, lit from the top left
        for (b in bracts) {
            val lit = (-b.x * 0.35f - b.y * 0.25f).coerceIn(-0.4f, 0.4f) + (b.shade - 0.5f) * 0.3f
            p.style = Paint.Style.FILL
            p.color = if (lit >= 0) Gfx.mix(color, Color.WHITE, lit * 0.45f) else Gfx.mix(color, dark, -lit * 1.2f)
            bract(path, b, 1f)
            c.drawPath(path, p)
            // darker flick down the shadow side to show the calyx shape
            p.style = Paint.Style.STROKE
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 0.035f
            p.color = Gfx.alpha(dark, 0.85f)
            c.save(); c.translate(b.x, b.y); c.rotate(b.ang)
            path.reset()
            path.moveTo(b.w * 0.12f, -b.h * 0.32f)
            path.quadTo(b.w * 0.36f, b.h * 0.02f, b.w * 0.06f, b.h * 0.42f)
            c.drawPath(path, p)
            // little light flick on the lit side
            p.strokeWidth = 0.022f
            p.color = Gfx.alpha(Gfx.mix(color, Color.WHITE, 0.5f), 0.6f)
            path.reset()
            path.moveTo(-b.w * 0.2f, -b.h * 0.18f)
            path.quadTo(-b.w * 0.28f, b.h * 0.05f, -b.w * 0.14f, b.h * 0.2f)
            c.drawPath(path, p)
            c.restore()
        }

        // one sugar leaf tucked in front
        leaf(c, p, path, -0.15f + rnd.nextFloat() * 0.3f, 0.35f, 0.5f, 160f + rnd.nextFloat() * 40f, Gfx.alpha(leafCol, 0.95f))

        // pistils (the little curly hairs)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.022f
        p.color = pistilCol
        val pistils = 7 + rnd.nextInt(5)
        for (i in 0 until pistils) {
            val b = bracts[rnd.nextInt(bracts.size)]
            val a = Math.toRadians((-90f + b.x * 70f + (rnd.nextFloat() - 0.5f) * 80f).toDouble())
            val len = 0.16f + rnd.nextFloat() * 0.14f
            val ex = b.x + cos(a).toFloat() * len; val ey = b.y - b.h * 0.3f + sin(a).toFloat() * len
            path.reset()
            path.moveTo(b.x, b.y - b.h * 0.3f)
            path.quadTo((b.x + ex) / 2 + (rnd.nextFloat() - 0.5f) * 0.12f, (b.y + ey) / 2 - 0.04f, ex, ey)
            c.drawPath(path, p)
        }

        // frost
        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 255, 255, 255)
        for (i in 0 until 40) {
            val b = bracts[rnd.nextInt(bracts.size)]
            c.drawCircle(b.x + (rnd.nextFloat() - 0.5f) * b.w, b.y + (rnd.nextFloat() - 0.5f) * b.h, 0.012f + rnd.nextFloat() * 0.012f, p)
        }
    }

    private fun isBrown(): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r > g * 0.95f && r > b * 1.3f
    }

    private fun bract(path: Path, b: Bract, scale: Float) {
        val w = b.w * scale; val h = b.h * scale
        path.reset()
        path.moveTo(0f, -h / 2)
        path.cubicTo(w * 0.55f, -h * 0.2f, w * 0.62f, h * 0.42f, 0f, h / 2)
        path.cubicTo(-w * 0.62f, h * 0.42f, -w * 0.55f, -h * 0.2f, 0f, -h / 2)
        path.close()
        val m = android.graphics.Matrix()
        m.setRotate(b.ang)
        m.postTranslate(b.x, b.y)
        path.transform(m)
    }

    private fun leaf(c: Canvas, p: Paint, path: Path, x: Float, y: Float, len: Float, ang: Float, col: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        path.reset()
        path.moveTo(0f, 0f)
        path.quadTo(len * 0.45f, -len * 0.2f, len, 0f)
        path.quadTo(len * 0.45f, len * 0.14f, 0f, 0f)
        path.close()
        p.style = Paint.Style.FILL; p.color = col
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.02f; p.color = Gfx.alpha(Gfx.mix(col, Color.WHITE, 0.3f), 0.6f)
        c.drawLine(0.02f, 0f, len * 0.85f, 0f, p)
        c.restore()
    }
}

object Gfx {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val dst = android.graphics.RectF()
    private val hatPath = Path()

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
     * @param fx facing direction x (-1..1), used for eyes
     * @param mood 0 = friendly, 1 = angry (bad nug), 2 = knocked out
     * @param flail legs kick wildly (when launched)
     */
    fun nug(c: Canvas, x: Float, y: Float, r: Float, look: Look, fx: Float, fy: Float, phase: Float,
            moving: Boolean, flash: Float, mood: Int, alphaMul: Float = 1f, hat: Boolean = false, flail: Boolean = false,
            shadow: Boolean = true) {
        val a = (alphaMul * 255).toInt().coerceIn(0, 255)
        if (shadow) {
            fill.color = Color.argb((60 * alphaMul).toInt(), 0, 0, 0)
            c.drawOval(x - r * 0.8f, y + r * 1.0f, x + r * 0.8f, y + r * 1.22f, fill)
        }
        // legs
        val swing = if (flail) sin(phase * 2.3f) * r * 0.45f else if (moving) sin(phase) * r * 0.28f else 0f
        val legCol = Color.argb(a, 58, 64, 50)
        stroke.color = legCol; stroke.strokeWidth = r * 0.13f
        val hipY = y + r * 0.45f; val footY = y + r * 1.08f
        val lx = x - r * 0.28f; val rx = x + r * 0.28f
        c.drawLine(lx, hipY, lx + swing, footY, stroke)
        c.drawLine(rx, hipY, rx - swing, footY, stroke)
        fill.color = legCol
        c.drawOval(lx + swing - r * 0.15f + fx * r * 0.06f, footY - r * 0.07f, lx + swing + r * 0.15f + fx * r * 0.06f, footY + r * 0.08f, fill)
        c.drawOval(rx - swing - r * 0.15f + fx * r * 0.06f, footY - r * 0.07f, rx - swing + r * 0.15f + fx * r * 0.06f, footY + r * 0.08f, fill)

        // bud body
        val bob = if (moving && !flail) -kotlin.math.abs(sin(phase)) * r * 0.06f else 0f
        val cy = y + bob
        val s = r / Look.REF
        dst.set(x - Look.OX * s, cy - Look.OY * s, x + (Look.BW - Look.OX) * s, cy + (Look.BH - Look.OY) * s)
        bmpPaint.alpha = a
        if (flash > 0f) {
            val add = (flash.coerceIn(0f, 1f) * 170).toInt()
            bmpPaint.colorFilter = LightingColorFilter(0xFFFFFF, Color.rgb(add, add, add))
        } else bmpPaint.colorFilter = null
        c.drawBitmap(look.bitmap(), null, dst, bmpPaint)

        // eyes
        val ex = x + fx * r * 0.16f; val ey = cy - r * 0.08f + fy * r * 0.05f
        val es = r * 0.15f
        if (mood == 2) {
            stroke.color = Color.argb(a, 20, 20, 20); stroke.strokeWidth = r * 0.07f
            for (k in intArrayOf(-1, 1)) {
                val ccx = ex + k * r * 0.22f
                c.drawLine(ccx - es * 0.6f, ey - es * 0.6f, ccx + es * 0.6f, ey + es * 0.6f, stroke)
                c.drawLine(ccx - es * 0.6f, ey + es * 0.6f, ccx + es * 0.6f, ey - es * 0.6f, stroke)
            }
        } else {
            for (k in intArrayOf(-1, 1)) {
                val ccx = ex + k * r * 0.22f
                fill.color = Color.argb(a, 245, 245, 240); c.drawCircle(ccx, ey, es, fill)
                fill.color = Color.argb(a, 15, 15, 15); c.drawCircle(ccx + fx * es * 0.4f, ey + fy * es * 0.4f, es * 0.52f, fill)
            }
            if (mood == 1) {
                stroke.color = Color.argb(a, 25, 15, 5); stroke.strokeWidth = r * 0.08f
                c.drawLine(ex - r * 0.42f, ey - es * 1.6f, ex - r * 0.08f, ey - es * 0.9f, stroke)
                c.drawLine(ex + r * 0.42f, ey - es * 1.6f, ex + r * 0.08f, ey - es * 0.9f, stroke)
            }
        }
        if (hat) {
            val top = cy + look.top * r * 0.85f + r * 0.1f
            hatPath.reset()
            hatPath.moveTo(x - r * 0.42f, top); hatPath.lineTo(x + r * 0.42f, top)
            hatPath.quadTo(x + r * 0.2f, top - r * 0.45f, x + r * 0.5f, top - r * 0.55f)
            hatPath.quadTo(x - r * 0.1f, top - r * 0.55f, x - r * 0.42f, top)
            hatPath.close()
            fill.color = Color.argb(a, 168, 72, 72); c.drawPath(hatPath, fill)
            fill.color = Color.argb(a, 236, 236, 232)
            c.drawRoundRect(x - r * 0.5f, top - r * 0.08f, x + r * 0.5f, top + r * 0.1f, r * 0.09f, r * 0.09f, fill)
            c.drawCircle(x + r * 0.5f, top - r * 0.55f, r * 0.1f, fill)
        }
    }
}
