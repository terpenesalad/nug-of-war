package com.terpenesalad.nugofwar

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.sqrt

/** Per-player controls for one frame. Edge fields ("...P") are true only on the frame a button went down. */
class Ctl {
    var mx = 0f; var my = 0f
    var ax = 0f; var ay = 0f
    var jump = false; var jumpP = false
    var attack = false; var attackP = false
    var block = false; var blockP = false
    var throwP = false
    var confirmP = false
    var backP = false
    var startP = false
    var upP = false; var downP = false; var leftP = false; var rightP = false
    var hasPad = false
    var padName = ""

    internal var pJump = false; internal var pAttack = false; internal var pBlock = false; internal var pThrow = false
    internal var pConfirm = false; internal var pBack = false
    internal var pStart = false; internal var pUp = false; internal var pDown = false
    internal var pLeft = false; internal var pRight = false
}

private class Pad(val deviceId: Int) {
    var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
    var hx = 0f; var hy = 0f; var lt = 0f; var rt = 0f
    val keys = HashSet<Int>()
    var name = "Controller"
}

private class TouchPtr(val role: Int, val player: Int, val ox: Float, val oy: Float) {
    var x = ox; var y = oy
}

/**
 * Collects input from Bluetooth/USB gamepads, keyboards, TV remotes and touch,
 * and turns it into two [Ctl] snapshots (one per player).
 *
 * Gamepads claim a player slot the first time they press a button, in order:
 * the first controller to press anything is Player 1, the next is Player 2.
 */
class InputHub(context: Context) : InputManager.InputDeviceListener {
    companion object {
        const val ROLE_STICK = 0
        const val ROLE_JUMP = 1
        const val ROLE_HIT = 2
        const val ROLE_BLOCK = 3
        const val PLAYERS = 4
        // Touch layout (virtual 1920x1080 coordinates), for player 1; player 2 is mirrored
        val BTN_X = floatArrayOf(0f, 830f, 660f, 720f)
        val BTN_Y = floatArrayOf(0f, 930f, 990f, 810f)
        val BTN_R = floatArrayOf(0f, 95f, 70f, 58f)
        val BTN_LABEL = arrayOf("", "JUMP", "HIT", "BLOCK")
        fun btnX(p: Int, role: Int) = if (p == 0) BTN_X[role] else Game.W - BTN_X[role]
        const val STICK_RANGE = 120f
        private const val DEAD = 0.22f
    }

    private val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val lock = Any()
    private val pads = HashMap<Int, Pad>()
    val slot = intArrayOf(-1, -1, -1, -1)      // deviceId per player slot
    private val keys = HashSet<Int>()  // keyboard / remote keys held
    private val touches = HashMap<Int, TouchPtr>()
    private val taps = ArrayList<FloatArray>()
    private var tScale = 1f; private var tOffX = 0f; private var tOffY = 0f
    @Volatile var lastTouchMs = 0L
    @Volatile var disconnectFlag = false

    val ctl = Array(PLAYERS) { Ctl() }

    init {
        im.registerInputDeviceListener(this, null)
    }

    fun release() { im.unregisterInputDeviceListener(this) }

    fun setTransform(scale: Float, offX: Float, offY: Float) {
        synchronized(lock) { tScale = scale; tOffX = offX; tOffY = offY }
    }

    fun touchRecently(): Boolean = SystemClock.uptimeMillis() - lastTouchMs < 20000 && lastTouchMs != 0L

    fun takeTaps(): List<FloatArray> = synchronized(lock) {
        val r = ArrayList(taps); taps.clear(); r
    }

    fun resetSlots() = synchronized(lock) { for (i in slot.indices) slot[i] = -1 }

    fun slotLabel(p: Int): String = synchronized(lock) {
        val id = slot[p]
        if (id < 0) "" else pads[id]?.name ?: "Controller"
    }

    // ---------------------------------------------------------------- devices

    private fun isPadDevice(dev: InputDevice?, keyCode: Int): Boolean {
        if (dev == null) return false
        val s = dev.sources
        val joystick = (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        val gamepad = (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        return joystick || (gamepad && KeyEvent.isGamepadButton(keyCode))
    }

    private fun padFor(dev: InputDevice, claim: Boolean): Pad? {
        val id = dev.id
        val pad = pads.getOrPut(id) { Pad(id).also { it.name = dev.name ?: "Controller" } }
        if (claim && slot.none { it == id }) {
            val free = slot.indexOfFirst { it < 0 }
            if (free >= 0) slot[free] = id
        }
        return pad
    }

    override fun onInputDeviceAdded(deviceId: Int) {}
    override fun onInputDeviceChanged(deviceId: Int) {}
    override fun onInputDeviceRemoved(deviceId: Int) {
        synchronized(lock) {
            pads.remove(deviceId)
            for (i in slot.indices) if (slot[i] == deviceId) { slot[i] = -1; disconnectFlag = true }
        }
    }

    // ---------------------------------------------------------------- events

    fun onKey(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN ||
            code == KeyEvent.KEYCODE_VOLUME_MUTE || code == KeyEvent.KEYCODE_HOME ||
            code == KeyEvent.KEYCODE_POWER) return false
        val down = when (e.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        synchronized(lock) {
            val dev = e.device
            if (isPadDevice(dev, code)) {
                val pad = padFor(dev!!, claim = down) ?: return true
                if (down) pad.keys.add(code) else pad.keys.remove(code)
                return true
            }
            if (down) keys.add(code) else keys.remove(code)
        }
        return true
    }

    fun onMotion(e: MotionEvent): Boolean {
        val src = e.source
        if ((src and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK ||
            e.action != MotionEvent.ACTION_MOVE) return false
        val dev = e.device ?: return false
        synchronized(lock) {
            val lx = axis(e, dev, MotionEvent.AXIS_X)
            val ly = axis(e, dev, MotionEvent.AXIS_Y)
            val useZ = dev.getMotionRange(MotionEvent.AXIS_Z, src) != null
            val rx = axis(e, dev, if (useZ) MotionEvent.AXIS_Z else MotionEvent.AXIS_RX)
            val ry = axis(e, dev, if (useZ) MotionEvent.AXIS_RZ else MotionEvent.AXIS_RY)
            val hx = axis(e, dev, MotionEvent.AXIS_HAT_X)
            val hy = axis(e, dev, MotionEvent.AXIS_HAT_Y)
            val rt = maxOf(axis(e, dev, MotionEvent.AXIS_RTRIGGER), axis(e, dev, MotionEvent.AXIS_GAS))
            val lt = maxOf(axis(e, dev, MotionEvent.AXIS_LTRIGGER), axis(e, dev, MotionEvent.AXIS_BRAKE))
            val strong = abs(lx) > 0.6f || abs(ly) > 0.6f || abs(hx) > 0.5f || abs(hy) > 0.5f || rt > 0.6f
            val pad = padFor(dev, claim = strong) ?: return true
            pad.lx = lx; pad.ly = ly; pad.rx = rx; pad.ry = ry
            pad.hx = hx; pad.hy = hy; pad.lt = lt; pad.rt = rt
        }
        return true
    }

    private fun axis(e: MotionEvent, dev: InputDevice, a: Int): Float {
        val range = dev.getMotionRange(a, e.source) ?: return 0f
        val v = e.getAxisValue(a)
        return if (abs(v) > range.flat) v else 0f
    }

    fun onTouch(e: MotionEvent) {
        lastTouchMs = SystemClock.uptimeMillis()
        synchronized(lock) {
            val idx = e.actionIndex
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val x = (e.getX(idx) - tOffX) / tScale
                    val y = (e.getY(idx) - tOffY) / tScale
                    taps.add(floatArrayOf(x, y))
                    touches[e.getPointerId(idx)] = classify(x, y)
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        val t = touches[e.getPointerId(i)] ?: continue
                        t.x = (e.getX(i) - tOffX) / tScale
                        t.y = (e.getY(i) - tOffY) / tScale
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> touches.remove(e.getPointerId(idx))
                MotionEvent.ACTION_CANCEL -> touches.clear()
            }
        }
    }

    private fun classify(x: Float, y: Float): TouchPtr {
        val p = if (x < Game.W / 2) 0 else 1
        val inner = if (p == 0) x > 520f else x < Game.W - 520f
        if (!inner) return TouchPtr(ROLE_STICK, p, x, y)
        var best = ROLE_JUMP; var bd = Float.MAX_VALUE
        for (role in 1..3) {
            val d = dist(x, y, btnX(p, role), BTN_Y[role]) / BTN_R[role]
            if (d < bd) { bd = d; best = role }
        }
        return TouchPtr(best, p, x, y)
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    /** Touch stick vectors for drawing: [ox, oy, x, y] per player or null. */
    fun touchStick(p: Int): FloatArray? = synchronized(lock) {
        touches.values.firstOrNull { it.role == ROLE_STICK && it.player == p }?.let { floatArrayOf(it.ox, it.oy, it.x, it.y) }
    }

    /** Any one finger on screen as [downX, downY, x, y], used by the wall editor. */
    fun rawTouch(): FloatArray? = synchronized(lock) {
        touches.values.firstOrNull()?.let { floatArrayOf(it.ox, it.oy, it.x, it.y) }
    }

    fun touchHeld(p: Int, role: Int): Boolean = synchronized(lock) {
        touches.values.any { it.role == role && it.player == p }
    }

    // ---------------------------------------------------------------- polling

    private fun k(vararg codes: Int): Boolean = codes.any { keys.contains(it) }

    fun poll() {
        synchronized(lock) {
            for (p in 0 until PLAYERS) {
                val c = ctl[p]
                var mx = 0f; var my = 0f; var ax = 0f; var ay = 0f
                var jump = false; var attack = false; var block = false; var thr = false
                var confirm = false; var back = false; var start = false
                var nUp = false; var nDown = false; var nLeft = false; var nRight = false

                // keyboard / TV remote
                if (p == 0) {
                    if (k(KeyEvent.KEYCODE_A)) mx -= 1f
                    if (k(KeyEvent.KEYCODE_D)) mx += 1f
                    if (k(KeyEvent.KEYCODE_W)) my -= 1f
                    if (k(KeyEvent.KEYCODE_S)) my += 1f
                    jump = k(KeyEvent.KEYCODE_SPACE)
                    attack = k(KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_J)
                    block = k(KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_K)
                    thr = k(KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_L)
                    confirm = jump
                    back = k(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_DEL)
                    start = k(KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_MENU)
                } else if (p == 1) {
                    if (k(KeyEvent.KEYCODE_DPAD_LEFT)) mx -= 1f
                    if (k(KeyEvent.KEYCODE_DPAD_RIGHT)) mx += 1f
                    if (k(KeyEvent.KEYCODE_DPAD_UP)) my -= 1f
                    if (k(KeyEvent.KEYCODE_DPAD_DOWN)) my += 1f
                    jump = k(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_NUMPAD_0)
                    attack = k(KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_NUMPAD_1, KeyEvent.KEYCODE_PERIOD)
                    block = k(KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_NUMPAD_2, KeyEvent.KEYCODE_SLASH)
                    thr = k(KeyEvent.KEYCODE_NUMPAD_3, KeyEvent.KEYCODE_SEMICOLON)
                    confirm = jump
                }
                if (my < 0) nUp = true
                if (my > 0) nDown = true
                if (mx < 0) nLeft = true
                if (mx > 0) nRight = true

                // gamepad
                val pad = if (slot[p] >= 0) pads[slot[p]] else null
                c.hasPad = pad != null
                c.padName = pad?.name ?: ""
                if (pad != null) {
                    var px = pad.lx; var py = pad.ly
                    if (sqrt(px * px + py * py) < DEAD) { px = 0f; py = 0f }
                    if (pad.hx != 0f || pad.hy != 0f) { px = pad.hx; py = pad.hy }
                    val pk = pad.keys
                    if (pk.contains(KeyEvent.KEYCODE_DPAD_LEFT)) px = -1f
                    if (pk.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) px = 1f
                    if (pk.contains(KeyEvent.KEYCODE_DPAD_UP)) py = -1f
                    if (pk.contains(KeyEvent.KEYCODE_DPAD_DOWN)) py = 1f
                    mx += px; my += py
                    if (py < -0.55f) nUp = true
                    if (py > 0.55f) nDown = true
                    if (px < -0.55f) nLeft = true
                    if (px > 0.55f) nRight = true
                    if (sqrt(pad.rx * pad.rx + pad.ry * pad.ry) > 0.4f) { ax = pad.rx; ay = pad.ry }
                    val a = pk.contains(KeyEvent.KEYCODE_BUTTON_A) || pk.contains(KeyEvent.KEYCODE_DPAD_CENTER)
                    val b = pk.contains(KeyEvent.KEYCODE_BUTTON_B) || pk.contains(KeyEvent.KEYCODE_BACK)
                    jump = jump || a
                    attack = attack || pk.contains(KeyEvent.KEYCODE_BUTTON_X) || pk.contains(KeyEvent.KEYCODE_BUTTON_R1) ||
                            pk.contains(KeyEvent.KEYCODE_BUTTON_R2) || pad.rt > 0.4f
                    block = block || b || pk.contains(KeyEvent.KEYCODE_BUTTON_L1) ||
                            pk.contains(KeyEvent.KEYCODE_BUTTON_L2) || pad.lt > 0.4f
                    thr = thr || pk.contains(KeyEvent.KEYCODE_BUTTON_Y)
                    confirm = confirm || a
                    back = back || b
                    start = start || pk.contains(KeyEvent.KEYCODE_BUTTON_START)
                }

                // touch (players 1 and 2 only)
                for (t in touches.values) {
                    if (t.player != p) continue
                    when (t.role) {
                        ROLE_STICK -> {
                            var dx = (t.x - t.ox) / STICK_RANGE; var dy = (t.y - t.oy) / STICK_RANGE
                            val l = sqrt(dx * dx + dy * dy)
                            if (l > 1f) { dx /= l; dy /= l }
                            if (l > 0.15f) { mx += dx; my += dy }
                        }
                        ROLE_JUMP -> jump = true
                        ROLE_HIT -> attack = true
                        ROLE_BLOCK -> block = true
                    }
                }

                val ml = sqrt(mx * mx + my * my)
                if (ml > 1f) { mx /= ml; my /= ml }
                c.mx = mx; c.my = my; c.ax = ax; c.ay = ay
                c.jumpP = jump && !c.pJump; c.pJump = jump; c.jump = jump
                c.attackP = attack && !c.pAttack; c.pAttack = attack; c.attack = attack
                c.blockP = block && !c.pBlock; c.pBlock = block; c.block = block
                c.throwP = thr && !c.pThrow; c.pThrow = thr
                c.confirmP = confirm && !c.pConfirm; c.pConfirm = confirm
                c.backP = back && !c.pBack; c.pBack = back
                c.startP = start && !c.pStart; c.pStart = start
                c.upP = nUp && !c.pUp; c.pUp = nUp
                c.downP = nDown && !c.pDown; c.pDown = nDown
                c.leftP = nLeft && !c.pLeft; c.pLeft = nLeft
                c.rightP = nRight && !c.pRight; c.pRight = nRight
            }
        }
    }
}
