package com.terpenesalad.nugofwar

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate
import java.awt.AWTEvent
import java.awt.Color as AwtColor
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "--shots" -> { Headless.shots(File(args.getOrElse(1) { "shots" })); return }
        "--icons" -> { Headless.icons(File(args.getOrElse(1) { "icons" })); return }
    }
    SwingUtilities.invokeLater { DesktopApp().start() }
}

private fun settingsFile(): File {
    val appData = System.getenv("APPDATA")
    val dir = if (!appData.isNullOrBlank()) File(appData, "NugOfWar") else File(System.getProperty("user.home"), ".nugofwar")
    return File(dir, "settings.properties")
}

/** Renders one Citrusquest nug, used for the window / exe icon. */
private fun iconPng(size: Int): ByteArray {
    val surf = Surface.makeRasterN32Premul(size, size)
    val c = android.graphics.Canvas(surf.canvas)
    val r = size * 0.3f
    Gfx.nug(c, size / 2f, size * 0.46f, r, Look.of(Game.COLORS[0], 0, 11, 0), 0.3f, 0.1f, 0f, false, 0f, 0, shadow = false)
    return surf.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
}

class DesktopApp {
    private val frame = JFrame("Nug of War")
    private val layer = SkiaLayer()
    private val input = InputHub()
    private val sound = Sound()
    private val prefs = FilePrefs(settingsFile())
    private val game = Game(input, sound, prefs) { quit() }
    private var last = 0L
    private var fullscreen = true
    private var cursorHidden = false
    private val blankCursor: Cursor = Toolkit.getDefaultToolkit().createCustomCursor(
        BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank")

    fun start() {
        layer.renderDelegate = object : SkikoRenderDelegate {
            override fun onRender(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
                frameTick(canvas, width, height, nanoTime)
            }
        }
        frame.contentPane.background = AwtColor.BLACK
        frame.background = AwtColor.BLACK
        layer.attachTo(frame.contentPane as javax.swing.JComponent)
        frame.defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = quit()
            override fun windowDeactivated(e: WindowEvent) { input.clearKeys() }
        })
        try { frame.iconImage = ImageIO.read(ByteArrayInputStream(iconPng(128))) } catch (_: Throwable) {}

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { e ->
            when (e.id) {
                KeyEvent.KEY_PRESSED ->
                    if (e.keyCode == KeyEvent.VK_F11 || (e.keyCode == KeyEvent.VK_ENTER && e.isAltDown)) toggleFullscreen()
                    else input.keyDown(e.keyCode)
                KeyEvent.KEY_RELEASED -> input.keyUp(e.keyCode)
            }
            false
        }
        Toolkit.getDefaultToolkit().addAWTEventListener({ ev ->
            val e = ev as? MouseEvent ?: return@addAWTEventListener
            val p = SwingUtilities.convertPoint(e.component, e.point, frame.contentPane)
            when (e.id) {
                MouseEvent.MOUSE_PRESSED -> input.mousePressed(p.x.toFloat(), p.y.toFloat())
                MouseEvent.MOUSE_DRAGGED -> input.mouseDragged(p.x.toFloat(), p.y.toFloat())
                MouseEvent.MOUSE_RELEASED -> input.mouseReleased()
                MouseEvent.MOUSE_MOVED -> input.lastMouseMs = System.currentTimeMillis()
            }
        }, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)

        applyWindowMode()
        frame.isVisible = true
        layer.needRedraw()
    }

    private fun applyWindowMode() {
        val wasVisible = frame.isVisible
        if (wasVisible) frame.dispose()
        frame.isUndecorated = fullscreen
        if (fullscreen) {
            val screen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
            frame.bounds = screen
        } else {
            frame.minimumSize = Dimension(640, 360)
            frame.setSize(1280, 760)
            frame.setLocationRelativeTo(null)
        }
        if (wasVisible) frame.isVisible = true
    }

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        applyWindowMode()
    }

    private fun frameTick(canvas: org.jetbrains.skia.Canvas, width: Int, height: Int, nanoTime: Long) {
        val dt = if (last == 0L) 1f / 60f else ((nanoTime - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = nanoTime
        val cs = layer.contentScale
        val scale = minOf(width / Game.W, height / Game.H)
        val offX = (width - Game.W * scale) / 2f
        val offY = (height - Game.H * scale) / 2f
        input.setTransform(scale / cs, offX / cs, offY / cs)

        input.poll()
        game.update(dt)

        canvas.clear(0xFF000000.toInt())
        val c = android.graphics.Canvas(canvas)
        c.save()
        c.translate(offX, offY)
        c.scale(scale, scale)
        c.clipRect(0f, 0f, Game.W, Game.H)
        game.draw(c)
        c.restore()

        // hide the mouse pointer when it isn't being used
        val idle = System.currentTimeMillis() - input.lastMouseMs > 2500
        if (idle != cursorHidden) {
            cursorHidden = idle
            frame.contentPane.cursor = if (idle) blankCursor else Cursor.getDefaultCursor()
        }
        layer.needRedraw()
    }

    private fun quit() {
        try { input.release() } catch (_: Throwable) {}
        try { sound.release() } catch (_: Throwable) {}
        try { layer.dispose() } catch (_: Throwable) {}
        frame.dispose()
        exitProcess(0)
    }
}

/** Off-screen rendering for checks on build machines (no window, no audio, no controllers). */
object Headless {
    private fun save(s: Surface, f: File) {
        f.parentFile?.mkdirs()
        f.writeBytes(s.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }

    fun icons(dir: File) {
        dir.mkdirs()
        for (s in intArrayOf(256, 64, 48, 32, 16)) File(dir, "icon_$s.png").writeBytes(iconPng(s))
    }

    fun shots(dir: File) {
        dir.mkdirs()
        val surf = Surface.makeRasterN32Premul(1920, 1080)
        val canvas = android.graphics.Canvas(surf.canvas)

        // every art style, side by side
        canvas.drawColor(0xFF000000.toInt())
        for (st in 0..3) for (k in 0..3) {
            val col = if (k == 3) Game.BAD_COLORS[0] else Game.COLORS[k]
            Gfx.label(canvas, Look.STYLE_NAMES[st].uppercase(), 40f, 175f + st * 255f, 40f, 0xFFAAAAAA.toInt(), android.graphics.Paint.Align.LEFT, shadow = false)
            Gfx.nug(canvas, 520f + k * 400f, 150f + st * 255f, 82f, Look.of(col, k, 11 + k, st), 0.4f, 0.2f, 0f, false, 0f, if (k == 3) 1 else 0)
        }
        save(surf, File(dir, "desktop_00_styles.png"))

        val input = InputHub(useControllers = false)
        val prefsFile = File(dir, "prefs.properties"); prefsFile.delete()
        val prefs = FilePrefs(prefsFile)
        prefs.edit().putBoolean("projector", false).commit()
        val game = Game(input, Sound(enabledOutput = false), prefs) {}
        var f = 0
        fun clear(c: Ctl) {
            c.jumpP = false; c.attackP = false; c.blockP = false; c.throwP = false; c.confirmP = false
            c.backP = false; c.startP = false; c.upP = false; c.downP = false; c.leftP = false; c.rightP = false
            c.mx = 0f; c.my = 0f; c.jump = false; c.attack = false; c.block = false
        }
        fun frames(n: Int, setup: (Array<Ctl>, Int) -> Unit = { _, _ -> }) {
            repeat(n) { input.ctl.forEach { clear(it) }; setup(input.ctl, f); game.update(1f / 60f); f++ }
        }
        fun shot(name: String) { canvas.drawColor(0xFF000000.toInt()); game.draw(canvas); save(surf, File(dir, name)) }

        frames(30); shot("desktop_01_title.png")
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(5)
        frames(1) { c, _ -> c[0].confirmP = true }
        frames(1) { c, _ -> c[1].confirmP = true }
        frames(10); shot("desktop_02_lobby.png")
        frames(1) { c, _ -> c[0].confirmP = true; c[1].confirmP = true }
        frames(150)
        val script: (Array<Ctl>, Int) -> Unit = { c, fr ->
            c[0].mx = if ((fr / 50) % 3 == 0) -1f else 1f
            c[0].attack = fr % 20 < 2; c[0].attackP = fr % 20 == 0
            c[0].jump = fr % 70 < 10; c[0].jumpP = fr % 70 == 0
            c[1].mx = if ((fr / 40) % 2 == 0) -1f else 1f
            c[1].jumpP = fr % 45 == 0; c[1].jump = fr % 45 < 12
            c[1].attack = fr % 25 < 2; c[1].attackP = fr % 25 == 3
        }
        for (k in 0 until 2) { frames(200, script); shot("desktop_03_brawl_$k.png") }

        // time a few hundred frames to make sure drawing is quick enough
        val t0 = System.nanoTime()
        repeat(120) { frames(1, script); canvas.drawColor(0xFF000000.toInt()); game.draw(canvas) }
        val ms = (System.nanoTime() - t0) / 1e6 / 120
        File(dir, "desktop_timing.txt").writeText("avg frame (update + software draw): %.2f ms\n".format(ms))
        println("avg frame %.2f ms".format(ms))
    }
}
