package io.github.mwalczak.spelucky.game

import kotlin.math.floor

/** Anything that moves around the level. (x, y) is the top-left corner of its hitbox. */
abstract class Entity(var x: Float, var y: Float, val w: Float, val h: Float) {
    var vx = 0f
    var vy = 0f
    var onGround = false
    var dead = false

    val right get() = x + w
    val bottom get() = y + h
    val centerX get() = x + w / 2
    val centerY get() = y + h / 2

    fun overlaps(o: Entity) = overlaps(o.x, o.y, o.w, o.h)

    fun overlaps(ox: Float, oy: Float, ow: Float, oh: Float) =
        x < ox + ow && ox < x + w && y < oy + oh && oy < y + h

    /** Moves horizontally, stopping at walls. Returns true if a wall was hit. */
    fun moveX(level: Level, dx: Float): Boolean {
        if (dx == 0f) return false
        x += dx
        val top = tile(y)
        val bot = tile(bottom - 0.01f)
        if (dx > 0) {
            val tx = tile(right - 0.01f)
            for (ty in top..bot) if (level.isSolid(tx, ty)) {
                x = tx * TILE - w
                return true
            }
        } else {
            val tx = tile(x)
            for (ty in top..bot) if (level.isSolid(tx, ty)) {
                x = (tx + 1) * TILE
                return true
            }
        }
        return false
    }

    /**
     * Moves vertically, stopping at floors and ceilings. Ladder tops count as floors
     * when [ladderTopIsFloor] is set. Returns true if something was hit.
     */
    fun moveY(level: Level, dy: Float, ladderTopIsFloor: Boolean = false): Boolean {
        if (dy == 0f) return false
        val prevBottom = bottom
        y += dy
        val left = tile(x)
        val rightT = tile(right - 0.01f)
        if (dy > 0) {
            val ty = tile(bottom - 0.01f)
            for (tx in left..rightT) {
                val oneWay = ladderTopIsFloor && level.isLadderTop(tx, ty) && prevBottom <= ty * TILE + 0.01f
                if (level.isSolid(tx, ty) || oneWay) {
                    y = ty * TILE - h
                    return true
                }
            }
        } else {
            val ty = tile(y)
            for (tx in left..rightT) if (level.isSolid(tx, ty)) {
                y = (ty + 1) * TILE
                return true
            }
        }
        return false
    }

    /** Is there floor (solid or ladder top) right below our feet? */
    fun standingOnSomething(level: Level, ladderTopIsFloor: Boolean = true): Boolean {
        val ty = tile(bottom + 0.5f)
        if (bottom % TILE > 0.02f && TILE - bottom % TILE > 0.02f) return false
        for (tx in tile(x)..tile(right - 0.01f)) {
            if (level.isSolid(tx, ty)) return true
            if (ladderTopIsFloor && level.isLadderTop(tx, ty)) return true
        }
        return false
    }

    companion object {
        fun tile(p: Float) = floor(p / TILE).toInt()
    }
}
