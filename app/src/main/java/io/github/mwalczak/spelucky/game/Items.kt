package io.github.mwalczak.spelucky.game

import kotlin.math.min

/**
 * Everything the shop sells. [equipment] items are kept until the run ends and are never
 * offered twice; the others are supplies or guns.
 */
enum class ShopItem(val title: String, val description: String, val price: Int, val equipment: Boolean = false, val gun: Boolean = false) {
    BOMBS("Bombs x4", "Press BOMB to blow up walls and monsters", 2000),
    ROPES("Ropes x4", "Four more ropes for climbing", 1500),
    BASEBALL_GLOVE("Baseball glove", "Throw bombs straight and far. They bounce off walls", 3000, equipment = true),
    CLIMBING_GLOVES("Climbing gloves", "Push against a wall and hold ▲ to climb it", 6000, equipment = true),
    PISTOL("Pistol", "FIRE shoots a bullet. Each bullet takes 1 heart", 5000, gun = true),
    SHOTGUN("Shotgun", "FIRE shoots 5 pellets at once", 9000, gun = true),
    FREEZE_GUN("Freeze gun", "Freezes monsters for 5 seconds. Jump on them to smash!", 7000, gun = true),
    WALL_BREAKER("Wall-breaker gun", "Bullets smash the wall they hit. 10 shots", 10000, gun = true),
    CAPE("Cape", "Hold JUMP while falling to glide", 8000, equipment = true),
    PARACHUTE("Parachute", "Opens after falling 5 tiles and lands you safely. One use", 2000, equipment = true),
    SPRING_BOOTS("Spring boots", "Jump twice as high", 5000, equipment = true),
}

/** One item on a shop pedestal. */
class ShopOffer(val item: ShopItem, val tx: Int, val ty: Int) {
    var sold = false
}

/** A lit bomb. Explodes when its fuse runs out. */
class Bomb(x: Float, y: Float) : Entity(x, y, 6f, 6f) {
    var fuse = FUSE
    /** Thrown with the baseball glove: flies level until it hits a wall. */
    var straight = false

    fun update(dt: Float, level: Level) {
        fuse -= dt
        if (straight) {
            if (moveX(level, vx * dt)) {
                vx = -vx * 0.6f
                straight = false
            }
            return
        }
        vy = min(vy + Player.GRAVITY * dt, Player.MAX_FALL)
        if (moveX(level, vx * dt)) vx = -vx * 0.5f
        if (moveY(level, vy * dt, true)) {
            if (vy > 0) {
                onGround = true
                vy = if (vy > 80f) -vy * 0.3f else 0f
            } else {
                vy = 0f
            }
        }
        if (onGround) vx *= 0.9f
    }

    companion object {
        const val FUSE = 3f
        /** Dirt this close to the blast (in tiles) is destroyed. */
        const val BREAK_RADIUS = 1.6f
        /** Monsters and players this close (in tiles) get hurt. */
        const val HURT_RADIUS = 2.2f
    }
}

enum class BulletKind { BULLET, PELLET, ICE, BREAKER }

class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float, val kind: BulletKind, var life: Float) {
    var dead = false
}

/** A short flash where a bomb went off, for drawing. */
class Explosion(val x: Float, val y: Float) {
    var time = 0f
}
