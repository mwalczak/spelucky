package io.github.mwalczak.spelucky.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LevelGeneratorTest {

    @Test
    fun everyLevelCanBeFinished() {
        var totalAttempts = 0
        var count = 0
        for (seed in 0L until 400L) {
            val gen = LevelGenerator(Random(seed))
            val level = gen.generate(depth = 1 + (seed % 8).toInt())
            totalAttempts += gen.lastAttempts
            count++
            assertTrue("seed $seed\n${level.dump()}", gen.isSolvable(level))
        }
        val avg = totalAttempts.toFloat() / count
        println("average generation attempts: $avg")
        assertTrue("templates too often produce unsolvable levels (avg $avg tries)", avg < 8f)
    }

    @Test
    fun doorsAndBordersAreSane() {
        for (seed in 0L until 200L) {
            val level = LevelGenerator(Random(seed)).generate(3)
            assertEquals(LevelGenerator.WIDTH, level.width)
            for (x in 0 until level.width) {
                assertEquals(Tile.BORDER, level[x, 0])
                assertEquals(Tile.BORDER, level[x, level.height - 1])
            }
            for (y in 0 until level.height) {
                assertEquals(Tile.BORDER, level[0, y])
                assertEquals(Tile.BORDER, level[level.width - 1, y])
            }
            for ((x, y) in listOf(level.entranceX to level.entranceY, level.exitX to level.exitY)) {
                assertEquals(Tile.EMPTY, level[x, y])
                assertTrue(level.isSolid(x, y + 1))
            }
            assertTrue("entrance should be in the top row of rooms", level.entranceY <= LevelGenerator.RH)
            assertTrue("exit should be in the bottom row of rooms", level.exitY > level.height - 2 - LevelGenerator.RH)
            for (s in level.spawns) assertTrue(!level.isSolid(s.tx, s.ty))
            assertTrue(level.spawns.any { it.kind == SpawnKind.SNAKE })
        }
    }

    @Test
    fun printSampleLevel() {
        println(LevelGenerator(Random(42)).generate(1).dump())
    }
}
