package com.terpenesalad.nugofwar

/** A platform traced on the wall. Thin ones are ledges you can jump up through; tall ones are solid blocks. */
class Plat(val l: Float, val t: Float, val r: Float, val b: Float) {
    val solid get() = b - t > 50f
}

object Wpn {
    const val NONE = 0; const val SWORD = 1; const val BLASTER = 2; const val GRENADE = 3; const val ROCKET = 4; const val SNOW = 5
    val NAMES = arrayOf("Bonk", "Sword", "Seed Blaster", "Grenades", "Rocket Launcher", "Snowball Blaster")
    fun ammo(t: Int) = when (t) { BLASTER -> 26; GRENADE -> 3; ROCKET -> 4; SNOW -> 30; else -> 0 }
}

/** Anything with nug physics: players and bad nugs. */
open class Body(var r: Float) {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    val hw get() = r * 0.75f
    val hTop get() = r * 0.95f
    val hBot get() = r * 1.12f
    var onGround = false
    var dropT = 0f
    var rot = 0f; var spin = 0f
    var launchT = 0f
    var stunT = 0f
    var hp = 100f; var maxHp = 100f
    var facing = 1f
    var phase = 0f
    var flash = 0f
    var inv = 0f
    var team = 0
    var dead = false
    var look = Look.of(0xFF8DB580.toInt(), 0, 1)
    var lastHitBy: Body? = null
    var moving = false
}

class Player(val idx: Int) : Body(34f) {
    var active = false
    var lives = 3
    var respawnT = 0f
    var out = false
    var jumps = 1
    var fuel = 1f
    var weapon = Wpn.NONE
    var ammo = 0
    var atkCd = 0f
    var swingT = 0f
    var blocking = false
    var blockStart = -10f
    var shield = 100f
    var kos = 0
    var kills = 0
    var aimX = 1f; var aimY = 0f
}

class Enemy(val type: Int, r: Float) : Body(r) {
    var speed = 200f
    var dmg = 8f
    var kb = 500f
    var atkCd = 1f
    var spawnT = 3f
    var poundT = 4f
    var wasAir = false
    var thinkT = 0f
}

class Proj(var x: Float, var y: Float, var vx: Float, var vy: Float, val type: Int, var team: Int, var owner: Body?) {
    var life = 3f
    var dead = false
    var rot = 0f
    companion object { const val SEED = 0; const val GRENADE = 1; const val ROCKET = 2; const val THROWN = 3; const val SPIT = 4; const val SNOW = 5 }
}

class Item(var x: Float, var y: Float, val type: Int) {
    var vy = 0f
    var life = 16f
    var dead = false
    var gift = false
    var landed = false
    companion object { const val BARREL = 100 }
}

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, val maxLife: Float,
               val size: Float, val color: Int, val grav: Float = 0f) {
    var life = maxLife
}

class Floater(val text: String, var x: Float, var y: Float, val color: Int) {
    var life = 1.4f
}

class Ring(val x: Float, val y: Float, val maxR: Float, val color: Int) {
    var t = 0f
}
