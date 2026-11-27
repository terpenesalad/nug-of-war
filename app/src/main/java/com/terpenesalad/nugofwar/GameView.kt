package com.terpenesalad.nugofwar

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Hosts the game loop on its own thread and draws to a fixed 1920x1080 virtual canvas. */
@SuppressLint("ViewConstructor")
class GameView(private val activity: Activity) : SurfaceView(activity), SurfaceHolder.Callback, Runnable {

    val input = InputHub(activity)
    private val sound = Sound(activity)
    private val game = Game(input, sound, activity.getSharedPreferences("nugofwar", 0)) { activity.runOnUiThread { activity.finish() } }

    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    private var thread: Thread? = null

    private var scale = 1f
    private var offX = 0f
    private var offY = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        scale = minOf(width / Game.W, height / Game.H)
        offX = (width - Game.W * scale) / 2f
        offY = (height - Game.H * scale) / 2f
        input.setTransform(scale, offX, offY)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopLoop()
    }

    fun onHostPause() {
        game.onHostPause()
        stopLoop()
    }

    fun onHostResume() {
        if (surfaceReady) startLoop()
    }

    fun release() {
        stopLoop()
        input.release()
        sound.release()
    }

    private fun startLoop() {
        if (running) return
        running = true
        thread = Thread(this, "game-loop").also { it.start() }
    }

    private fun stopLoop() {
        running = false
        try { thread?.join(500) } catch (_: InterruptedException) {}
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 1f / 30f) dt = 1f / 30f
            input.poll()
            game.update(dt)
            drawFrame()
            val spent = (System.nanoTime() - now) / 1_000_000L
            if (spent < 8) {
                try { Thread.sleep(8 - spent) } catch (_: InterruptedException) {}
            }
        }
    }

    private fun drawFrame() {
        if (!surfaceReady) return
        val h = holder
        var c: Canvas? = null
        try {
            c = if (Build.VERSION.SDK_INT >= 26) h.lockHardwareCanvas() else h.lockCanvas()
            if (c == null) return
            c.drawColor(Color.BLACK)
            c.save()
            c.translate(offX, offY)
            c.scale(scale, scale)
            c.clipRect(0f, 0f, Game.W, Game.H)
            game.draw(c)
            c.restore()
        } catch (_: Exception) {
        } finally {
            if (c != null) {
                try { h.unlockCanvasAndPost(c) } catch (_: Exception) {}
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        input.onTouch(event)
        return true
    }
}
