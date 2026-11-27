package com.terpenesalad.nugofwar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Puffy cartoon buds: scalloped lumps, thick ink outline, serrated fan leaves. */
class PuffyArt(val color: Int, val shape: Int, val seed: Int) {
    private class Puff(val x: Float, val y: Float, val r: Float)
    /** Half width of the bud at height t (0 = top, 1 = bottom). Rounded top, full shoulders, flat-ish base. */
    private fun profile(t: Float, peak: Float, pow: Float, maxW: Float): Float =
        if (t < peak) maxW * Math.pow(sin((t / peak) * PI.toFloat() / 2f).toDouble(), pow.toDouble()).toFloat()
        else { val u = (t - peak) / (1f - peak); maxW * (0.45f + 0.55f * sqrt(max(0f, 1f - u * u))) }
    fun paint(c: Canvas) {
        c.translate(Look.OX, Look.OY)
        c.scale(Look.REF, Look.REF) // draw in units of body radius
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
                val rr = rf(0.25f, 0.34f) * small
                val dx = -x; val dy = cyMid - y; val dl = max(0.001f, sqrt(dx * dx + dy * dy))
                edge.add(Puff(x + dx / dl * rr * 0.55f, y + dy / dl * rr * 0.55f, rr))
                nextAt += rr * 1.55f
            }
            acc += seg
        }
        // foxtails: little stacks of lumps climbing off the top
        if (shape == 3) {
            for (sx in floatArrayOf(-0.3f, 0.02f, 0.32f)) {
                var x = sx + rf(-0.05f, 0.05f); var y = yTop + 0.05f
                val h = 2 + rnd.nextInt(2)
                for (k in 0 until h) {
                    val rr = 0.21f - k * 0.03f
                    edge.add(Puff(x, y, rr))
                    y -= rr * 1.1f; x += rf(-0.05f, 0.05f) + sx * 0.12f
                }
            }
        }
        val inner = ArrayList<Puff>()
        run {
            var y = yTop + 0.38f
            while (y < yBot - 0.15f) {
                val t = (y - yTop) / (yBot - yTop)
                val w = profile(t, peak, pow, maxW) * 0.55f
                var x = -w + rf(0f, 0.12f)
                while (x <= w) { inner.add(Puff(x, y + rf(-0.05f, 0.05f), rf(0.24f, 0.31f))); x += rf(0.42f, 0.5f) }
                y += rf(0.36f, 0.42f)
            }
        }

        val sil = Path(core)
        val tmp = Path()
        for (pf in edge) { tmp.reset(); tmp.addCircle(pf.x, pf.y, pf.r, Path.Direction.CW); sil.op(tmp, Path.Op.UNION) }

        val brown = isBrown()
        val ink = Gfx.mix(Gfx.shade(color, 0.34f), 0xFF0E1A0C.toInt(), if (brown) 0.15f else 0.35f)
        val dark = Gfx.shade(color, 0.72f)
        val leafCol = if (brown) Gfx.mix(Gfx.shade(color, 0.7f), 0xFF5A5A2E.toInt(), 0.35f)
                      else Gfx.mix(Gfx.shade(color, 0.7f), 0xFF2F6A2A.toInt(), 0.6f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path()

        // ---- fan leaves and curly tendrils behind the bud
        val baseY = yBot - 0.05f
        val leafSpots = ArrayList<FloatArray>()  // x, y, size, angle, leaflets
        val lw = right[(n * 0.75f).toInt()][0] * 0.78f
        leafSpots.add(floatArrayOf(lw, baseY - rf(0.2f, 0.32f), rf(0.66f, 0.74f), rf(5f, 35f), 7f))
        leafSpots.add(floatArrayOf(-lw, baseY - rf(0.2f, 0.32f), rf(0.66f, 0.74f), 180f - rf(5f, 35f), 7f))
        if (rnd.nextBoolean() || shape == 1) {
            val side = if (rnd.nextBoolean()) 1f else -1f
            val yy = yTop + (yBot - yTop) * rf(0.3f, 0.45f)
            val ww = profile((yy - yTop) / (yBot - yTop), peak, pow, maxW) * 0.7f
            leafSpots.add(floatArrayOf(side * ww, yy, rf(0.5f, 0.58f), if (side > 0) rf(-40f, -15f) else 180f + rf(15f, 40f), 5f))
        }
        if (rnd.nextFloat() < 0.5f) leafSpots.add(floatArrayOf(rf(-0.1f, 0.1f), baseY, rf(0.4f, 0.46f), rf(80f, 100f), 5f))
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
            if (rnd.nextFloat() < 0.5f) continue
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

/** Shared bits for the simpler styles. */
private object ArtKit {
    fun brown(color: Int): Boolean {
        val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
        return r > g * 0.95f && r > b * 1.3f
    }

    fun profile(t: Float, peak: Float, pow: Float, maxW: Float): Float =
        if (t < peak) maxW * Math.pow(sin((t / peak) * PI.toFloat() / 2f).toDouble(), pow.toDouble()).toFloat()
        else { val u = (t - peak) / (1f - peak); maxW * (0.45f + 0.55f * sqrt(max(0f, 1f - u * u))) }

    class Body(val sil: Path, val lumps: List<FloatArray>, val yTop: Float, val yBot: Float, val widthAt: (Float) -> Float)

    /** A bud silhouette from a ring of lumps of radius [rMin]..[rMax] around the shape's profile. */
    fun body(shape: Int, rnd: Random, rMin: Float, rMax: Float, spacing: Float): Body {
        val yTop: Float; val yBot = 0.6f; val peak: Float; val pow: Float; val maxW: Float
        when (shape) {
            1 -> { yTop = -1.35f; peak = 0.72f; pow = 1.1f; maxW = 0.7f }
            2 -> { yTop = -0.7f; peak = 0.74f; pow = 0.85f; maxW = 0.95f }
            3 -> { yTop = -0.6f; peak = 0.55f; pow = 0.7f; maxW = 0.8f }
            else -> { yTop = -0.78f; peak = 0.5f; pow = 0.65f; maxW = 0.85f }
        }
        val widthAt = { y: Float -> profile(((y - yTop) / (yBot - yTop)).coerceIn(0f, 1f), peak, pow, maxW) }
        val cy = (yTop + yBot) / 2f
        val n = 24
        val ring = ArrayList<FloatArray>()
        for (i in 0..n) { val y = yTop + i / n.toFloat() * (yBot - yTop); ring.add(floatArrayOf(widthAt(y), y)) }
        for (i in n downTo 0) { val y = yTop + i / n.toFloat() * (yBot - yTop); ring.add(floatArrayOf(-widthAt(y), y)) }
        val core = Path()
        for ((i, pt) in ring.withIndex()) {
            val x = pt[0] * 0.8f; val y = cy + (pt[1] - cy) * 0.86f
            if (i == 0) core.moveTo(x, y) else core.lineTo(x, y)
        }
        core.close()
        val lumps = ArrayList<FloatArray>()
        var acc = 0f; var nextAt = 0f
        for (i in 0 until ring.size - 1) {
            val p0 = ring[i]; val p1 = ring[i + 1]
            val seg = sqrt((p1[0] - p0[0]) * (p1[0] - p0[0]) + (p1[1] - p0[1]) * (p1[1] - p0[1]))
            while (nextAt <= acc + seg) {
                val f = if (seg > 0f) (nextAt - acc) / seg else 0f
                val x = p0[0] + (p1[0] - p0[0]) * f; val y = p0[1] + (p1[1] - p0[1]) * f
                val rr = (rMin + rnd.nextFloat() * (rMax - rMin)) * (if (y < yTop + 0.3f) 0.8f else 1f)
                val dx = -x; val dy = cy - y; val dl = max(0.001f, sqrt(dx * dx + dy * dy))
                lumps.add(floatArrayOf(x + dx / dl * rr * 0.55f, y + dy / dl * rr * 0.55f, rr))
                nextAt += rr * spacing
            }
            acc += seg
        }
        if (shape == 3) for (sx in floatArrayOf(-0.3f, 0.02f, 0.32f)) {
            var x = sx; var y = yTop + 0.02f
            for (k in 0 until 2 + rnd.nextInt(2)) {
                val rr = rMin * (0.75f - k * 0.1f)
                lumps.add(floatArrayOf(x, y, rr)); y -= rr * 1.15f; x += sx * 0.12f
            }
        }
        val sil = Path(core)
        val tmp = Path()
        for (l in lumps) { tmp.reset(); tmp.addCircle(l[0], l[1], l[2], Path.Direction.CW); sil.op(tmp, Path.Op.UNION) }
        return Body(sil, lumps, yTop, yBot, widthAt)
    }

    /** A smooth lance leaflet along +x (no teeth). */
    fun lance(path: Path, len: Float, wid: Float) {
        path.reset()
        path.moveTo(0f, 0f)
        path.cubicTo(len * 0.3f, -wid, len * 0.7f, -wid * 0.8f, len, 0f)
        path.cubicTo(len * 0.7f, wid * 0.8f, len * 0.3f, wid, 0f, 0f)
        path.close()
    }
}

/**
 * Flat: big simple lumps, no outline, one flat colour with a single offset shadow shape,
 * a couple of deliberate crease marks and one plain leaf. Sticker / editorial illustration.
 */
class FlatArt(val color: Int, val shape: Int, val seed: Int) {
    fun paint(c: Canvas) {
        c.translate(Look.OX, Look.OY); c.scale(Look.REF, Look.REF)
        val rnd = Random(seed * 13 + shape * 5 + 1)
        val b = ArtKit.body(shape, rnd, 0.33f, 0.4f, 1.5f)
        val brown = ArtKit.brown(color)
        val shadow = Gfx.shade(color, 0.76f)
        val leaf = if (brown) Gfx.shade(Gfx.mix(color, 0xFF55552E.toInt(), 0.4f), 0.7f) else Gfx.mix(Gfx.shade(color, 0.55f), 0xFF2C5A2A.toInt(), 0.5f)
        val leafShadow = Gfx.shade(leaf, 0.78f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.strokeCap = Paint.Cap.ROUND
        val path = Path()

        // one leaf, behind, to one side; five plain leaflets
        val side = if (seed % 2 == 0) 1f else -1f
        val ly = b.yBot - 0.3f
        c.save(); c.translate(side * b.widthAt(ly) * 0.7f, ly); c.rotate(if (side > 0) -25f else 205f)
        for (k in intArrayOf(-2, 2, -1, 1, 0)) {
            c.save(); c.rotate(k * 34f)
            ArtKit.lance(path, 0.72f * (1f - 0.15f * abs(k)), 0.12f)
            p.style = Paint.Style.FILL; p.color = if (k > 0) leafShadow else leaf
            c.drawPath(path, p)
            c.restore()
        }
        c.restore()

        // body: flat fill, shadow is the silhouette itself nudged down-right
        p.style = Paint.Style.FILL; p.color = shadow
        c.drawPath(b.sil, p)
        c.save(); c.clipPath(b.sil)
        c.save(); c.translate(-0.09f, -0.11f); p.color = color; c.drawPath(b.sil, p); c.restore()
        // two or three crease marks, each a short arc from a lump
        p.style = Paint.Style.STROKE; p.strokeWidth = 0.05f; p.color = shadow
        val oval = RectF()
        val picks = b.lumps.filter { abs(it[0]) < b.widthAt(it[1]) * 0.75f || it[1] > (b.yTop + b.yBot) / 2 }.shuffled(rnd).take(3)
        for (l in picks) {
            val rr = l[2] * 0.7f
            oval.set(l[0] - rr, l[1] - rr, l[0] + rr, l[1] + rr)
            c.drawArc(oval, 30f, 70f, false, p)
        }
        c.restore()
        // a few frost dots, grouped on the lit side
        p.style = Paint.Style.FILL; p.color = Gfx.alpha(Color.WHITE, 0.55f)
        val fx = -b.widthAt(-0.1f) * 0.45f
        for (i in 0 until 3) c.drawCircle(fx + i * 0.07f - (i % 2) * 0.02f, -0.25f + (i % 2) * 0.07f, 0.022f, p)
    }
}

/**
 * Sketch: like it was drawn with a pen. Wobbly, uneven line, flat muted fill,
 * a few hatch marks on the shadow side, line-drawn leaves.
 */
class SketchArt(val color: Int, val shape: Int, val seed: Int) {
    fun paint(c: Canvas) {
        c.translate(Look.OX, Look.OY); c.scale(Look.REF, Look.REF)
        val rnd = Random(seed * 17 + shape * 3 + 9)
        val b = ArtKit.body(shape, rnd, 0.25f, 0.32f, 1.55f)
        val brown = ArtKit.brown(color)
        val fill = Gfx.shade(color, 0.92f)
        val ink = Gfx.mix(Gfx.shade(color, 0.28f), 0xFF101010.toInt(), 0.4f)
        val leafFill = if (brown) Gfx.shade(color, 0.6f) else Gfx.mix(Gfx.shade(color, 0.6f), 0xFF2C5A2A.toInt(), 0.45f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG); p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path()

        // pen line: walk a path and redraw it in short strokes with drifting offset and width
        fun pen(src: Path, w: Float, wobble: Float, gapAt: Float = -1f) {
            val pm = PathMeasure(src, false)
            val pos = FloatArray(2); val tan = FloatArray(2)
            val ph1 = rnd.nextFloat() * 6f; val ph2 = rnd.nextFloat() * 6f
            do {
                val len = pm.length
                var d = 0f; var px = 0f; var py = 0f; var first = true
                val step = 0.035f
                while (d <= len) {
                    pm.getPosTan(d, pos, tan)
                    val off = (sin(d * 5f + ph1) * 0.6f + sin(d * 13f + ph2) * 0.4f) * wobble
                    val x = pos[0] - tan[1] * off; val y = pos[1] + tan[0] * off
                    val frac = d / max(0.001f, len)
                    val skip = gapAt >= 0f && abs(frac - gapAt) < 0.025f
                    if (!first && !skip) {
                        p.strokeWidth = w * (0.75f + 0.45f * (0.5f + 0.5f * sin(d * 3.3f + ph2)))
                        c.drawLine(px, py, x, y, p)
                    }
                    px = x; py = y; first = false; d += step
                }
            } while (pm.nextContour())
        }

        // leaves: two, outlined by pen, filled dark
        val side = if (seed % 2 == 0) 1f else -1f
        for ((i, s) in floatArrayOf(side, -side).withIndex()) {
            val ly = b.yBot - (if (i == 0) 0.32f else 0.1f)
            val size = if (i == 0) 0.7f else 0.45f
            c.save(); c.translate(s * b.widthAt(ly) * 0.7f, ly); c.rotate(if (s > 0) -25f + i * 40f else 205f - i * 40f)
            for (k in intArrayOf(-2, 2, -1, 1, 0)) {
                if (i == 1 && abs(k) == 2) continue
                c.save(); c.rotate(k * 32f)
                ArtKit.lance(path, size * (1f - 0.15f * abs(k)), 0.11f)
                p.style = Paint.Style.FILL; p.color = leafFill; c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.color = ink; pen(path, 0.035f, 0.01f)
                c.drawLine(0.03f, 0f, size * (1f - 0.15f * abs(k)) * 0.75f, 0f, p.apply { strokeWidth = 0.02f })
                c.restore()
            }
            c.restore()
        }

        p.style = Paint.Style.FILL; p.color = fill
        c.drawPath(b.sil, p)
        // hatching on the lower right
        c.save(); c.clipPath(b.sil)
        p.style = Paint.Style.STROKE; p.color = Gfx.alpha(ink, 0.55f); p.strokeWidth = 0.022f
        val cy = (b.yTop + b.yBot) / 2f
        var k = -0.2f
        while (k < 1.6f) {
            val x0 = 0.1f + k; val y0 = b.yBot
            c.drawLine(x0, y0, x0 + 0.5f, y0 - 0.5f - k * 0.1f, p)
            k += 0.09f
        }
        // keep the hatching only near the edge: paint the inner area back
        p.style = Paint.Style.FILL; p.color = fill
        c.save(); c.translate(-0.12f, -0.12f); c.scale(0.9f, 0.9f, 0f, cy); c.drawPath(b.sil, p); c.restore()
        c.restore()

        // creases: three short pen ticks inside
        p.style = Paint.Style.STROKE; p.color = ink
        val oval = RectF()
        for (l in b.lumps.filter { it[1] > b.yTop + 0.2f }.shuffled(rnd).take(4)) {
            val rr = l[2] * 0.75f
            path.reset(); oval.set(l[0] - rr, l[1] - rr, l[0] + rr, l[1] + rr)
            path.addArc(oval, 25f + rnd.nextFloat() * 30f, 60f + rnd.nextFloat() * 30f)
            pen(path, 0.03f, 0.006f)
        }
        // the outline, with one small break
        pen(b.sil, 0.06f, 0.014f, gapAt = rnd.nextFloat())
        // a couple of pistils as quick flicks
        p.color = if (brown) 0xFF6E4C2C.toInt() else 0xFFC98A4A.toInt()
        for (l in b.lumps.filter { it[1] < cy }.shuffled(rnd).take(2)) {
            val dir = if (l[0] >= 0) 1f else -1f
            path.reset(); path.moveTo(l[0], l[1] - l[2] * 0.6f)
            path.quadTo(l[0] + dir * 0.12f, l[1] - l[2] * 0.6f - 0.12f, l[0] + dir * 0.2f, l[1] - l[2] * 0.6f - 0.08f)
            pen(path, 0.026f, 0.004f)
        }
    }
}
