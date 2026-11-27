package com.terpenesalad.nugofwar

class Player(val idx: Int) {
    var x = 0f; var y = 0f
    val r = 40f
    var fx = 1f; var fy = 0f
    var hp = 100f
    val maxHp = 100f
    var fireCd = 0f
    var dashT = 0f; var dashCd = 0f; var dvx = 0f; var dvy = 0f
    var inv = 0f
    var downed = false; var downT = 0f
    var phase = 0f; var moving = false
    var flash = 0f
    var rapidT = 0f; var tripleT = 0f
    var kills = 0
    var active = false
    var look = Look(0xFF7BD34A.toInt(), 0, 1)
    val dashHit = HashSet<Enemy>()
    val alive get() = active && !downed && hp > 0f
}

class Enemy(val type: Int, var x: Float, var y: Float, val r: Float, var hp: Float,
            val speed: Float, val wallDps: Float, val meleeDps: Float, val look: Look) {
    val maxHp = hp
    var phase = (x * 0.37f) % 6.28f
    var flash = 0f
    var atkT = 0f
    var shotT = 1.5f
    var spawnT = 3f
    var kx = 0f; var ky = 0f
    var stopX = 0f
    var wobble = 0f
    var moving = true
    var fx = -1f; var fy = 0f
    var dead = false
}

class Bullet(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float,
             val dmg: Float, val owner: Int, val color: Int) {
    var life = 2.2f
    var dead = false
}

class Pickup(val x: Float, val y: Float, val type: Int) {
    var life = 12f
    var dead = false
    var gift = false
    companion object { const val HEAL = 0; const val RAPID = 1; const val TRIPLE = 2; const val REPAIR = 3 }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val maxLife: Float,
               val size: Float, val color: Int) {
    var life = maxLife
}

class Wall(val l: Float, val r: Float, val top: Float, val bot: Float, val maxHp: Float) {
    var hp = maxHp
    var flash = 0f
    var thudT = 0f
}

class Crate(val cx: Float, val cy: Float, val w: Float, val h: Float, val maxHp: Float) {
    var hp = maxHp
    var flash = 0f
    val l get() = cx - w / 2
    val r get() = cx + w / 2
    val t get() = cy - h / 2
    val b get() = cy + h / 2
}

class Floater(val text: String, var x: Float, var y: Float, val color: Int) {
    var life = 1.2f
}
