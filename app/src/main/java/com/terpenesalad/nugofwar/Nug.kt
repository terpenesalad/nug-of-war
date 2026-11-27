package com.terpenesalad.nugofwar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
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

    private class Puff(val x: Float, val y: Float, val r: Float)

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

    /** Half width of the bud at height t (0 = top, 1 = bottom). Rounded top, full shoulders, flat-ish base. */
    private fun profile(t: Float, peak: Float, pow: Float, maxW: Float): Float =
        if (t < peak) maxW * Math.pow(sin((t / peak) * PI.toFloat() / 2f).toDouble(), pow.toDouble()).toFloat()
        else { val u = (t - peak) / (1f - peak); maxW * (0.45f + 0.55f * sqrt(max(0f, 1f - u * u))) }

    private fun paint(c: Canvas) {
        c.translate(OX, OY)
        c.scale(REF, REF) // draw in units of body radius
        val rnd = Random(seed * 31 + shape * 7 + 3)
        fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

        // ---- the silhouette: a core plus a ring of puffy lumps around its edge
        val yTop: Float; val yBot = 0.6f; val peak: Float; val pow: Float; val maxW: Float
        when (shape) {
            1 -> { yTop = -1.4f; peak = 0.72f; pow = 1.1f; maxW = 0.7f }      // tall cola
            2 -> { yTop = -0.74f; peak = 0.74f; pow = 0.85f; maxW = 0.97f }   // chunky pyramid
            3 -> { yTop = -0.62f; peak = 0.55f; pow = 0.7f; maxW = 0.8f }     // foxtail base
            else -> { yTop = -0.8f; peak = 0.5f; pow = 0.65f; maxW = 0.86f }  // dense round
        }
        val n = 24
        val right = ArrayList<FloatArray>()
        for (i in 0..n) {
            val t = i / n.toFloat()
            val y = yTop + t * (yBot - yTop)
            right.add(floatArrayOf(profile(t, peak, pow, maxW) * (0.94f + rnd.nextFloat() * 0.1f), y))
        }
        val ring = ArrayList<FloatArray>()
        for (pt in right) ring.add(floatArrayOf(pt[0], pt[1]))
        for (pt in right.asReversed()) ring.add(floatArrayOf(-pt[0], pt[1]))
        val cyMid = (yTop + yBot) / 2f

        val core = Path()
        for ((i, pt) in ring.withIndex()) {
            val x = pt[0] * 0.8f; val y = cyMid + (pt[1] - cyMid) * 0.86f
            if (i == 0) core.moveTo(x, y) else core.lineTo(x, y)
        }
        core.close()

        val edge = ArrayList<Puff>()
        var acc = 0f; var nextAt = 0f
        for (i in 0 until ring.size) {
            val p0 = ring[i]; val p1 = ring[(i + 1) % ring.size]
            val seg = sqrt((p1[0] - p0[0]) * (p1[0] - p0[0]) + (p1[1] - p0[1]) * (p1[1] - p0[1]))
            while (nextAt <= acc + seg) {
                val f = if (seg > 0f) (nextAt - acc) / seg else 0f
                val x = p0[0] + (p1[0] - p0[0]) * f; val y = p0[1] + (p1[1] - p0[1]) * f
                val small = if (y < yTop + 0.35f) 0.8f else 1f
                val rr = rf(0.19f, 0.27f) * small
                val dx = -x; val dy = cyMid - y; val dl = max(0.001f, sqrt(dx * dx + dy * dy))
                edge.add(Puff(x + dx / dl * rr * 0.55f, y + dy / dl * rr * 0.55f, rr))
                nextAt += rr * 1.45f
            }
            acc += seg
        }
        // foxtails: little stacks of lumps climbing off the top
        if (shape == 3) {
            for (sx in floatArrayOf(-0.36f, 0.06f, 0.4f)) {
                var x = sx + rf(-0.05f, 0.05f); var y = yTop + 0.05f
                val h = 3 + rnd.nextInt(3)
                for (k in 0 until h) {
                    val rr = 0.17f - k * 0.018f
                    edge.add(Puff(x, y, rr))
                    y -= rr * 1.25f; x += rf(-0.06f, 0.06f) + sx * 0.08f
                }
            }
        }
        val inner = ArrayList<Puff>()
        run {
            var y = yTop + 0.32f
            while (y < yBot - 0.15f) {
                val t = (y - yTop) / (yBot - yTop)
                val w = profile(t, peak, pow, maxW) * 0.62f
                var x = -w + rf(0f, 0.12f)
                while (x <= w) { inner.add(Puff(x, y + rf(-0.04f, 0.04f), rf(0.17f, 0.24f))); x += rf(0.3f, 0.38f) }
                y += rf(0.25f, 0.3f)
            }
        }

        val sil = Path(core)
        val tmp = Path()
        for (pf in edge) { tmp.reset(); tmp.addCircle(pf.x, pf.y, pf.r, Path.Direction.CW); sil.op(tmp, Path.Op.UNION) }

        val brown = isBrown()
        val ink = Gfx.mix(Gfx.shade(color, 0.34f), 0xFF0E1A0C.toInt(), if (brown) 0.15f else 0.35f)
        val dark = Gfx.shade(color, 0.72f)
        val leafCol = if (brown) Gfx.mix(Gfx.shade(color, 0.7f), 0xFF5A5A2E.toInt(), 0.35f)
                      else Gfx.mix(Gfx.shade(color, 0.78f), 0xFF3F7A33.toInt(), 0.45f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path()

        // ---- fan leaves and curly tendrils behind the bud
        val baseY = yBot - 0.05f
        val leafSpots = ArrayList<FloatArray>()  // x, y, size, angle, leaflets
        val lw = right[(n * 0.8f).toInt()][0] * 0.7f
        leafSpots.add(floatArrayOf(lw, baseY - rf(0.15f, 0.3f), rf(0.5f, 0.6f), rf(-10f, 25f), 7f))
        leafSpots.add(floatArrayOf(-lw, baseY - rf(0.15f, 0.3f), rf(0.5f, 0.6f), 180f - rf(-10f, 25f), 7f))
        if (rnd.nextBoolean() || shape == 1) {
            val side = if (rnd.nextBoolean()) 1f else -1f
            val yy = yTop + (yBot - yTop) * rf(0.3f, 0.45f)
            val ww = profile((yy - yTop) / (yBot - yTop), peak, pow, maxW) * 0.7f
            leafSpots.add(floatArrayOf(side * ww, yy, rf(0.38f, 0.46f), if (side > 0) rf(-40f, -15f) else 180f + rf(15f, 40f), 5f))
        }
        if (rnd.nextFloat() < 0.5f) leafSpots.add(floatArrayOf(rf(-0.2f, 0.2f), baseY, rf(0.36f, 0.44f), rf(70f, 110f), 5f))
        for (l in leafSpots) fanLeaf(c, p, path, l[0], l[1], l[2], l[3], l[4].toInt(), leafCol, ink)

        val curls = 2 + rnd.nextInt(3)
        for (i in 0 until curls) {
            val side = if (i % 2 == 0) 1f else -1f
            val x0 = side * rf(0.15f, 0.55f); val y0 = baseY - 0.05f
            path.reset()
            path.moveTo(x0, y0)
            path.cubicTo(x0 + side * 0.18f, y0 + 0.22f, x0 + side * 0.42f, y0 + 0.2f, x0 + side * rf(0.36f, 0.46f), y0 + rf(-0.02f, 0.06f))
            p.style = Paint.Style.STROKE
            p.color = ink; p.strokeWidth = 0.085f; c.drawPath(path, p)
            p.color = leafCol; p.strokeWidth = 0.04f; c.drawPath(path, p)
        }

        // ---- body: thick ink outline, then lumps shaded inside the outline
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.13f
        c.drawPath(sil, p)
        p.style = Paint.Style.FILL; c.drawPath(sil, p)

        c.save()
        c.clipPath(sil)
        p.color = dark; c.drawPath(sil, p)
        val all = ArrayList<Puff>(edge.size + inner.size)
        all.addAll(edge); all.addAll(inner)
        all.sortBy { it.y }
        for (pf in all) {
            val lit = (-pf.x * 0.3f - (pf.y - cyMid) * 0.25f).coerceIn(-0.3f, 0.35f)
            p.color = Gfx.mix(color, dark, 0.85f)
            c.drawCircle(pf.x + pf.r * 0.08f, pf.y + pf.r * 0.1f, pf.r, p)
            p.color = if (lit >= 0) Gfx.mix(color, Color.WHITE, lit * 0.35f) else Gfx.mix(color, dark, -lit * 1.3f)
            c.drawCircle(pf.x - pf.r * 0.06f, pf.y - pf.r * 0.08f, pf.r * 0.86f, p)
            if (lit > 0.05f) {
                p.color = Gfx.alpha(Gfx.mix(color, Color.WHITE, 0.45f), 0.45f)
                c.drawOval(pf.x - pf.r * 0.55f, pf.y - pf.r * 0.6f, pf.x - pf.r * 0.05f, pf.y - pf.r * 0.25f, p)
            }
        }
        c.restore()

        // ---- crease curls between lumps (the cartoon "C" lines), and speckles
        p.style = Paint.Style.STROKE; p.color = Gfx.alpha(ink, 0.9f)
        val oval = RectF()
        for (pf in all) {
            if (rnd.nextFloat() < 0.35f) continue
            val inside = sqrt(pf.x * pf.x + (pf.y - cyMid) * (pf.y - cyMid))
            if (edge.contains(pf) && inside > maxW * 0.9f && rnd.nextFloat() < 0.6f) continue
            p.strokeWidth = rf(0.035f, 0.05f)
            val rr = pf.r * rf(0.7f, 0.85f)
            oval.set(pf.x - rr, pf.y - rr, pf.x + rr, pf.y + rr)
            c.drawArc(oval, rf(10f, 60f), rf(80f, 130f), false, p)
        }
        p.style = Paint.Style.FILL; p.color = Gfx.alpha(ink, 0.55f)
        for (i in 0 until 12 + rnd.nextInt(6)) {
            val pf = inner.getOrNull(rnd.nextInt(max(1, inner.size))) ?: break
            c.drawCircle(pf.x + rf(-0.12f, 0.12f), pf.y + rf(-0.1f, 0.1f), rf(0.014f, 0.024f), p)
        }
        // outline again on top so lumps stay crisp
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.06f
        c.drawPath(sil, p)

        // a sugar-leaf tip tucked in front at one side
        val ts = if (rnd.nextBoolean()) 1f else -1f
        val ty = yTop + (yBot - yTop) * rf(0.55f, 0.75f)
        val tw = profile((ty - yTop) / (yBot - yTop), peak, pow, maxW) * 0.75f
        leaflet(c, p, path, ts * tw, ty, rf(0.3f, 0.36f), if (ts > 0) rf(-25f, 10f) else 180f + rf(-10f, 25f), leafCol, ink)
    }

    private fun isBrown(): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r > g * 0.95f && r > b * 1.3f
    }

    /** One serrated leaflet pointing along +x from (x, y). */
    private fun leaflet(c: Canvas, p: Paint, path: Path, x: Float, y: Float, len: Float, ang: Float, col: Int, ink: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        val wid = len * 0.2f
        val steps = 9
        path.reset(); path.moveTo(0f, 0f)
        for (j in 1 until steps) {
            val f = j / steps.toFloat()
            val w = wid * Math.pow(sin(f * PI).toDouble(), 0.7).toFloat() * (if (j % 2 == 0) 0.72f else 1f)
            path.lineTo(f * len, -w)
        }
        path.lineTo(len, 0f)
        for (j in steps - 1 downTo 1) {
            val f = j / steps.toFloat()
            val w = wid * Math.pow(sin(f * PI).toDouble(), 0.7).toFloat() * (if (j % 2 == 0) 0.72f else 1f)
            path.lineTo(f * len, w)
        }
        path.close()
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.06f; c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = col; c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.018f; p.color = Gfx.alpha(Gfx.shade(col, 0.7f), 0.9f)
        c.drawLine(0.02f, 0f, len * 0.82f, 0f, p)
        c.restore()
    }

    /** A cannabis fan leaf: [count] leaflets spread from one stem point, the middle one longest. */
    private fun fanLeaf(c: Canvas, p: Paint, path: Path, x: Float, y: Float, size: Float, ang: Float, count: Int, col: Int, ink: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        val half = count / 2
        // outer leaflets first so the middle one sits on top
        val order = (0 until count).sortedByDescending { abs(it - half) }
        for (i in order) {
            val k = i - half
            leaflet(c, p, path, size * 0.08f, 0f, size * (1f - 0.17f * abs(k)), k * (78f / half), col, ink)
        }
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
