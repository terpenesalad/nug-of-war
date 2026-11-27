package com.terpenesalad.nugofwar

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class Game(
    private val input: InputHub,
    private val sfx: Sound,
    private val prefs: SharedPreferences,
    private val quit: () -> Unit
) {
    companion object {
        const val W = 1920f
        const val H = 1080f
        const val TOP = 140f
        const val BOT = 1060f

        val COLORS = intArrayOf(
            0xFF8DB580.toInt(), 0xFF9D8CC4.toInt(), 0xFF7FB8A8.toInt(), 0xFFC48A9F.toInt(),
            0xFF5E8A5A.toInt(), 0xFF7F93BF.toInt(), 0xFFB5C98A.toInt(), 0xFFD0D4D0.toInt()
        )
        val COLOR_NAMES = arrayOf("Lime Kush", "Purple Haze", "Frosty Mint", "Pink Candy",
            "Forest OG", "Blueberry", "Sour Apple", "White Widow")
        val SHAPE_NAMES = arrayOf("Round", "Tall Cola", "Chunky", "Popcorn")
        val BAD_COLORS = intArrayOf(
            0xFFB3A36A.toInt(), 0xFF8C7450.toInt(), 0xFF6E5A40.toInt(),
            0xFFA6965C.toInt(), 0xFF7F6A4E.toInt(), 0xFFC2B88A.toInt()
        )

        const val SHRIVEL = 0; const val MULCH = 1; const val BRICK = 2; const val SPITTER = 3; const val BOSS = 4

        val ACCENT = 0xFF9CC79A.toInt()
        val SOFT = 0xFFD8DCD8.toInt()
        val SEL = 0x26FFFFFF
        val HPCOL = 0xFFA8C8A4.toInt()
        val LOWCOL = 0xFFC46A6A.toInt()
        val WALLCOL = 0xFFA8A49A.toInt()
        val DUST = 0xFF8A8680.toInt()
        val MUTED = 0xFF8F948F.toInt()
        const val BG = 0xFF0C0E0D.toInt()

        const val COOP = 0; const val VERSUS = 1

        const val S_TITLE = 0; const val S_HELP = 1; const val S_LOBBY = 2; const val S_PLAY = 3
        const val S_PAUSE = 4; const val S_OVER = 5
    }

    private val rnd = Random(System.nanoTime())
    @Volatile private var state = S_TITLE
    private var mode = COOP
    private var time = 0f
    private var menuIdx = 0
    private var pauseIdx = 0
    private var overIdx = 0
    private var pauseMsg = ""

    // lobby
    private val joined = booleanArrayOf(true, false)
    private val ready = booleanArrayOf(false, false)
    private val colorIdx = intArrayOf(0, 1)
    private val shapeIdx = intArrayOf(0, 2)
    private var lobbyCountdown = -1f

    // world
    private val players = arrayOf(Player(0), Player(1))
    private val enemies = ArrayList<Enemy>()
    private val bullets = ArrayList<Bullet>()
    private val pickups = ArrayList<Pickup>()
    private val particles = ArrayList<Particle>()
    private val floaters = ArrayList<Floater>()
    private val crates = ArrayList<Crate>()
    private var coopWall = Wall(250f, 310f, TOP, BOT, 1000f)
    private val vsWalls = arrayOf(Wall(110f, 170f, TOP, BOT, 400f), Wall(1750f, 1810f, TOP, BOT, 400f))
    private var shake = 0f

    // co-op
    private var wave = 0
    private val spawnQueue = ArrayList<Int>()
    private var spawnT = 0f
    private var bannerT = 0f
    private var bannerText = ""
    private var bannerSub = ""
    private var bestWave = prefs.getInt("bestWave", 0)

    // versus
    private val score = intArrayOf(0, 0)
    private var round = 0
    private var roundIntro = 0f
    private var roundOver = 0f
    private var roundWinner = -1
    private var vsPickupT = 6f
    private var matchWinner = -1

    // decor
    private val bgCache = HashMap<Int, Bitmap>()

    // commentary
    private val talk = Commentary(sfx)
    private var commentary = prefs.getBoolean("commentary", true)
    private val streakT = floatArrayOf(0f, 0f)
    private val streakN = intArrayOf(0, 0)
    private var wallLowSaid = false
    private val vsWallSaid = booleanArrayOf(false, false)
    private fun say(e: Commentary.Ev, who: String = "", n: Int = 0, force: Boolean = false) { if (commentary) talk.event(e, who, n, force) }
    private fun nameOf(p: Int) = COLOR_NAMES[colorIdx[p]]

    // christmas
    private var xmas = prefs.getBoolean("xmas", true)
    private val flakes = FloatArray(160 * 4)
    private var santaT = 8f
    private var santaX = -1f
    private var santaY = 200f
    private var santaDir = 1f
    private var santaDropped = false
    private val wallCracks = FloatArray(4 * 40) { 0f }
    private val titleNugs = ArrayList<Enemy>()

    init {
        val cr = Random(99)
        for (i in 0 until 40) {
            wallCracks[i * 4] = cr.nextFloat()
            wallCracks[i * 4 + 1] = cr.nextFloat()
            wallCracks[i * 4 + 2] = (cr.nextFloat() - 0.5f) * 50f
            wallCracks[i * 4 + 3] = (cr.nextFloat() - 0.5f) * 70f
        }
        for (i in 0 until 160) {
            flakes[i * 4] = rnd.nextFloat() * W
            flakes[i * 4 + 1] = rnd.nextFloat() * H
            flakes[i * 4 + 2] = 25f + rnd.nextFloat() * 50f
            flakes[i * 4 + 3] = 1.5f + rnd.nextFloat() * 3f
        }
        for (i in 0 until 7) {
            val good = i % 2 == 0
            val col = if (good) COLORS[i % COLORS.size] else BAD_COLORS[i % BAD_COLORS.size]
            val e = Enemy(if (good) 0 else 1, rnd.nextFloat() * W, 900f + (i % 3) * 50f, 34f + (i % 3) * 6f,
                1f, 90f + i * 12f, 0f, 0f, Look(col, i % 4, i))
            e.fx = if (i % 3 == 0) -1f else 1f
            titleNugs.add(e)
        }
    }

    fun onHostPause() {
        if (state == S_PLAY) { state = S_PAUSE; pauseIdx = 0; pauseMsg = "" }
    }

    // ================================================================= input helpers

    private val c0 get() = input.ctl[0]
    private val c1 get() = input.ctl[1]
    private fun anyUp() = c0.upP || c1.upP
    private fun anyDown() = c0.downP || c1.downP
    private fun anyConfirm() = c0.confirmP || c1.confirmP
    private fun anyBack() = c0.backP || c1.backP
    private fun anyStart() = c0.startP || c1.startP

    private fun inRect(t: FloatArray, l: Float, top: Float, r: Float, b: Float) = t[0] in l..r && t[1] in top..b

    // ================================================================= update

    fun update(dt: Float) {
        time += dt
        if (xmas) updateChristmas(dt)
        if (commentary) talk.update(dt, state == S_PLAY)
        for (i in 0..1) { streakT[i] -= dt; if (streakT[i] <= 0f) streakN[i] = 0 }
        val taps = input.takeTaps()
        when (state) {
            S_TITLE -> updateTitle(dt, taps)
            S_HELP -> if (anyConfirm() || anyBack() || taps.isNotEmpty()) { sfx.play(Sound.SELECT); state = S_TITLE }
            S_LOBBY -> updateLobby(dt, taps)
            S_PLAY -> updatePlay(dt, taps)
            S_PAUSE -> updatePause(taps)
            S_OVER -> { updateOver(taps); updateParticles(dt); for (p in players) p.phase += dt * 8f }
        }
        if (input.disconnectFlag) {
            input.disconnectFlag = false
            if (state == S_PLAY) { state = S_PAUSE; pauseIdx = 0; pauseMsg = "A controller disconnected. Reconnect it and press A." }
        }
    }

    private val titleItems get() = arrayOf("Co-op: Defend the Wall", "Versus: Bud vs Bud",
        if (xmas) "Christmas Mode: ON" else "Christmas Mode: OFF",
        if (commentary) "Streamer Commentary: ON" else "Streamer Commentary: OFF", "How to Play", "Reset Controllers", "Quit")
    private fun titleItemY(i: Int) = 430f + i * 82f

    private fun updateTitle(dt: Float, taps: List<FloatArray>) {
        for (e in titleNugs) {
            e.x += e.fx * e.speed * dt
            e.phase += dt * 9f
            if (e.x > W + 80) e.x = -80f
            if (e.x < -80) e.x = W + 80f
        }
        if (anyUp()) { menuIdx = (menuIdx + titleItems.size - 1) % titleItems.size; sfx.play(Sound.SELECT) }
        if (anyDown()) { menuIdx = (menuIdx + 1) % titleItems.size; sfx.play(Sound.SELECT) }
        var choose = anyConfirm() || anyStart()
        for (t in taps) {
            for (i in titleItems.indices) {
                val y = titleItemY(i)
                if (inRect(t, W / 2 - 400, y - 60, W / 2 + 400, y + 26)) { menuIdx = i; choose = true }
            }
        }
        if (anyBack() && !choose) { quit(); return }
        if (!choose) return
        sfx.play(Sound.SELECT)
        when (menuIdx) {
            0 -> openLobby(COOP)
            1 -> openLobby(VERSUS)
            2 -> { xmas = !xmas; prefs.edit().putBoolean("xmas", xmas).apply(); if (xmas) sfx.play(Sound.JINGLE) }
            3 -> { commentary = !commentary; prefs.edit().putBoolean("commentary", commentary).apply() }
            4 -> state = S_HELP
            5 -> { input.resetSlots(); floaters.add(Floater("Controllers reset: press A on each one", W / 2, 1000f, Color.WHITE)) }
            6 -> quit()
        }
    }

    private fun openLobby(m: Int) {
        mode = m
        joined[0] = true
        joined[1] = m == VERSUS || input.ctl[1].hasPad || input.touchRecently()
        ready[0] = false; ready[1] = false
        lobbyCountdown = -1f
        state = S_LOBBY
    }

    private fun lobbyCanStart(): Boolean {
        if (mode == VERSUS && !joined[1]) return false
        for (p in 0..1) if (joined[p] && !ready[p]) return false
        return true
    }

    private fun updateLobby(dt: Float, taps: List<FloatArray>) {
        for (p in 0..1) {
            val c = input.ctl[p]
            if (!joined[p]) {
                if (c.confirmP || c.startP) { joined[p] = true; sfx.play(Sound.SELECT) }
                continue
            }
            if (!ready[p]) {
                if (c.leftP) { colorIdx[p] = (colorIdx[p] + COLORS.size - 1) % COLORS.size; sfx.play(Sound.SELECT) }
                if (c.rightP) { colorIdx[p] = (colorIdx[p] + 1) % COLORS.size; sfx.play(Sound.SELECT) }
                if (c.upP || c.altP) { shapeIdx[p] = (shapeIdx[p] + SHAPE_NAMES.size - 1) % SHAPE_NAMES.size; sfx.play(Sound.SELECT) }
                if (c.downP) { shapeIdx[p] = (shapeIdx[p] + 1) % SHAPE_NAMES.size; sfx.play(Sound.SELECT) }
                if (c.confirmP || c.startP) { ready[p] = true; sfx.play(Sound.PICKUP) }
                if (c.backP) {
                    if (p == 1 && mode == COOP) joined[1] = false
                    else if (p == 0) { state = S_TITLE; sfx.play(Sound.SELECT); return }
                }
            } else if (c.backP) { ready[p] = false; sfx.play(Sound.SELECT) }
        }
        // touch
        for (t in taps) {
            for (p in 0..1) {
                val l = if (p == 0) 160f else 1000f
                if (!inRect(t, l, 190f, l + 760f, 860f)) continue
                if (!joined[p]) { joined[p] = true; sfx.play(Sound.SELECT); continue }
                if (t[1] < 640f) colorIdx[p] = (colorIdx[p] + 1) % COLORS.size
                else shapeIdx[p] = (shapeIdx[p] + 1) % SHAPE_NAMES.size
                sfx.play(Sound.SELECT)
            }
            if (inRect(t, W / 2 - 220, 900f, W / 2 + 220, 1010f)) {
                if (mode == VERSUS && !joined[1]) joined[1] = true
                for (p in 0..1) if (joined[p]) ready[p] = true
                lobbyCountdown = 0.01f
            }
        }
        if (lobbyCanStart()) {
            if (lobbyCountdown < 0f) lobbyCountdown = 1.5f
            lobbyCountdown -= dt
            if (lobbyCountdown <= 0f) startMatch()
        } else lobbyCountdown = -1f
    }

    private fun startMatch() {
        for (p in 0..1) {
            val pl = players[p]
            pl.active = joined[p]
            pl.look = Look(COLORS[colorIdx[p]], shapeIdx[p], 11 + p * 7 + colorIdx[p])
            pl.kills = 0
        }
        enemies.clear(); bullets.clear(); pickups.clear(); particles.clear(); floaters.clear(); crates.clear()
        talk.reset()
        shake = 0f
        if (mode == COOP) {
            coopWall = Wall(250f, 310f, TOP, BOT, 1000f)
            for (p in players) resetPlayer(p, 440f, if (p.idx == 0) 450f else 750f, 1f)
            if (!players[1].active) players[0].y = 600f
            wave = 0
            nextWave()
        } else {
            score[0] = 0; score[1] = 0
            round = 0
            matchWinner = -1
            startRound()
        }
        state = S_PLAY
    }

    private fun resetPlayer(p: Player, x: Float, y: Float, fx: Float) {
        p.x = x; p.y = y; p.fx = fx; p.fy = 0f
        p.hp = p.maxHp; p.downed = false; p.downT = 0f
        p.fireCd = 0f; p.dashT = 0f; p.dashCd = 0f; p.inv = 1f
        p.rapidT = 0f; p.tripleT = 0f; p.flash = 0f
    }

    // ----------------------------------------------------------------- co-op waves

    private fun nextWave() {
        wave++
        spawnQueue.clear()
        val count = 5 + wave * 3
        for (i in 0 until count) {
            val roll = rnd.nextFloat()
            val t = when {
                wave >= 4 && roll < 0.12f + wave * 0.01f -> BRICK
                wave >= 3 && roll < 0.32f -> SPITTER
                wave >= 2 && roll < 0.62f -> MULCH
                else -> SHRIVEL
            }
            spawnQueue.add(t)
        }
        if (wave % 5 == 0) spawnQueue.add(count / 2, BOSS)
        spawnQueue.shuffle(rnd)
        if (wave % 5 == 0 && spawnQueue.last() != BOSS) { spawnQueue.remove(BOSS); spawnQueue.add(spawnQueue.size / 3, BOSS) }
        spawnT = 0f
        bannerT = 3f
        bannerText = "WAVE $wave"
        bannerSub = if (wave % 5 == 0) "A MOLDY MEGA NUG IS COMING!" else if (wave == 1) "Keep the bad nugs off the wall!" else "Wall repaired a little"
        if (wave > 1) coopWall.hp = min(coopWall.maxHp, coopWall.hp + 150f)
        for (p in players) if (p.active && p.downed) { p.downed = false; p.hp = 60f; p.inv = 2f }
        sfx.play(Sound.WAVE)
        wallLowSaid = false
        say(Commentary.Ev.WAVE, n = wave, force = true)
    }

    private fun spawnEnemy(type: Int, xIn: Float = -1f, yIn: Float = -1f) {
        val hpMul = 1f + (wave - 1) * 0.09f
        val e = when (type) {
            SHRIVEL -> Enemy(type, 0f, 0f, 24f, 18f * hpMul, 165f + wave * 3f, 6f, 10f, Look(pick(0, 5), rnd.nextInt(4), rnd.nextInt(999)))
            MULCH -> Enemy(type, 0f, 0f, 34f, 45f * hpMul, 105f + wave * 2f, 11f, 14f, Look(pick(1, 4), rnd.nextInt(4), rnd.nextInt(999)))
            BRICK -> Enemy(type, 0f, 0f, 54f, 150f * hpMul, 62f, 28f, 25f, Look(BAD_COLORS[2], 2, rnd.nextInt(999)))
            SPITTER -> Enemy(type, 0f, 0f, 30f, 32f * hpMul, 115f, 0f, 10f, Look(BAD_COLORS[3], 1, rnd.nextInt(999)))
            else -> Enemy(type, 0f, 0f, 112f, 900f + wave * 160f, 36f, 60f, 40f, Look(0xFF4E4232.toInt(), 0, rnd.nextInt(999)))
        }
        e.x = if (xIn >= 0) xIn else W + e.r + rnd.nextFloat() * 40f
        e.y = if (yIn >= 0) yIn else TOP + e.r + 10f + rnd.nextFloat() * (BOT - TOP - e.r * 2 - 20f)
        e.stopX = 1150f + rnd.nextFloat() * 450f
        e.wobble = rnd.nextFloat() * 6.28f
        enemies.add(e)
        if (type == BOSS) say(Commentary.Ev.BOSS, force = true)
    }

    private fun pick(a: Int, b: Int) = if (rnd.nextBoolean()) BAD_COLORS[a] else BAD_COLORS[b]

    // ----------------------------------------------------------------- versus rounds

    private fun startRound() {
        round++
        bullets.clear(); pickups.clear(); crates.clear()
        for (w in vsWalls) { w.hp = w.maxHp; w.flash = 0f }
        resetPlayer(players[0], 420f, 595f, 1f)
        resetPlayer(players[1], W - 420f, 595f, -1f)
        for (cy in floatArrayOf(330f, 595f, 860f)) crates.add(Crate(W / 2, cy, 70f, 130f, 90f))
        roundIntro = 2.2f
        roundOver = 0f
        roundWinner = -1
        vsPickupT = 7f
        sfx.play(Sound.WAVE)
        vsWallSaid[0] = false; vsWallSaid[1] = false
        if (round == 1) say(Commentary.Ev.FIGHT, force = true)
    }

    private fun endRound(winner: Int) {
        if (roundOver > 0f) return
        roundWinner = winner
        roundOver = 2.8f
        score[winner]++
        sfx.play(Sound.WIN)
        if (score[winner] >= 3) say(Commentary.Ev.MATCH, nameOf(winner), force = true)
        else say(Commentary.Ev.ROUND, nameOf(winner), force = true)
        val loser = players[1 - winner]
        burst(loser.x, loser.y, loser.look.color, 30, 420f)
    }

    // ----------------------------------------------------------------- play loop

    private fun updatePlay(dt: Float, taps: List<FloatArray>) {
        if (anyStart() || anyBack()) { state = S_PAUSE; pauseIdx = 0; pauseMsg = ""; sfx.play(Sound.SELECT); return }
        for (t in taps) if (inRect(t, W / 2 - 70, 0f, W / 2 + 70, 70f)) { state = S_PAUSE; pauseIdx = 0; pauseMsg = ""; return }
        shake = max(0f, shake - dt * 30f)

        if (mode == COOP) updateCoop(dt) else updateVersus(dt)

        updateBullets(dt)
        updatePickups(dt)
        updateParticles(dt)
    }

    private fun updateCoop(dt: Float) {
        if (bannerT > 0f) bannerT -= dt
        else if (spawnQueue.isNotEmpty()) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnEnemy(spawnQueue.removeAt(0))
                spawnT = max(0.3f, 1.55f - wave * 0.09f) * (0.6f + rnd.nextFloat() * 0.8f)
            }
        } else if (enemies.isEmpty()) {
            floaters.add(Floater("WAVE CLEAR!", W / 2, 400f, ACCENT))
            say(Commentary.Ev.CLEAR)
            nextWave()
        }

        for (p in players) if (p.active) updatePlayer(p, dt)
        updateEnemies(dt)
        coopWall.flash = max(0f, coopWall.flash - dt * 4f)
        if (!wallLowSaid && coopWall.hp < coopWall.maxHp * 0.3f) { wallLowSaid = true; say(Commentary.Ev.WALL_LOW, force = true) }

        if (coopWall.hp <= 0f) {
            coopWall.hp = 0f
            if (wave > bestWave) { bestWave = wave; prefs.edit().putInt("bestWave", wave).apply() }
            sfx.play(Sound.LOSE)
            say(Commentary.Ev.GAME_OVER, force = true)
            burst(coopWall.r, H / 2, DUST, 50, 600f)
            state = S_OVER; overIdx = 0
        }
    }

    private fun updateVersus(dt: Float) {
        if (roundIntro > 0f) roundIntro -= dt
        if (roundOver > 0f) {
            roundOver -= dt
            if (roundOver <= 0f) {
                if (score[roundWinner] >= 3) { matchWinner = roundWinner; state = S_OVER; overIdx = 0; sfx.play(Sound.WIN) }
                else startRound()
                return
            }
        }
        for (p in players) updatePlayer(p, dt)
        for (w in vsWalls) w.flash = max(0f, w.flash - dt * 4f)
        for (i in 0..1) if (!vsWallSaid[i] && vsWalls[i].hp < vsWalls[i].maxHp * 0.3f) { vsWallSaid[i] = true; say(Commentary.Ev.VS_WALL_LOW, nameOf(i)) }
        for (c in crates) c.flash = max(0f, c.flash - dt * 4f)

        if (roundOver <= 0f && roundIntro <= 0f) {
            vsPickupT -= dt
            if (vsPickupT <= 0f) {
                vsPickupT = 8f + rnd.nextFloat() * 4f
                val type = rnd.nextInt(4)
                val d = 260f + rnd.nextFloat() * 420f
                val y = TOP + 80f + rnd.nextFloat() * (BOT - TOP - 160f)
                pickups.add(Pickup(W / 2 - d, y, type))
                pickups.add(Pickup(W / 2 + d, y, type))
            }
            for (i in 0..1) {
                if (players[i].hp <= 0f) endRound(1 - i)
                else if (vsWalls[i].hp <= 0f) { vsWalls[i].hp = 0f; endRound(1 - i) }
            }
        }
    }

    private fun updatePlayer(p: Player, dt: Float) {
        val c = input.ctl[p.idx]
        p.flash = max(0f, p.flash - dt * 5f)
        p.inv = max(0f, p.inv - dt)
        p.fireCd -= dt
        p.dashCd -= dt
        p.rapidT = max(0f, p.rapidT - dt)
        p.tripleT = max(0f, p.tripleT - dt)

        if (p.downed) {
            val mate = players[1 - p.idx]
            val near = mate.alive && dist(mate.x, mate.y, p.x, p.y) < 130f
            p.downT -= dt * (if (near) 3.5f else 1f)
            if (p.downT <= 0f) { p.downed = false; p.hp = 60f; p.inv = 2f; sfx.play(Sound.PICKUP); if (near) say(Commentary.Ev.REVIVE, nameOf(p.idx)); floaters.add(Floater("BACK UP!", p.x, p.y - 70, Color.WHITE)) }
            p.moving = false
            return
        }
        val frozen = mode == VERSUS && (roundIntro > 0f || roundOver > 0f)

        // facing
        val al = sqrt(c.ax * c.ax + c.ay * c.ay)
        val ml = sqrt(c.mx * c.mx + c.my * c.my)
        if (al > 0.35f) { p.fx = c.ax / al; p.fy = c.ay / al }
        else if (ml > 0.2f) { p.fx = c.mx / ml; p.fy = c.my / ml }

        // dash
        if (c.dashP && p.dashCd <= 0f && !frozen) {
            val dx: Float; val dy: Float
            if (ml > 0.2f) { dx = c.mx / ml; dy = c.my / ml } else { dx = p.fx; dy = p.fy }
            p.dvx = dx * 1350f; p.dvy = dy * 1350f
            p.dashT = 0.17f; p.dashCd = 0.9f
            p.dashHit.clear()
            sfx.play(Sound.DASH, 0.7f)
        }
        if (p.dashT > 0f) {
            p.dashT -= dt
            p.x += p.dvx * dt; p.y += p.dvy * dt
            if (rnd.nextFloat() < 0.6f) particles.add(Particle(p.x, p.y + p.r * 0.5f, -p.dvx * 0.05f, -p.dvy * 0.05f, 0.35f, 10f, Gfx.alpha(p.look.light, 0.8f)))
            if (mode == COOP) {
                for (e in enemies) {
                    if (!e.dead && !p.dashHit.contains(e) && dist(e.x, e.y, p.x, p.y) < e.r + p.r) {
                        p.dashHit.add(e)
                        val kb = 900f * (40f / e.r)
                        e.kx += p.dvx / 1350f * kb; e.ky += p.dvy / 1350f * kb
                        damageEnemy(e, 20f, p)
                    }
                }
            }
        } else {
            val sp = if (frozen && roundOver > 0f) 0f else 430f
            p.x += c.mx * sp * dt; p.y += c.my * sp * dt
        }
        p.moving = ml > 0.15f || p.dashT > 0f
        if (p.moving) p.phase += dt * 14f

        // bounds
        val minX: Float; val maxX: Float
        if (mode == COOP) { minX = coopWall.r + p.r; maxX = W - p.r - 10f }
        else if (p.idx == 0) { minX = vsWalls[0].r + p.r; maxX = W / 2 - 50f - p.r }
        else { minX = W / 2 + 50f + p.r; maxX = vsWalls[1].l - p.r }
        p.x = p.x.coerceIn(minX, maxX)
        p.y = p.y.coerceIn(TOP + p.r, BOT - p.r * 1.1f)

        // fire
        if (c.fire && p.fireCd <= 0f && !frozen) {
            var dx = p.fx; var dy = p.fy
            val assisted = aimAssist(p, dx, dy, al > 0.35f)
            if (assisted != null) { dx = assisted[0]; dy = assisted[1] }
            p.fireCd = if (p.rapidT > 0f) 0.085f else 0.19f
            val dmg = if (mode == COOP) 10f else 7f
            val spreads = if (p.tripleT > 0f) floatArrayOf(-0.17f, 0f, 0.17f) else floatArrayOf(0f)
            val base = atan2(dy, dx)
            for (s in spreads) {
                val a = base + s
                val vx = cos(a) * 1150f; val vy = sin(a) * 1150f
                bullets.add(Bullet(p.x + cos(a) * (p.r + 8f), p.y + sin(a) * (p.r + 8f), vx, vy, 11f, dmg, p.idx, p.look.light))
            }
            sfx.play(Sound.SHOOT, 0.35f)
        }

        // pickups
        for (k in pickups) {
            if (!k.dead && dist(k.x, k.y, p.x, p.y) < p.r + 34f) {
                k.dead = true
                applyPickup(p, k.type)
            }
        }
    }

    private fun aimAssist(p: Player, dx: Float, dy: Float, stickAim: Boolean): FloatArray? {
        val minDot = if (mode == COOP) (if (stickAim) 0.97f else 0.9f) else (if (stickAim) 0.985f else 0.94f)
        var best = minDot
        var out: FloatArray? = null
        fun consider(tx: Float, ty: Float) {
            val ex = tx - p.x; val ey = ty - p.y
            val l = sqrt(ex * ex + ey * ey)
            if (l < 1f || l > 1500f) return
            val d = (ex * dx + ey * dy) / l
            if (d > best) { best = d; out = floatArrayOf(ex / l, ey / l) }
        }
        if (mode == COOP) { for (e in enemies) if (!e.dead) consider(e.x, e.y) }
        else { val o = players[1 - p.idx]; if (o.hp > 0f) consider(o.x, o.y) }
        return out
    }

    private fun applyPickup(p: Player, type: Int) {
        sfx.play(Sound.PICKUP)
        val txt = when (type) {
            Pickup.HEAL -> { p.hp = min(p.maxHp, p.hp + 40f); "+40 HP" }
            Pickup.RAPID -> { p.rapidT = 8f; "RAPID FIRE!" }
            Pickup.TRIPLE -> { p.tripleT = 8f; "TRIPLE SHOT!" }
            else -> {
                val w = if (mode == COOP) coopWall else vsWalls[p.idx]
                w.hp = min(w.maxHp, w.hp + (if (mode == COOP) 120f else 90f)); "WALL REPAIRED!"
            }
        }
        floaters.add(Floater(txt, p.x, p.y - 70f, SOFT))
        if (type == Pickup.RAPID || type == Pickup.TRIPLE) say(Commentary.Ev.POWER, nameOf(p.idx))
    }

    private fun hurtPlayer(p: Player, dmg: Float) {
        if (!p.active || p.downed || p.inv > 0f || p.dashT > 0f || p.hp <= 0f) return
        if (mode == VERSUS && roundOver > 0f) return
        p.hp -= dmg
        p.flash = 1f
        shake = max(shake, 6f)
        sfx.play(Sound.OUCH, 0.6f)
        if (p.hp <= 0f) {
            if (mode == COOP) {
                p.hp = 0f; p.downed = true; p.downT = 7f
                say(Commentary.Ev.DOWN, nameOf(p.idx), force = true)
                floaters.add(Floater("DOWN! Stand next to them to revive", p.x, p.y - 80f, LOWCOL))
            } else p.hp = 0f
        }
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (e.dead) continue
            e.flash = max(0f, e.flash - dt * 6f)
            e.x += e.kx * dt; e.y += e.ky * dt
            e.kx *= 0.86f; e.ky *= 0.86f
            e.wobble += dt * 2f

            var tgt: Player? = null
            var td = if (e.type == BOSS) 230f else 270f
            for (p in players) if (p.alive) { val d = dist(p.x, p.y, e.x, e.y); if (d < td) { td = d; tgt = p } }

            e.moving = true
            if (tgt != null && e.type != SPITTER) {
                val dx = tgt.x - e.x; val dy = tgt.y - e.y; val l = max(1f, sqrt(dx * dx + dy * dy))
                e.fx = dx / l; e.fy = dy / l
                if (l > e.r + tgt.r - 4f) {
                    e.x += e.fx * e.speed * 1.1f * dt; e.y += e.fy * e.speed * 1.1f * dt
                } else {
                    e.moving = false
                    e.atkT -= dt
                    if (e.atkT <= 0f) { e.atkT = 0.5f; hurtPlayer(tgt, e.meleeDps * 0.5f) }
                }
            } else if (e.type == SPITTER && e.x <= e.stopX) {
                e.moving = false
                e.shotT -= dt
                var tx = coopWall.r; var ty = e.y
                for (p in players) if (p.alive && dist(p.x, p.y, e.x, e.y) < 750f) { tx = p.x; ty = p.y }
                val dx = tx - e.x; val dy = ty - e.y; val l = max(1f, sqrt(dx * dx + dy * dy))
                e.fx = dx / l; e.fy = dy / l
                if (e.shotT <= 0f) {
                    e.shotT = 1.9f
                    bullets.add(Bullet(e.x + e.fx * e.r, e.y + e.fy * e.r, e.fx * 520f, e.fy * 520f, 13f, 10f, -1, 0xFFA6965C.toInt()))
                    sfx.play(Sound.HIT, 0.3f)
                }
            } else if (e.x - e.r > coopWall.r + 2f) {
                e.fx = -1f; e.fy = sin(e.wobble) * 0.3f
                e.x -= e.speed * dt
                e.y += sin(e.wobble) * 30f * dt
            } else {
                e.moving = false
                e.fx = -1f; e.fy = 0f
                coopWall.hp -= e.wallDps * dt
                e.atkT -= dt
                if (e.atkT <= 0f) {
                    e.atkT = 0.6f
                    coopWall.flash = 1f
                    sfx.play(Sound.THUD, if (e.type >= BRICK) 0.9f else 0.4f)
                    if (e.type == BRICK || e.type == BOSS) shake = max(shake, if (e.type == BOSS) 14f else 7f)
                    for (i in 0 until 4) particles.add(Particle(coopWall.r, e.y + (rnd.nextFloat() - 0.5f) * e.r,
                        rnd.nextFloat() * 220f, (rnd.nextFloat() - 0.5f) * 260f, 0.5f, 7f, DUST))
                }
            }
            if (e.type == BOSS) {
                e.spawnT -= dt
                if (e.spawnT <= 0f) {
                    e.spawnT = 3.5f
                    spawnEnemy(SHRIVEL, e.x + 60f, (e.y - 90f).coerceIn(TOP + 30f, BOT - 30f))
                    spawnEnemy(SHRIVEL, e.x + 60f, (e.y + 90f).coerceIn(TOP + 30f, BOT - 30f))
                }
            }
            if (e.moving) e.phase += dt * (6f + e.speed * 0.04f)
            e.x = max(e.x, coopWall.r + e.r)
            e.y = e.y.coerceIn(TOP + e.r * 0.6f, BOT - e.r)
        }
        // keep enemies from stacking on top of each other
        val n = enemies.size
        for (i in 0 until n) {
            val a = enemies[i]
            for (j in i + 1 until n) {
                val b = enemies[j]
                val dx = b.x - a.x; val dy = b.y - a.y
                val minD = (a.r + b.r) * 0.85f
                val d2 = dx * dx + dy * dy
                if (d2 < minD * minD && d2 > 0.01f) {
                    val d = sqrt(d2); val push = (minD - d) * 0.5f
                    val wa = b.r / (a.r + b.r); val wb = 1f - wa
                    a.x -= dx / d * push * wa; a.y -= dy / d * push * wa
                    b.x += dx / d * push * wb; b.y += dy / d * push * wb
                }
            }
        }
        enemies.removeAll { it.dead }
    }

    private fun damageEnemy(e: Enemy, dmg: Float, by: Player?) {
        if (e.dead) return
        e.hp -= dmg
        e.flash = 1f
        if (e.hp <= 0f) {
            e.dead = true
            by?.let {
                it.kills++
                streakN[it.idx]++; streakT[it.idx] = 4f
                if (streakN[it.idx] == 8) say(Commentary.Ev.STREAK, nameOf(it.idx))
            }
            sfx.play(Sound.POP, if (e.type == BOSS) 1f else 0.6f)
            burst(e.x, e.y, e.look.color, if (e.type == BOSS) 60 else 14, if (e.type == BOSS) 700f else 320f)
            if (e.type == BOSS) {
                shake = 20f
                floaters.add(Floater("MEGA NUG DEFEATED!", e.x, e.y - 140f, ACCENT))
                for (i in 0 until 3) pickups.add(Pickup((e.x - 60f + i * 60f).coerceIn(400f, W - 60f), e.y, i % 4))
            } else if (rnd.nextFloat() < 0.075f) {
                pickups.add(Pickup(e.x.coerceIn(coopWall.r + 60f, W - 60f), e.y, rnd.nextInt(4)))
            }
        }
    }

    private fun updateBullets(dt: Float) {
        for (b in bullets) {
            if (b.dead) continue
            b.x += b.vx * dt; b.y += b.vy * dt
            b.life -= dt
            if (b.life <= 0f || b.x < -40 || b.x > W + 40 || b.y < TOP - 40 || b.y > BOT + 40) { b.dead = true; continue }
            if (mode == COOP) {
                if (b.owner >= 0) {
                    for (e in enemies) {
                        if (!e.dead && dist(b.x, b.y, e.x, e.y) < e.r + b.r) {
                            b.dead = true
                            val kb = 160f * (40f / e.r)
                            e.kx += b.vx / 1150f * kb; e.ky += b.vy / 1150f * kb
                            damageEnemy(e, b.dmg, players[b.owner])
                            spark(b.x, b.y, e.look.color)
                            sfx.play(Sound.HIT, 0.35f)
                            break
                        }
                    }
                } else {
                    for (p in players) {
                        if (p.alive && dist(b.x, b.y, p.x, p.y) < p.r + b.r) { b.dead = true; hurtPlayer(p, b.dmg); break }
                    }
                    if (!b.dead && b.x - b.r <= coopWall.r) {
                        b.dead = true; coopWall.hp -= b.dmg; coopWall.flash = 1f
                        spark(b.x, b.y, DUST); sfx.play(Sound.THUD, 0.3f)
                    }
                }
            } else {
                val foe = 1 - b.owner
                val o = players[foe]
                if (o.hp > 0f && o.dashT <= 0f && dist(b.x, b.y, o.x, o.y) < o.r + b.r) {
                    b.dead = true
                    if (o.inv <= 0f) { hurtPlayer(o, b.dmg); spark(b.x, b.y, o.look.color) }
                    continue
                }
                for (c in crates) {
                    if (c.hp > 0f && b.x + b.r > c.l && b.x - b.r < c.r && b.y + b.r > c.t && b.y - b.r < c.b) {
                        b.dead = true; c.hp -= b.dmg; c.flash = 1f
                        spark(b.x, b.y, DUST); sfx.play(Sound.HIT, 0.3f)
                        if (c.hp <= 0f) { burst(c.cx, c.cy, DUST, 20, 300f); sfx.play(Sound.THUD) }
                        break
                    }
                }
                if (b.dead) continue
                val w = vsWalls[foe]
                val hit = if (foe == 1) b.x + b.r >= w.l else b.x - b.r <= w.r
                if (hit && roundOver <= 0f) {
                    b.dead = true; w.hp -= b.dmg; w.flash = 1f
                    spark(b.x, b.y, DUST); sfx.play(Sound.THUD, 0.35f)
                }
            }
        }
        bullets.removeAll { it.dead }
        crates.removeAll { it.hp <= 0f }
    }

    private fun updatePickups(dt: Float) {
        for (k in pickups) { k.life -= dt; if (k.life <= 0f) k.dead = true }
        pickups.removeAll { it.dead }
    }

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= 0.94f; p.vy *= 0.94f
            p.life -= dt
        }
        particles.removeAll { it.life <= 0f }
        for (f in floaters) { f.life -= dt; f.y -= 40f * dt }
        floaters.removeAll { it.life <= 0f }
        if (particles.size > 600) particles.subList(0, particles.size - 600).clear()
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val s = speed * (0.3f + rnd.nextFloat() * 0.7f)
            particles.add(Particle(x, y, cos(a) * s, sin(a) * s, 0.4f + rnd.nextFloat() * 0.5f, 6f + rnd.nextFloat() * 10f, color))
        }
    }

    private fun spark(x: Float, y: Float, color: Int) {
        for (i in 0 until 4) {
            val a = rnd.nextFloat() * 6.283f
            particles.add(Particle(x, y, cos(a) * 180f, sin(a) * 180f, 0.25f, 5f, color))
        }
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return sqrt(dx * dx + dy * dy)
    }

    private val pauseItems = arrayOf("Resume", "Restart", "Quit to Menu")
    private fun pauseItemY(i: Int) = 520f + i * 100f

    private fun updatePause(taps: List<FloatArray>) {
        if (anyUp()) { pauseIdx = (pauseIdx + 2) % 3; sfx.play(Sound.SELECT) }
        if (anyDown()) { pauseIdx = (pauseIdx + 1) % 3; sfx.play(Sound.SELECT) }
        var choose = anyConfirm()
        if (anyStart() || anyBack()) { pauseIdx = 0; choose = true }
        for (t in taps) for (i in 0..2) {
            val y = pauseItemY(i)
            if (inRect(t, W / 2 - 300, y - 60, W / 2 + 300, y + 26)) { pauseIdx = i; choose = true }
        }
        if (!choose) return
        sfx.play(Sound.SELECT)
        when (pauseIdx) {
            0 -> state = S_PLAY
            1 -> startMatch()
            2 -> state = S_TITLE
        }
    }

    private fun updateOver(taps: List<FloatArray>) {
        if (anyUp() || anyDown() || c0.leftP || c0.rightP || c1.leftP || c1.rightP) { overIdx = 1 - overIdx; sfx.play(Sound.SELECT) }
        var choose = anyConfirm() || anyStart()
        if (anyBack()) { overIdx = 1; choose = true }
        for (t in taps) {
            if (inRect(t, W / 2 - 520, 860f, W / 2 - 40, 980f)) { overIdx = 0; choose = true }
            if (inRect(t, W / 2 + 40, 860f, W / 2 + 520, 980f)) { overIdx = 1; choose = true }
        }
        if (!choose) return
        sfx.play(Sound.SELECT)
        if (overIdx == 0) startMatch() else state = S_TITLE
    }

    // ================================================================= drawing

    fun draw(c: Canvas) {
        when (state) {
            S_TITLE -> drawTitle(c)
            S_HELP -> drawHelp(c)
            S_LOBBY -> drawLobby(c)
            S_PLAY, S_PAUSE, S_OVER -> {
                c.save()
                if (shake > 0f && state == S_PLAY) c.translate((rnd.nextFloat() - 0.5f) * shake, (rnd.nextFloat() - 0.5f) * shake)
                drawWorld(c)
                c.restore()
                drawHud(c)
                drawSnowAndSanta(c)
                if (commentary && state != S_PAUSE) {
                    if (mode == COOP) talk.draw(c, 14f, BOT - 10f, 226f, W / 2 + 140f, BOT - 30f)
                    else talk.draw(c, W / 2 - 160f, BOT - 10f, 320f, W / 2, TOP + 70f)
                }
                if (state == S_PLAY) drawTouchControls(c)
                if (state == S_PAUSE) drawPause(c)
                if (state == S_OVER) drawOver(c)
            }
        }
        // floating toast messages outside of gameplay (e.g. "controllers reset")
        if (state == S_TITLE) for (f in floaters) Gfx.label(c, f.text, f.x, f.y, 40f, Gfx.alpha(f.color, f.life))
        if (state == S_TITLE) { for (f in floaters) f.life -= 1f / 120f; floaters.removeAll { it.life <= 0f } }
    }

    private fun background(v: Boolean): Bitmap {
        val key = (if (v) 1 else 0) + (if (xmas) 2 else 0)
        bgCache[key]?.let { return it }
        bgCache.clear() // keep only one full-screen background in memory
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        cv.drawColor(BG)
        if (!v) {
            p.shader = LinearGradient(W * 0.6f, 0f, W, 0f, 0x00000000, 0x14C2A070, Shader.TileMode.CLAMP)
            cv.drawRect(W * 0.6f, 0f, W, H, p)
            p.shader = null
        }
        p.color = 0x10FFFFFF
        var gy = TOP + 20f
        while (gy < BOT) {
            var gx = 40f
            while (gx < W) { cv.drawCircle(gx, gy, 2f, p); gx += 80f }
            gy += 80f
        }
        if (v) {
            p.color = 0x22FFFFFF
            var y = TOP
            while (y < BOT) { cv.drawRect(W / 2 - 2f, y, W / 2 + 2f, y + 24f, p); y += 56f }
        }
        if (xmas) {
            val r = Random(12)
            // light snow lying on the ground
            p.color = 0x0EFFFFFF
            for (i in 0 until 60) {
                val x = r.nextFloat() * W; val y = TOP + r.nextFloat() * (BOT - TOP)
                cv.drawOval(x - 60f - r.nextFloat() * 60f, y - 10f, x + 60f + r.nextFloat() * 60f, y + 12f, p)
            }
            // minimal pine trees
            val trees = if (v) floatArrayOf(55f, 300f, 55f, 900f, W - 55f, 300f, W - 55f, 900f)
                        else floatArrayOf(120f, 330f, 120f, 640f, 120f, 950f)
            var i = 0
            while (i < trees.size) { pine(cv, p, trees[i], trees[i + 1], if (v) 0.7f else 1f); i += 2 }
        }
        bgCache[key] = bmp
        return bmp
    }

    private fun pine(cv: Canvas, p: Paint, x: Float, y: Float, s: Float) {
        val path = android.graphics.Path()
        p.color = 0xFF1E2B22.toInt()
        for (k in 0 until 3) {
            val top = y - 110f * s + k * 38f * s; val half = (40f + k * 18f) * s
            path.reset(); path.moveTo(x, top); path.lineTo(x + half, top + 62f * s); path.lineTo(x - half, top + 62f * s); path.close()
            cv.drawPath(path, p)
        }
        p.color = 0xFF2A2420.toInt(); cv.drawRect(x - 7f * s, y + 14f * s, x + 7f * s, y + 34f * s, p)
        p.color = 0x22FFFFFF
        for (k in 0 until 3) cv.drawOval(x - (30f + k * 14f) * s, y - 60f * s + k * 38f * s, x + (30f + k * 14f) * s, y - 52f * s + k * 38f * s, p)
        p.color = 0xFFD8C27A.toInt(); cv.drawCircle(x, y - 112f * s, 7f * s, p)
    }

    private val lightCols = intArrayOf(0xFFC46A6A.toInt(), 0xFF9CC79A.toInt(), 0xFFD8C27A.toInt(), 0xFF8FA3C9.toInt())

    private fun drawTreeLights(c: Canvas) {
        val trees = if (mode == VERSUS) floatArrayOf(55f, 300f, 55f, 900f, W - 55f, 300f, W - 55f, 900f)
                    else floatArrayOf(120f, 330f, 120f, 640f, 120f, 950f)
        val s = if (mode == VERSUS) 0.7f else 1f
        val f = Gfx.fill
        var i = 0
        while (i < trees.size) {
            val x = trees[i]; val y = trees[i + 1]
            for (k in 0 until 6) {
                val tw = (sin(time * 3f + k * 1.7f + x) + 1f) / 2f
                f.color = Gfx.alpha(lightCols[k % 4], 0.35f + 0.65f * tw)
                val ly = y - 70f * s + (k / 2) * 38f * s
                c.drawCircle(x + (if (k % 2 == 0) -1 else 1) * (18f + (k / 2) * 12f) * s, ly, 4.5f * s, f)
            }
            f.color = Gfx.alpha(0xFFFFF2C0.toInt(), 0.4f + 0.3f * sin(time * 2f))
            c.drawCircle(x, y - 112f * s, 14f * s, f)
            i += 2
        }
    }

    private fun updateChristmas(dt: Float) {
        for (i in 0 until 160) {
            flakes[i * 4 + 1] += flakes[i * 4 + 2] * dt
            flakes[i * 4] += sin(time * 0.8f + i) * 12f * dt
            if (flakes[i * 4 + 1] > H + 10f) { flakes[i * 4 + 1] = -10f; flakes[i * 4] = rnd.nextFloat() * W }
        }
        if (santaX < -900f || santaX > W + 900f || santaT > 0f) {
            if (santaT > 0f) {
                santaT -= dt
                if (santaT <= 0f) {
                    santaDir = if (rnd.nextBoolean()) 1f else -1f
                    santaX = if (santaDir > 0) -400f else W + 400f
                    santaY = TOP + 40f + rnd.nextFloat() * 70f
                    santaDropped = false
                    sfx.play(Sound.JINGLE, 0.6f)
                }
            } else santaT = 25f + rnd.nextFloat() * 25f
            if (santaT > 0f) return
        }
        santaX += santaDir * 380f * dt
        // Santa drops a present (a random power-up) when flying over the battlefield
        if (state == S_PLAY && !santaDropped && abs(santaX - W / 2) < 40f) {
            santaDropped = true
            val type = rnd.nextInt(4)
            if (mode == COOP) {
                pickups.add(Pickup(500f + rnd.nextFloat() * 900f, TOP + 120f + rnd.nextFloat() * (BOT - TOP - 240f), type).also { it.gift = true })
            } else if (roundOver <= 0f) {
                val d = 260f + rnd.nextFloat() * 400f
                val y = TOP + 120f + rnd.nextFloat() * (BOT - TOP - 240f)
                pickups.add(Pickup(W / 2 - d, y, type).also { it.gift = true })
                pickups.add(Pickup(W / 2 + d, y, type).also { it.gift = true })
            }
            floaters.add(Floater("Ho ho ho! A present!", santaX, santaY + 70f, SOFT))
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
        val x = santaX; val y = santaY + sin(time * 3f) * 10f
        val d = santaDir
        c.save()
        c.translate(x, y)
        c.scale(d, 1f)
        // reindeer team in front (positive x is "forward")
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
        // sleigh
        f.color = 0xFF8A3E3E.toInt()
        c.drawRoundRect(-60f, -14f, 40f, 22f, 14f, 14f, f)
        s.color = 0xFFB8B0A0.toInt(); s.strokeWidth = 4f
        c.drawLine(-66f, 32f, 46f, 32f, s); c.drawLine(46f, 32f, 54f, 22f, s)
        // santa
        f.color = 0xFFA84848.toInt(); c.drawCircle(-18f, -26f, 20f, f)
        f.color = 0xFFE8DCCB.toInt(); c.drawCircle(-6f, -32f, 10f, f)
        f.color = 0xFFF2F2F2.toInt(); c.drawCircle(-2f, -24f, 9f, f)
        f.color = 0xFFA84848.toInt(); c.drawCircle(-10f, -46f, 9f, f)
        f.color = Color.WHITE; c.drawCircle(-20f, -52f, 5f, f)
        // sack
        f.color = 0xFF6E5A48.toInt(); c.drawCircle(-46f, -22f, 16f, f)
        c.restore()
    }

    private fun drawWall(c: Canvas, w: Wall) {
        val f = Gfx.fill; val s = Gfx.stroke
        val frac = (w.hp / w.maxHp).coerceIn(0f, 1f)
        f.color = 0xFF1A1B1A.toInt()
        c.drawRect(w.l - 6, w.top - 6, w.r + 6, w.bot + 6, f)
        val base = Gfx.mix(0xFF2E2F2E.toInt(), 0xFF6A6B67.toInt(), 0.3f + 0.7f * frac)
        val bh = 46f
        var row = 0
        var y = w.top
        while (y < w.bot) {
            val off = if (row % 2 == 0) 0f else (w.r - w.l) / 2
            var x = w.l - off
            while (x < w.r) {
                val l = max(x, w.l); val r = min(x + (w.r - w.l), w.r)
                f.color = if ((row + (x / 10).toInt()) % 3 == 0) Gfx.shade(base, 0.9f) else base
                c.drawRect(l + 2, y + 2, r - 2, min(y + bh, w.bot) - 2, f)
                x += (w.r - w.l)
            }
            y += bh; row++
        }
        // cracks appear as it takes damage
        val cracks = ((1f - frac) * 40).toInt()
        s.color = 0xCC0A0A0A.toInt(); s.strokeWidth = 4f
        for (i in 0 until cracks) {
            val cx = w.l + wallCracks[i * 4] * (w.r - w.l)
            val cy = w.top + wallCracks[i * 4 + 1] * (w.bot - w.top)
            c.drawLine(cx, cy, cx + wallCracks[i * 4 + 2] * 0.5f, cy + wallCracks[i * 4 + 3], s)
        }
        if (w.flash > 0f) {
            f.color = Color.argb((w.flash * 120).toInt(), 255, 255, 255)
            c.drawRect(w.l, w.top, w.r, w.bot, f)
        }
        if (xmas) {
            // snow on top and a string of fairy lights
            f.color = 0xFFE6E8E6.toInt()
            c.drawRoundRect(w.l - 8, w.top - 14, w.r + 8, w.top + 6, 10f, 10f, f)
            s.color = 0x55000000; s.strokeWidth = 2f
            val mid = (w.l + w.r) / 2
            var k = 0
            var ly = w.top + 30f
            while (ly < w.bot) {
                val lx = mid + sin(ly * 0.05f) * (w.r - w.l) * 0.35f
                val tw = (sin(time * 3f + k * 1.3f) + 1f) / 2f
                f.color = Gfx.alpha(lightCols[k % 4], 0.3f + 0.7f * tw)
                c.drawCircle(lx, ly, 5f, f)
                k++; ly += 34f
            }
        }
    }

    private fun drawPickup(c: Canvas, k: Pickup) {
        val bob = sin(time * 4f + k.x) * 6f
        val y = k.y + bob
        if (k.life < 3f && ((time * 8f).toInt() % 2 == 0)) return
        val col = when (k.type) {
            Pickup.HEAL -> HPCOL
            Pickup.RAPID -> 0xFFC9A27A.toInt()
            Pickup.TRIPLE -> 0xFF8FA3C9.toInt()
            else -> WALLCOL
        }
        val f = Gfx.fill
        f.color = Color.argb(70, 0, 0, 0); c.drawOval(k.x - 26, k.y + 26, k.x + 26, k.y + 38, f)
        if (k.gift) {
            f.color = Gfx.alpha(col, 0.25f); c.drawCircle(k.x, y, 40f + sin(time * 6f) * 4f, f)
            f.color = 0xFF8A3E3E.toInt(); c.drawRoundRect(k.x - 24, y - 20, k.x + 24, y + 24, 6f, 6f, f)
            f.color = col; c.drawRect(k.x - 5, y - 20, k.x + 5, y + 24, f); c.drawRect(k.x - 24, y - 3, k.x + 24, y + 7, f)
            Gfx.stroke.color = col; Gfx.stroke.strokeWidth = 4f
            c.drawCircle(k.x - 8, y - 27, 7f, Gfx.stroke); c.drawCircle(k.x + 8, y - 27, 7f, Gfx.stroke)
            return
        }
        f.color = Gfx.alpha(col, 0.3f); c.drawCircle(k.x, y, 40f + sin(time * 6f) * 4f, f)
        f.color = col; c.drawCircle(k.x, y, 28f, f)
        f.color = Color.WHITE
        when (k.type) {
            Pickup.HEAL -> { c.drawRect(k.x - 16, y - 5, k.x + 16, y + 5, f); c.drawRect(k.x - 5, y - 16, k.x + 5, y + 16, f) }
            Pickup.RAPID -> { Gfx.label(c, "»", k.x, y + 14, 44f, Color.WHITE, shadow = false) }
            Pickup.TRIPLE -> { for (i in -1..1) c.drawCircle(k.x + i * 11f, y, 5f, f) }
            else -> { f.color = 0xFF4A4B48.toInt(); c.drawRect(k.x - 16, y - 10, k.x + 16, y + 10, f)
                Gfx.stroke.color = Color.WHITE; Gfx.stroke.strokeWidth = 3f; c.drawLine(k.x - 16, y, k.x + 16, y, Gfx.stroke) }
        }
    }

    private fun drawPlayer(c: Canvas, p: Player) {
        if (!p.active) return
        val blink = p.inv > 0f && !p.downed && ((time * 12f).toInt() % 2 == 0)
        val alphaMul = if (blink) 0.45f else 1f
        val f = Gfx.fill
        // player ring
        f.color = Gfx.alpha(p.look.color, 0.25f)
        c.drawOval(p.x - p.r * 1.1f, p.y + p.r * 0.85f, p.x + p.r * 1.1f, p.y + p.r * 1.35f, f)
        Gfx.nug(c, p.x, p.y, p.r, p.look, p.fx, p.fy, p.phase, p.moving, p.flash, if (p.downed || p.hp <= 0f) 2 else 0, alphaMul, xmas)
        // aim arrow
        if (!p.downed && p.hp > 0f) {
            val ax = p.x + p.fx * (p.r + 28f); val ay = p.y + p.fy * (p.r + 28f)
            f.color = Gfx.alpha(p.look.light, 0.85f)
            c.drawCircle(ax, ay, 8f, f)
        }
        Gfx.label(c, "P${p.idx + 1}", p.x, p.y - p.r * 1.35f, 30f, p.look.light)
        if (p.downed) {
            val frac = 1f - (p.downT / 7f)
            Gfx.stroke.color = Color.WHITE; Gfx.stroke.strokeWidth = 8f
            c.drawArc(p.x - 60, p.y - 60, p.x + 60, p.y + 60, -90f, 360f * frac, false, Gfx.stroke)
        }
        if (p.rapidT > 0f || p.tripleT > 0f) {
            Gfx.stroke.color = if (p.rapidT > 0f) 0x88C9A27A.toInt() else 0x888FA3C9.toInt(); Gfx.stroke.strokeWidth = 4f
            c.drawCircle(p.x, p.y, p.r * 1.25f + sin(time * 10f) * 3f, Gfx.stroke)
        }
    }

    private fun drawWorld(c: Canvas) {
        c.drawBitmap(background(mode == VERSUS), 0f, 0f, null)
        if (mode == COOP) drawWall(c, coopWall) else { drawWall(c, vsWalls[0]); drawWall(c, vsWalls[1]) }
        val f = Gfx.fill
        for (cr in crates) {
            f.color = 0xFF1A1B1A.toInt(); c.drawRoundRect(cr.l - 4, cr.t - 4, cr.r + 4, cr.b + 4, 10f, 10f, f)
            f.color = Gfx.mix(0xFF2E2F2E.toInt(), 0xFF5A5B58.toInt(), cr.hp / cr.maxHp); c.drawRoundRect(cr.l, cr.t, cr.r, cr.b, 8f, 8f, f)
            Gfx.stroke.color = 0xFF1A1B1A.toInt(); Gfx.stroke.strokeWidth = 4f
            c.drawLine(cr.l + 6, cr.t + 6, cr.r - 6, cr.b - 6, Gfx.stroke); c.drawLine(cr.r - 6, cr.t + 6, cr.l + 6, cr.b - 6, Gfx.stroke)
            if (cr.flash > 0f) { f.color = Color.argb((cr.flash * 140).toInt(), 255, 255, 255); c.drawRect(cr.l, cr.t, cr.r, cr.b, f) }
        }
        if (xmas) drawTreeLights(c)
        for (k in pickups) drawPickup(c, k)

        // draw nugs sorted by y so lower ones overlap higher ones
        val list = ArrayList<Any>(enemies.size + 2)
        list.addAll(enemies); for (p in players) if (p.active) list.add(p)
        list.sortBy { if (it is Enemy) it.y else (it as Player).y }
        for (o in list) {
            if (o is Enemy) {
                Gfx.nug(c, o.x, o.y, o.r, o.look, o.fx, o.fy, o.phase, o.moving, o.flash, 1)
                if (o.hp < o.maxHp) Gfx.bar(c, o.x - o.r, o.y - o.r * 1.25f - 10f, o.r * 2, 8f, o.hp / o.maxHp, LOWCOL)
            } else drawPlayer(c, o as Player)
        }
        for (b in bullets) {
            f.color = Gfx.alpha(b.color, 0.35f); c.drawCircle(b.x - b.vx * 0.012f, b.y - b.vy * 0.012f, b.r * 0.9f, f)
            f.color = b.color; c.drawCircle(b.x, b.y, b.r, f)
            f.color = Gfx.shade(b.color, 0.5f); c.drawCircle(b.x, b.y, b.r * 0.45f, f)
        }
        for (p in particles) {
            f.color = Gfx.alpha(p.color, p.life / p.maxLife)
            c.drawCircle(p.x, p.y, p.size * (0.4f + 0.6f * p.life / p.maxLife), f)
        }
        for (fl in floaters) Gfx.label(c, fl.text, fl.x, fl.y, 38f, Gfx.alpha(fl.color, min(1f, fl.life * 2f)))
    }

    private fun drawHud(c: Canvas) {
        val f = Gfx.fill
        f.color = 0xFF111312.toInt()
        c.drawRect(0f, 0f, W, TOP - 14f, f)
        if (mode == COOP) {
            Gfx.label(c, "WAVE $wave", W / 2, 52f, 46f, Color.WHITE)
            Gfx.bar(c, W / 2 - 300, 74f, 600f, 26f, coopWall.hp / coopWall.maxHp,
                if (coopWall.hp / coopWall.maxHp > 0.3f) WALLCOL else LOWCOL)
            Gfx.label(c, "WALL ${coopWall.hp.toInt().coerceAtLeast(0)}", W / 2, 96f, 22f, Color.BLACK, shadow = false)
            Gfx.label(c, "Bad nugs left: ${enemies.size + spawnQueue.size}", W / 2 + 500, 70f, 30f, MUTED)
            Gfx.label(c, "Best: wave $bestWave", W / 2 - 500, 70f, 30f, 0xFFAAAAAA.toInt())
        } else {
            Gfx.label(c, "ROUND $round", W / 2, 50f, 42f, Color.WHITE)
            Gfx.label(c, "${score[0]}  -  ${score[1]}", W / 2, 100f, 44f, SOFT)
        }
        for (p in players) {
            if (!p.active) continue
            val left = p.idx == 0
            val x = if (left) 30f else W - 450f
            Gfx.label(c, "P${p.idx + 1} ${COLOR_NAMES[colorIdx[p.idx]]}", if (left) x else x + 420f, 40f, 30f, p.look.light,
                if (left) Paint.Align.LEFT else Paint.Align.RIGHT)
            Gfx.bar(c, x, 56f, 420f, 22f, p.hp / p.maxHp, if (p.hp > 35f) HPCOL else LOWCOL)
            if (mode == COOP) {
                Gfx.label(c, "Kills ${p.kills}", if (left) x else x + 420f, 112f, 26f, 0xFFDDDDDD.toInt(),
                    if (left) Paint.Align.LEFT else Paint.Align.RIGHT)
            } else {
                val w = vsWalls[p.idx]
                Gfx.bar(c, x, 92f, 420f, 16f, w.hp / w.maxHp, WALLCOL)
            }
        }
        // pause button for touch players
        if (input.touchRecently() && state == S_PLAY) {
            f.color = Color.argb(120, 255, 255, 255)
            c.drawRect(W / 2 - 22 + 380, 20f, W / 2 - 8 + 380, 56f, f)
            c.drawRect(W / 2 + 8 + 380, 20f, W / 2 + 22 + 380, 56f, f)
        }
        if (mode == COOP && bannerT > 0f && state == S_PLAY) {
            val a = min(1f, bannerT)
            Gfx.label(c, bannerText, W / 2, 470f, 120f, Gfx.alpha(Color.WHITE, a))
            Gfx.label(c, bannerSub, W / 2, 550f, 44f, Gfx.alpha(SOFT, a))
        }
        if (mode == VERSUS && state == S_PLAY) {
            if (roundIntro > 0f) {
                Gfx.label(c, "ROUND $round", W / 2, 470f, 110f, Color.WHITE)
                Gfx.label(c, if (roundIntro > 0.8f) "Get ready..." else "FIGHT!", W / 2, 560f, 60f, SOFT)
            }
            if (roundOver > 0f && roundWinner >= 0) {
                Gfx.label(c, "PLAYER ${roundWinner + 1} TAKES THE ROUND", W / 2, 500f, 80f, players[roundWinner].look.light)
            }
        }
    }

    private fun drawTouchControls(c: Canvas) {
        if (!input.touchRecently()) return
        val f = Gfx.fill; val s = Gfx.stroke
        for (p in 0..1) {
            if (!players[p].active) continue
            val col = players[p].look.light
            f.color = Gfx.alpha(col, if (input.touchHeld(p, InputHub.ROLE_FIRE)) 0.45f else 0.2f)
            c.drawCircle(InputHub.FIRE_X[p], InputHub.FIRE_Y, InputHub.FIRE_R, f)
            Gfx.label(c, "FIRE", InputHub.FIRE_X[p], InputHub.FIRE_Y + 12f, 34f, Gfx.alpha(Color.WHITE, 0.7f), shadow = false)
            f.color = Gfx.alpha(col, if (input.touchHeld(p, InputHub.ROLE_DASH)) 0.45f else 0.2f)
            c.drawCircle(InputHub.DASH_X[p], InputHub.DASH_Y, InputHub.DASH_R, f)
            Gfx.label(c, "DASH", InputHub.DASH_X[p], InputHub.DASH_Y + 9f, 26f, Gfx.alpha(Color.WHITE, 0.7f), shadow = false)
            val st = input.touchStick(p)
            if (st != null) {
                s.color = Gfx.alpha(Color.WHITE, 0.35f); s.strokeWidth = 5f
                c.drawCircle(st[0], st[1], InputHub.STICK_RANGE, s)
                var dx = st[2] - st[0]; var dy = st[3] - st[1]
                val l = sqrt(dx * dx + dy * dy)
                if (l > InputHub.STICK_RANGE) { dx = dx / l * InputHub.STICK_RANGE; dy = dy / l * InputHub.STICK_RANGE }
                f.color = Gfx.alpha(Color.WHITE, 0.45f)
                c.drawCircle(st[0] + dx, st[1] + dy, 48f, f)
            } else {
                Gfx.label(c, "drag to move", if (p == 0) 260f else W - 260f, 960f, 28f, Gfx.alpha(Color.WHITE, 0.35f), shadow = false)
            }
        }
    }

    private fun dim(c: Canvas, a: Int = 170) { Gfx.fill.color = Color.argb(a, 0, 0, 0); c.drawRect(0f, 0f, W, H, Gfx.fill) }

    private fun menuItem(c: Canvas, text: String, y: Float, selected: Boolean, width: Float = 800f) {
        if (selected) {
            Gfx.fill.color = SEL
            c.drawRoundRect(W / 2 - width / 2, y - 58, W / 2 + width / 2, y + 22, 40f, 40f, Gfx.fill)
            Gfx.stroke.color = 0x66FFFFFF; Gfx.stroke.strokeWidth = 2f
            c.drawRoundRect(W / 2 - width / 2, y - 58, W / 2 + width / 2, y + 22, 40f, 40f, Gfx.stroke)
        }
        Gfx.label(c, text, W / 2, y, 46f, if (selected) Color.WHITE else 0xFF6F7471.toInt(), shadow = false)
    }

    private fun drawPause(c: Canvas) {
        dim(c)
        Gfx.label(c, "PAUSED", W / 2, 380f, 110f, Color.WHITE)
        if (pauseMsg.isNotEmpty()) Gfx.label(c, pauseMsg, W / 2, 440f, 34f, LOWCOL)
        for (i in pauseItems.indices) menuItem(c, pauseItems[i], pauseItemY(i), i == pauseIdx, 600f)
        Gfx.label(c, controllerStatus(), W / 2, 1000f, 30f, 0xFFAAAAAA.toInt())
    }

    private fun drawOver(c: Canvas) {
        dim(c, 160)
        if (mode == COOP) {
            Gfx.label(c, "THE WALL HAS FALLEN", W / 2, 300f, 100f, LOWCOL)
            Gfx.label(c, "You held out until wave $wave", W / 2, 390f, 54f, Color.WHITE)
            Gfx.label(c, "Best ever: wave $bestWave", W / 2, 460f, 40f, SOFT)
            var x = if (players[1].active) W / 2 - 260f else W / 2
            for (p in players) {
                if (!p.active) continue
                Gfx.nug(c, x, 620f, 70f, p.look, 0f, 0.3f, p.phase, true, 0f, 0, 1f, xmas)
                Gfx.label(c, "P${p.idx + 1}: ${p.kills} kills", x, 780f, 40f, p.look.light)
                x += 520f
            }
        } else {
            val w = players[matchWinner.coerceAtLeast(0)]
            Gfx.label(c, "PLAYER ${w.idx + 1} WINS!", W / 2, 300f, 120f, w.look.light)
            Gfx.label(c, "${score[0]}  -  ${score[1]}", W / 2, 400f, 60f, Color.WHITE)
            val hop = abs(sin(time * 6f)) * 40f
            Gfx.nug(c, W / 2, 620f - hop, 110f, w.look, 0f, 0.2f, w.phase, true, 0f, 0, 1f, xmas)
            Gfx.label(c, "Top bud: ${COLOR_NAMES[colorIdx[w.idx]]}", W / 2, 800f, 44f, Color.WHITE)
        }
        for (i in 0..1) {
            val cx = if (i == 0) W / 2 - 280 else W / 2 + 280
            Gfx.fill.color = if (overIdx == i) SEL else 0x66000000
            c.drawRoundRect(cx - 240, 860f, cx + 240, 980f, 50f, 50f, Gfx.fill)
            Gfx.label(c, if (i == 0) "Play Again" else "Main Menu", cx, 938f, 50f, Color.WHITE)
        }
    }

    private fun controllerStatus(): String {
        val a = input.slotLabel(0).ifEmpty { "press A on a controller" }
        val b = input.slotLabel(1).ifEmpty { "press A on a 2nd controller" }
        return "P1: $a     P2: $b"
    }

    private fun drawTitle(c: Canvas) {
        c.drawBitmap(background(false), 0f, 0f, null)
        dim(c, 60)
        for (e in titleNugs) Gfx.nug(c, e.x, e.y, e.r, e.look, e.fx, 0f, e.phase, true, 0f, if (e.type == 1) 1 else 0, 1f, xmas && e.type == 0)
        val bob = sin(time * 2f) * 8f
        Gfx.label(c, "NUG OF WAR", W / 2, 230f + bob, 150f, 0xFFEDEFED.toInt(), shadow = false)
        Gfx.label(c, "Defend the wall together, or battle it out bud vs bud", W / 2, 315f, 36f, MUTED, shadow = false)
        for (i in titleItems.indices) menuItem(c, titleItems[i], titleItemY(i), i == menuIdx)
        if (xmas) Gfx.label(c, "Merry Christmas", W / 2, 375f, 34f, 0xFFC46A6A.toInt(), shadow = false)
        Gfx.label(c, controllerStatus(), W / 2, 1040f, 30f, 0xFFCCCCCC.toInt())
        drawSnowAndSanta(c)
    }

    private fun drawHelp(c: Canvas) {
        c.drawBitmap(background(false), 0f, 0f, null)
        dim(c, 150)
        Gfx.label(c, "HOW TO PLAY", W / 2, 130f, 80f, ACCENT)
        val lines = arrayOf(
            "CO-OP: bad brown & yellow nugs march at your wall. Shoot them before they break it.",
            "If you get knocked down, your buddy can stand next to you to revive you faster.",
            "Every 5th wave a Moldy Mega Nug shows up. Pickups: green = heal, orange = rapid fire,",
            "blue = triple shot, brick = repair the wall. In Christmas Mode Santa drops presents!",
            "VERSUS: stay on your side. Knock out the other bud OR smash their wall. First to 3 rounds.",
            "",
            "CONTROLLER: left stick / d-pad = move     right stick = aim (optional)",
            "A, X, R1 or right trigger = shoot     B, L1 or left trigger = dash (you're invincible mid-dash)",
            "START = pause     In the lobby: left/right = colour, up/down or Y = shape, A = ready",
            "The first controller to press a button is Player 1, the next one is Player 2.",
            "",
            "KEYBOARD: P1 = WASD + Space (shoot) + Left Shift (dash)    P2 = Arrows + Enter + Right Shift",
            "TOUCH: each player gets half the screen: drag on the outer side to move, buttons to shoot/dash."
        )
        var y = 230f
        for (l in lines) { Gfx.label(c, l, W / 2, y, 34f, Color.WHITE); y += 58f }
        Gfx.label(c, "Press A or tap to go back", W / 2, 1030f, 34f, SOFT)
        drawSnowAndSanta(c)
    }

    private fun drawLobby(c: Canvas) {
        c.drawBitmap(background(mode == VERSUS), 0f, 0f, null)
        dim(c, 110)
        Gfx.label(c, if (mode == COOP) "CO-OP: DEFEND THE WALL" else "VERSUS: BUD VS BUD", W / 2, 120f, 72f, Color.WHITE)
        Gfx.label(c, "Pick your nug", W / 2, 172f, 36f, 0xFFDDDDDD.toInt())
        for (p in 0..1) {
            val l = if (p == 0) 160f else 1000f
            Gfx.panel(c, l, 190f, l + 760f, 860f)
            val cx = l + 380f
            Gfx.label(c, "PLAYER ${p + 1}", cx, 260f, 54f, Color.WHITE)
            if (!joined[p]) {
                Gfx.label(c, "Press A to join", cx, 520f, 54f, SOFT)
                Gfx.label(c, if (mode == COOP) "(optional: you can defend solo)" else "(needed for versus)", cx, 580f, 32f, 0xFFAAAAAA.toInt())
                continue
            }
            val look = Look(COLORS[colorIdx[p]], shapeIdx[p], 11 + p * 7 + colorIdx[p])
            val hop = if (ready[p]) abs(sin(time * 7f)) * 30f else 0f
            Gfx.nug(c, cx, 450f - hop, 100f, look, if (p == 0) 0.6f else -0.6f, 0.2f, time * 8f, ready[p], 0f, 0, 1f, xmas)
            Gfx.label(c, "<  ${COLOR_NAMES[colorIdx[p]]}  >", cx, 650f, 46f, look.light)
            Gfx.label(c, "Shape: ${SHAPE_NAMES[shapeIdx[p]]}", cx, 715f, 38f, Color.WHITE)
            val pad = input.slotLabel(p)
            Gfx.label(c, if (pad.isEmpty()) "no controller yet" else pad, cx, 770f, 28f, 0xFFAAAAAA.toInt())
            Gfx.label(c, if (ready[p]) "READY!" else "Press A when ready", cx, 830f, 40f, if (ready[p]) ACCENT else SOFT)
        }
        Gfx.fill.color = SEL
        c.drawRoundRect(W / 2 - 220, 900f, W / 2 + 220, 1010f, 50f, 50f, Gfx.fill)
        val label = if (lobbyCountdown > 0f) "Starting..." else "START"
        Gfx.label(c, label, W / 2, 975f, 54f, Color.WHITE)
        Gfx.label(c, "B = back", 160f, 1040f, 28f, 0xFFAAAAAA.toInt(), Paint.Align.LEFT)
        drawSnowAndSanta(c)
    }
}
