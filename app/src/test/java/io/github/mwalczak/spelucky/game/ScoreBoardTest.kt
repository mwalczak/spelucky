package io.github.mwalczak.spelucky.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreBoardTest {

    @Test
    fun namesFollowTheServerRules() {
        assertEquals("Super Kuba", ScoreBoard.cleanName("  Super   Kuba "))
        assertEquals("Łukasz_2", ScoreBoard.cleanName("Łukasz_2"))
        assertEquals("A".repeat(16), ScoreBoard.cleanName("A".repeat(16)))
        assertNull(ScoreBoard.cleanName(""))
        assertNull(ScoreBoard.cleanName("   "))
        assertNull(ScoreBoard.cleanName("A".repeat(17)))
        assertNull(ScoreBoard.cleanName("<script>"))
    }

    @Test
    fun gameOverReportsLevelAndLootOnce() {
        val reports = mutableListOf<Pair<Int, Int>>()
        val game = gameOn(levelOf(
            "#######",
            "#.....#",
            "#P....#",
            "#######",
        ))
        game.onGameOver = { depth, money -> reports += depth to money }
        game.player.money = 1250
        game.player.totalGold = 1250
        game.hurtPlayer(99, 0f)
        game.hurtPlayer(99, 0f) // already dead: no second report
        game.run(2f)
        assertEquals(listOf(1 to 1250), reports)
    }
}
