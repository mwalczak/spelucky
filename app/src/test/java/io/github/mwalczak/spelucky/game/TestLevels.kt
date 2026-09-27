package io.github.mwalczak.spelucky.game

/**
 * Builds a level from ASCII art: '#' dirt, 'L' ladder, '^' spikes,
 * 'P' player start, 'E' exit, 's' snake. The outside is always solid.
 */
fun levelOf(vararg rows: String): Level {
    val level = Level(rows[0].length, rows.size)
    for ((y, row) in rows.withIndex()) for ((x, c) in row.withIndex()) {
        level[x, y] = when (c) {
            '#' -> Tile.DIRT
            'L' -> Tile.LADDER
            '^' -> Tile.SPIKES
            else -> Tile.EMPTY
        }
        when (c) {
            'P' -> { level.entranceX = x; level.entranceY = y }
            'E' -> { level.exitX = x; level.exitY = y }
            's' -> level.spawns += Spawn(SpawnKind.SNAKE, x, y)
        }
    }
    return level
}

fun gameOn(level: Level): Game = Game(seed = 1).apply {
    startWithLevel(level)
    for (s in level.spawns) if (s.kind == SpawnKind.SNAKE) enemies += Snake(s.tx * TILE + 2f, s.ty * TILE + 8f)
}

/** Runs the game for [seconds] with [held] buttons down; [press] buttons are pressed on the first step. */
fun Game.run(seconds: Float, held: Int = 0, press: Int = 0, each: (Game) -> Unit = {}) {
    val input = InputState()
    val dt = 1f / 120f
    var first = true
    var t = 0f
    while (t < seconds) {
        input.held = held or (if (first) press else 0)
        input.pressed = if (first) press or held else 0
        update(dt, input)
        each(this)
        first = false
        t += dt
    }
}

fun Level.dump(): String = buildString {
    for (y in 0 until height) {
        for (x in 0 until width) {
            val spawn = spawns.firstOrNull { it.tx == x && it.ty == y }
            append(
                when {
                    x == entranceX && y == entranceY -> 'P'
                    x == exitX && y == exitY -> 'E'
                    spawn != null -> when (spawn.kind) {
                        SpawnKind.SNAKE -> 's'
                        SpawnKind.BAT -> 'b'
                        SpawnKind.ROPE_PILE -> 'r'
                        else -> '$'
                    }
                    else -> when (this@dump[x, y]) {
                        Tile.DIRT -> '#'
                        Tile.BORDER -> '='
                        Tile.LADDER -> 'L'
                        Tile.SPIKES -> '^'
                        else -> '.'
                    }
                }
            )
        }
        append('\n')
    }
}
