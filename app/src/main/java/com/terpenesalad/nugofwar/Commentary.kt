package com.terpenesalad.nugofwar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.random.Random

/**
 * Laid-back "streamer on the couch" commentary plus a small scrolling live chat.
 * The streamer is an original made-up character. Text only, with a soft blip.
 */
class Commentary(private val sfx: Sound) {
    enum class Ev { WAVE, BOSS, WALL_LOW, DOWN, REVIVE, STREAK, CLEAR, GAME_OVER, ROUND, MATCH, SANTA, POWER, IDLE, VS_WALL_LOW, FIGHT }

    private class Msg(val user: String, val text: String, val color: Int) { var life = 9f }

    private val rnd = Random(System.nanoTime())
    private var line = ""
    private var lineT = 0f
    private var cooldown = 0f
    private var idleT = 18f
    private var chatT = 2f
    private val chat = ArrayList<Msg>()
    private var burst = 0
    private var burstPool: Array<String> = GENERIC
    private var burstT = 0f

    fun reset() { line = ""; lineT = 0f; cooldown = 2f; idleT = 18f; chat.clear(); burst = 0 }

    fun event(e: Ev, who: String = "", n: Int = 0, force: Boolean = false) {
        val pool = when (e) {
            Ev.WAVE -> if (n == 1) arrayOf(
                "ok chat, chat, listen. we defend the wall. that's the whole praxis today.",
                "alright dude, we're live. the wall is the people's wall. protect it.",
                "ok ok ok, here we go. I genuinely have a good feeling about this. famous last words."
            ) else arrayOf(
                "wave $n. dude. we are SO locked in right now.",
                "wave $n, let's go. hydrate chat, I'm serious.",
                "they keep sending them, bro. who is FUNDING these guys?",
                "wave $n. they're unionising, chat. I kind of respect it.",
                "ok wave $n. nobody panic. I'm panicking a little, but nobody panic."
            )
            Ev.BOSS -> arrayOf(
                "chat. CHAT. what is that. dude what IS that.",
                "oh that's a landlord nug. that thing owns six walls and fixes none of them.",
                "bro that's the bourgeoisie. eat the... ok just shoot it, shoot it.",
                "that's not a nug, that's a hedge fund with legs."
            )
            Ev.WALL_LOW -> arrayOf(
                "dude, the wall is NOT okay.",
                "the wall is held together with vibes right now, chat.",
                "somebody grab a brick, I'm literally begging.",
                "this is crumbling infrastructure, dawg. typical."
            )
            Ev.DOWN -> arrayOf(
                "$who is DOWN, bro, go get them!",
                "no no no no, $who what are you DOING?",
                "$who just got absolutely cooked, chat.",
                "dude $who is getting jumped, help them!"
            )
            Ev.REVIVE -> arrayOf(
                "see? solidarity. that's what solidarity looks like, chat.",
                "$who is back, LET'S GOOO.",
                "nobody gets left behind. mutual aid, baby."
            )
            Ev.STREAK -> arrayOf(
                "$who is COOKING right now, holy.",
                "ok $who is genuinely cracked, dude.",
                "$who is just deleting them. this is a public service.",
                "$who is a sicko. I love it. deranged behaviour."
            )
            Ev.CLEAR -> arrayOf(
                "clean. that was so clean, dude.",
                "chat, are we... are we good at this? unironically?",
                "wave cleared. huge W. we take those.",
                "we're SO back."
            )
            Ev.GAME_OVER -> arrayOf(
                "it's so over. the wall is gone, chat.",
                "dude that was rigged. I'm not saying it was rigged, but it was rigged.",
                "ok that's the stream, wall has fallen. gg chat, gg.",
                "we'll be back. we're always back. we're so back. eventually."
            )
            Ev.ROUND -> arrayOf(
                "$who takes it! dude the other bud is in SHAMBLES.",
                "that's a round for $who. clip that, clip that.",
                "$who with the round. chat, be honest, did you see that coming? no you didn't."
            )
            Ev.MATCH -> arrayOf(
                "$who wins it ALL. absolute dynasty, bro.",
                "and that's the match. $who is built different, genuinely.",
                "$who is the champion. I'm not even mad, that was beautiful dude."
            )
            Ev.SANTA -> arrayOf(
                "is that... is that Santa? chat, Santa's carrying us.",
                "big man in the sleigh just dropped loot. redistribution of wealth, baby.",
                "Santa's doing more for the people than most governments, honestly."
            )
            Ev.POWER -> arrayOf(
                "$who powered up. oh it's dangerous now, dude.",
                "$who grabbed the good stuff. top shelf.",
                "$who is juiced up. everybody run."
            )
            Ev.VS_WALL_LOW -> arrayOf(
                "$who's wall is crumbling, bro, this is a demolition.",
                "chat, $who has like three bricks left.",
                "the wall is cooked, $who. it's so over for you."
            )
            Ev.FIGHT -> arrayOf(
                "ok chat, bud versus bud. pick a side. there's no neutral here.",
                "I'm staying neutral. I'm not staying neutral, I'm never neutral.",
                "let's see who's the real top shelf, dude."
            )
            Ev.IDLE -> arrayOf(
                "chat's going crazy right now. relax chat, relax.",
                "honestly the bad nugs have a point about one thing. not the wall thing though.",
                "I'm not even mad, that's kind of impressive.",
                "real ones defend the wall. I'm being so serious.",
                "dude this game is peak. genuinely peak.",
                "somebody in chat said 'wall diff'. fair, actually.",
                "the brown ones look like they haven't been watered since 2019, bro.",
                "chat, is this... is this what late stage nug-ism looks like?",
                "ok hold on, I need to stretch. no I don't, keep going.",
                "bro the little legs. look at the little legs, chat."
            )
        }
        if (cooldown > 0f && !force) { queueChat(e); return }
        line = pool[rnd.nextInt(pool.size)]
        lineT = 4.5f
        cooldown = 6f
        idleT = 22f + rnd.nextFloat() * 14f
        sfx.play(Sound.SELECT, 0.12f)
        queueChat(e)
    }

    private fun queueChat(e: Ev) {
        burstPool = when (e) {
            Ev.BOSS -> BOSS_CHAT
            Ev.WALL_LOW, Ev.VS_WALL_LOW -> WALL_CHAT
            Ev.DOWN -> DOWN_CHAT
            Ev.STREAK, Ev.ROUND, Ev.MATCH, Ev.CLEAR, Ev.REVIVE -> HYPE_CHAT
            Ev.GAME_OVER -> SAD_CHAT
            Ev.SANTA -> SANTA_CHAT
            else -> GENERIC
        }
        burst = 2 + rnd.nextInt(3)
        burstT = 0.3f
    }

    fun update(dt: Float, playing: Boolean) {
        if (lineT > 0f) lineT -= dt
        if (cooldown > 0f) cooldown -= dt
        for (m in chat) m.life -= dt
        chat.removeAll { it.life <= 0f }
        if (!playing) return
        idleT -= dt
        if (idleT <= 0f) event(Ev.IDLE)
        if (burst > 0) {
            burstT -= dt
            if (burstT <= 0f) { burst--; burstT = 0.35f + rnd.nextFloat() * 0.5f; post(burstPool) }
        } else {
            chatT -= dt
            if (chatT <= 0f) { chatT = 2.5f + rnd.nextFloat() * 3.5f; post(GENERIC) }
        }
    }

    private fun post(pool: Array<String>) {
        val u = rnd.nextInt(USERS.size)
        chat.add(Msg(USERS[u], pool[rnd.nextInt(pool.size)], USER_COLORS[u % USER_COLORS.size]))
        while (chat.size > 6) chat.removeAt(0)
    }

    /** Draws the chat column at [x],[bottomY] going up, plus the streamer caption at [capY]. */
    fun draw(c: Canvas, x: Float, bottomY: Float, width: Float, capX: Float, capY: Float) {
        val t = Gfx.text
        var y = bottomY
        for (i in chat.indices.reversed()) {
            val m = chat[i]
            val a = (m.life / 2f).coerceIn(0f, 1f) * 0.75f
            t.textSize = 22f
            t.textAlign = Paint.Align.LEFT
            val name = m.user + ": "
            val nw = t.measureText(name)
            var msg = m.text
            while (msg.length > 3 && nw + t.measureText(msg) > width) msg = msg.dropLast(2)
            if (msg != m.text) msg = msg.trimEnd() + "…"
            t.color = Gfx.alpha(m.color, a); c.drawText(name, x, y, t)
            t.color = Gfx.alpha(0xFFC8CCC8.toInt(), a); c.drawText(msg, x + nw, y, t)
            y -= 30f
        }
        if (chat.isNotEmpty()) {
            t.textSize = 18f
            t.color = Gfx.alpha(0xFF8F948F.toInt(), 0.6f)
            c.drawText("● LIVE CHAT", x, y - 4f, t)
        }
        if (lineT > 0f && line.isNotEmpty()) {
            val a = (lineT / 0.6f).coerceIn(0f, 1f)
            t.textSize = 30f
            t.textAlign = Paint.Align.CENTER
            val w = t.measureText(line) + 120f
            Gfx.fill.color = Color.argb((170 * a).toInt(), 14, 16, 15)
            c.drawRoundRect(capX - w / 2, capY - 40f, capX + w / 2, capY + 14f, 26f, 26f, Gfx.fill)
            Gfx.fill.color = Gfx.alpha(0xFFC46A6A.toInt(), a)
            c.drawCircle(capX - w / 2 + 30f, capY - 13f, 7f, Gfx.fill)
            t.color = Gfx.alpha(0xFF8F948F.toInt(), a)
            t.textSize = 20f
            t.textAlign = Paint.Align.LEFT
            c.drawText("couchcaster", capX - w / 2 + 44f, capY - 50f, t)
            t.textSize = 30f
            t.textAlign = Paint.Align.CENTER
            t.color = Gfx.alpha(0xFFE6E8E6.toInt(), a)
            c.drawText(line, capX + 20f, capY - 2f, t)
        }
    }

    companion object {
        val USERS = arrayOf("budlover420", "terp_tom", "wallguy", "sativa_sam", "kief_queen", "mr_grinder",
            "dankfrank", "ashtray_andy", "gramma_kush", "nuglife99", "rollup_ricky", "trichome_tina",
            "couch_lock", "bong_appetit", "resin_rachel", "lowkey_leaf")
        val USER_COLORS = intArrayOf(0xFF9CC79A.toInt(), 0xFF9D8CC4.toInt(), 0xFF7FB8A8.toInt(),
            0xFFC48A9F.toInt(), 0xFF8FA3C9.toInt(), 0xFFC9A27A.toInt(), 0xFFB5C98A.toInt())
        val GENERIC = arrayOf("W", "W stream", "first", "hydrate check", "chat is this real", "nug moment",
            "lol", "who's winning", "o7", "LMAOOO", "just got here what did I miss", "the legs are so tiny",
            "W wall", "real", "based", "he's so locked in", "posture check", "we're so back", "DUDE",
            "the people's wall", "bro is cooking", "solidarity with the wall", "true", "chat is cooked")
        val HYPE_CHAT = arrayOf("LETS GOOO", "CLIP IT", "W", "insane", "built different", "cracked",
            "absolute cinema", "GG", "sheesh", "W W W", "WE'RE SO BACK", "goated")
        val SAD_CHAT = arrayOf("L", "gg go next", "it's so over", "wall diff", "rip wall", "o7", "unlucky", "run it back")
        val BOSS_CHAT = arrayOf("WHAT IS THAT", "nah", "big boy incoming", "so moldy", "RUN", "boss music", "landlord nug 💀", "tax the mega nug")
        val WALL_CHAT = arrayOf("the wall 💀", "repair it!!", "wall diff", "it's crumbling", "brick check")
        val DOWN_CHAT = arrayOf("L", "skill issue", "get up!!", "revive them", "noooo", "unlucky")
        val SANTA_CHAT = arrayOf("SANTA??", "W santa", "ho ho ho", "santa carrying", "present!!", "merry christmas chat")
    }
}
