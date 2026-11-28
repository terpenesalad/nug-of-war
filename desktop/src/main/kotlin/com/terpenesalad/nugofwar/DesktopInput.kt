package com.terpenesalad.nugofwar

import com.studiohartman.jamepad.Configuration
import com.studiohartman.jamepad.ControllerManager
import java.awt.event.KeyEvent
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

/**
 * Desktop input: Xbox / PlayStation / Switch / 8BitDo controllers through SDL (Jamepad),
 * the keyboard, and the mouse (clicks work as taps, dragging works in Wall Setup).
 *
 * Controllers claim a player slot the first time they press a button, in order.
 */
class InputHub(useControllers: Boolean = true) {
    companion object {
        const val ROLE_STICK = 0
        const val ROLE_JUMP = 1
        const val ROLE_HIT = 2
        const val ROLE_BLOCK = 3
        const val PLAYERS = 4
        val BTN_X = floatArrayOf(0f, 830f, 660f, 720f)
        val BTN_Y = floatArrayOf(0f, 930f, 990f, 810f)
        val BTN_R = floatArrayOf(0f, 95f, 70f, 58f)
        val BTN_LABEL = arrayOf("", "JUMP", "HIT", "BLOCK")
        fun btnX(p: Int, role: Int) = if (p == 0) BTN_X[role] else Game.W - BTN_X[role]
        const val STICK_RANGE = 120f
        private const val DEAD = 0.22f
        private const val MAX_PADS = 4
    }

    private val lock = Any()
    val slot = intArrayOf(-1, -1, -1, -1)       // controller index per player slot
    private val keys = HashSet<Int>()
    private val taps = ArrayList<FloatArray>()
    private var mouseDown = false
    private var downX = 0f; private var downY = 0f; private var curX = 0f; private var curY = 0f
    private var tScale = 1f; private var tOffX = 0f; private var tOffY = 0f
    @Volatile var disconnectFlag = false
    @Volatile var lastMouseMs = 0L

    val ctl = Array(PLAYERS) { Ctl() }

    private val pads: ControllerManager? = if (!useControllers) null else try {
        ControllerManager(Configuration().apply { maxNumControllers = MAX_PADS }).also { it.initSDLGamepad() }
    } catch (t: Throwable) {
        System.err.println("Controllers unavailable: $t"); null
    }
    private val padNames = Array(MAX_PADS) { "Controller" }

    fun release() { try { pads?.quitSDLGamepad() } catch (_: Throwable) {} }

    /** Window pixels -> game coordinates. */
    fun setTransform(scale: Float, offX: Float, offY: Float) {
        synchronized(lock) { tScale = scale; tOffX = offX; tOffY = offY }
    }

    fun touchRecently(): Boolean = false
    fun touchStick(p: Int): FloatArray? = null
    fun touchHeld(p: Int, role: Int): Boolean = false

    fun takeTaps(): List<FloatArray> = synchronized(lock) { val r = ArrayList(taps); taps.clear(); r }

    /** The mouse while a button is held, as [downX, downY, x, y]; used by Wall Setup. */
    fun rawTouch(): FloatArray? = synchronized(lock) { if (mouseDown) floatArrayOf(downX, downY, curX, curY) else null }

    fun resetSlots() = synchronized(lock) { for (i in slot.indices) slot[i] = -1 }

    fun slotLabel(p: Int): String = synchronized(lock) {
        val id = slot[p]
        if (id < 0) "" else padNames[id]
    }

    // ---------------------------------------------------------------- events (from the UI thread)

    fun keyDown(code: Int) = synchronized(lock) { keys.add(code) }
    fun keyUp(code: Int) = synchronized(lock) { keys.remove(code) }
    fun clearKeys() = synchronized(lock) { keys.clear() }

    fun mousePressed(px: Float, py: Float) = synchronized(lock) {
        val x = (px - tOffX) / tScale; val y = (py - tOffY) / tScale
        mouseDown = true; downX = x; downY = y; curX = x; curY = y
        taps.add(floatArrayOf(x, y))
        lastMouseMs = System.currentTimeMillis()
    }
    fun mouseDragged(px: Float, py: Float) = synchronized(lock) {
        curX = (px - tOffX) / tScale; curY = (py - tOffY) / tScale
        lastMouseMs = System.currentTimeMillis()
    }
    fun mouseReleased() = synchronized(lock) { mouseDown = false }

    // ---------------------------------------------------------------- polling (game thread)

    private class PadState {
        var connected = false
        var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f; var lt = 0f; var rt = 0f
        var a = false; var b = false; var x = false; var y = false; var lb = false; var rb = false
        var start = false; var back = false
        var up = false; var down = false; var left = false; var right = false
        fun any() = a || b || x || y || lb || rb || start || back || up || down || left || right || lt > 0.5f || rt > 0.5f
    }
    private val padState = Array(MAX_PADS) { PadState() }

    private fun readPads() {
        val m = pads ?: return
        try { m.update() } catch (_: Throwable) {}
        for (i in 0 until MAX_PADS) {
            val s = padState[i]
            val st = try { m.getState(i) } catch (_: Throwable) { null }
            if (st == null || !st.isConnected) { s.connected = false; continue }
            s.connected = true
            s.lx = st.leftStickX; s.ly = -st.leftStickY
            s.rx = st.rightStickX; s.ry = -st.rightStickY
            s.lt = st.leftTrigger; s.rt = st.rightTrigger
            s.a = st.a; s.b = st.b; s.x = st.x; s.y = st.y; s.lb = st.lb; s.rb = st.rb
            s.start = st.start; s.back = st.back
            s.up = st.dpadUp; s.down = st.dpadDown; s.left = st.dpadLeft; s.right = st.dpadRight
            val name = st.controllerType?.toString()
            if (!name.isNullOrBlank()) padNames[i] = prettyName(name)
        }
        // free slots of unplugged controllers, claim slots for controllers that press something
        for (p in slot.indices) {
            val id = slot[p]
            if (id >= 0 && !padState[id].connected) { slot[p] = -1; disconnectFlag = true }
        }
        for (i in 0 until MAX_PADS) {
            val s = padState[i]
            if (s.connected && s.any() && slot.none { it == i }) {
                val free = slot.indexOfFirst { it < 0 }
                if (free >= 0) slot[free] = i
            }
        }
    }

    private fun prettyName(raw: String): String {
        val r = raw.uppercase()
        return when {
            "XBOX" in r -> "Xbox controller"
            "PS5" in r || "DUALSENSE" in r -> "PS5 controller"
            "PS4" in r || "DUALSHOCK" in r -> "PS4 controller"
            "PS3" in r -> "PS3 controller"
            "SWITCH" in r -> "Switch controller"
            else -> raw.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
        }
    }

    private fun k(vararg codes: Int): Boolean = codes.any { keys.contains(it) }

    fun poll() {
        synchronized(lock) {
            readPads()
            for (p in 0 until PLAYERS) {
                val c = ctl[p]
                var mx = 0f; var my = 0f; var ax = 0f; var ay = 0f
                var jump = false; var attack = false; var block = false; var thr = false
                var confirm = false; var back = false; var start = false
                var nUp = false; var nDown = false; var nLeft = false; var nRight = false

                if (p == 0) {
                    if (k(KeyEvent.VK_A)) mx -= 1f
                    if (k(KeyEvent.VK_D)) mx += 1f
                    if (k(KeyEvent.VK_W)) my -= 1f
                    if (k(KeyEvent.VK_S)) my += 1f
                    jump = k(KeyEvent.VK_SPACE)
                    attack = k(KeyEvent.VK_F, KeyEvent.VK_J)
                    block = k(KeyEvent.VK_G, KeyEvent.VK_K)
                    thr = k(KeyEvent.VK_H, KeyEvent.VK_L)
                    confirm = jump
                    back = k(KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE)
                    start = k(KeyEvent.VK_ESCAPE, KeyEvent.VK_P)
                } else if (p == 1) {
                    if (k(KeyEvent.VK_LEFT)) mx -= 1f
                    if (k(KeyEvent.VK_RIGHT)) mx += 1f
                    if (k(KeyEvent.VK_UP)) my -= 1f
                    if (k(KeyEvent.VK_DOWN)) my += 1f
                    jump = k(KeyEvent.VK_ENTER, KeyEvent.VK_NUMPAD0)
                    attack = k(KeyEvent.VK_PERIOD, KeyEvent.VK_NUMPAD1)
                    block = k(KeyEvent.VK_SLASH, KeyEvent.VK_NUMPAD2)
                    thr = k(KeyEvent.VK_SEMICOLON, KeyEvent.VK_NUMPAD3)
                    confirm = jump
                }
                if (my < 0) nUp = true
                if (my > 0) nDown = true
                if (mx < 0) nLeft = true
                if (mx > 0) nRight = true

                val id = slot[p]
                val pad = if (id >= 0) padState[id].takeIf { it.connected } else null
                c.hasPad = pad != null
                c.padName = if (pad != null) padNames[id] else ""
                if (pad != null) {
                    var px = pad.lx; var py = pad.ly
                    if (sqrt(px * px + py * py) < DEAD) { px = 0f; py = 0f }
                    if (pad.left) px = -1f
                    if (pad.right) px = 1f
                    if (pad.up) py = -1f
                    if (pad.down) py = 1f
                    mx += px; my += py
                    if (py < -0.55f) nUp = true
                    if (py > 0.55f) nDown = true
                    if (px < -0.55f) nLeft = true
                    if (px > 0.55f) nRight = true
                    if (sqrt(pad.rx * pad.rx + pad.ry * pad.ry) > 0.4f) { ax = pad.rx; ay = pad.ry }
                    jump = jump || pad.a
                    attack = attack || pad.x || pad.rb || pad.rt > 0.4f
                    block = block || pad.b || pad.lb || pad.lt > 0.4f
                    thr = thr || pad.y
                    confirm = confirm || pad.a
                    back = back || pad.b
                    start = start || pad.start
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
