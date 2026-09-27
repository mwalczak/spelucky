package io.github.mwalczak.spelucky.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

abstract class Enemy(x: Float, y: Float, w: Float, h: Float) : Entity(x, y, w, h) {
    var facing = 1
    var animTime = 0f
    abstract fun update(dt: Float, game: Game)
}

/** Walks back and forth, turning at walls and edges. */
class Snake(x: Float, y: Float) : Enemy(x, y, 12f, 8f) {
    private val speed = 22f
    private var pause = 0f

    override fun update(dt: Float, game: Game) {
        val level = game.level
        animTime += dt
        vy = min(vy + Player.GRAVITY * dt, Player.MAX_FALL)
        if (moveY(level, vy * dt, true)) {
            if (vy > 0) onGround = true
            vy = 0f
        }
        if (pause > 0) {
            pause -= dt
            return
        }
        if (!onGround) return
        if (moveX(level, facing * speed * dt)) turn()
        // Don't walk off edges.
        val aheadX = if (facing > 0) right + 1f else x - 1f
        val below = tile(bottom + 1f)
        val tx = tile(aheadX)
        if (!level.isSolid(tx, below) && !level.isLadderTop(tx, below)) turn()
        if (game.rng.nextInt(600) == 0) pause = 0.6f + game.rng.nextFloat()
    }

    private fun turn() {
        facing = -facing
    }
}

/** Hangs from the ceiling until the player comes close, then chases them. */
class Bat(x: Float, y: Float) : Enemy(x, y, 10f, 8f) {
    var awake = false
    private val speed = 58f

    override fun update(dt: Float, game: Game) {
        val p = game.player
        animTime += dt
        if (!awake) {
            val dx = p.centerX - centerX
            val dy = p.centerY - centerY
            if (abs(dx) < 5 * TILE && dy > -TILE && dy < 7 * TILE && p.state != Player.State.DEAD) {
                awake = true
                game.sound(Sound.BAT)
            }
            return
        }
        val dx = p.centerX - centerX
        val dy = p.centerY - centerY
        val d = hypot(dx, dy).coerceAtLeast(1f)
        facing = if (dx >= 0) 1 else -1
        vx = dx / d * speed
        vy = dy / d * speed + sin(animTime * 9f) * 30f
        if (p.state == Player.State.DEAD) vy = -speed
        moveX(game.level, vx * dt)
        moveY(game.level, vy * dt)
    }
}

enum class Treasure(val value: Int) {
    NUGGET(250), GOLD_BAR(500), EMERALD(800), SAPPHIRE(1200), RUBY(1600), ROPE_PILE(0)
}

class Pickup(x: Float, y: Float, val kind: Treasure) : Entity(x, y, 8f, 7f) {
    var bobTime = 0f

    fun update(dt: Float, level: Level) {
        bobTime += dt
        if (onGround) return
        vy = min(vy + Player.GRAVITY * dt, Player.MAX_FALL)
        if (moveY(level, vy * dt, true)) {
            vy = 0f
            onGround = true
        }
    }
}

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val color: Int,
    val size: Float = 2f,
    val gravity: Boolean = true,
) {
    val maxLife = life
}

class FloatingText(var x: Float, var y: Float, val text: String, val color: Int) {
    var life = 1.2f
}
