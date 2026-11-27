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
        const val BW = 300
        const val BH = 290
        const val OX = 150f
        const val OY = 190f
        private val cache = LinkedHashMap<Long, Look>()

        fun of(color: Int, shape: Int, seed: Int): Look {
            val key = (color.toLong() shl 24) xor (shape.toLong() shl 16) xor seed.toLong()
            cache[key]?.let { return it }
            if (cache.size > 48) cache.remove(cache.keys.first())
            return Look(color, shape, seed).also { cache[key] = it }
        }
    }


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

    private class Calyx(val x: Float, val y: Float, val r: Float, val lean: Float)
    private val mtx = android.graphics.Matrix()

    /** A calyx: round base drawn up to a soft point, leaning outward like the real thing. */
    private fun calyxPath(path: Path, k: Calyx, grow: Float) {
        val r = k.r * grow
        path.reset()
        path.moveTo(0f, -1.3f * r)
        path.cubicTo(0.28f * r, -1.02f * r, 1.0f * r, -0.58f * r, 1.0f * r, 0.04f * r)
        path.cubicTo(1.0f * r, 0.62f * r, 0.56f * r, 1.0f * r, 0f, 1.0f * r)
        path.cubicTo(-0.56f * r, 1.0f * r, -1.0f * r, 0.62f * r, -1.0f * r, 0.04f * r)
        path.cubicTo(-1.0f * r, -0.58f * r, -0.28f * r, -1.02f * r, 0f, -1.3f * r)
        path.close()
        mtx.setRotate(k.lean); mtx.postTranslate(k.x, k.y); path.transform(mtx)
    }

    private fun paint(c: Canvas) {
        c.translate(OX, OY)
        c.scale(REF, REF) // draw in units of body radius
        val rnd = Random(seed * 31 + shape * 7 + 3)
        fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

        val yTop: Float; val yBot = 0.58f; val peak: Float; val pow: Float; val maxW: Float
        when (shape) {
            1 -> { yTop = -1.3f; peak = 0.72f; pow = 1.1f; maxW = 0.7f }      // tall cola
            2 -> { yTop = -0.66f; peak = 0.74f; pow = 0.85f; maxW = 0.95f }   // wide pyramid
            3 -> { yTop = -0.55f; peak = 0.55f; pow = 0.7f; maxW = 0.8f }     // foxtail base
            else -> { yTop = -0.72f; peak = 0.5f; pow = 0.65f; maxW = 0.85f } // dense round
        }
        val cyMid = (yTop + yBot) / 2f
        fun widthAt(y: Float) = profile(((y - yTop) / (yBot - yTop)).coerceIn(0f, 1f), peak, pow, maxW)

        // ---- calyxes laid in staggered rows, bottom to top: a clear, repeating structure
        val rows = ArrayList<ArrayList<Calyx>>()
        var y = yBot - 0.16f
        var row = 0
        while (y > yTop + 0.06f) {
            val w = widthAt(y)
            val r = 0.17f + 0.07f * (w / maxW)
            val step = r * 1.7f
            var count = max(1, ((2f * w - r * 0.4f) / step).toInt() + 1)
            if (row % 2 == 1 && count > 1) count -= 1
            val span = (count - 1) * step
            val list = ArrayList<Calyx>()
            for (i in 0 until count) {
                val x = -span / 2f + i * step + rf(-0.015f, 0.015f)
                list.add(Calyx(x, y + rf(-0.015f, 0.015f), r, (x * 30f).coerceIn(-28f, 28f)))
            }
            rows.add(list)
            y -= r * 1.22f; row++
        }
        rows.add(arrayListOf(Calyx(rf(-0.03f, 0.03f), yTop + 0.1f, 0.15f, 0f)))  // the crown
        if (shape == 3) {                                  // foxtails: thin spires of small calyxes
            for (sx in floatArrayOf(-0.26f, 0.04f, 0.3f)) {
                var x = sx; var yy = yTop - 0.02f
                for (k in 0 until 3) {
                    val r = 0.13f - k * 0.018f
                    rows.add(arrayListOf(Calyx(x, yy, r, sx * 40f)))
                    yy -= r * 1.3f; x += sx * 0.16f
                }
            }
        }
        val all = rows.flatten()

        // the silhouette: every calyx unioned with a core so there are no holes
        val sil = Path()
        val core = Path()
        run {
            var first = true
            var yy = yTop + 0.1f
            val pts = ArrayList<FloatArray>()
            while (yy <= yBot) { pts.add(floatArrayOf(widthAt(yy) * 0.78f, yy)); yy += 0.08f }
            for (pt in pts) { if (first) { core.moveTo(pt[0], pt[1]); first = false } else core.lineTo(pt[0], pt[1]) }
            for (pt in pts.asReversed()) core.lineTo(-pt[0], pt[1])
            core.close()
        }
        sil.set(core)
        val tmp = Path()
        for (k in all) { calyxPath(tmp, k, 1.0f); sil.op(tmp, Path.Op.UNION) }

        val brown = isBrown()
        val ink = Gfx.mix(Gfx.shade(color, 0.3f), 0xFF0B140A.toInt(), if (brown) 0.1f else 0.3f)
        val toneLight = Gfx.mix(color, Color.WHITE, 0.16f)
        val toneMid = color
        val toneShade = Gfx.shade(color, 0.8f)
        val toneDeep = Gfx.shade(color, 0.64f)
        val gap = Gfx.shade(color, 0.48f)
        val leafA = if (brown) Gfx.mix(Gfx.shade(color, 0.75f), 0xFF5C5A30.toInt(), 0.4f) else Gfx.mix(Gfx.shade(color, 0.72f), 0xFF3A7A34.toInt(), 0.55f)
        val leafB = Gfx.shade(leafA, 0.72f)
        val pistilCol = if (brown) 0xFF6E4C2C.toInt() else 0xFFD08A48.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path()

        // ---- one fan leaf behind, composed to one side, and a smaller one opposite
        val side = if (seed % 2 == 0) 1f else -1f
        val by = yBot - 0.28f
        fanLeaf(c, p, path, side * widthAt(by) * 0.55f, by, 0.66f, if (side > 0) -22f else 202f, 7, leafA, leafB, ink)
        val sy = yBot - 0.1f
        fanLeaf(c, p, path, -side * widthAt(sy) * 0.6f, sy, 0.42f, if (side > 0) 200f else -20f, 5, leafA, leafB, ink)

        // ---- body
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.12f
        c.drawPath(sil, p)
        p.style = Paint.Style.FILL; p.color = gap
        c.drawPath(sil, p)
        c.save(); c.clipPath(sil)
        // draw top rows first so lower calyxes overlap the ones above, like scales
        for (k in all.sortedBy { it.y }) {
            val score = -k.x * 0.7f - (k.y - cyMid) * 0.45f
            val face = when { score > 0.16f -> toneLight; score > -0.22f -> toneMid; else -> toneShade }
            val half = when (face) { toneLight -> toneMid; toneMid -> toneShade; else -> toneDeep }
            calyxPath(path, k, 1.12f); p.color = gap; c.drawPath(path, p)
            calyxPath(path, k, 1.0f); p.color = face; c.drawPath(path, p)
            // hard-edged shadow on the right half, split along a curved midline
            c.save(); c.clipPath(path)
            c.translate(k.x, k.y); c.rotate(k.lean)
            val r = k.r
            tmp.reset()
            tmp.moveTo(0f, -1.35f * r)
            tmp.quadTo(0.3f * r, -0.1f * r, 0.12f * r, 1.1f * r)
            tmp.lineTo(1.2f * r, 1.1f * r); tmp.lineTo(1.2f * r, -1.35f * r); tmp.close()
            p.color = half; c.drawPath(tmp, p)
            c.restore()
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.055f
        c.drawPath(sil, p)

        // ---- a single sugar leaf tucked against one side
        val ty = yTop + (yBot - yTop) * 0.6f
        leaflet(c, p, path, side * widthAt(ty) * 0.82f, ty, 0.32f, if (side > 0) -8f else 188f, leafA, leafB, ink)

        // ---- pistils: a handful, from calyx tips, all the same gentle curve
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.03f; p.color = pistilCol
        val upper = all.filter { it.y < cyMid + 0.1f }.shuffled(rnd).take(4 + rnd.nextInt(2))
        for (k in upper) {
            val a = Math.toRadians((-90f + k.lean * 1.4f).toDouble())
            val tx = k.x + sin(Math.toRadians(k.lean.toDouble())).toFloat() * k.r * 1.25f
            val tyy = k.y - cos(Math.toRadians(k.lean.toDouble())).toFloat() * k.r * 1.25f
            val len = 0.24f
            val ex = tx + cos(a).toFloat() * len; val ey = tyy + sin(a).toFloat() * len
            val bend = if (k.x >= 0) 1f else -1f
            path.reset(); path.moveTo(tx, tyy)
            path.quadTo((tx + ex) / 2 + bend * 0.07f, (tyy + ey) / 2, ex, ey)
            c.drawPath(path, p)
        }
    }

    /** Half width of the bud at height t (0 = top, 1 = bottom). Rounded top, full shoulders, flat-ish base. */
    private fun isBrown(): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r > g * 0.95f && r > b * 1.3f
    }

    /** One leaflet pointing along +x: lance shaped, finely toothed, split light / dark along the midrib. */
    private fun leaflet(c: Canvas, p: Paint, path: Path, x: Float, y: Float, len: Float, ang: Float, a: Int, b: Int, ink: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        val wid = len * 0.18f
        val steps = 12
        fun w(f: Float, j: Int) = wid * Math.pow(sin(f * PI).toDouble(), 0.65).toFloat() * (if (j % 2 == 0) 0.86f else 1f)
        path.reset(); path.moveTo(0f, 0f)
        for (j in 1 until steps) { val f = j / steps.toFloat(); path.lineTo(f * len, -w(f, j)) }
        path.lineTo(len, 0f)
        for (j in steps - 1 downTo 1) { val f = j / steps.toFloat(); path.lineTo(f * len, w(f, j)) }
        path.close()
        p.style = Paint.Style.STROKE; p.color = ink; p.strokeWidth = 0.05f; c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = a; c.drawPath(path, p)
        c.save(); c.clipPath(path); p.color = b; c.drawRect(0f, 0f, len, wid * 1.2f, p); c.restore()
        c.restore()
    }

    /** A fan leaf: leaflets fanned from one point, longest in the middle. */
    private fun fanLeaf(c: Canvas, p: Paint, path: Path, x: Float, y: Float, size: Float, ang: Float, count: Int, a: Int, b: Int, ink: Int) {
        c.save(); c.translate(x, y); c.rotate(ang)
        val half = count / 2
        for (i in (0 until count).sortedByDescending { abs(it - half) }) {
            val k = i - half
            leaflet(c, p, path, 0f, 0f, size * (1f - 0.16f * abs(k)), k * (74f / half), a, b, ink)
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
