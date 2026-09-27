package io.github.mwalczak.spelucky.game

import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Spelunky-style level generator.
 *
 * The level is a 4x4 grid of rooms. A guaranteed path of rooms runs from the
 * entrance (top row) to the exit (bottom row), moving sideways and dropping down.
 * Every room is filled from a hand-made template with some random tiles, and the
 * finished level is checked with a simple "can the player walk/jump there" search.
 * If the exit can't be reached, we simply try again.
 */
class LevelGenerator(private val rng: Random) {

    companion object {
        const val ROOMS_X = 4
        const val ROOMS_Y = 4
        const val RW = 10 // room width in tiles
        const val RH = 8 // room height in tiles
        const val WIDTH = ROOMS_X * RW + 2
        const val HEIGHT = ROOMS_Y * RH + 2

        /** Tiles the player can jump up (kept below the real jump height on purpose). */
        const val JUMP_UP = 2
        /** Tiles the player can move sideways while in the air at jump height. */
        const val JUMP_SIDE = 2

        // Legend: . empty   # dirt   1 dirt 50%   2 dirt 25%   L ladder
        //         ^ spikes 60%   $ treasure spot
        private val PLAIN = arrayOf(
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "..........",
            "##########",
        )

        private val LR = listOf(
            arrayOf(
                "..........",
                "..........",
                "....$.....",
                "..######..",
                "..........",
                "..........",
                "...2......",
                "##########",
            ),
            arrayOf(
                "..........",
                "....$..$..",
                "..###L###.",
                ".....L....",
                ".....L....",
                ".....L....",
                ".....L....",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "..........",
                "....$.....",
                "...####...",
                "..######..",
                ".########.",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "..........",
                "..........",
                "...$..$...",
                "##......##",
                "##.^^^^.##",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "$........$",
                "####..####",
                "..........",
                "..........",
                "....11....",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "........$.",
                "......####",
                "....##....",
                "..##......",
                "..........",
                "##########",
            ),
            arrayOf(
                "..........",
                ".11....11.",
                "..........",
                "....22....",
                "..2....2..",
                "..........",
                "...1..1...",
                "##########",
            ),
            arrayOf(
                "1111111111",
                "1........1",
                "...$..$...",
                "..######..",
                "..........",
                "..........",
                ".^......^.",
                "##########",
            ),
        )

        private val DROP = listOf(
            arrayOf(
                "..........",
                "..........",
                "...####...",
                "..........",
                "..........",
                "..........",
                "..........",
                "##########",
            ),
            arrayOf(
                "..........",
                "..$....$..",
                ".##....##.",
                "..........",
                "....##....",
                "..........",
                "..........",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "#........#",
                "##..$...##",
                "..........",
                "..1....1..",
                "..........",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                ".....$....",
                "..2..L..2.",
                ".....L....",
                ".....L....",
                ".....L....",
                "##########",
            ),
        )

        private val SIDE = listOf(
            arrayOf(
                "1111111111",
                "1........1",
                "1..$..$..1",
                "1.######.1",
                "..........",
                "..........",
                "....$.....",
                "##########",
            ),
            arrayOf(
                "..........",
                "..........",
                "...1111...",
                "..........",
                "1........1",
                "11..$$..11",
                "111....111",
                "##########",
            ),
            arrayOf(
                "##########",
                "##########",
                "###1..1###",
                "##......##",
                "#...$$...#",
                "..........",
                "..........",
                "##########",
            ),
            arrayOf(
                "..........",
                "...$.$....",
                "..##L##...",
                "....L.....",
                "....L.....",
                "....L.....",
                "....L.....",
                "##########",
            ),
            arrayOf(
                "1111111111",
                "1111111111",
                "11......11",
                "1..$..$...",
                "1..####...",
                "..........",
                "..^....^..",
                "##########",
            ),
        )
    }

    private class Room {
        var onPath = false
        var openTop = false
        var openBottom = false
        var holeX = 0 // column of the 2-wide hole in the floor when openBottom
    }

    /** Remembers "$" spots while building a level. */
    private val treasureSpots = mutableListOf<Pair<Int, Int>>()

    /** How many tries the last [generate] call needed (for tests). */
    var lastAttempts = 0
        private set

    fun generate(depth: Int): Level {
        lastAttempts = 0
        repeat(150) {
            lastAttempts++
            val level = tryGenerate(depth, simple = false)
            if (level != null && isSolvable(level)) return level
        }
        // Extremely unlikely: fall back to plain rooms, which are always solvable.
        while (true) {
            val level = tryGenerate(depth, simple = true)
            if (level != null && isSolvable(level)) return level
        }
    }

    private fun tryGenerate(depth: Int, simple: Boolean): Level? {
        treasureSpots.clear()
        val rooms = Array(ROOMS_Y) { Array(ROOMS_X) { Room() } }

        // 1. Walk a path from the top row to the bottom row.
        var x = rng.nextInt(ROOMS_X)
        var y = 0
        val startX = x
        rooms[y][x].onPath = true
        var dir = if (rng.nextBoolean()) 1 else -1
        while (true) {
            val nx = x + dir
            val goDown = rng.nextInt(5) == 0 || nx !in 0 until ROOMS_X
            if (!goDown) {
                x = nx
                rooms[y][x].onPath = true
                continue
            }
            if (y == ROOMS_Y - 1) break
            val room = rooms[y][x]
            room.openBottom = true
            room.holeX = 1 + rng.nextInt(RW - 3)
            y++
            rooms[y][x].onPath = true
            rooms[y][x].openTop = true
            dir = when (x) {
                0 -> 1
                ROOMS_X - 1 -> -1
                else -> if (rng.nextBoolean()) 1 else -1
            }
        }
        val exitRoomX = x

        // 2. Fill rooms from templates.
        val level = Level(WIDTH, HEIGHT)
        for (ty in 0 until HEIGHT) for (tx in 0 until WIDTH) level[tx, ty] = Tile.BORDER
        for (ry in 0 until ROOMS_Y) for (rx in 0 until ROOMS_X) {
            val room = rooms[ry][rx]
            val template = when {
                simple -> PLAIN
                room.onPath && room.openBottom -> DROP.random(rng)
                room.onPath -> LR.random(rng)
                rng.nextInt(3) == 0 -> LR.random(rng)
                else -> SIDE.random(rng)
            }
            stamp(level, template, 1 + rx * RW, 1 + ry * RH, mirror = rng.nextBoolean(), noSpikes = simple)
        }

        // 3. Carve openings so path rooms connect.
        for (ry in 0 until ROOMS_Y) for (rx in 0 until ROOMS_X) {
            val room = rooms[ry][rx]
            if (!room.onPath) continue
            val ox = 1 + rx * RW
            val oy = 1 + ry * RH
            // A walking lane along the bottom on both sides.
            for (ly in RH - 3 until RH - 1) {
                if (rx > 0) level[ox, oy + ly] = Tile.EMPTY
                if (rx < ROOMS_X - 1) level[ox + RW - 1, oy + ly] = Tile.EMPTY
            }
            if (room.openBottom) {
                for (ly in RH - 3 until RH) for (hx in 0..1) level[ox + room.holeX + hx, oy + ly] = Tile.EMPTY
                val below = oy + RH
                for (ly in 0 until 3) for (hx in 0..1) level[ox + room.holeX + hx, below + ly] = Tile.EMPTY
            }
        }

        // 4. Entrance and exit doors.
        val entrance = pickFloorCell(level, startX, 0) ?: return null
        level.entranceX = entrance.first
        level.entranceY = entrance.second
        val exit = pickFloorCell(level, exitRoomX, ROOMS_Y - 1, avoid = entrance) ?: return null
        level.exitX = exit.first
        level.exitY = exit.second

        // 5. Treasure and monsters.
        if (!simple) addSpawns(level, depth)
        return level
    }

    private fun stamp(level: Level, t: Array<String>, ox: Int, oy: Int, mirror: Boolean, noSpikes: Boolean) {
        for (ly in 0 until RH) for (lx in 0 until RW) {
            val c = t[ly][if (mirror) RW - 1 - lx else lx]
            val tile = when (c) {
                '#' -> Tile.DIRT
                '1' -> if (rng.nextBoolean()) Tile.DIRT else Tile.EMPTY
                '2' -> if (rng.nextInt(4) == 0) Tile.DIRT else Tile.EMPTY
                'L' -> Tile.LADDER
                '^' -> if (!noSpikes && rng.nextInt(10) < 6) Tile.SPIKES else Tile.EMPTY
                '$' -> {
                    treasureSpots += (ox + lx) to (oy + ly)
                    Tile.EMPTY
                }
                else -> Tile.EMPTY
            }
            level[ox + lx, oy + ly] = tile
        }
    }

    private fun isFloorCell(level: Level, x: Int, y: Int) =
        level[x, y] == Tile.EMPTY && level[x, y - 1] == Tile.EMPTY && level.isSolid(x, y + 1)

    private fun pickFloorCell(level: Level, rx: Int, ry: Int, avoid: Pair<Int, Int>? = null): Pair<Int, Int>? {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (ly in 1 until RH) for (lx in 0 until RW) {
            val x = 1 + rx * RW + lx
            val y = 1 + ry * RH + ly
            if (isFloorCell(level, x, y) && (avoid == null || abs(avoid.first - x) > 2 || avoid.second != y)) {
                cells += x to y
            }
        }
        return if (cells.isEmpty()) null else cells.random(rng)
    }

    private fun addSpawns(level: Level, depth: Int) {
        fun farFromEntrance(x: Int, y: Int) =
            abs(x - level.entranceX) + abs(y - level.entranceY) > 6
        fun isDoor(x: Int, y: Int) =
            (x == level.entranceX && y == level.entranceY) || (x == level.exitX && y == level.exitY)

        val floor = mutableListOf<Pair<Int, Int>>()
        val ceiling = mutableListOf<Pair<Int, Int>>()
        for (y in 1 until level.height - 1) for (x in 1 until level.width - 1) {
            if (level[x, y] != Tile.EMPTY || isDoor(x, y)) continue
            if (level.isSolid(x, y + 1) && level.hasNoRopeOrLadderNear(x, y)) floor += x to y
            if (level.isSolid(x, y - 1) && level[x, y + 1] == Tile.EMPTY && level[x, y + 2] == Tile.EMPTY) ceiling += x to y
        }
        floor.shuffle(rng)
        ceiling.shuffle(rng)
        val used = HashSet<Pair<Int, Int>>()

        // Treasure from "$" spots in the templates.
        for (spot in treasureSpots) {
            if (spot in used || level[spot.first, spot.second] != Tile.EMPTY || isDoor(spot.first, spot.second)) continue
            if (rng.nextInt(100) < 65) {
                level.spawns += Spawn(randomTreasure(), spot.first, spot.second)
                used += spot
            }
        }
        // A few extra gold nuggets lying around.
        var extraGold = 5
        for (c in floor) {
            if (extraGold == 0) break
            if (c in used) continue
            level.spawns += Spawn(if (rng.nextInt(4) == 0) randomTreasure() else SpawnKind.NUGGET, c.first, c.second)
            used += c
            extraGold--
        }
        // Sometimes a bundle of ropes.
        if (rng.nextInt(100) < 60) {
            floor.firstOrNull { it !in used }?.let {
                level.spawns += Spawn(SpawnKind.ROPE_PILE, it.first, it.second)
                used += it
            }
        }
        // Snakes walk on the floor, bats hang from the ceiling.
        var snakes = min(3 + depth, 10)
        for (c in floor) {
            if (snakes == 0) break
            if (c in used || !farFromEntrance(c.first, c.second)) continue
            level.spawns += Spawn(SpawnKind.SNAKE, c.first, c.second)
            used += c
            snakes--
        }
        var bats = min(1 + depth, 8)
        for (c in ceiling) {
            if (bats == 0) break
            if (c in used || !farFromEntrance(c.first, c.second)) continue
            level.spawns += Spawn(SpawnKind.BAT, c.first, c.second)
            used += c
            bats--
        }
    }

    private fun Level.hasNoRopeOrLadderNear(x: Int, y: Int) =
        this[x, y + 1] != Tile.LADDER && this[x, y] != Tile.LADDER

    private fun randomTreasure(): SpawnKind {
        val r = rng.nextInt(100)
        return when {
            r < 55 -> SpawnKind.NUGGET
            r < 80 -> SpawnKind.GOLD_BAR
            r < 90 -> SpawnKind.EMERALD
            r < 96 -> SpawnKind.SAPPHIRE
            else -> SpawnKind.RUBY
        }
    }

    // ---------------------------------------------------------------------------------
    // Reachability check: a simplified model of what the player can do (no ropes).
    // ---------------------------------------------------------------------------------

    /** The player can stand (or hold on) in this cell. */
    private fun supported(l: Level, x: Int, y: Int): Boolean =
        !l.isSolid(x, y) && (l.isSolid(x, y + 1) || l.isLadderTop(x, y + 1) || l.isClimbable(x, y))

    /** Where the player ends up after dropping from (x, y); null if that's deadly spikes. */
    private fun fall(l: Level, x: Int, startY: Int): Int? {
        var y = startY
        while (!supported(l, x, y)) {
            y++
            if (y >= l.height) return null
        }
        if (y > startY && l[x, y] == Tile.SPIKES) return null
        return y
    }

    fun isSolvable(l: Level): Boolean {
        val seen = BooleanArray(l.width * l.height)
        val queue = ArrayDeque<Int>()
        fun visit(x: Int, y: Int?) {
            if (y == null || !l.inside(x, y)) return
            val i = y * l.width + x
            if (!seen[i]) {
                seen[i] = true
                queue.addLast(i)
            }
        }
        visit(l.entranceX, l.entranceY)
        while (queue.isNotEmpty()) {
            val i = queue.removeFirst()
            val x = i % l.width
            val y = i / l.width
            if (x == l.exitX && y == l.exitY) return true

            // Walk (and maybe fall) sideways.
            for (dx in intArrayOf(-1, 1)) {
                if (!l.isSolid(x + dx, y)) visit(x + dx, fall(l, x + dx, y))
            }
            // Climb up and down.
            if (l.isClimbable(x, y) && !l.isSolid(x, y - 1)) visit(x, fall(l, x, y - 1))
            if (l.isClimbable(x, y + 1)) visit(x, y + 1)
            // Jump up, then drift sideways.
            for (k in 1..JUMP_UP) {
                if (l.isSolid(x, y - k)) break
                visit(x, fall(l, x, y - k))
                for (dx in intArrayOf(-1, 1)) {
                    for (s in 1..JUMP_SIDE) {
                        val nx = x + dx * s
                        if (l.isSolid(nx, y - k)) break
                        visit(nx, fall(l, nx, y - k))
                    }
                }
            }
        }
        return false
    }
}
