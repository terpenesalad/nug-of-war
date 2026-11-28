@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package android.graphics

/*
 * A small stand-in for the parts of android.graphics the game uses, drawn with Skia (Skiko).
 * Android's Canvas is itself Skia, so the shared game code renders the same on Windows.
 */

import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathOp
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect as SkRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

object Color {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val TRANSPARENT = 0
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val GRAY = 0xFF888888.toInt()
    const val DKGRAY = 0xFF444444.toInt()
    const val LTGRAY = 0xFFCCCCCC.toInt()
    const val YELLOW = 0xFFFFFF00.toInt()

    @JvmStatic fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    @JvmStatic fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    @JvmStatic fun alpha(c: Int) = (c ushr 24) and 0xFF
    @JvmStatic fun red(c: Int) = (c shr 16) and 0xFF
    @JvmStatic fun green(c: Int) = (c shr 8) and 0xFF
    @JvmStatic fun blue(c: Int) = c and 0xFF
}

class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) / 2f
    fun centerY() = (top + bottom) / 2f
    internal fun sk() = SkRect.makeLTRB(left, top, right, bottom)
}

class Rect(@JvmField var left: Int = 0, @JvmField var top: Int = 0, @JvmField var right: Int = 0, @JvmField var bottom: Int = 0) {
    fun set(l: Int, t: Int, r: Int, b: Int) { left = l; top = t; right = r; bottom = b }
}

class Matrix {
    internal var m: Matrix33 = Matrix33.IDENTITY
    fun reset() { m = Matrix33.IDENTITY }
    fun setRotate(deg: Float) { m = Matrix33.makeRotate(deg) }
    fun setTranslate(x: Float, y: Float) { m = Matrix33.makeTranslate(x, y) }
    fun postTranslate(x: Float, y: Float): Boolean { m = Matrix33.makeTranslate(x, y).makeConcat(m); return true }
    fun postRotate(deg: Float): Boolean { m = Matrix33.makeRotate(deg).makeConcat(m); return true }
    fun postScale(sx: Float, sy: Float): Boolean { m = Matrix33.makeScale(sx, sy).makeConcat(m); return true }
}

open class ColorFilter internal constructor(internal val sk: org.jetbrains.skia.ColorFilter)

class LightingColorFilter(mul: Int, add: Int) :
    ColorFilter(org.jetbrains.skia.ColorFilter.makeLighting(mul, add))

class Typeface private constructor(internal val sk: org.jetbrains.skia.Typeface?) {
    companion object {
        const val NORMAL = 0
        const val BOLD = 1
        const val ITALIC = 2
        const val BOLD_ITALIC = 3
        @JvmField val DEFAULT = Typeface(find(false))
        @JvmField val DEFAULT_BOLD = Typeface(find(true))

        @JvmStatic fun create(family: Typeface?, style: Int): Typeface =
            if (style and BOLD != 0) DEFAULT_BOLD else DEFAULT

        private fun find(bold: Boolean): org.jetbrains.skia.Typeface? {
            val st = if (bold) FontStyle.BOLD else FontStyle.NORMAL
            val mgr = FontMgr.default
            for (name in arrayOf("Segoe UI", "Roboto", "Helvetica Neue", "Arial", "Liberation Sans", "DejaVu Sans", "Noto Sans")) {
                val tf = try { mgr.matchFamilyStyle(name, st) } catch (_: Throwable) { null }
                if (tf != null) return tf
            }
            return try { mgr.legacyMakeTypeface("", st) } catch (_: Throwable) { null }
        }
    }
}

open class Paint(flags: Int = 0) {
    companion object {
        const val ANTI_ALIAS_FLAG = 1
        const val FILTER_BITMAP_FLAG = 2
    }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Align { LEFT, CENTER, RIGHT }

    var color: Int = Color.BLACK
    var alpha: Int
        get() = Color.alpha(color)
        set(v) { color = (color and 0x00FFFFFF) or (v.coerceIn(0, 255) shl 24) }
    var style = Style.FILL
    var strokeWidth = 0f
    var strokeCap = Cap.BUTT
    var strokeJoin = Join.MITER
    var textSize = 12f
    var textAlign = Align.LEFT
    var typeface: Typeface? = null
    var colorFilter: ColorFilter? = null
    var isAntiAlias = flags and ANTI_ALIAS_FLAG != 0

    private val sk = org.jetbrains.skia.Paint()
    private var font: Font? = null
    private var fontSize = -1f
    private var fontFace: Typeface? = null

    internal fun skia(): org.jetbrains.skia.Paint {
        sk.isAntiAlias = isAntiAlias
        sk.color = color
        sk.mode = when (style) { Style.FILL -> PaintMode.FILL; Style.STROKE -> PaintMode.STROKE; Style.FILL_AND_STROKE -> PaintMode.STROKE_AND_FILL }
        sk.strokeWidth = strokeWidth
        sk.strokeCap = when (strokeCap) { Cap.BUTT -> PaintStrokeCap.BUTT; Cap.ROUND -> PaintStrokeCap.ROUND; Cap.SQUARE -> PaintStrokeCap.SQUARE }
        sk.strokeJoin = when (strokeJoin) { Join.MITER -> PaintStrokeJoin.MITER; Join.ROUND -> PaintStrokeJoin.ROUND; Join.BEVEL -> PaintStrokeJoin.BEVEL }
        sk.colorFilter = colorFilter?.sk
        return sk
    }

    internal fun font(): Font {
        val f = font
        if (f != null && fontSize == textSize && fontFace === typeface) return f
        val tf = (typeface ?: Typeface.DEFAULT).sk
        return Font(tf, textSize).also { it.isSubpixel = true; font = it; fontSize = textSize; fontFace = typeface }
    }

    fun measureText(s: String): Float = font().measureTextWidth(s)
}

class Path() {
    enum class Direction { CW, CCW }
    enum class Op { DIFFERENCE, INTERSECT, UNION, XOR, REVERSE_DIFFERENCE }

    // Skia paths are immutable now: build with a PathBuilder and snapshot when drawn
    private var b = PathBuilder()
    private var snap: org.jetbrains.skia.Path? = null

    internal val sk: org.jetbrains.skia.Path
        get() = snap ?: b.snapshot().also { snap = it }

    private inline fun edit(f: PathBuilder.() -> Unit) { b.f(); snap = null }
    private fun dir(d: Direction) = if (d == Direction.CW) PathDirection.CLOCKWISE else PathDirection.COUNTER_CLOCKWISE

    constructor(src: Path) : this() { set(src) }

    fun set(src: Path) { b = PathBuilder(src.sk); snap = null }
    fun reset() = edit { reset() }
    fun rewind() = edit { reset() }
    fun moveTo(x: Float, y: Float) = edit { moveTo(x, y) }
    fun lineTo(x: Float, y: Float) = edit { lineTo(x, y) }
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = edit { quadTo(x1, y1, x2, y2) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = edit { cubicTo(x1, y1, x2, y2, x3, y3) }
    fun close() = edit { closePath() }
    fun addCircle(x: Float, y: Float, r: Float, d: Direction) = edit { addCircle(x, y, r, dir(d)) }
    fun addArc(oval: RectF, startAngle: Float, sweepAngle: Float) = edit { addArc(oval.sk(), startAngle, sweepAngle) }
    fun addRect(l: Float, t: Float, r: Float, bt: Float, d: Direction) = edit { addRect(l, t, r, bt, dir(d), 0) }
    fun transform(m: Matrix) = edit { transform(m.m) }
    fun op(path: Path, op: Op): Boolean {
        val o = when (op) {
            Op.DIFFERENCE -> PathOp.DIFFERENCE; Op.INTERSECT -> PathOp.INTERSECT; Op.UNION -> PathOp.UNION
            Op.XOR -> PathOp.XOR; Op.REVERSE_DIFFERENCE -> PathOp.REVERSE_DIFFERENCE
        }
        val r = org.jetbrains.skia.Path.makeCombining(sk, path.sk, o) ?: return false
        b = PathBuilder(r); snap = r
        return true
    }
}

class PathMeasure(path: Path, forceClosed: Boolean) {
    private val sk = org.jetbrains.skia.PathMeasure(path.sk, forceClosed)
    val length: Float get() = sk.length
    fun getPosTan(distance: Float, pos: FloatArray?, tan: FloatArray?): Boolean {
        val p = sk.getPosition(distance) ?: return false
        val t = sk.getTangent(distance) ?: return false
        if (pos != null) { pos[0] = p.x; pos[1] = p.y }
        if (tan != null) { tan[0] = t.x; tan[1] = t.y }
        return true
    }
    fun nextContour(): Boolean = sk.nextContour()
}

class Bitmap private constructor(val width: Int, val height: Int) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

    internal val surface: Surface = Surface.makeRasterN32Premul(width, height)
    internal var dirty = true
    private var img: Image? = null

    internal fun image(): Image {
        val i = img
        if (i != null && !dirty) return i
        return surface.makeImageSnapshot().also { img = it; dirty = false }
    }

    companion object {
        @JvmStatic fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(width, height)
    }
}

class Canvas(internal val sk: org.jetbrains.skia.Canvas, private val owner: Bitmap? = null) {
    constructor(bitmap: Bitmap) : this(bitmap.surface.canvas, bitmap)

    private val tmpPaint = org.jetbrains.skia.Paint()
    private fun touch() { owner?.dirty = true }

    fun save(): Int = sk.save()
    fun restore() { sk.restore() }
    fun restoreToCount(n: Int) { sk.restoreToCount(n) }
    fun translate(dx: Float, dy: Float) { sk.translate(dx, dy) }
    fun scale(sx: Float, sy: Float) { sk.scale(sx, sy) }
    fun scale(sx: Float, sy: Float, px: Float, py: Float) { sk.translate(px, py); sk.scale(sx, sy); sk.translate(-px, -py) }
    fun rotate(deg: Float) { sk.rotate(deg) }
    fun rotate(deg: Float, px: Float, py: Float) { sk.translate(px, py); sk.rotate(deg); sk.translate(-px, -py) }
    fun clipPath(path: Path): Boolean { sk.clipPath(path.sk, ClipMode.INTERSECT, true); return true }
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { sk.clipRect(SkRect.makeLTRB(l, t, r, b), ClipMode.INTERSECT, true); return true }

    fun drawColor(color: Int) { touch(); tmpPaint.color = color; sk.drawPaint(tmpPaint) }
    fun drawCircle(cx: Float, cy: Float, r: Float, p: Paint) { touch(); sk.drawCircle(cx, cy, r, p.skia()) }
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, p: Paint) { touch(); sk.drawLine(x0, y0, x1, y1, p.skia()) }
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) { touch(); sk.drawRect(SkRect.makeLTRB(l, t, r, b), p.skia()) }
    fun drawRect(r: RectF, p: Paint) = drawRect(r.left, r.top, r.right, r.bottom, p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) {
        touch(); sk.drawRRect(RRect.makeLTRB(l, t, r, b, rx, ry), p.skia())
    }
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, p)
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) { touch(); sk.drawOval(SkRect.makeLTRB(l, t, r, b), p.skia()) }
    fun drawOval(r: RectF, p: Paint) = drawOval(r.left, r.top, r.right, r.bottom, p)
    fun drawArc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float, useCenter: Boolean, p: Paint) {
        touch(); sk.drawArc(l, t, r, b, start, sweep, useCenter, p.skia())
    }
    fun drawArc(oval: RectF, start: Float, sweep: Float, useCenter: Boolean, p: Paint) =
        drawArc(oval.left, oval.top, oval.right, oval.bottom, start, sweep, useCenter, p)
    fun drawPath(path: Path, p: Paint) { touch(); sk.drawPath(path.sk, p.skia()) }
    fun drawText(s: String, x: Float, y: Float, p: Paint) {
        touch()
        val f = p.font()
        val dx = when (p.textAlign) {
            Paint.Align.LEFT -> 0f
            Paint.Align.CENTER -> -f.measureTextWidth(s) / 2f
            Paint.Align.RIGHT -> -f.measureTextWidth(s)
        }
        sk.drawString(s, x + dx, y, f, p.skia())
    }
    fun drawBitmap(bmp: Bitmap, src: Rect?, dst: RectF, p: Paint?) {
        touch()
        val s = if (src == null) SkRect.makeWH(bmp.width.toFloat(), bmp.height.toFloat())
                else SkRect.makeLTRB(src.left.toFloat(), src.top.toFloat(), src.right.toFloat(), src.bottom.toFloat())
        sk.drawImageRect(bmp.image(), s, dst.sk(), SamplingMode.LINEAR, p?.skia(), true)
    }
}
