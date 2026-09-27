package io.github.mwalczak.spelucky.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTest {

    @Test
    fun jumpIsBetweenTwoAndThreeTilesHigh() {
        val game = gameOn(levelOf(
            "#######",
            "#.....#",
            "#.....#",
            "#.....#",
            "#.....#",
            "#..P..#",
            "#######",
        ))
        val startBottom = game.player.bottom
        var highest = startBottom
        game.run(1f, held = Btn.JUMP, press = Btn.JUMP) { highest = minOf(highest, it.player.bottom) }
        val tiles = (startBottom - highest) / TILE
        assertTrue("jump height $tiles", tiles > LevelGenerator.JUMP_UP + 0.3f && tiles < 3f)
        assertTrue(game.player.onGround)
    }

    @Test
    fun canJumpOntoATwoTileStep() {
        val game = gameOn(levelOf(
            "############",
            "#..........#",
            "#..........#",
            "#..........#",
            "#......#####",
            "#P.....#####",
            "############",
        ))
        game.run(0.6f, held = Btn.RIGHT)
        game.run(1.5f, held = Btn.RIGHT or Btn.JUMP, press = Btn.JUMP)
        assertTrue(game.player.onGround)
        assertEquals(4 * TILE, game.player.bottom, 0.01f)
    }

    @Test
    fun grabsLedgeAndClimbsUp() {
        val game = gameOn(levelOf(
            "############",
            "#..........#",
            "#..........#",
            "#......#####",
            "#......#####",
            "#P.....#####",
            "############",
        ))
        game.run(0.6f, held = Btn.RIGHT)
        var hung = false
        game.run(0.8f, held = Btn.RIGHT or Btn.JUMP, press = Btn.JUMP) {
            if (it.player.state == Player.State.HANG) hung = true
        }
        assertTrue("should be hanging on the ledge", hung)
        assertEquals(Player.State.HANG, game.player.state)
        game.run(1.2f, held = Btn.RIGHT or Btn.JUMP, press = Btn.JUMP)
        assertTrue(game.player.onGround)
        assertEquals(3 * TILE, game.player.bottom, 0.01f)
    }

    @Test
    fun climbsLadderOntoPlatformAndBackDown() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "#####L###",
            "#....L..#",
            "#....L..#",
            "#...PL..#",
            "#########",
        ))
        game.run(0.2f, held = Btn.RIGHT)
        game.run(3f, held = Btn.UP)
        assertTrue(game.player.onGround)
        assertEquals(3 * TILE, game.player.bottom, 0.01f)
        game.run(3f, held = Btn.DOWN)
        assertEquals(7 * TILE, game.player.bottom, 0.01f)
    }

    @Test
    fun ropeLetsYouClimbUp() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "###..####",
            "#.......#",
            "#.......#",
            "#...P...#",
            "#########",
        ))
        val ropes = game.player.ropes
        game.run(0.1f, press = Btn.ROPE)
        assertEquals(ropes - 1, game.player.ropes)
        game.run(3f, held = Btn.UP)
        assertTrue("climbed up the rope", game.player.bottom < 4 * TILE)
    }

    @Test
    fun stompKillsSnakeButWalkingIntoItHurts() {
        val game = gameOn(levelOf(
            "##########",
            "#........#",
            "#........#",
            "#........#",
            "#P....s..#",
            "##########",
        ))
        game.run(3f, held = Btn.RIGHT)
        assertEquals(Player.MAX_HEALTH - 1, game.player.health)

        val game2 = gameOn(levelOf(
            "##########",
            "#........#",
            "#........#",
            "#...P....#",
            "#...#....#",
            "#...#s...#",
            "##########",
        ))
        // Walk off the ledge onto the snake below.
        game2.run(0.2f)
        game2.run(0.5f, held = Btn.RIGHT)
        assertTrue(game2.enemies.isEmpty())
        assertEquals(Player.MAX_HEALTH, game2.player.health)
    }

    @Test
    fun whipKillsSnake() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "#.Ps....#",
            "#########",
        ))
        game.run(0.5f, press = Btn.WHIP)
        assertTrue(game.enemies.isEmpty())
        assertEquals(Player.MAX_HEALTH, game.player.health)
    }

    @Test
    fun fallingOnSpikesIsDeadly() {
        val game = gameOn(levelOf(
            "#######",
            "#.....#",
            "#P....#",
            "##....#",
            "#.....#",
            "#..^^.#",
            "#######",
        ))
        game.run(1.5f, held = Btn.RIGHT)
        assertEquals(Game.State.DEAD, game.state)
    }

    @Test
    fun walkingThroughSpikesIsSafe() {
        val game = gameOn(levelOf(
            "#######",
            "#.....#",
            "#.....#",
            "#P.^^.#",
            "#######",
        ))
        game.run(1.5f, held = Btn.RIGHT)
        assertEquals(Game.State.PLAYING, game.state)
        assertEquals(Player.MAX_HEALTH, game.player.health)
    }

    @Test
    fun exitDoorLeadsToNextLevel() {
        val game = gameOn(levelOf(
            "#######",
            "#.....#",
            "#.....#",
            "#P..E.#",
            "#######",
        ))
        game.run(0.45f, held = Btn.RIGHT)
        game.run(0.3f)
        assertTrue(game.atExit)
        game.run(0.1f, press = Btn.UP)
        assertEquals(2, game.depth)
        assertEquals(Game.State.INTRO, game.state)
    }
}
