package io.github.mwalczak.spelucky.game

import kotlin.math.floor

/** Size of one tile in world units. Everything in the game is measured in these. */
const val TILE = 16f

object Tile {
    const val EMPTY = 0
    const val DIRT = 1
    const val BORDER = 2 // indestructible outer wall
    const val LADDER = 3
    const val SPIKES = 4
}

enum class SpawnKind { SNAKE, BAT, NUGGET, GOLD_BAR, EMERALD, SAPPHIRE, RUBY, ROPE_PILE }

class Spawn(val kind: SpawnKind, val tx: Int, val ty: Int)

class Level(val width: Int, val height: Int) {
    val tiles = IntArray(width * height)
    val rope = BooleanArray(width * height)
    val spawns = mutableListOf<Spawn>()

    var entranceX = 0
    var entranceY = 0
    var exitX = 0
    var exitY = 0

    val pixelWidth get() = width * TILE
    val pixelHeight get() = height * TILE

    fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Int = if (inside(x, y)) tiles[y * width + x] else Tile.BORDER

    operator fun set(x: Int, y: Int, t: Int) {
        if (inside(x, y)) tiles[y * width + x] = t
    }

    fun isSolid(x: Int, y: Int): Boolean {
        val t = this[x, y]
        return t == Tile.DIRT || t == Tile.BORDER
    }

    fun hasRope(x: Int, y: Int) = inside(x, y) && rope[y * width + x]

    fun setRope(x: Int, y: Int) {
        if (inside(x, y)) rope[y * width + x] = true
    }

    fun isClimbable(x: Int, y: Int) = this[x, y] == Tile.LADDER || hasRope(x, y)

    /** The top rung of a ladder can be stood on like a platform. */
    fun isLadderTop(x: Int, y: Int) = this[x, y] == Tile.LADDER && this[x, y - 1] != Tile.LADDER

    fun isSolidAt(px: Float, py: Float) = isSolid(tileOf(px), tileOf(py))
    fun isClimbableAt(px: Float, py: Float) = isClimbable(tileOf(px), tileOf(py))
    fun tileAt(px: Float, py: Float) = this[tileOf(px), tileOf(py)]

    fun isExit(tx: Int, ty: Int) = tx == exitX && ty == exitY

    companion object {
        fun tileOf(p: Float) = floor(p / TILE).toInt()
    }
}
