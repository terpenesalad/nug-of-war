package com.terpenesalad.nugofwar

import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Nug of War: a side-on ragdoll-ish brawler played on your own wall.
 * Trace your shelves and frames in Wall Setup and they become the platforms.
 */
class Game(
    private val input: InputHub,
    private val sfx: Sound,
    private val prefs: SharedPreferences,
    private val quit: () -> Unit
) {
    companion object {
        const val W = 1920f
        const val H = 1080f
        const val FLOOR = 1052f
        const val GRAV = 2700f
        const val RUN = 540f
        const val JUMP = 1080f
        const val DJUMP = 960f
        const val PARRY_WIN = 0.18f
        const val WIN_ROUNDS = 2

        val COLORS = intArrayOf(
            0xFF8BE83A.toInt(), 0xFF9D8CC4.toInt(), 0xFF7FB8A8.toInt(), 0xFFC48A9F.toInt(),
            0xFF5E8A5A.toInt(), 0xFF7F93BF.toInt(), 0xFFB5C98A.toInt(), 0xFFD0D4D0.toInt()
        )
        val COLOR_NAMES = arrayOf("Citrusquest", "Purple Haze", "Frosty Mint", "Pink Candy",
            "Forest OG", "Blueberry", "Lime Kush", "White Widow")
        val SHAPE_NAMES = arrayOf("Dense Round", "Tall Cola", "Chunky", "Foxtails")
        val BAD_COLORS = intArrayOf(
            0xFFB3A36A.toInt(), 0xFF8C7450.toInt(), 0xFF6E5A40.toInt(),
            0xFFA6965C.toInt(), 0xFF7F6A4E.toInt(), 0xFFC2B88A.toInt()
        )
        val ACCENT = 0xFF9CC79A.toInt()
        val SOFT = 0xFFD8DCD8.toInt()
        val SEL = 0x26FFFFFF
        val HPCOL = 0xFFA8C8A4.toInt()
        val LOWCOL = 0xFFC46A6A.toInt()
        val MUTED = 0xFF8F948F.toInt()
        val DUST = 0xFF8A8680.toInt()
        val BOOMCOL = 0xFFD8B27A.toInt()

        const val SHRIVEL = 0; const val MULCH = 1; const val BRICK = 2; const val SPITTER = 3; const val BOSS = 4

        const val BRAWL = 0; const val SURVIVAL = 1

        const val S_TITLE = 0; const val S_HELP = 1; const val S_LOBBY = 2; const val S_PLAY = 3
        const val S_PAUSE = 4; const val S_OVER = 5; const val S_SETTINGS = 6; const val S_WALLMENU = 7
        const val S_EDITOR = 8; const val S_ARENA = 9

        const val TW_CLASSIC = 0; const val TW_JETPACK = 1; const val TW_LOWGRAV = 2; const val TW_ROCKETS = 3
        const val TW_SWORDS = 4; const val TW_BARRELS = 5; const val TW_HOTFLOOR = 6; const val TW_SNOW = 7
        val TWIST_NAMES = arrayOf("Classic", "Jetpacks", "Low Gravity", "Rockets Only", "Sword Fight",
            "Boom Barrels", "Hot Floor", "Snowball Fight")
        val TWIST_DESC = arrayOf("Weapons drop in. Last nug standing.", "Hold JUMP to fly.", "Everything floats.",
            "Only rocket launchers drop.", "Everyone starts with a sword.", "Explosive barrels everywhere. Hit them.",
            "The floor burns. Stay up on the shelves!", "Snowball blasters only. Big knockback.")

        val ARENA_NAMES = arrayOf("My Wall 1", "My Wall 2", "My Wall 3", "Bookshelf", "Gallery Wall", "Bedroom", "Open Wall")
    }

    private val rnd = Random(System.nanoTime())
    @Volatile private var state = S_TITLE
    private var mode = BRAWL
    private var time = 0f
    private var idx = 0
    private var pauseMsg = ""

    // settings
    private var projector = prefs.getBoolean("projector", true)
    private var xmas = prefs.getBoolean("xmas", true)
    private var commentary = prefs.getBoolean("commentary", true)
    init {
        sfx.uiEnabled = prefs.getBoolean("uiSounds", true)
        sfx.gameEnabled = prefs.getBoolean("gameSounds", true)
    }

    // lobby
    private val joined = booleanArrayOf(true, false, false, false)
    private val ready = BooleanArray(4)
    private val colorIdx = intArrayOf(0, 1, 2, 3)
    private val shapeIdx = intArrayOf(0, 3, 2, 1)
    private var lobbyCountdown = -1f
    private var arena = prefs.getInt("arena", 3)

    // world
    private val plats = ArrayList<Plat>()
    private val players = Array(4) { Player(it) }
    private val enemies = ArrayList<Enemy>()
    private val projs = ArrayList<Proj>()
    private val items = ArrayList<Item>()
    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private val rings = ArrayList<Ring>()
    private var shake = 0f
    private var itemT = 5f
    private var barrelT = 3f
    private var twist = TW_CLASSIC
    private val grav get() = if (twist == TW_LOWGRAV) 0.5f else 1f

    // brawl
    private val score = IntArray(4)
    private var round = 0
    private var roundIntro = 0f
    private var roundOver = 0f
    private var roundWinner = -1
    private var matchWinner = -1

    // survival
    private var wave = 0
    private val spawnQueue = ArrayList<Int>()
    private var spawnT = 0f
    private var bannerT = 0f
    private var teamLives = 3
    private var bestWave = prefs.getInt("bestWave2", 0)

    // editor
    private var editSlot = 0
    private val editRects = ArrayList<Plat>()
    private var curX = W / 2; private var curY = H / 2
    private var anchor: FloatArray? = null
    private var clearHold = 0f
    private var lastRaw: FloatArray? = null
    private val undoStack = ArrayList<List<Plat>>()
    private var held = -1                 // controller: box being carried
    private var heldOrig: Plat? = null
    private var grabDX = 0f; private var grabDY = 0f
    private var sel = -1                  // touch: selected box
    private var touchMode = 0             // 0 none, 1 draw, 2 move, 3 resize, 4 button
    private var touchOrig: Plat? = null
    private var touchIdx = -1
    private var editMsg = ""; private var editMsgT = 0f

    // commentary
    private val talk = Commentary(sfx)
    private val streakT = FloatArray(4)
    private val streakN = IntArray(4)
    private fun say(e: Commentary.Ev, who: String = "", n: Int = 0, force: Boolean = false) { if (commentary) talk.event(e, who, n, force) }
    private fun nameOf(p: Int) = COLOR_NAMES[colorIdx[p]]

    // christmas
    private val flakes = FloatArray(160 * 4)
    private var santaT = 8f
    private var santaX = -1f
    private var santaY = 100f
    private var santaDir = 1f
    private var santaDropped = false

    private val titleNugs = ArrayList<Enemy>()

    init {
        for (i in 0 until 160) {
            flakes[i * 4] = rnd.nextFloat() * W
            flakes[i * 4 + 1] = rnd.nextFloat() * H
            flakes[i * 4 + 2] = 25f + rnd.nextFloat() * 50f
            flakes[i * 4 + 3] = 1.5f + rnd.nextFloat() * 3f
        }
        for (i in 0 until 6) {
            val good = i % 2 == 0
            val col = if (good) COLORS[(i / 2) % COLORS.size] else BAD_COLORS[i % BAD_COLORS.size]
            val e = Enemy(if (good) 0 else 1, 40f + (i % 3) * 6f)
            e.look = Look.of(col, i % 4, i)
            e.x = rnd.nextFloat() * W; e.y = FLOOR - e.hBot
            e.speed = 90f + i * 14f
            e.facing = if (i % 3 == 0) -1f else 1f
            titleNugs.add(e)
        }
        loadArena(arena)
    }

    fun onHostPause() {
        if (state == S_PLAY) { state = S_PAUSE; idx = 0; pauseMsg = "" }
    }

    // ================================================================= arenas

    private fun preset(id: Int): List<Plat> = when (id) {
        3 -> listOf( // bookshelf in the middle, two floating shelves
            Plat(700f, 830f, 1220f, 846f), Plat(700f, 630f, 1220f, 646f), Plat(700f, 430f, 1220f, 446f), Plat(700f, 240f, 1220f, 256f),
            Plat(150f, 690f, 470f, 706f), Plat(1450f, 690f, 1770f, 706f), Plat(300f, 420f, 520f, 436f), Plat(1400f, 420f, 1620f, 436f))
        4 -> listOf( // gallery wall of frames
            Plat(230f, 430f, 530f, 620f), Plat(800f, 260f, 1120f, 470f), Plat(1390f, 430f, 1690f, 620f),
            Plat(640f, 680f, 820f, 800f), Plat(1100f, 680f, 1280f, 800f), Plat(560f, 900f, 1360f, 916f))
        5 -> listOf( // bedroom: bed, bedside table, window sill, shelf, picture
            Plat(80f, 880f, 760f, 1052f), Plat(820f, 930f, 980f, 1052f), Plat(1120f, 640f, 1640f, 656f),
            Plat(260f, 560f, 660f, 576f), Plat(1250f, 290f, 1520f, 420f), Plat(820f, 400f, 1040f, 416f))
        6 -> listOf( // open wall with a few ledges
            Plat(200f, 760f, 600f, 776f), Plat(1320f, 760f, 1720f, 776f), Plat(760f, 540f, 1160f, 556f),
            Plat(380f, 330f, 700f, 346f), Plat(1220f, 330f, 1540f, 346f))
        else -> emptyList()
    }

    private fun loadWall(slot: Int): ArrayList<Plat> {
        val out = ArrayList<Plat>()
        val s = prefs.getString("wall$slot", "") ?: ""
        for (part in s.split(";")) {
            val n = part.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (n.size == 4) out.add(Plat(n[0], n[1], n[2], n[3]))
        }
        return out
    }

    private fun saveWall(slot: Int, list: List<Plat>) {
        prefs.edit().putString("wall$slot", list.joinToString(";") { "${it.l.toInt()},${it.t.toInt()},${it.r.toInt()},${it.b.toInt()}" }).apply()
    }

    private fun loadArena(id: Int) {
        plats.clear()
        if (id < 3) plats.addAll(loadWall(id)) else plats.addAll(preset(id))
    }

    private fun arenaLabel(id: Int): String {
        if (id >= 3) return ARENA_NAMES[id]
        val n = loadWallCached(id).size
        return if (n == 0) "${ARENA_NAMES[id]} (empty)" else "${ARENA_NAMES[id]} ($n traced)"
    }

    // ================================================================= input helpers

    private fun c(i: Int) = input.ctl[i]
    private fun anyUp() = input.ctl.any { it.upP }
    private fun anyDown() = input.ctl.any { it.downP }
    private fun anyConfirm() = input.ctl.any { it.confirmP }
    private fun anyBack() = input.ctl.any { it.backP }
    private fun anyStart() = input.ctl.any { it.startP }
    private fun inRect(t: FloatArray, l: Float, top: Float, r: Float, b: Float) = t[0] in l..r && t[1] in top..b

    /** Shared vertical list menu: returns the index chosen this frame, or -1. */
    private fun listMenu(n: Int, y0: Float, step: Float, width: Float, taps: List<FloatArray>, cx: Float = W / 2): Int {
        if (anyUp()) { idx = (idx + n - 1) % n; sfx.play(Sound.SELECT) }
        if (anyDown()) { idx = (idx + 1) % n; sfx.play(Sound.SELECT) }
        idx = idx.coerceIn(0, n - 1)
        for (t in taps) for (i in 0 until n) {
            val y = y0 + i * step
            if (inRect(t, cx - width / 2, y - 56, cx + width / 2, y + 22)) { idx = i; sfx.play(Sound.SELECT); return i }
        }
        if (anyConfirm() || anyStart()) { sfx.play(Sound.UI_OK); return idx }
        return -1
    }

    private fun go(s: Int) { state = s; idx = 0 }

    // ================================================================= update

    fun update(dt: Float) {
        time += dt
        if (xmas) updateChristmas(dt)
        if (commentary) talk.update(dt, state == S_PLAY)
        for (i in 0..3) { streakT[i] -= dt; if (streakT[i] <= 0f) streakN[i] = 0 }
        val taps = input.takeTaps()
        when (state) {
            S_TITLE -> updateTitle(dt, taps)
            S_SETTINGS -> updateSettings(taps)
            S_HELP -> if (anyConfirm() || anyBack() || taps.isNotEmpty()) { sfx.play(Sound.SELECT); go(S_TITLE) }
            S_WALLMENU -> updateWallMenu(taps)
            S_EDITOR -> updateEditor(dt, taps)
            S_ARENA -> updateArenaSelect(taps)
            S_LOBBY -> updateLobby(dt, taps)
            S_PLAY -> updatePlay(dt, taps)
            S_PAUSE -> updatePause(taps)
            S_OVER -> { updateOver(taps); updateFx(dt); for (p in players) p.phase += dt * 8f }
        }
        if (input.disconnectFlag) {
            input.disconnectFlag = false
            if (state == S_PLAY) { state = S_PAUSE; idx = 0; pauseMsg = "A controller disconnected. Reconnect it and press A." }
        }
    }

    private val titleItems = arrayOf("Versus Brawl", "Bad Nug Survival", "Wall Setup", "Settings", "How to Play", "Quit")

    private fun updateTitle(dt: Float, taps: List<FloatArray>) {
        for (e in titleNugs) {
            e.x += e.facing * e.speed * dt
            e.phase += dt * 9f
            if (e.x > W + 80) e.x = -80f
            if (e.x < -80) e.x = W + 80f
        }
        if (anyBack()) { quit(); return }
        when (listMenu(titleItems.size, 470f, 84f, 760f, taps)) {
            0 -> { mode = BRAWL; go(S_ARENA); idx = arena }
            1 -> { mode = SURVIVAL; go(S_ARENA); idx = arena }
            2 -> go(S_WALLMENU)
            3 -> go(S_SETTINGS)
            4 -> go(S_HELP)
            5 -> quit()
        }
    }

    private fun settingsItems() = arrayOf(
        "Projector Mode: " + if (projector) "ON" else "OFF",
        "Christmas Mode: " + if (xmas) "ON" else "OFF",
        "Streamer Commentary: " + if (commentary) "ON" else "OFF",
        "Menu sounds: " + if (sfx.uiEnabled) "ON" else "OFF",
        "Game sounds: " + if (sfx.gameEnabled) "ON" else "OFF",
        "Reset Controllers", "Back")

    private fun updateSettings(taps: List<FloatArray>) {
        if (anyBack()) { go(S_TITLE); idx = 3; return }
        when (listMenu(7, 380f, 86f, 820f, taps)) {
            0 -> { projector = !projector; prefs.edit().putBoolean("projector", projector).apply() }
            1 -> { xmas = !xmas; prefs.edit().putBoolean("xmas", xmas).apply(); if (xmas) sfx.play(Sound.JINGLE) }
            2 -> { commentary = !commentary; prefs.edit().putBoolean("commentary", commentary).apply() }
            3 -> { sfx.uiEnabled = !sfx.uiEnabled; prefs.edit().putBoolean("uiSounds", sfx.uiEnabled).apply() }
            4 -> { sfx.gameEnabled = !sfx.gameEnabled; prefs.edit().putBoolean("gameSounds", sfx.gameEnabled).apply() }
            5 -> { input.resetSlots(); floaters.add(Floater("Controllers reset: press A on each one in order", W / 2, 990f, Color.WHITE)) }
            6 -> { go(S_TITLE); idx = 3 }
        }
    }

    private fun updateWallMenu(taps: List<FloatArray>) {
        if (anyBack()) { go(S_TITLE); idx = 2; return }
        when (val i = listMenu(4, 520f, 90f, 760f, taps)) {
            0, 1, 2 -> {
                editSlot = i
                editRects.clear(); editRects.addAll(loadWall(i))
                anchor = null; curX = W / 2; curY = H / 2; clearHold = 0f; lastRaw = null
                undoStack.clear(); held = -1; heldOrig = null; sel = -1; touchMode = 0
                state = S_EDITOR
            }
            3 -> { go(S_TITLE); idx = 2 }
        }
    }

    // ----------------------------------------------------------------- wall editor

    private fun hovered(x: Float, y: Float): Int {
        for (i in editRects.indices.reversed()) {
            val p = editRects[i]
            if (x >= p.l - 12 && x <= p.r + 12 && y >= p.t - 14 && y <= p.b + 14) return i
        }
        return -1
    }

    private fun snapshot() {
        undoStack.add(ArrayList(editRects))
        if (undoStack.size > 60) undoStack.removeAt(0)
    }

    private fun undo() {
        if (undoStack.isEmpty()) { editNote("Nothing to undo"); return }
        editRects.clear(); editRects.addAll(undoStack.removeAt(undoStack.size - 1))
        held = -1; heldOrig = null; sel = -1; anchor = null
        sfx.play(Sound.SWISH, 0.6f); editNote("Undone")
    }

    private fun editNote(m: String) { editMsg = m; editMsgT = 1.6f }

    /** Keeps a box on screen and at least a usable size, without changing its size when moved. */
    private fun clampBox(l0: Float, t0: Float, r0: Float, b0: Float): Plat {
        val w = (r0 - l0).coerceIn(30f, W); val h = (b0 - t0).coerceIn(16f, FLOOR)
        val l = l0.coerceIn(0f, W - w); val t = t0.coerceIn(0f, FLOOR - h)
        return Plat(l, t, l + w, t + h)
    }

    private fun finishRect(x0: Float, y0: Float, x1: Float, y1: Float) {
        var l = min(x0, x1); var r = max(x0, x1); var t = min(y0, y1); var b = max(y0, y1)
        if (r - l < 30f) return
        if (b - t < 16f) b = t + 16f
        l = l.coerceIn(0f, W); r = r.coerceIn(0f, W); t = t.coerceIn(0f, FLOOR - 16f); b = b.coerceIn(t + 16f, FLOOR)
        snapshot()
        editRects.add(Plat(l, t, r, b))
        sel = editRects.size - 1
        sfx.play(Sound.UI_OK, 0.6f)
    }

    private fun deleteBox(i: Int) {
        if (i !in editRects.indices) return
        snapshot(); editRects.removeAt(i)
        held = -1; heldOrig = null; sel = -1
        sfx.play(Sound.HIT); editNote("Deleted")
    }

    private fun copyBox(i: Int): Int {
        if (i !in editRects.indices) return -1
        val p = editRects[i]
        snapshot()
        editRects.add(clampBox(p.l + 30f, p.t + 30f, p.r + 30f, p.b + 30f))
        sfx.play(Sound.UI_OK, 0.6f); editNote("Copied")
        return editRects.size - 1
    }

    // editor touch buttons, right side of the top bar
    private val btnSave = floatArrayOf(W - 250f, 16f, W - 20f, 86f)
    private val btnUndo = floatArrayOf(W - 500f, 16f, W - 270f, 86f)
    private val btnDel = floatArrayOf(W - 750f, 16f, W - 520f, 86f)
    private val btnCopy = floatArrayOf(W - 1000f, 16f, W - 770f, 86f)
    private fun onBtn(x: Float, y: Float, b: FloatArray) = x in b[0]..b[2] && y in b[1]..b[3]
    private fun onAnyBtn(x: Float, y: Float) = onBtn(x, y, btnSave) || onBtn(x, y, btnUndo) ||
        (sel >= 0 && (onBtn(x, y, btnDel) || onBtn(x, y, btnCopy)))

    /** Resize handle: the bottom-right corner of a box (generous for fingers). */
    private fun onHandle(i: Int, x: Float, y: Float): Boolean {
        if (i !in editRects.indices) return false
        val p = editRects[i]
        return abs(x - p.r) < 50f && abs(y - p.b) < 50f
    }

    private fun updateEditor(dt: Float, taps: List<FloatArray>) {
        var mx = 0f; var my = 0f; var ax = 0f; var ay = 0f
        var place = false; var del = false; var copy = false; var back = false; var holdDel = false; var undoP = false
        for (k in 0..3) {
            val c = c(k)
            mx += c.mx; my += c.my; ax += c.ax; ay += c.ay
            if (c.confirmP) place = true
            if (c.attackP) del = true
            if (c.attack) holdDel = true
            if (c.throwP) copy = true
            if (c.blockP && !c.backP) undoP = true   // L1 / LT (B also blocks, but B means save)
            if (c.backP || c.startP) back = true
        }
        if (editMsgT > 0f) editMsgT -= dt
        val ml = sqrt(mx * mx + my * my).coerceAtMost(1f)
        if (ml > 0.05f) {
            val sp = 950f * ml * ml // a gentle push moves slowly for precise lining up
            val inv = 1f / sqrt(mx * mx + my * my)
            curX += mx * inv * sp * dt
            curY += my * inv * sp * dt
        }
        curX = curX.coerceIn(0f, W); curY = curY.coerceIn(0f, FLOOR)

        // ---- controller: carrying a box
        if (held in editRects.indices) {
            val p = editRects[held]
            var w = p.r - p.l; var h = p.b - p.t
            if (abs(ax) > 0.2f || abs(ay) > 0.2f) {  // right stick stretches / squashes
                w += ax * abs(ax) * 600f * dt
                h -= ay * abs(ay) * 400f * dt
            }
            editRects[held] = clampBox(curX - grabDX, curY - grabDY, curX - grabDX + w, curY - grabDY + h)
            when {
                place -> { held = -1; heldOrig = null; sfx.play(Sound.THUD, 0.6f); editNote("Placed") }
                del -> { val o = heldOrig; if (o != null) editRects[held] = o; if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.size - 1); deleteBox(held) }
                copy -> { val n = copyBox(held); if (n >= 0) { held = n; heldOrig = editRects[n]; grabDX -= 30f; grabDY -= 30f } }
                undoP || back -> {          // cancel the move, put it back where it was
                    heldOrig?.let { editRects[held] = it }
                    if (undoStack.isNotEmpty()) undoStack.removeAt(undoStack.size - 1)
                    held = -1; heldOrig = null; sfx.play(Sound.SWISH, 0.6f); editNote("Move cancelled")
                }
            }
            if (place || del || copy || undoP || back) { clearHold = 0f; lastRaw = input.rawTouch(); return }
        } else {
            held = -1
            if (place) {
                val a = anchor
                val hv = hovered(curX, curY)
                if (a == null && hv >= 0) {
                    snapshot()
                    held = hv; heldOrig = editRects[hv]
                    grabDX = curX - editRects[hv].l; grabDY = curY - editRects[hv].t
                    sfx.play(Sound.SELECT); editNote("Moving: stick to move, right stick to resize, A to drop")
                } else if (a == null) { anchor = floatArrayOf(curX, curY); sfx.play(Sound.SELECT) }
                else { finishRect(a[0], a[1], curX, curY); anchor = null; sel = -1 }
            }
            if (del) { val h = hovered(curX, curY); if (h >= 0) deleteBox(h) }
            if (copy) {
                val h = hovered(curX, curY)
                if (h >= 0) {
                    val n = copyBox(h)
                    if (n >= 0) { held = n; heldOrig = editRects[n]; grabDX = curX - editRects[n].l; grabDY = curY - editRects[n].t }
                }
            }
            if (undoP) undo()
        }
        clearHold = if (holdDel && held < 0) clearHold + dt else 0f
        if (clearHold > 1.6f) { snapshot(); editRects.clear(); clearHold = 0f; sel = -1; sfx.play(Sound.BOOM, 0.5f); editNote("Cleared (L1 to undo)") }

        // ---- touch: drag empty space to draw, drag a box to move it,
        //      drag its corner handle to resize, tap to select
        val raw = input.rawTouch()
        val lr = lastRaw
        if (raw != null) {
            curX = raw[2]; curY = raw[3]
            if (lr == null) {               // finger just went down
                val x = raw[0]; val y = raw[1]
                touchOrig = null; touchIdx = -1
                touchMode = when {
                    onAnyBtn(x, y) -> 4
                    sel >= 0 && onHandle(sel, x, y) -> { touchIdx = sel; 3 }
                    hovered(x, y) >= 0 -> { touchIdx = hovered(x, y); 2 }
                    else -> 1
                }
                if (touchIdx >= 0) touchOrig = editRects[touchIdx]
            }
            val o = touchOrig
            val moved = abs(raw[2] - raw[0]) + abs(raw[3] - raw[1])
            if (o != null && touchIdx in editRects.indices && moved > 14f) {
                if (lr != null && abs(lr[2] - lr[0]) + abs(lr[3] - lr[1]) <= 14f) snapshot()  // first real drag
                val dx = raw[2] - raw[0]; val dy = raw[3] - raw[1]
                editRects[touchIdx] = if (touchMode == 3) clampBox(o.l, o.t, o.r + dx, o.b + dy)
                                      else clampBox(o.l + dx, o.t + dy, o.r + dx, o.b + dy)
                sel = touchIdx
            }
        } else if (lr != null) {            // finger lifted
            val moved = abs(lr[2] - lr[0]) + abs(lr[3] - lr[1])
            val x = lr[0]; val y = lr[1]
            when (touchMode) {
                4 -> if (moved < 40f) when {
                    onBtn(x, y, btnSave) -> back = true
                    onBtn(x, y, btnUndo) -> undo()
                    sel >= 0 && onBtn(x, y, btnDel) -> deleteBox(sel)
                    sel >= 0 && onBtn(x, y, btnCopy) -> { sel = copyBox(sel) }
                }
                1 -> if (moved < 20f) sel = -1 else finishRect(lr[0], lr[1], lr[2], lr[3])
                2, 3 -> if (moved <= 14f) { sel = if (sel == touchIdx) -1 else touchIdx; sfx.play(Sound.SELECT, 0.5f) }
                         else sfx.play(Sound.THUD, 0.5f)
            }
            touchMode = 0; touchOrig = null; touchIdx = -1
        }
        lastRaw = raw
        if (sel >= editRects.size) sel = -1

        if (back) {
            if (anchor != null) { anchor = null; return }
            saveWall(editSlot, editRects)
            wallCacheSlot = -1
            sfx.play(Sound.UI_OK)
            go(S_WALLMENU); idx = editSlot
        }
    }

    // ----------------------------------------------------------------- arena + lobby

    private fun updateArenaSelect(taps: List<FloatArray>) {
        if (anyBack()) { go(S_TITLE); idx = mode; return }
        val pick = listMenu(ARENA_NAMES.size, 290f, 92f, 620f, taps, W / 2 - 420f)
        if (pick >= 0) {
            arena = pick
            prefs.edit().putInt("arena", arena).apply()
            loadArena(arena)
            openLobby()
        }
    }

    private fun openLobby() {
        joined[0] = true
        for (i in 1..3) joined[i] = input.ctl[i].hasPad || (i == 1 && input.touchRecently())
        ready.fill(false)
        lobbyCountdown = -1f
        state = S_LOBBY
    }

    private fun lobbyCanStart(): Boolean {
        val n = joined.count { it }
        if (mode == BRAWL && n < 2) return false
        for (p in 0..3) if (joined[p] && !ready[p]) return false
        return true
    }

    private fun panelL(p: Int) = 50f + p * 460f

    private fun updateLobby(dt: Float, taps: List<FloatArray>) {
        for (p in 0..3) {
            val c = c(p)
            if (!joined[p]) {
                if (c.confirmP || c.startP) { joined[p] = true; sfx.play(Sound.SELECT) }
                continue
            }
            if (!ready[p]) {
                if (c.leftP) { colorIdx[p] = (colorIdx[p] + COLORS.size - 1) % COLORS.size; sfx.play(Sound.SELECT) }
                if (c.rightP) { colorIdx[p] = (colorIdx[p] + 1) % COLORS.size; sfx.play(Sound.SELECT) }
                if (c.upP || c.throwP) { shapeIdx[p] = (shapeIdx[p] + SHAPE_NAMES.size - 1) % SHAPE_NAMES.size; sfx.play(Sound.SELECT) }
                if (c.downP) { shapeIdx[p] = (shapeIdx[p] + 1) % SHAPE_NAMES.size; sfx.play(Sound.SELECT) }
                if (c.confirmP || c.startP) { ready[p] = true; sfx.play(Sound.UI_OK) }
                if (c.backP) {
                    if (p > 0) joined[p] = false
                    else { go(S_ARENA); idx = arena; sfx.play(Sound.SELECT); return }
                }
            } else if (c.backP) { ready[p] = false; sfx.play(Sound.SELECT) }
        }
        for (t in taps) {
            for (p in 0..3) {
                val l = panelL(p)
                if (!inRect(t, l, 190f, l + 420f, 850f)) continue
                if (!joined[p]) { joined[p] = true; sfx.play(Sound.SELECT); continue }
                if (t[1] < 620f) colorIdx[p] = (colorIdx[p] + 1) % COLORS.size
                else shapeIdx[p] = (shapeIdx[p] + 1) % SHAPE_NAMES.size
                sfx.play(Sound.SELECT)
            }
            if (inRect(t, W / 2 - 220, 900f, W / 2 + 220, 1010f)) {
                if (mode == BRAWL && !joined[1]) joined[1] = true
                for (p in 0..3) if (joined[p]) ready[p] = true
                lobbyCountdown = 0.01f
            }
        }
        if (lobbyCanStart()) {
            if (lobbyCountdown < 0f) lobbyCountdown = 1.2f
            lobbyCountdown -= dt
            if (lobbyCountdown <= 0f) startMatch()
        } else lobbyCountdown = -1f
    }

    private fun startMatch() {
        for (p in players) {
            p.active = joined[p.idx]
            p.look = Look.of(COLORS[colorIdx[p.idx]], shapeIdx[p.idx], 11 + p.idx * 7 + colorIdx[p.idx])
            p.kos = 0; p.kills = 0
            p.team = if (mode == SURVIVAL) 0 else p.idx
            p.out = false; p.respawnT = 0f
        }
        loadArena(arena)
        enemies.clear(); projs.clear(); items.clear(); particles.clear(); floaters.clear(); rings.clear()
        talk.reset()
        shake = 0f
        score.fill(0)
        round = 0
        matchWinner = -1
        if (mode == BRAWL) startRound()
        else {
            twist = TW_CLASSIC
            wave = 0
            itemT = 4f
            teamLives = 2 + players.count { it.active }
            val act = players.filter { it.active }
            for ((k, p) in act.withIndex()) { p.lives = 1; spawnPlayer(p, true, W * (k + 1) / (act.size + 1)) }
            nextWave()
        }
        state = S_PLAY
    }

    private fun pickTwist(): Int {
        if (round == 1) return TW_CLASSIC
        val opts = ArrayList<Int>()
        for (t in 1..6) opts.add(t)
        if (plats.size < 3) opts.remove(TW_HOTFLOOR)
        if (xmas) { opts.add(TW_SNOW); opts.add(TW_SNOW) }
        return opts[rnd.nextInt(opts.size)]
    }

    private fun startRound() {
        round++
        twist = pickTwist()
        projs.clear(); items.clear(); enemies.clear()
        val act = players.filter { it.active }
        for ((k, p) in act.withIndex()) {
            p.lives = 3; p.out = false
            spawnPlayer(p, true, W * (k + 1) / (act.size + 1))
        }
        roundIntro = 2.6f
        roundOver = 0f
        roundWinner = -1
        itemT = 2.5f
        barrelT = 0.5f
        sfx.play(Sound.WAVE)
        say(Commentary.Ev.FIGHT, TWIST_NAMES[twist], force = true)
    }

    private fun spawnX(): Float {
        if (plats.isNotEmpty() && rnd.nextFloat() < 0.7f) {
            val p = plats[rnd.nextInt(plats.size)]
            return (p.l + 40f + rnd.nextFloat() * max(1f, p.r - p.l - 80f)).coerceIn(60f, W - 60f)
        }
        return 120f + rnd.nextFloat() * (W - 240f)
    }

    private fun spawnPlayer(p: Player, first: Boolean, xAt: Float = -1f) {
        p.x = if (xAt >= 0) xAt else spawnX()
        p.y = -60f
        p.vx = 0f; p.vy = 0f; p.rot = 0f; p.spin = 0f; p.launchT = 0f; p.stunT = 0f
        p.hp = p.maxHp; p.inv = if (first) 1f else 2f
        p.respawnT = 0f; p.dead = false
        p.weapon = if (twist == TW_SWORDS) Wpn.SWORD else Wpn.NONE
        p.ammo = 0; p.shield = 100f; p.blocking = false; p.fuel = 1f
        p.facing = if (p.x < W / 2) 1f else -1f
        p.lastHitBy = null
    }

    // ----------------------------------------------------------------- survival waves

    private fun nextWave() {
        wave++
        spawnQueue.clear()
        val np = players.count { it.active }
        val count = 3 + wave * 2 + (np - 1) * 2
        for (i in 0 until count) {
            val roll = rnd.nextFloat()
            spawnQueue.add(when {
                wave >= 4 && roll < 0.12f + wave * 0.01f -> BRICK
                wave >= 3 && roll < 0.32f -> SPITTER
                wave >= 2 && roll < 0.62f -> MULCH
                else -> SHRIVEL
            })
        }
        spawnQueue.shuffle(rnd)
        if (wave % 5 == 0) spawnQueue.add(spawnQueue.size / 3, BOSS)
        spawnT = 0f
        bannerT = 3f
        for (p in players) if (p.active) {
            if (p.respawnT > 0f || p.out) { p.out = false; spawnPlayer(p, false) }
            else p.hp = min(p.maxHp, p.hp + 30f)
        }
        sfx.play(Sound.WAVE)
        say(Commentary.Ev.WAVE, n = wave, force = true)
    }

    private fun spawnEnemy(type: Int, xIn: Float = -1f, yIn: Float = -1f) {
        val hpMul = 1f + (wave - 1) * 0.1f
        val e = when (type) {
            SHRIVEL -> Enemy(type, 24f).apply { hp = 20f; speed = 300f; dmg = 6f; kb = 450f; look = badLook(0, 5) }
            MULCH -> Enemy(type, 32f).apply { hp = 45f; speed = 210f; dmg = 9f; kb = 560f; look = badLook(1, 4) }
            BRICK -> Enemy(type, 50f).apply { hp = 140f; speed = 130f; dmg = 16f; kb = 850f; look = Look.of(BAD_COLORS[2], 2, rnd.nextInt(4)) }
            SPITTER -> Enemy(type, 28f).apply { hp = 30f; speed = 180f; dmg = 7f; kb = 300f; look = Look.of(BAD_COLORS[3], 1, rnd.nextInt(4)) }
            else -> Enemy(type, 100f).apply { hp = 700f + wave * 120f; speed = 90f; dmg = 20f; kb = 1100f; look = Look.of(0xFF4E4232.toInt(), 3, 1) }
        }
        if (type != BOSS) e.hp *= hpMul
        e.maxHp = e.hp
        e.team = 9
        val fromSide = rnd.nextFloat() < 0.55f
        if (xIn >= 0) { e.x = xIn; e.y = yIn }
        else if (fromSide) { e.x = if (rnd.nextBoolean()) e.hw + 2f else W - e.hw - 2f; e.y = FLOOR - e.hBot - 2f }
        else { e.x = spawnX(); e.y = -80f }
        e.facing = if (e.x < W / 2) 1f else -1f
        e.atkCd = 0.8f + rnd.nextFloat()
        enemies.add(e)
        if (type == BOSS) say(Commentary.Ev.BOSS, force = true)
    }

    private fun badLook(a: Int, b: Int) = Look.of(if (rnd.nextBoolean()) BAD_COLORS[a] else BAD_COLORS[b], rnd.nextInt(4), rnd.nextInt(3))

    // ----------------------------------------------------------------- main loop

    private fun updatePlay(dt: Float, taps: List<FloatArray>) {
        if (anyStart()) { state = S_PAUSE; idx = 0; pauseMsg = ""; sfx.play(Sound.SELECT); return }
        if (input.ctl[0].backP && !input.ctl[0].hasPad) { state = S_PAUSE; idx = 0; pauseMsg = ""; return }
        for (t in taps) if (inRect(t, W - 100f, 110f, W, 170f)) { state = S_PAUSE; idx = 0; pauseMsg = ""; return }
        shake = max(0f, shake - dt * 30f)

        if (mode == BRAWL) updateBrawlFlow(dt) else updateSurvivalFlow(dt)
        if (state != S_PLAY) return

        for (p in players) if (p.active) updatePlayer(p, dt)
        for (i in enemies.indices) updateEnemy(enemies[i], dt)
        enemies.removeAll { it.dead }
        updateProjs(dt)
        updateItems(dt)
        updateFx(dt)
    }

    private fun updateBrawlFlow(dt: Float) {
        if (roundIntro > 0f) roundIntro -= dt
        if (roundOver > 0f) {
            roundOver -= dt
            if (roundOver <= 0f) {
                if (roundWinner >= 0 && score[roundWinner] >= WIN_ROUNDS) {
                    matchWinner = roundWinner; state = S_OVER; idx = 0; sfx.play(Sound.WIN)
                } else startRound()
            }
            return
        }
        if (roundIntro > 0f) return
        spawnItems(dt)
        val alive = players.filter { it.active && !it.out }
        if (alive.size <= 1) {
            roundWinner = alive.firstOrNull()?.idx ?: -1
            roundOver = 3f
            if (roundWinner >= 0) {
                score[roundWinner]++
                sfx.play(Sound.WIN)
                if (score[roundWinner] >= WIN_ROUNDS) say(Commentary.Ev.MATCH, nameOf(roundWinner), force = true)
                else say(Commentary.Ev.ROUND, nameOf(roundWinner), force = true)
            }
        }
    }

    private fun updateSurvivalFlow(dt: Float) {
        if (bannerT > 0f) bannerT -= dt
        else if (spawnQueue.isNotEmpty()) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnEnemy(spawnQueue.removeAt(0))
                spawnT = max(0.4f, 1.6f - wave * 0.08f) * (0.6f + rnd.nextFloat() * 0.8f)
            }
        } else if (enemies.isEmpty()) {
            floaters.add(Floater("WAVE CLEAR!", W / 2, 400f, ACCENT))
            say(Commentary.Ev.CLEAR)
            nextWave()
        }
        spawnItems(dt)
        if (players.none { it.active && !it.out }) {
            if (wave > bestWave) { bestWave = wave; prefs.edit().putInt("bestWave2", wave).apply() }
            sfx.play(Sound.LOSE)
            say(Commentary.Ev.GAME_OVER, force = true)
            state = S_OVER; idx = 0
        }
    }

    private fun spawnItems(dt: Float) {
        itemT -= dt
        val weapons = items.count { it.type != Item.BARREL }
        if (itemT <= 0f) {
            itemT = if (twist == TW_ROCKETS) 3.5f else if (mode == SURVIVAL) 7f else 5.5f + rnd.nextFloat() * 3f
            if (weapons < 3) {
                val t = when (twist) {
                    TW_ROCKETS -> Wpn.ROCKET
                    TW_SNOW -> Wpn.SNOW
                    TW_SWORDS -> Wpn.SWORD
                    else -> intArrayOf(Wpn.SWORD, Wpn.SWORD, Wpn.BLASTER, Wpn.BLASTER, Wpn.GRENADE, Wpn.ROCKET)[rnd.nextInt(6)]
                }
                items.add(Item(spawnX(), -40f, t))
            }
        }
        if (twist == TW_BARRELS) {
            barrelT -= dt
            if (barrelT <= 0f) {
                barrelT = 3f
                if (items.count { it.type == Item.BARREL } < 5) items.add(Item(spawnX(), -40f, Item.BARREL).also { it.life = 60f })
            }
        }
    }

    // ----------------------------------------------------------------- physics

    private fun overlaps(b: Body, p: Plat) =
        b.x + b.hw > p.l && b.x - b.hw < p.r && b.y + b.hBot > p.t + 1f && b.y - b.hTop < p.b - 1f

    private fun standingOnLedge(b: Body): Boolean {
        val bottom = b.y + b.hBot
        return plats.any { !it.solid && abs(bottom - it.t) < 3f && b.x + b.hw > it.l && b.x - b.hw < it.r }
    }

    private fun step(b: Body, dt: Float) {
        b.vy = min(b.vy + GRAV * grav * dt, 2400f)
        b.dropT -= dt
        b.launchT -= dt
        // horizontal
        b.x += b.vx * dt
        for (p in plats) if (p.solid && overlaps(b, p)) {
            if (b.vx > 0) b.x = p.l - b.hw
            else if (b.vx < 0) b.x = p.r + b.hw
            else b.x = if (b.x < (p.l + p.r) / 2) p.l - b.hw else p.r + b.hw
            sideHit(b)
        }
        if (b.x < b.hw) { b.x = b.hw; sideHit(b) }
        if (b.x > W - b.hw) { b.x = W - b.hw; sideHit(b) }
        // vertical
        val prevBottom = b.y + b.hBot
        b.y += b.vy * dt
        b.onGround = false
        for (p in plats) {
            if (b.x + b.hw <= p.l || b.x - b.hw >= p.r) continue
            val bottom = b.y + b.hBot
            if (p.solid) {
                if (bottom > p.t && b.y - b.hTop < p.b) {
                    if (b.vy >= 0 && prevBottom <= p.t + 14f) { b.y = p.t - b.hBot; land(b) }
                    else if (b.vy < 0) { b.y = p.b + b.hTop; b.vy = 0f }
                    else { b.y = p.t - b.hBot; land(b) }
                }
            } else if (b.vy >= 0 && b.dropT <= 0f && prevBottom <= p.t + 3f && bottom >= p.t) {
                b.y = p.t - b.hBot; land(b)
            }
        }
        if (b.y + b.hBot >= FLOOR) { b.y = FLOOR - b.hBot; land(b) }
        if (b.y < -500f) { b.y = -500f; if (b.vy < 0) b.vy = 0f }
        // tumble
        if (b.launchT > 0f) b.rot += b.spin * dt * 57.3f
        else {
            b.rot = ((b.rot % 360f) + 540f) % 360f - 180f
            b.rot *= max(0f, 1f - dt * 12f)
        }
        if (b.onGround && b.launchT <= 0f) b.vx *= max(0f, 1f - dt * 4f)
        b.flash = max(0f, b.flash - dt * 6f)
    }

    private fun land(b: Body) {
        if (b.launchT > 0f && b.vy > 950f) {
            b.vy = -b.vy * 0.38f
            b.vx *= 0.8f
            puff(b.x, b.y + b.hBot, 5)
        } else {
            b.vy = 0f
            b.onGround = true
        }
    }

    private fun sideHit(b: Body) {
        if (b.launchT > 0f && abs(b.vx) > 900f) {
            b.hp -= 6f
            b.flash = 1f
            b.vx = -b.vx * 0.4f
            shake = max(shake, 10f)
            sfx.play(Sound.THUD)
            puff(b.x, b.y, 10)
            if (b is Player) {
                floaters.add(Floater("SPLAT!", b.x, b.y - 80f, SOFT))
                say(Commentary.Ev.SPLAT, nameOf(b.idx))
            }
            if (b.hp <= 0f) knockOut(b)
        } else b.vx = 0f
    }

    // ----------------------------------------------------------------- players

    private fun updatePlayer(p: Player, dt: Float) {
        if (p.out) return
        if (p.respawnT > 0f) {
            p.respawnT -= dt
            if (p.respawnT <= 0f) spawnPlayer(p, false)
            return
        }
        val c = c(p.idx)
        p.atkCd -= dt; p.swingT -= dt; p.inv = max(0f, p.inv - dt); p.stunT -= dt
        val frozen = mode == BRAWL && (roundIntro > 0f || roundOver > 0f)
        val canAct = p.stunT <= 0f && !frozen

        // block / parry
        val wantBlock = c.block && canAct && p.shield > 5f
        if (wantBlock && !p.blocking) p.blockStart = time
        p.blocking = wantBlock
        if (p.blocking) { p.shield -= 28f * dt; if (p.shield <= 0f) breakShield(p) }
        else p.shield = min(100f, p.shield + 24f * dt)

        // run
        val target = if (p.blocking || !canAct) 0f else c.mx * RUN
        val accel = (if (p.onGround) 5200f else 2800f) * (if (p.launchT > 0f) 0.25f else 1f)
        if (p.vx < target) p.vx = min(target, p.vx + accel * dt) else if (p.vx > target) p.vx = max(target, p.vx - accel * dt)
        if (abs(c.mx) > 0.3f && canAct && p.swingT <= 0f) p.facing = sign(c.mx)

        // jump / double jump / drop through ledges / jetpack
        if (p.onGround) { p.jumps = 1; p.fuel = min(1f, p.fuel + dt * 0.9f) }
        if (c.jumpP && canAct && !p.blocking) {
            if (p.onGround) {
                if (c.my > 0.6f && standingOnLedge(p)) p.dropT = 0.25f
                else { p.vy = -JUMP; p.onGround = false; sfx.play(Sound.JUMP, 0.4f) }
            } else if (p.jumps > 0 && twist != TW_JETPACK) {
                p.vy = -DJUMP; p.jumps--; puff(p.x, p.y + p.hBot, 6); sfx.play(Sound.JUMP, 0.4f)
            }
        }
        if (twist == TW_JETPACK && c.jump && !p.onGround && p.fuel > 0f && canAct) {
            p.vy = max(p.vy - 5600f * dt * grav, -760f)
            p.fuel -= dt * 0.65f
            particles.add(Particle(p.x - p.facing * p.r * 0.6f, p.y + p.r * 0.3f, (rnd.nextFloat() - 0.5f) * 80f, 260f, 0.3f, 8f, BOOMCOL))
        }
        if (!c.jump && p.vy < -300f && p.launchT <= 0f && twist != TW_JETPACK) p.vy += GRAV * grav * dt * 0.9f

        // aim
        val al = sqrt(c.ax * c.ax + c.ay * c.ay)
        if (al > 0.4f) { p.aimX = c.ax / al; p.aimY = c.ay / al; if (abs(c.ax) > 0.2f && canAct) p.facing = sign(c.ax) }
        else {
            p.aimX = p.facing
            p.aimY = if (c.my < -0.5f) -0.8f else if (c.my > 0.6f && !p.onGround) 0.8f else 0f
            val l = sqrt(p.aimX * p.aimX + p.aimY * p.aimY); p.aimX /= l; p.aimY /= l
        }

        // attack
        if (canAct && !p.blocking) {
            when (p.weapon) {
                Wpn.NONE -> if (c.attackP && p.atkCd <= 0f) melee(p, 8f, 80f, 620f, 0.3f)
                Wpn.SWORD -> if (c.attackP && p.atkCd <= 0f) melee(p, 15f, 130f, 900f, 0.4f)
                Wpn.BLASTER, Wpn.SNOW -> if (c.attack && p.atkCd <= 0f) {
                    p.atkCd = if (p.weapon == Wpn.SNOW) 0.2f else 0.13f
                    val a = atan2(p.aimY, p.aimX) + (rnd.nextFloat() - 0.5f) * 0.06f
                    val sp = if (p.weapon == Wpn.SNOW) 1000f else 1400f
                    projs.add(Proj(p.x + cos(a) * p.r, p.y - 4f + sin(a) * p.r, cos(a) * sp, sin(a) * sp,
                        if (p.weapon == Wpn.SNOW) Proj.SNOW else Proj.SEED, p.team, p))
                    sfx.play(Sound.SHOOT, 0.3f)
                    useAmmo(p)
                }
                Wpn.GRENADE -> if (c.attackP && p.atkCd <= 0f) {
                    p.atkCd = 0.45f
                    val gy = if (p.aimY < -0.3f) -1050f else if (p.aimY > 0.3f) 100f else -620f
                    projs.add(Proj(p.x + p.facing * p.r, p.y - p.r * 0.5f, p.facing * 820f + p.vx * 0.4f, gy, Proj.GRENADE, p.team, p).also { it.life = 1.7f })
                    sfx.play(Sound.SWISH, 0.6f)
                    useAmmo(p)
                }
                Wpn.ROCKET -> if (c.attackP && p.atkCd <= 0f) {
                    p.atkCd = 0.65f
                    projs.add(Proj(p.x + p.aimX * p.r * 1.2f, p.y - 4f + p.aimY * p.r * 1.2f, p.aimX * 1050f, p.aimY * 1050f, Proj.ROCKET, p.team, p))
                    p.vx -= p.aimX * 260f
                    sfx.play(Sound.DASH, 0.8f)
                    useAmmo(p)
                }
            }
        }

        // pick up / throw / swap
        val near = items.firstOrNull { !it.dead && it.type != Item.BARREL && abs(it.x - p.x) < p.hw + 34f && abs(it.y - p.y) < p.r + 40f }
        if (c.throwP && canAct) {
            if (near != null) {
                if (p.weapon != Wpn.NONE) items.add(Item(p.x, p.y, p.weapon).also { it.vy = -400f })
                pickUp(p, near)
            } else if (p.weapon != Wpn.NONE) {
                projs.add(Proj(p.x + p.facing * p.r, p.y - p.r * 0.3f, p.facing * 1100f, -260f, Proj.THROWN, p.team, p).also { it.life = 1.6f })
                p.weapon = Wpn.NONE; p.ammo = 0
                sfx.play(Sound.SWISH)
            }
        } else if (near != null && p.weapon == Wpn.NONE) pickUp(p, near)

        step(p, dt)
        p.moving = abs(p.vx) > 40f
        if (p.moving || p.launchT > 0f) p.phase += dt * (if (p.launchT > 0f) 20f else 14f)

        // hot floor
        if (twist == TW_HOTFLOOR && p.onGround && p.y + p.hBot >= FLOOR - 1f && p.inv <= 0f) {
            p.hp -= 30f * dt
            if (rnd.nextFloat() < 0.4f) particles.add(Particle(p.x + (rnd.nextFloat() - 0.5f) * 40f, FLOOR, 0f, -120f, 0.4f, 6f, LOWCOL))
            if (p.hp <= 0f) knockOut(p)
        }
    }

    private fun useAmmo(p: Player) {
        p.ammo--
        if (p.ammo <= 0) { p.weapon = if (twist == TW_SWORDS) Wpn.SWORD else Wpn.NONE; p.ammo = 0 }
    }

    private fun pickUp(p: Player, it: Item) {
        it.dead = true
        p.weapon = it.type; p.ammo = Wpn.ammo(it.type)
        sfx.play(Sound.PICKUP, 0.6f)
        floaters.add(Floater(Wpn.NAMES[it.type], p.x, p.y - 80f, SOFT))
        if (it.type == Wpn.ROCKET || it.type == Wpn.GRENADE || it.gift) say(Commentary.Ev.POWER, nameOf(p.idx))
    }

    private fun breakShield(p: Player) {
        p.blocking = false; p.shield = 0f; p.stunT = 1.3f
        sfx.play(Sound.CLANG, 0.8f)
        floaters.add(Floater("SHIELD BROKEN", p.x, p.y - 80f, LOWCOL))
    }

    private fun targets(team: Int): List<Body> {
        val out = ArrayList<Body>()
        for (p in players) if (p.active && !p.out && p.respawnT <= 0f && p.team != team) out.add(p)
        for (e in enemies) if (!e.dead && e.team != team) out.add(e)
        return out
    }

    private fun melee(p: Player, dmg: Float, range: Float, kb: Float, cd: Float) {
        p.atkCd = cd; p.swingT = 0.2f
        p.vx += p.facing * 140f
        sfx.play(Sound.SWISH, 0.6f)
        val up = p.aimY < -0.5f
        for (t in targets(p.team)) {
            val dx = (t.x - p.x) * p.facing; val dy = t.y - p.y
            val inReach = if (up) abs(t.x - p.x) < range * 0.6f + t.hw && dy < 30f && dy > -(range + t.r)
                          else dx > -t.hw * 0.5f && dx < range + t.hw && abs(dy) < 50f + t.r * 0.7f
            if (inReach) {
                if (up) hit(t, dmg, p.facing * kb * 0.25f, -kb * 1.05f, p)
                else hit(t, dmg, p.facing * kb, -kb * 0.5f - 120f, p)
            }
        }
        for (b in ArrayList(items)) if (b.type == Item.BARREL && !b.dead && abs(b.x - p.x) < range + 30f && abs(b.y - p.y) < 80f) explodeBarrel(b, p)
    }

    /** Returns true if the hit was parried. */
    private fun hit(t: Body, dmgIn: Float, kbxIn: Float, kbyIn: Float, by: Body?, isProj: Boolean = false): Boolean {
        if (t.dead || t.inv > 0f) return false
        if (t is Player && (t.respawnT > 0f || t.out)) return false
        var dmg = dmgIn; var kbx = kbxIn; var kby = kbyIn
        if (t is Player && t.blocking) {
            if (time - t.blockStart < PARRY_WIN) {
                sfx.play(Sound.CLANG)
                rings.add(Ring(t.x, t.y, 110f, Color.WHITE))
                floaters.add(Floater("PARRY!", t.x, t.y - 90f, Color.WHITE))
                if (by != null && !isProj) { by.stunT = 0.9f; by.vx = -sign(kbx) * 520f; by.vy = -300f }
                say(Commentary.Ev.PARRY, nameOf(t.idx))
                return true
            }
            t.shield -= dmg * 2.5f
            dmg *= 0.2f; kbx *= 0.25f; kby *= 0.25f
            sfx.play(Sound.CLANG, 0.35f)
            if (t.shield <= 0f) breakShield(t)
        }
        t.hp -= dmg
        t.lastHitBy = by
        var scale = 1f + (1f - t.hp / t.maxHp).coerceIn(0f, 1f) * 1.3f
        if (t is Enemy) scale *= when (t.type) { BRICK -> 0.5f; BOSS -> 0.15f; else -> 1f }
        t.vx = kbx * scale
        t.vy = min(t.vy, kby * scale)
        val power = sqrt(kbx * kbx + kby * kby) * scale
        if (power > 600f) {
            t.launchT = 0.3f + scale * 0.2f
            t.spin = (if (kbx >= 0f) 1f else -1f) * (6f + rnd.nextFloat() * 8f)
            t.onGround = false
        }
        t.flash = 1f
        sfx.play(Sound.HIT, 0.5f)
        spark(t.x, t.y, t.look.color)
        if (t is Player && power > 1300f) { shake = max(shake, 8f); say(Commentary.Ev.BIG_HIT, nameOf(t.idx)) }
        if (t.hp <= 0f) knockOut(t)
        return false
    }

    private fun knockOut(b: Body) {
        if (b.dead) return
        if (b is Player && (b.respawnT > 0f || b.out)) return
        burst(b.x, b.y, b.look.color, 26, 480f)
        sfx.play(Sound.POP)
        shake = max(shake, 10f)
        val killer = b.lastHitBy
        if (b is Player) {
            if (b.weapon != Wpn.NONE && b.weapon != Wpn.SWORD) items.add(Item(b.x, b.y, b.weapon).also { it.vy = -500f })
            b.weapon = Wpn.NONE
            b.hp = 0f
            b.blocking = false
            if (killer is Player && killer !== b) {
                killer.kos++
                streakN[killer.idx]++; streakT[killer.idx] = 8f
            }
            if (mode == BRAWL) {
                b.lives--
                if (b.lives <= 0) { b.out = true; floaters.add(Floater("${nameOf(b.idx).uppercase()} IS OUT", b.x.coerceIn(200f, W - 200f), 300f, LOWCOL)) }
                else b.respawnT = 1.8f
                say(Commentary.Ev.KO, nameOf(b.idx), force = true)
            } else {
                if (teamLives > 0) { teamLives--; b.respawnT = 3f } else b.out = true
                say(Commentary.Ev.DOWN, nameOf(b.idx), force = true)
            }
        } else if (b is Enemy) {
            b.dead = true
            if (killer is Player) {
                killer.kills++
                streakN[killer.idx]++; streakT[killer.idx] = 4f
                if (streakN[killer.idx] == 8) say(Commentary.Ev.STREAK, nameOf(killer.idx))
            }
            if (b.type == BOSS) {
                shake = 20f
                floaters.add(Floater("MEGA NUG DEFEATED!", b.x, b.y - 140f, ACCENT))
                for (i in 0 until 3) items.add(Item(b.x - 60f + i * 60f, b.y, intArrayOf(Wpn.ROCKET, Wpn.GRENADE, Wpn.SWORD)[i]))
            } else if (rnd.nextFloat() < 0.08f) items.add(Item(b.x, b.y, 1 + rnd.nextInt(4)))
        }
    }

    // ----------------------------------------------------------------- bad nugs

    private fun updateEnemy(e: Enemy, dt: Float) {
        if (e.dead) return
        e.stunT -= dt; e.atkCd -= dt; e.thinkT -= dt
        var tgt: Player? = null; var td = Float.MAX_VALUE
        for (p in players) if (p.active && !p.out && p.respawnT <= 0f) {
            val d = abs(p.x - e.x) + abs(p.y - e.y) * 0.7f
            if (d < td) { td = d; tgt = p }
        }
        val canAct = e.stunT <= 0f && e.launchT <= 0f
        if (tgt != null && canAct) {
            val dx = tgt.x - e.x; val dy = tgt.y - e.y
            val dir = if (dx >= 0f) 1f else -1f
            e.facing = dir
            val keepAway = e.type == SPITTER && abs(dx) < 420f && abs(dy) < 200f
            val want = if (keepAway) -dir * e.speed * 0.6f else if (abs(dx) > e.hw + tgt.hw) dir * e.speed else 0f
            val acc = if (e.onGround) 3000f else 1400f
            if (e.vx < want) e.vx = min(want, e.vx + acc * dt) else if (e.vx > want) e.vx = max(want, e.vx - acc * dt)
            if (e.onGround && e.thinkT <= 0f) {
                e.thinkT = 0.35f + rnd.nextFloat() * 0.6f
                if (dy < -90f && abs(dx) < 520f) e.vy = -1080f
                else if (dy > 120f && standingOnLedge(e)) e.dropT = 0.3f
                else if (abs(e.vx) < 30f && abs(dx) > 80f) e.vy = -1000f
            }
            if (e.type == SPITTER) {
                if (e.atkCd <= 0f && abs(dx) < 900f) {
                    e.atkCd = 2f
                    val l = max(1f, sqrt(dx * dx + dy * dy))
                    projs.add(Proj(e.x + dx / l * e.r, e.y + dy / l * e.r, dx / l * 650f, dy / l * 650f, Proj.SPIT, 9, e))
                    sfx.play(Sound.HIT, 0.3f)
                }
            } else if (abs(dx) < e.hw + tgt.hw + 30f && abs(dy) < e.r + 50f && e.atkCd <= 0f) {
                e.atkCd = if (e.type == SHRIVEL) 0.8f else 1.1f
                e.vx += e.facing * 160f
                hit(tgt, e.dmg, e.facing * e.kb, -e.kb * 0.5f, e)
            }
            if (e.type == BOSS) {
                e.spawnT -= dt; e.poundT -= dt
                if (e.spawnT <= 0f) { e.spawnT = 5f; spawnEnemy(SHRIVEL, e.x, e.y - e.r); spawnEnemy(SHRIVEL, e.x, e.y - e.r) }
                if (e.poundT <= 0f && e.onGround) { e.poundT = 4.5f; e.vy = -1300f; e.wasAir = true }
            }
        }
        val inAir = !e.onGround
        step(e, dt)
        if (e.type == BOSS && e.wasAir && inAir && e.onGround) {
            e.wasAir = false
            explode(e.x, e.y + e.hBot, 300f, 14f, 1000f, e, 9, boom = false)
            shake = 18f
            sfx.play(Sound.THUD)
        }
        e.moving = abs(e.vx) > 30f
        if (e.moving || e.launchT > 0f) e.phase += dt * (if (e.launchT > 0f) 20f else 8f + e.speed * 0.02f)
    }

    // ----------------------------------------------------------------- projectiles, items, explosions

    private fun insideSolid(x: Float, y: Float) = plats.any { it.solid && x > it.l && x < it.r && y > it.t && y < it.b }

    private fun updateProjs(dt: Float) {
        for (i in projs.indices) {
            val pr = projs[i]
            if (pr.dead) continue
            pr.life -= dt
            when (pr.type) {
                Proj.GRENADE -> {
                    pr.vy += GRAV * grav * dt
                    val prevY = pr.y
                    pr.x += pr.vx * dt; pr.y += pr.vy * dt
                    pr.rot += pr.vx * dt * 0.02f
                    if (pr.y > FLOOR - 12f) { pr.y = FLOOR - 12f; pr.vy = -pr.vy * 0.45f; pr.vx *= 0.7f }
                    for (p in plats) {
                        if (pr.x < p.l || pr.x > p.r) continue
                        if (pr.vy > 0 && prevY <= p.t - 10f && pr.y > p.t - 12f) { pr.y = p.t - 12f; pr.vy = -pr.vy * 0.45f; pr.vx *= 0.7f }
                    }
                    if (insideSolid(pr.x, pr.y)) { pr.vx = -pr.vx * 0.5f; pr.x += pr.vx * dt * 2f }
                    if (pr.x < 12f || pr.x > W - 12f) pr.vx = -pr.vx * 0.6f
                    var touched = false
                    for (t in targets(pr.team)) if (dist(t.x, t.y, pr.x, pr.y) < t.r + 14f) touched = true
                    if (pr.life <= 0f || touched) { pr.dead = true; explode(pr.x, pr.y, 190f, 26f, 1250f, pr.owner, pr.team) }
                }
                Proj.ROCKET -> {
                    pr.x += pr.vx * dt; pr.y += pr.vy * dt
                    if (rnd.nextFloat() < 0.7f) particles.add(Particle(pr.x, pr.y, -pr.vx * 0.05f, -pr.vy * 0.05f, 0.4f, 9f, 0x88A0A0A0.toInt()))
                    var boom = pr.life <= 0f || pr.x < 0 || pr.x > W || pr.y > FLOOR || pr.y < -200f || insideSolid(pr.x, pr.y)
                    if (!boom) for (t in targets(pr.team)) {
                        if (dist(t.x, t.y, pr.x, pr.y) < t.r + 12f) {
                            if (t is Player && t.blocking && time - t.blockStart < PARRY_WIN) { reflect(pr, t); break }
                            boom = true; break
                        }
                    }
                    if (!boom) for (b in items) if (b.type == Item.BARREL && !b.dead && dist(b.x, b.y, pr.x, pr.y) < 34f) boom = true
                    if (boom && !pr.dead) { pr.dead = true; explode(pr.x, pr.y, 170f, 22f, 1150f, pr.owner, pr.team) }
                }
                else -> { // seeds, spit, snowballs, thrown weapons
                    if (pr.type == Proj.THROWN) { pr.vy += GRAV * 0.5f * dt; pr.rot += dt * 900f }
                    if (pr.type == Proj.SNOW) pr.vy += GRAV * 0.15f * dt
                    pr.x += pr.vx * dt; pr.y += pr.vy * dt
                    if (pr.life <= 0f || pr.x < -20 || pr.x > W + 20 || pr.y > FLOOR || pr.y < -300f || insideSolid(pr.x, pr.y)) {
                        pr.dead = true; spark(pr.x, pr.y, DUST); continue
                    }
                    val barrel = items.firstOrNull { it.type == Item.BARREL && !it.dead && dist(it.x, it.y, pr.x, pr.y) < 30f }
                    if (barrel != null) { pr.dead = true; explodeBarrel(barrel, pr.owner); continue }
                    for (t in targets(pr.team)) {
                        if (dist(t.x, t.y, pr.x, pr.y) < t.r + 10f) {
                            val l = max(1f, sqrt(pr.vx * pr.vx + pr.vy * pr.vy))
                            val dmg: Float; val kb: Float
                            when (pr.type) {
                                Proj.SEED -> { dmg = 5f; kb = 280f }
                                Proj.SNOW -> { dmg = 3f; kb = 620f }
                                Proj.SPIT -> { dmg = 8f; kb = 350f }
                                else -> { dmg = 12f; kb = 760f }
                            }
                            val parried = hit(t, dmg, pr.vx / l * kb, pr.vy / l * kb - kb * 0.35f, pr.owner, isProj = true)
                            if (parried) reflect(pr, t) else { pr.dead = true; if (pr.type == Proj.SNOW) burst(pr.x, pr.y, Color.WHITE, 6, 150f) }
                            break
                        }
                    }
                }
            }
        }
        projs.removeAll { it.dead }
    }

    private fun reflect(pr: Proj, by: Body) {
        pr.vx = -pr.vx * 1.15f; pr.vy = -pr.vy
        pr.team = by.team; pr.owner = by
        pr.life = 2f
        sfx.play(Sound.CLANG)
    }

    private fun updateItems(dt: Float) {
        for (it in items) {
            if (it.dead) continue
            val prevY = it.y
            if (!it.landed) {
                it.vy = min(it.vy + GRAV * grav * dt, 1800f)
                it.y += it.vy * dt
                var ground = FLOOR - 22f
                for (p in plats) if (it.x > p.l && it.x < p.r && prevY <= p.t - 21f && p.t - 22f < ground && it.y >= p.t - 22f) ground = p.t - 22f
                if (it.y >= ground) { it.y = ground; it.vy = 0f; it.landed = true }
            } else {
                it.life -= dt
                if (it.life <= 0f) it.dead = true
            }
        }
        items.removeAll { it.dead }
    }

    private fun explodeBarrel(b: Item, by: Body?) {
        if (b.dead) return
        b.dead = true
        explode(b.x, b.y, 220f, 28f, 1300f, by, -1)
    }

    private val chain = ArrayList<Item>()

    private fun explode(x: Float, y: Float, radius: Float, dmg: Float, kb: Float, owner: Body?, team: Int, boom: Boolean = true) {
        if (boom) {
            sfx.play(Sound.BOOM)
            burst(x, y, BOOMCOL, 30, 650f)
            burst(x, y, 0xFF6A6A6A.toInt(), 16, 300f)
            rings.add(Ring(x, y, radius, BOOMCOL))
            shake = max(shake, 14f)
        } else rings.add(Ring(x, y, radius, DUST))
        val victims = ArrayList<Body>()
        for (p in players) if (p.active && !p.out && p.respawnT <= 0f) victims.add(p)
        for (e in enemies) if (!e.dead) victims.add(e)
        for (b in victims) {
            if (team == 9 && b is Enemy) continue
            if (team >= 0 && team != 9 && b.team == team && b !== owner) continue
            val dx = b.x - x; val dy = b.y - y
            val d = sqrt(dx * dx + dy * dy)
            if (d > radius + b.r) continue
            val f = 1f - (d / (radius + b.r)) * 0.6f
            val nx = if (d > 1f) dx / d else 0f
            val ny = if (d > 1f) dy / d else -1f
            val self = if (b === owner) 0.5f else 1f
            hit(b, dmg * f * self, nx * kb * f, (ny - 0.6f) * kb * f, owner, isProj = true)
        }
        for (b in items) if (b.type == Item.BARREL && !b.dead && dist(b.x, b.y, x, y) < radius && !chain.contains(b)) chain.add(b)
    }

    private fun updateFx(dt: Float) {
        if (chain.isNotEmpty()) {
            val now = ArrayList(chain); chain.clear()
            for (b in now) explodeBarrel(b, null)
            items.removeAll { it.dead }
        }
        for (p in particles) {
            p.vy += p.grav * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.94f; p.vy *= 0.94f
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        if (particles.size > 700) particles.subList(0, particles.size - 700).clear()
        for (f in floaters) { f.life -= dt; f.y -= 40f * dt }
        floaters.removeAll { it.life <= 0f }
        for (r in rings) r.t += dt
        rings.removeAll { it.t > 0.4f }
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val s = speed * (0.3f + rnd.nextFloat() * 0.7f)
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, 0.4f + rnd.nextFloat() * 0.5f, 5f + rnd.nextFloat() * 9f, color, 900f))
        }
    }

    private fun spark(x: Float, y: Float, color: Int) {
        for (i in 0 until 4) {
            val a = rnd.nextFloat() * 6.283f
            particles.add(Particle(x, y, cos(a) * 200f, sin(a) * 200f, 0.25f, 5f, color))
        }
    }

    private fun puff(x: Float, y: Float, n: Int) {
        for (i in 0 until n) particles.add(Particle(x + (rnd.nextFloat() - 0.5f) * 30f, y, (rnd.nextFloat() - 0.5f) * 220f, -rnd.nextFloat() * 80f, 0.35f, 7f, 0x66FFFFFF))
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    // ----------------------------------------------------------------- pause / over

    private val pauseItems = arrayOf("Resume", "Restart", "Quit to Menu")

    private fun updatePause(taps: List<FloatArray>) {
        if (anyBack() || anyStart()) { state = S_PLAY; return }
        when (listMenu(3, 520f, 100f, 600f, taps)) {
            0 -> state = S_PLAY
            1 -> startMatch()
            2 -> go(S_TITLE)
        }
    }

    private fun updateOver(taps: List<FloatArray>) {
        if (input.ctl.any { it.leftP || it.rightP || it.upP || it.downP }) { idx = 1 - idx.coerceIn(0, 1); sfx.play(Sound.SELECT) }
        var choose = anyConfirm() || anyStart()
        if (anyBack()) { idx = 1; choose = true }
        for (t in taps) {
            if (inRect(t, W / 2 - 520, 860f, W / 2 - 40, 980f)) { idx = 0; choose = true }
            if (inRect(t, W / 2 + 40, 860f, W / 2 + 520, 980f)) { idx = 1; choose = true }
        }
        if (!choose) return
        sfx.play(Sound.SELECT)
        if (idx == 0) startMatch() else go(S_TITLE)
    }

    // ----------------------------------------------------------------- christmas

    private val lightCols = intArrayOf(0xFFC46A6A.toInt(), 0xFF9CC79A.toInt(), 0xFFD8C27A.toInt(), 0xFF8FA3C9.toInt())

    private fun updateChristmas(dt: Float) {
        for (i in 0 until 160) {
            flakes[i * 4 + 1] += flakes[i * 4 + 2] * dt
            flakes[i * 4] += sin(time * 0.8f + i) * 12f * dt
            if (flakes[i * 4 + 1] > H + 10f) { flakes[i * 4 + 1] = -10f; flakes[i * 4] = rnd.nextFloat() * W }
        }
        if (santaT > 0f) {
            santaT -= dt
            if (santaT <= 0f) {
                santaDir = if (rnd.nextBoolean()) 1f else -1f
                santaX = if (santaDir > 0) -400f else W + 400f
                santaY = 150f + rnd.nextFloat() * 50f
                santaDropped = false
                sfx.play(Sound.JINGLE, 0.5f)
            }
            return
        }
        santaX += santaDir * 380f * dt
        if (state == S_PLAY && !santaDropped && abs(santaX - W / 2) < 500f && rnd.nextFloat() < dt * 2f) {
            santaDropped = true
            val t = if (twist == TW_SNOW) Wpn.SNOW else intArrayOf(Wpn.ROCKET, Wpn.GRENADE, Wpn.SWORD, Wpn.SNOW)[rnd.nextInt(4)]
            items.add(Item(santaX.coerceIn(80f, W - 80f), santaY + 40f, t).also { it.gift = true })
            floaters.add(Floater("Ho ho ho! A present!", santaX.coerceIn(200f, W - 200f), santaY + 90f, SOFT))
            say(Commentary.Ev.SANTA)
        }
        if (santaX < -900f || santaX > W + 900f) santaT = 25f + rnd.nextFloat() * 25f
    }

    private fun drawSnowAndSanta(c: Canvas) {
        if (!xmas) return
        val f = Gfx.fill
        f.color = 0xB0FFFFFF.toInt()
        for (i in 0 until 160) c.drawCircle(flakes[i * 4], flakes[i * 4 + 1], flakes[i * 4 + 3], f)
        if (santaT > 0f) return
        val y = santaY + sin(time * 3f) * 10f
        c.save()
        c.translate(santaX, y)
        c.scale(santaDir, 1f)
        val s = Gfx.stroke
        s.color = 0xFF6A6660.toInt(); s.strokeWidth = 2f
        c.drawLine(40f, 4f, 250f, -4f, s)
        for (k in 0 until 3) {
            val rx = 110f + k * 70f; val ry = -6f + sin(time * 8f + k) * 4f
            f.color = 0xFF6E5A48.toInt()
            c.drawOval(rx - 24f, ry - 10f, rx + 24f, ry + 10f, f)
            c.drawCircle(rx + 26f, ry - 12f, 9f, f)
            s.color = 0xFF6E5A48.toInt(); s.strokeWidth = 4f
            val leg = sin(time * 14f + k) * 8f
            c.drawLine(rx - 14f, ry + 6f, rx - 18f - leg, ry + 24f, s); c.drawLine(rx + 14f, ry + 6f, rx + 18f + leg, ry + 24f, s)
            s.color = 0xFFA89A88.toInt(); s.strokeWidth = 3f
            c.drawLine(rx + 24f, ry - 20f, rx + 18f, ry - 34f, s); c.drawLine(rx + 28f, ry - 20f, rx + 34f, ry - 34f, s)
            if (k == 2) { f.color = Gfx.alpha(0xFFE07070.toInt(), 0.6f + 0.4f * sin(time * 6f)); c.drawCircle(rx + 35f, ry - 10f, 5f, f) }
        }
        f.color = 0xFF8A3E3E.toInt()
        c.drawRoundRect(-60f, -14f, 40f, 22f, 14f, 14f, f)
        s.color = 0xFFB8B0A0.toInt(); s.strokeWidth = 4f
        c.drawLine(-66f, 32f, 46f, 32f, s); c.drawLine(46f, 32f, 54f, 22f, s)
        f.color = 0xFFA84848.toInt(); c.drawCircle(-18f, -26f, 20f, f)
        f.color = 0xFFE8DCCB.toInt(); c.drawCircle(-6f, -32f, 10f, f)
        f.color = 0xFFF2F2F2.toInt(); c.drawCircle(-2f, -24f, 9f, f)
        f.color = 0xFFA84848.toInt(); c.drawCircle(-10f, -46f, 9f, f)
        f.color = Color.WHITE; c.drawCircle(-20f, -52f, 5f, f)
        f.color = 0xFF6E5A48.toInt(); c.drawCircle(-46f, -22f, 16f, f)
        c.restore()
    }

    // ================================================================= drawing

    fun draw(c: Canvas) {
        when (state) {
            S_TITLE -> drawTitle(c)
            S_SETTINGS -> drawSettings(c)
            S_HELP -> drawHelp(c)
            S_WALLMENU -> drawWallMenu(c)
            S_EDITOR -> drawEditor(c)
            S_ARENA -> drawArenaSelect(c)
            S_LOBBY -> drawLobby(c)
            S_PLAY, S_PAUSE, S_OVER -> {
                c.save()
                if (shake > 0f && state == S_PLAY) c.translate((rnd.nextFloat() - 0.5f) * shake, (rnd.nextFloat() - 0.5f) * shake)
                drawWorld(c)
                c.restore()
                drawSnowAndSanta(c)
                drawHud(c)
                if (commentary && state != S_PAUSE) talk.draw(c, 24f, FLOOR - 16f, 380f, W / 2, FLOOR - 24f)
                if (state == S_PLAY) drawTouchControls(c)
                if (state == S_PAUSE) drawPause(c)
                if (state == S_OVER) drawOver(c)
            }
        }
        if (state == S_TITLE || state == S_SETTINGS) {
            for (f in floaters) Gfx.label(c, f.text, f.x, f.y, 36f, Gfx.alpha(f.color, f.life))
            for (f in floaters) f.life -= 1f / 120f
            floaters.removeAll { it.life <= 0f }
        }
    }

    private fun backdrop(c: Canvas) {
        if (projector) { c.drawColor(Color.BLACK); return }
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.fill.color = 0x10FFFFFF
        var gy = 40f
        while (gy < FLOOR) { var gx = 40f; while (gx < W) { c.drawCircle(gx, gy, 2f, Gfx.fill); gx += 80f }; gy += 80f }
    }

    private fun drawPlats(c: Canvas, list: List<Plat>, scale: Float = 1f, ox: Float = 0f, oy: Float = 0f, outlineOnly: Boolean = projector) {
        val f = Gfx.fill; val s = Gfx.stroke
        for ((k, p) in list.withIndex()) {
            val l = ox + p.l * scale; val t = oy + p.t * scale; val r = ox + p.r * scale; val b = oy + p.b * scale
            if (outlineOnly) {
                s.color = if (p.solid) 0x38FFFFFF else 0x60FFFFFF; s.strokeWidth = if (p.solid) 2f else 3f
                if (p.solid) c.drawRoundRect(l, t, r, b, 6f, 6f, s) else c.drawLine(l, t, r, t, s)
            } else {
                f.color = if (p.solid) 0xFF1C1E1D.toInt() else 0xFF2A2D2B.toInt()
                c.drawRoundRect(l, t, r, b, 6f * scale, 6f * scale, f)
                f.color = 0xFF3E4240.toInt()
                c.drawRect(l, t, r, t + 4f * scale, f)
            }
            if (xmas && scale == 1f) {
                f.color = 0xFFE6E8E6.toInt()
                c.drawRoundRect(l - 3f, t - 8f, r + 3f, t + 3f, 6f, 6f, f)
                var x = l + 14f; var n = k * 3
                while (x < r - 8f) {
                    val tw = (sin(time * 3f + n * 1.3f) + 1f) / 2f
                    f.color = Gfx.alpha(lightCols[n % 4], 0.3f + 0.7f * tw)
                    c.drawCircle(x, t + 9f + sin(x * 0.06f) * 3f, 4.5f, f)
                    x += 34f; n++
                }
            }
        }
    }

    private fun drawWorld(c: Canvas) {
        backdrop(c)
        val f = Gfx.fill
        if (twist == TW_HOTFLOOR && mode == BRAWL) {
            f.color = Gfx.alpha(LOWCOL, 0.35f + 0.15f * sin(time * 5f)); c.drawRect(0f, FLOOR - 6f, W, H, f)
        } else if (!projector) { f.color = 0xFF1A1C1B.toInt(); c.drawRect(0f, FLOOR, W, H, f) }
        drawPlats(c, plats)

        for (it in items) drawItem(c, it)
        for (e in enemies) drawBody(c, e, 1, false)
        for (p in players) if (p.active && !p.out && p.respawnT <= 0f) drawPlayer(c, p)
        for (pr in projs) drawProj(c, pr)
        for (p in particles) {
            f.color = Gfx.alpha(p.color, p.life / p.maxLife)
            c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * p.life / p.maxLife), f)
        }
        val s = Gfx.stroke
        for (r in rings) {
            val k = r.t / 0.4f
            s.color = Gfx.alpha(r.color, 1f - k); s.strokeWidth = 6f * (1f - k) + 1f
            c.drawCircle(r.x, r.y, r.maxR * (0.3f + 0.7f * k), s)
        }
        for (fl in floaters) Gfx.label(c, fl.text, fl.x, fl.y, 36f, Gfx.alpha(fl.color, min(1f, fl.life * 2f)))
    }

    private fun drawBody(c: Canvas, b: Body, mood: Int, hat: Boolean) {
        c.save()
        if (abs(b.rot) > 0.5f) c.rotate(b.rot, b.x, b.y)
        val blink = b.inv > 0f && ((time * 12f).toInt() % 2 == 0)
        Gfx.nug(c, b.x, b.y, b.r, b.look, b.facing * 0.8f, 0f, b.phase, b.moving, b.flash,
            if (b.stunT > 0f) 2 else mood, if (blink) 0.45f else 1f, hat, flail = b.launchT > 0f, shadow = b.onGround)
        c.restore()
        if (b is Enemy && b.hp < b.maxHp) Gfx.bar(c, b.x - b.r, b.y - b.r * 1.5f - 12f, b.r * 2, 7f, b.hp / b.maxHp, LOWCOL)
    }

    private fun drawPlayer(c: Canvas, p: Player) {
        drawBody(c, p, 0, xmas)
        val s = Gfx.stroke; val f = Gfx.fill
        if (p.weapon != Wpn.NONE && p.launchT <= 0f) {
            val hx = p.x + p.facing * p.r * 0.75f; val hy = p.y + p.r * 0.15f
            when (p.weapon) {
                Wpn.SWORD -> {
                    val k = if (p.swingT > 0f) 1f - p.swingT / 0.2f else -1f
                    val deg = if (k < 0f) -65.0 else -110.0 + 150.0 * k
                    val a = Math.toRadians(deg).toFloat()
                    val dx = cos(a) * p.facing; val dy = sin(a)
                    s.color = 0xFFD0D4D0.toInt(); s.strokeWidth = 7f
                    c.drawLine(hx, hy, hx + dx * 80f, hy + dy * 80f, s)
                    s.color = 0xFF6E5A48.toInt(); s.strokeWidth = 9f
                    c.drawLine(hx - dx * 6f, hy - dy * 6f, hx + dx * 14f, hy + dy * 14f, s)
                    if (p.swingT > 0f) {
                        s.color = 0x55FFFFFF; s.strokeWidth = 4f
                        c.drawArc(p.x - 130f, p.y - 130f, p.x + 130f, p.y + 130f, if (p.facing > 0) -70f else 110f, 140f, false, s)
                    }
                }
                Wpn.BLASTER, Wpn.SNOW, Wpn.ROCKET -> {
                    val len = if (p.weapon == Wpn.ROCKET) 70f else 46f
                    s.color = if (p.weapon == Wpn.ROCKET) 0xFF5E6A5A.toInt() else if (p.weapon == Wpn.SNOW) 0xFFC8D4DC.toInt() else 0xFF8F948F.toInt()
                    s.strokeWidth = if (p.weapon == Wpn.ROCKET) 18f else 12f
                    c.drawLine(hx - p.aimX * 10f, hy - p.aimY * 10f, hx + p.aimX * len, hy + p.aimY * len, s)
                }
                Wpn.GRENADE -> { f.color = 0xFF5E6A5A.toInt(); c.drawCircle(hx, hy, 12f, f) }
            }
        } else if (p.swingT > 0f) {
            s.color = 0x44FFFFFF; s.strokeWidth = 4f
            c.drawArc(p.x - 90f, p.y - 90f, p.x + 90f, p.y + 90f, if (p.facing > 0) -40f else 140f, 80f, false, s)
        }
        if (p.blocking) {
            s.color = Gfx.alpha(p.look.light, 0.35f + 0.5f * p.shield / 100f); s.strokeWidth = 5f
            c.drawCircle(p.x, p.y + 4f, p.r * 1.5f, s)
            f.color = Gfx.alpha(p.look.light, 0.1f); c.drawCircle(p.x, p.y + 4f, p.r * 1.5f, f)
        }
        Gfx.label(c, "P${p.idx + 1}", p.x, p.y - p.r * 1.9f + (if (xmas) -14f else 0f), 26f, p.look.light, shadow = false)
    }

    private fun drawProj(c: Canvas, pr: Proj) {
        val f = Gfx.fill
        when (pr.type) {
            Proj.SEED -> { f.color = 0xFFC9B27A.toInt(); c.drawOval(pr.x - 9f, pr.y - 6f, pr.x + 9f, pr.y + 6f, f) }
            Proj.SPIT -> { f.color = 0xFFA6965C.toInt(); c.drawCircle(pr.x, pr.y, 11f, f) }
            Proj.SNOW -> { f.color = 0xFFF2F4F2.toInt(); c.drawCircle(pr.x, pr.y, 13f, f) }
            Proj.GRENADE -> {
                f.color = 0xFF5E6A5A.toInt(); c.drawCircle(pr.x, pr.y, 13f, f)
                if ((time * (if (pr.life < 0.6f) 16f else 6f)).toInt() % 2 == 0) { f.color = LOWCOL; c.drawCircle(pr.x, pr.y - 12f, 4f, f) }
            }
            Proj.ROCKET -> {
                c.save(); c.rotate(Math.toDegrees(atan2(pr.vy, pr.vx).toDouble()).toFloat(), pr.x, pr.y)
                f.color = 0xFFB0B4B0.toInt(); c.drawRoundRect(pr.x - 20f, pr.y - 7f, pr.x + 16f, pr.y + 7f, 7f, 7f, f)
                f.color = LOWCOL; c.drawCircle(pr.x + 16f, pr.y, 7f, f)
                f.color = BOOMCOL; c.drawCircle(pr.x - 24f, pr.y, 6f + rnd.nextFloat() * 4f, f)
                c.restore()
            }
            else -> {
                c.save(); c.rotate(pr.rot, pr.x, pr.y)
                Gfx.stroke.color = 0xFFD0D4D0.toInt(); Gfx.stroke.strokeWidth = 7f
                c.drawLine(pr.x - 30f, pr.y, pr.x + 30f, pr.y, Gfx.stroke)
                c.restore()
            }
        }
    }

    private fun drawItem(c: Canvas, it: Item) {
        if (it.landed && it.life < 3f && it.type != Item.BARREL && ((time * 8f).toInt() % 2 == 0)) return
        val f = Gfx.fill; val s = Gfx.stroke
        val x = it.x; val y = it.y + if (it.landed && it.type != Item.BARREL) sin(time * 4f + x) * 4f else 0f
        if (it.type == Item.BARREL) {
            f.color = 0xFF5A3A2E.toInt(); c.drawRoundRect(x - 24f, y - 22f, x + 24f, y + 22f, 8f, 8f, f)
            f.color = LOWCOL; c.drawRect(x - 24f, y - 6f, x + 24f, y + 6f, f)
            Gfx.label(c, "!", x, y + 9f, 26f, Color.WHITE, shadow = false)
            return
        }
        if (it.gift) {
            f.color = 0xFF8A3E3E.toInt(); c.drawRoundRect(x - 22f, y - 18f, x + 22f, y + 22f, 6f, 6f, f)
            f.color = 0xFFD8C27A.toInt(); c.drawRect(x - 4f, y - 18f, x + 4f, y + 22f, f); c.drawRect(x - 22f, y - 2f, x + 22f, y + 6f, f)
            s.color = 0xFFD8C27A.toInt(); s.strokeWidth = 4f
            c.drawCircle(x - 7f, y - 25f, 6f, s); c.drawCircle(x + 7f, y - 25f, 6f, s)
            return
        }
        s.color = 0x55FFFFFF; s.strokeWidth = 2f
        c.drawCircle(x, y, 30f + sin(time * 5f) * 3f, s)
        f.color = 0xFF2A2D2B.toInt(); c.drawCircle(x, y, 26f, f)
        when (it.type) {
            Wpn.SWORD -> { s.color = 0xFFD0D4D0.toInt(); s.strokeWidth = 6f; c.drawLine(x - 14f, y + 14f, x + 16f, y - 16f, s) }
            Wpn.BLASTER -> { s.color = 0xFF8F948F.toInt(); s.strokeWidth = 10f; c.drawLine(x - 14f, y, x + 14f, y, s) }
            Wpn.SNOW -> { f.color = 0xFFF2F4F2.toInt(); c.drawCircle(x, y, 11f, f) }
            Wpn.GRENADE -> { f.color = 0xFF5E6A5A.toInt(); c.drawCircle(x, y + 2f, 12f, f); f.color = LOWCOL; c.drawCircle(x, y - 11f, 4f, f) }
            Wpn.ROCKET -> { f.color = 0xFFB0B4B0.toInt(); c.drawRoundRect(x - 16f, y - 6f, x + 14f, y + 6f, 6f, 6f, f); f.color = LOWCOL; c.drawCircle(x + 14f, y, 6f, f) }
        }
    }

    private fun drawHud(c: Canvas) {
        val act = players.filter { it.active }
        val n = act.size.coerceAtLeast(1)
        val cardW = min(400f, (W - 560f) / n - 20f)
        val total = n * cardW + (n - 1) * 20f
        var x = (W - total) / 2 + 120f
        if (mode == BRAWL) {
            Gfx.label(c, "ROUND $round", 30f, 48f, 32f, Color.WHITE, Paint.Align.LEFT, shadow = false)
            Gfx.label(c, TWIST_NAMES[twist], 30f, 84f, 24f, MUTED, Paint.Align.LEFT, shadow = false)
        } else {
            Gfx.label(c, "WAVE $wave", 30f, 48f, 36f, Color.WHITE, Paint.Align.LEFT, shadow = false)
            Gfx.label(c, "Team lives $teamLives  ·  Bad nugs ${enemies.size + spawnQueue.size}", 30f, 84f, 22f, MUTED, Paint.Align.LEFT, shadow = false)
        }
        for (p in act) {
            val f = Gfx.fill
            f.color = 0xCC111312.toInt(); c.drawRoundRect(x, 16f, x + cardW, 104f, 18f, 18f, f)
            f.color = p.look.color; c.drawCircle(x + 26f, 42f, 11f, f)
            val label = "P${p.idx + 1} ${nameOf(p.idx)}" + if (mode == BRAWL) "  ${score[p.idx]}★" else "  ${p.kills}"
            Gfx.label(c, label, x + 46f, 51f, 24f, if (p.out) MUTED else Color.WHITE, Paint.Align.LEFT, shadow = false)
            Gfx.bar(c, x + 16f, 66f, cardW - 32f, 12f, if (p.respawnT > 0f || p.out) 0f else p.hp / p.maxHp, if (p.hp > 35f) HPCOL else LOWCOL)
            if (p.shield < 100f) Gfx.bar(c, x + 16f, 88f, (cardW - 32f) * 0.4f, 6f, p.shield / 100f, 0xFF8FA3C9.toInt())
            if (mode == BRAWL) for (k in 0 until 3) {
                f.color = if (k < p.lives) p.look.light else 0xFF2A2D2B.toInt()
                c.drawCircle(x + cardW - 22f - k * 18f, 42f, 6f, f)
            }
            val wtxt = if (p.out) "OUT" else if (p.respawnT > 0f) "respawning..." else if (p.weapon != Wpn.NONE) Wpn.NAMES[p.weapon] + (if (p.ammo > 0) " ${p.ammo}" else "") else ""
            if (wtxt.isNotEmpty()) Gfx.label(c, wtxt, x + cardW - 16f, 97f, 18f, MUTED, Paint.Align.RIGHT, shadow = false)
            x += cardW + 20f
        }
        if (input.touchRecently() && state == S_PLAY) {
            Gfx.fill.color = Color.argb(110, 255, 255, 255)
            c.drawRect(W - 70f, 120f, W - 58f, 150f, Gfx.fill); c.drawRect(W - 50f, 120f, W - 38f, 150f, Gfx.fill)
        }
        if (state != S_PLAY) return
        if (mode == SURVIVAL && bannerT > 0f) {
            val a = min(1f, bannerT)
            Gfx.label(c, "WAVE $wave", W / 2, 440f, 110f, Gfx.alpha(Color.WHITE, a))
            Gfx.label(c, if (wave % 5 == 0) "A Moldy Mega Nug is coming!" else if (wave == 1) "Bad nugs are climbing your wall. Knock them off!" else "Everyone healed a bit",
                W / 2, 510f, 40f, Gfx.alpha(SOFT, a))
        }
        if (mode == BRAWL) {
            if (roundIntro > 0f) {
                Gfx.label(c, "ROUND $round", W / 2, 400f, 100f, Color.WHITE)
                Gfx.label(c, TWIST_NAMES[twist].uppercase(), W / 2, 480f, 56f, ACCENT)
                Gfx.label(c, TWIST_DESC[twist], W / 2, 540f, 36f, SOFT)
                if (roundIntro < 0.8f) Gfx.label(c, "FIGHT!", W / 2, 640f, 80f, Color.WHITE)
            }
            if (roundOver > 0f) {
                if (roundWinner >= 0) Gfx.label(c, "${nameOf(roundWinner).uppercase()} TAKES THE ROUND", W / 2, 480f, 72f, players[roundWinner].look.light)
                else Gfx.label(c, "DRAW!", W / 2, 480f, 90f, Color.WHITE)
            }
        }
    }

    private fun drawTouchControls(c: Canvas) {
        if (!input.touchRecently()) return
        val f = Gfx.fill; val s = Gfx.stroke
        for (p in 0..1) {
            if (!players[p].active) continue
            val col = players[p].look.light
            for (role in 1..3) {
                val bx = InputHub.btnX(p, role); val by = InputHub.BTN_Y[role]; val br = InputHub.BTN_R[role]
                f.color = Gfx.alpha(col, if (input.touchHeld(p, role)) 0.4f else 0.15f)
                c.drawCircle(bx, by, br, f)
                Gfx.label(c, InputHub.BTN_LABEL[role], bx, by + 9f, 26f, Gfx.alpha(Color.WHITE, 0.6f), shadow = false)
            }
            val st = input.touchStick(p)
            if (st != null) {
                s.color = Gfx.alpha(Color.WHITE, 0.3f); s.strokeWidth = 4f
                c.drawCircle(st[0], st[1], InputHub.STICK_RANGE, s)
                var dx = st[2] - st[0]; var dy = st[3] - st[1]
                val l = sqrt(dx * dx + dy * dy)
                if (l > InputHub.STICK_RANGE) { dx = dx / l * InputHub.STICK_RANGE; dy = dy / l * InputHub.STICK_RANGE }
                f.color = Gfx.alpha(Color.WHITE, 0.4f)
                c.drawCircle(st[0] + dx, st[1] + dy, 46f, f)
            }
        }
    }

    private fun dim(c: Canvas, a: Int = 170) { Gfx.fill.color = Color.argb(a, 0, 0, 0); c.drawRect(0f, 0f, W, H, Gfx.fill) }

    private fun menuItem(c: Canvas, text: String, y: Float, selected: Boolean, width: Float = 800f, cx: Float = W / 2) {
        if (selected) {
            Gfx.fill.color = SEL
            c.drawRoundRect(cx - width / 2, y - 56, cx + width / 2, y + 22, 40f, 40f, Gfx.fill)
            Gfx.stroke.color = 0x66FFFFFF; Gfx.stroke.strokeWidth = 2f
            c.drawRoundRect(cx - width / 2, y - 56, cx + width / 2, y + 22, 40f, 40f, Gfx.stroke)
        }
        Gfx.label(c, text, cx, y, 44f, if (selected) Color.WHITE else 0xFF6F7471.toInt(), shadow = false)
    }

    private fun drawPause(c: Canvas) {
        dim(c)
        Gfx.label(c, "PAUSED", W / 2, 380f, 110f, Color.WHITE)
        if (pauseMsg.isNotEmpty()) Gfx.label(c, pauseMsg, W / 2, 440f, 34f, LOWCOL)
        for (i in pauseItems.indices) menuItem(c, pauseItems[i], 520f + i * 100f, i == idx, 600f)
        Gfx.label(c, controllerStatus(), W / 2, 1000f, 26f, 0xFFAAAAAA.toInt())
    }

    private fun drawOver(c: Canvas) {
        dim(c, 170)
        if (mode == SURVIVAL) {
            Gfx.label(c, "THE BAD NUGS TOOK THE WALL", W / 2, 280f, 90f, LOWCOL)
            Gfx.label(c, "You survived until wave $wave", W / 2, 370f, 52f, Color.WHITE)
            Gfx.label(c, "Best ever: wave $bestWave", W / 2, 440f, 38f, SOFT)
            val act = players.filter { it.active }
            var x = W / 2 - (act.size - 1) * 200f
            for (p in act) {
                Gfx.nug(c, x, 600f, 70f, p.look, 0f, 0.3f, p.phase, true, 0f, 0, 1f, xmas)
                Gfx.label(c, "P${p.idx + 1}: ${p.kills} knocked off", x, 780f, 34f, p.look.light)
                x += 400f
            }
        } else {
            val w = players[matchWinner.coerceAtLeast(0)]
            Gfx.label(c, "${nameOf(w.idx).uppercase()} WINS!", W / 2, 280f, 110f, w.look.light)
            Gfx.label(c, players.filter { it.active }.joinToString("    ") { "P${it.idx + 1}: ${score[it.idx]}★ ${it.kos} KOs" }, W / 2, 370f, 38f, Color.WHITE)
            val hop = abs(sin(time * 6f)) * 40f
            Gfx.nug(c, W / 2, 610f - hop, 110f, w.look, 0f, 0.2f, w.phase, true, 0f, 0, 1f, xmas)
        }
        for (i in 0..1) {
            val cx = if (i == 0) W / 2 - 280 else W / 2 + 280
            Gfx.fill.color = if (idx == i) 0x40FFFFFF else 0x18FFFFFF
            c.drawRoundRect(cx - 240, 860f, cx + 240, 980f, 50f, 50f, Gfx.fill)
            Gfx.label(c, if (i == 0) "Play Again" else "Main Menu", cx, 938f, 46f, Color.WHITE, shadow = false)
        }
    }

    private fun controllerStatus(): String {
        val parts = ArrayList<String>()
        for (i in 0..3) {
            val l = input.slotLabel(i)
            if (l.isNotEmpty()) parts.add("P${i + 1}: ${l.take(22)}")
        }
        return if (parts.isEmpty()) "No controller yet: press A on a controller (first one = Player 1)" else parts.joinToString("     ")
    }

    private fun drawTitle(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.fill.color = 0xFF151716.toInt(); c.drawRect(0f, FLOOR, W, H, Gfx.fill)
        for (e in titleNugs) Gfx.nug(c, e.x, e.y, e.r, e.look, e.facing, 0f, e.phase, true, 0f, if (e.type == 1) 1 else 0, 1f, xmas && e.type == 0)
        val bob = sin(time * 2f) * 6f
        Gfx.label(c, "NUG OF WAR", W / 2, 230f + bob, 150f, 0xFFEDEFED.toInt(), shadow = false)
        Gfx.label(c, "a wall brawler for nugs with little legs", W / 2, 305f, 36f, MUTED, shadow = false)
        if (xmas) Gfx.label(c, "Merry Christmas", W / 2, 360f, 32f, LOWCOL, shadow = false)
        for (i in titleItems.indices) menuItem(c, titleItems[i], 470f + i * 84f, i == idx, 760f)
        Gfx.label(c, controllerStatus(), W / 2, 1000f, 26f, 0xFFAAAAAA.toInt(), shadow = false)
        drawSnowAndSanta(c)
    }

    private fun drawSettings(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.label(c, "SETTINGS", W / 2, 200f, 90f, Color.WHITE, shadow = false)
        Gfx.label(c, "Projector Mode: pure black background so only the game lights up your wall", W / 2, 270f, 28f, MUTED, shadow = false)
        val items = settingsItems()
        for (i in items.indices) menuItem(c, items[i], 380f + i * 86f, i == idx, 820f)
        Gfx.label(c, controllerStatus(), W / 2, 1000f, 26f, 0xFFAAAAAA.toInt(), shadow = false)
        drawSnowAndSanta(c)
    }

    private fun drawWallMenu(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.label(c, "WALL SETUP", W / 2, 180f, 90f, Color.WHITE, shadow = false)
        val lines = arrayOf(
            "1. Point the projector at your wall (a little room light helps you see your shelves).",
            "2. Trace each shelf, frame or bit of furniture with the cursor.",
            "   Thin lines become ledges you can jump up through. Tall boxes become solid blocks.",
            "3. Save, then pick that wall as your arena.")
        var y = 260f
        for (l in lines) { Gfx.label(c, l, W / 2, y, 30f, MUTED, shadow = false); y += 44f }
        val items = arrayOf(arenaLabel(0), arenaLabel(1), arenaLabel(2), "Back")
        for (i in items.indices) menuItem(c, if (i < 3) "Edit ${items[i]}" else items[i], 520f + i * 90f, i == idx, 760f)
    }

    private fun drawEditor(c: Canvas) {
        c.drawColor(Color.BLACK)
        val s = Gfx.stroke; val f = Gfx.fill
        s.color = 0x30FFFFFF; s.strokeWidth = 1f
        run {
            var x = 0f; while (x <= W) { c.drawLine(x, 0f, x, H, s); x += 60f }
            var y = 0f; while (y <= H) { c.drawLine(0f, y, W, y, s); y += 60f }
        }
        val hv = if (held >= 0 || anchor != null) -1 else hovered(curX, curY)
        for ((i, p) in editRects.withIndex()) {
            val col = when {
                i == held || (i == touchIdx && touchMode >= 2) -> ACCENT
                i == sel -> 0xFF9FD0FF.toInt()
                i == hv -> 0xFFE0C080.toInt()
                else -> Color.WHITE
            }
            f.color = Gfx.alpha(col, 0.18f); c.drawRect(p.l, p.t, p.r, p.b, f)
            s.color = col; s.strokeWidth = 5f
            if (p.solid) c.drawRect(p.l, p.t, p.r, p.b, s) else c.drawLine(p.l, p.t, p.r, p.t, s)
            if (i == sel || i == held) {           // resize handle
                f.color = col; c.drawCircle(p.r, p.b, 14f, f)
                s.strokeWidth = 2f; s.color = Color.BLACK
                c.drawLine(p.r - 6f, p.b - 6f, p.r + 6f, p.b + 6f, s)
                Gfx.label(c, "${(p.r - p.l).toInt()} × ${(p.b - p.t).toInt()}", (p.l + p.r) / 2, p.t - 14f, 20f, col, shadow = false)
            }
        }
        s.color = 0x80FFFFFF.toInt(); s.strokeWidth = 3f
        c.drawLine(0f, FLOOR, W, FLOOR, s)
        anchor?.let { a ->
            s.color = ACCENT; s.strokeWidth = 4f
            c.drawRect(min(a[0], curX), min(a[1], curY), max(a[0], curX), max(a[1], curY), s)
        }
        if (touchMode == 1) lastRaw?.let { r ->
            s.color = ACCENT; s.strokeWidth = 4f
            c.drawRect(min(r[0], r[2]), min(r[1], r[3]), max(r[0], r[2]), max(r[1], r[3]), s)
        }
        s.color = Color.WHITE; s.strokeWidth = 3f
        c.drawLine(curX - 40f, curY, curX + 40f, curY, s); c.drawLine(curX, curY - 40f, curX, curY + 40f, s)
        c.drawCircle(curX, curY, 16f, s)

        Gfx.label(c, "Tracing ${ARENA_NAMES[editSlot]}  ·  ${editRects.size} platforms", 40f, 62f, 32f, Color.WHITE, Paint.Align.LEFT, shadow = false)
        val help = when {
            held >= 0 -> "Moving   Stick: move   Right stick: resize   A: drop   Y: copy   X: delete   B / L1: cancel move"
            anchor != null -> "Drawing   Stick: drag out the box   A: finish   B: cancel"
            else -> "Stick: cursor (push gently for precision)   A: draw a box, or pick up a box to move it   Y: copy   X: delete   L1: undo   B / Start: save"
        }
        Gfx.label(c, help, W / 2, 128f, 24f, MUTED, shadow = false)
        Gfx.label(c, "Touch: drag empty space to draw · drag a box to move it · tap a box, then drag its corner dot to resize", W / 2, 162f, 22f, MUTED, shadow = false)
        fun button(b: FloatArray, t: String, on: Boolean = true) {
            f.color = if (on) 0x30FFFFFF else 0x12FFFFFF; c.drawRoundRect(b[0], b[1], b[2], b[3], 26f, 26f, f)
            Gfx.label(c, t, (b[0] + b[2]) / 2, b[3] - 22f, 32f, if (on) Color.WHITE else MUTED, shadow = false)
        }
        button(btnSave, "SAVE"); button(btnUndo, "UNDO", undoStack.isNotEmpty())
        if (sel >= 0) { button(btnDel, "DELETE"); button(btnCopy, "COPY") }
        if (editMsgT > 0f) Gfx.label(c, editMsg, W / 2, FLOOR - 40f, 30f, Gfx.alpha(Color.WHITE, editMsgT.coerceAtMost(1f)), shadow = false)
        Gfx.label(c, "floor", 40f, FLOOR - 10f, 22f, MUTED, Paint.Align.LEFT, shadow = false)
    }

    private fun drawArenaSelect(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.label(c, if (mode == BRAWL) "VERSUS BRAWL" else "BAD NUG SURVIVAL", W / 2 - 420f, 170f, 64f, Color.WHITE, shadow = false)
        Gfx.label(c, "Pick your arena", W / 2 - 420f, 220f, 32f, MUTED, shadow = false)
        for (i in ARENA_NAMES.indices) menuItem(c, arenaLabel(i), 290f + i * 92f, i == idx, 620f, W / 2 - 420f)
        val sc = 0.42f; val ox = 1060f; val oy = 260f
        Gfx.stroke.color = 0x40FFFFFF; Gfx.stroke.strokeWidth = 2f
        c.drawRect(ox, oy, ox + W * sc, oy + H * sc, Gfx.stroke)
        val prev = if (idx < 3) loadWallCached(idx) else preset(idx)
        drawPlats(c, prev, sc, ox, oy, outlineOnly = false)
        if (prev.isEmpty()) Gfx.label(c, "Nothing traced yet: go to Wall Setup", ox + W * sc / 2, oy + H * sc / 2, 28f, MUTED, shadow = false)
        Gfx.label(c, "A: choose   B: back", W / 2, 1030f, 28f, MUTED, shadow = false)
    }

    private var wallCacheSlot = -1
    private var wallCacheT = 0f
    private var wallCache: List<Plat> = emptyList()
    private fun loadWallCached(slot: Int): List<Plat> {
        if (slot != wallCacheSlot || time - wallCacheT > 1f) { wallCache = loadWall(slot); wallCacheSlot = slot; wallCacheT = time }
        return wallCache
    }

    private fun drawHelp(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.label(c, "HOW TO PLAY", W / 2, 120f, 76f, Color.WHITE, shadow = false)
        val lines = arrayOf(
            "SET UP: plug your phone / TV box into the projector, aim it at a wall and trace your shelves in Wall Setup.",
            "VERSUS BRAWL: 2 to 4 players, 3 lives each. Knock the other nugs out. Win 2 rounds to win the match.",
            "Every round after the first has a twist: jetpacks, low gravity, rockets only, swords, barrels, hot floor...",
            "BAD NUG SURVIVAL: team up against waves of brown and yellow bad nugs. Every 5th wave: a Moldy Mega Nug.",
            "The more damage you've taken, the further you fly. Getting slammed into a wall hurts!",
            "",
            "A = jump (press again in the air to double jump, hold down + A to drop through a ledge)",
            "X / R1 / right trigger = attack or use your weapon     Y = pick up, swap or throw your weapon",
            "B / L1 / left trigger = block. Tap it just before a hit lands to PARRY (stuns them, reflects shots)",
            "Up + attack = uppercut. Right stick aims guns (or hold up/down). START = pause.",
            "",
            "Weapons drop from the top: sword, seed blaster, grenades, rocket launcher. Christmas: Santa drops presents!",
            "First controller to press a button is Player 1, then 2, 3, 4. Keyboard: P1 WASD+Space/F/G/H, P2 arrows+Enter."
        )
        var y = 210f
        for ((i, l) in lines.withIndex()) { Gfx.label(c, l, W / 2, y, 29f, if (i in 6..9) SOFT else MUTED, shadow = false); y += 56f }
        Gfx.label(c, "Press A or tap to go back", W / 2, 1040f, 30f, Color.WHITE, shadow = false)
        drawSnowAndSanta(c)
    }

    private fun drawLobby(c: Canvas) {
        c.drawColor(0xFF0C0E0D.toInt())
        Gfx.label(c, if (mode == BRAWL) "VERSUS BRAWL" else "BAD NUG SURVIVAL", W / 2, 100f, 64f, Color.WHITE, shadow = false)
        Gfx.label(c, "Arena: ${ARENA_NAMES[arena]}  ·  each player presses A on their controller to join", W / 2, 150f, 30f, MUTED, shadow = false)
        for (p in 0..3) {
            val l = panelL(p)
            Gfx.panel(c, l, 190f, l + 420f, 850f)
            val cx = l + 210f
            Gfx.label(c, "PLAYER ${p + 1}", cx, 250f, 44f, Color.WHITE, shadow = false)
            if (!joined[p]) {
                Gfx.label(c, "Press A to join", cx, 520f, 40f, MUTED, shadow = false)
                continue
            }
            val look = Look.of(COLORS[colorIdx[p]], shapeIdx[p], 11 + p * 7 + colorIdx[p])
            val hop = if (ready[p]) abs(sin(time * 7f + p)) * 26f else 0f
            Gfx.nug(c, cx, 440f - hop, 92f, look, 0.4f, 0.2f, time * 8f, ready[p], 0f, 0, 1f, xmas)
            Gfx.label(c, "<  ${COLOR_NAMES[colorIdx[p]]}  >", cx, 630f, 36f, look.light, shadow = false)
            Gfx.label(c, SHAPE_NAMES[shapeIdx[p]], cx, 685f, 30f, Color.WHITE, shadow = false)
            val pad = input.slotLabel(p)
            Gfx.label(c, if (pad.isEmpty()) (if (p < 2) "keyboard / touch" else "") else pad.take(26), cx, 740f, 22f, 0xFF8F948F.toInt(), shadow = false)
            Gfx.label(c, if (ready[p]) "READY!" else "A = ready", cx, 810f, 36f, if (ready[p]) ACCENT else SOFT, shadow = false)
        }
        Gfx.fill.color = 0x30FFFFFF
        c.drawRoundRect(W / 2 - 220, 900f, W / 2 + 220, 1010f, 50f, 50f, Gfx.fill)
        val need = mode == BRAWL && joined.count { it } < 2
        Gfx.label(c, if (lobbyCountdown > 0f) "Starting..." else if (need) "Need 2+ players" else "START", W / 2, 975f, 46f, Color.WHITE, shadow = false)
        Gfx.label(c, "left/right: colour   up/down: shape   B: back", 60f, 1050f, 24f, MUTED, Paint.Align.LEFT, shadow = false)
        drawSnowAndSanta(c)
    }
}
