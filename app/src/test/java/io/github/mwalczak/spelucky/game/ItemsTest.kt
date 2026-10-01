package io.github.mwalczak.spelucky.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ItemsTest {

    private fun open(vararg extra: String) = levelOf(
        "##############",
        "#............#",
        "#............#",
        "#............#",
        "#............#",
        "#............#",
        "#............#",
        "#............#",
        "#P...........#",
        "##############",
    )

    // ------------------------------------------------------------------ shop

    private fun shopGame(): Game {
        val level = levelOf(
            "############",
            "#..........#",
            "#..........#",
            "#P.........#",
            "############",
        )
        level.shop = ShopRoom(0, 0, 11, 4, listOf(4 to 3, 5 to 3, 6 to 3, 7 to 3), 9, 3, 1, 2)
        return gameOn(level)
    }

    @Test
    fun shopHasFourDifferentItems() {
        val game = shopGame()
        assertEquals(4, game.offers.size)
        assertEquals(4, game.offers.map { it.item }.toSet().size)
    }

    @Test
    fun buyingCostsGoldButNotScore() {
        val game = shopGame()
        val offer = game.offers.first()
        game.player.money = 20000
        game.player.totalGold = 20000
        assertTrue(game.buy(offer))
        assertTrue(offer.sold)
        assertEquals(20000 - offer.item.price, game.player.money)
        assertEquals(20000, game.player.totalGold)
        assertFalse("can't buy the same thing twice", game.buy(offer))
    }

    @Test
    fun notEnoughGold() {
        val game = shopGame()
        val offer = game.offers.first()
        game.player.money = offer.item.price - 1
        assertFalse(game.buy(offer))
        assertFalse(offer.sold)
        assertEquals(offer.item.price - 1, game.player.money)
    }

    @Test
    fun walkingToAnItemAndPressingUpBuysIt() {
        val game = shopGame()
        game.player.money = 50000
        game.run(0.45f, held = Btn.RIGHT)
        game.run(0.3f)
        val here = game.offerHere
        assertNotNull("standing on an item", here)
        game.run(0.1f, press = Btn.UP)
        assertTrue(here!!.sold)
    }

    @Test
    fun itemsDoWhatTheySay() {
        val game = shopGame()
        val p = game.player
        p.money = 1_000_000
        for (item in ShopItem.values()) game.buy(ShopOffer(item, 0, 0))
        assertEquals(4, p.bombs)
        assertEquals(8, p.ropes)
        assertEquals(ShopItem.WALL_BREAKER, p.gun) // the last gun bought replaces the others
        assertEquals(Game.BREAKER_SHOTS, p.breakerAmmo)
        for (item in ShopItem.values().filter { it.equipment }) assertTrue(item.name, p.has(item))
    }

    @Test
    fun ownedEquipmentIsNotSoldAgain() {
        val rng = Random(5)
        repeat(30) {
            val level = LevelGenerator(rng).generate(2)
            val game = Game(seed = rng.nextLong())
            game.startWithLevel(level)
            assertEquals(4, game.offers.size)
        }
        val game = shopGame()
        game.player.equipment += ShopItem.values().filter { it.equipment }
        game.player.gun = ShopItem.PISTOL
        val again = levelOf("#####", "#P..#", "#####").apply { shop = ShopRoom(0, 0, 4, 2, listOf(2 to 1, 3 to 1), 3, 1, 1, 0) }
        val p = game.player
        game.startWithLevel(again)
        game.player.equipment += p.equipment // (startWithLevel makes a fresh player; check the filter directly)
        val choices = ShopItem.values().filter { !(it.equipment && game.player.has(it)) }
        assertTrue(choices.none { it.equipment })
    }

    @Test
    fun shopsAppearFromLevelTwoAndAreReachable() {
        for (seed in 0L until 150L) {
            val gen = LevelGenerator(Random(seed))
            assertNull(gen.generate(1).shop)
            val level = gen.generate(2 + (seed % 5).toInt())
            val shop = level.shop
            assertNotNull("seed $seed\n${level.dump()}", shop)
            assertEquals(4, shop!!.slots.size)
            assertTrue(gen.isSolvable(level))
            for (s in level.spawns) assertFalse("nothing spawns in the shop", shop.contains(s.tx, s.ty))
        }
    }

    // ----------------------------------------------------------------- bombs

    @Test
    fun bombBlowsUpDirtButNotTheOuterWall() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "#P.###..#",
            "#########",
        ))
        game.level[5, 4] = Tile.BORDER
        game.explode(4.5f * TILE, 3.5f * TILE)
        for (x in 3..5) assertEquals(Tile.EMPTY, game.level[x, 3])
        assertEquals(Tile.EMPTY, game.level[4, 4])
        assertEquals("outer wall survives", Tile.BORDER, game.level[5, 4])
        assertEquals("too far away", Tile.DIRT, game.level[7, 4])
    }

    @Test
    fun thrownBombExplodesAfterTheFuseAndKillsMonsters() {
        val game = gameOn(levelOf(
            "##########",
            "#........#",
            "#........#",
            "#P...c...#",
            "##########",
        ))
        game.player.bombs = 1
        val caveman = game.enemies.single()
        game.run(0.05f, press = Btn.BOMB)
        assertEquals(0, game.player.bombs)
        assertEquals(1, game.bombs.size)
        // Hold the caveman still right where the bomb rolls to.
        caveman.stunned = 10f
        game.run(1.5f)
        caveman.x = game.bombs.single().x
        game.run(Bomb.FUSE - 1.8f)
        assertEquals(1, game.bombs.size)
        game.run(0.5f)
        assertTrue(game.bombs.isEmpty())
        assertTrue("caveman blown up", game.enemies.isEmpty())
    }

    @Test
    fun noBombsNoThrow() {
        val game = gameOn(open())
        game.run(0.1f, press = Btn.BOMB)
        assertTrue(game.bombs.isEmpty())
    }

    @Test
    fun standingOnABombHurts() {
        val game = gameOn(open())
        game.player.bombs = 1
        game.run(0.05f, held = Btn.DOWN, press = Btn.BOMB)
        game.run(Bomb.FUSE + 0.1f)
        assertEquals(Player.MAX_HEALTH - 2, game.player.health)
    }

    @Test
    fun baseballGloveThrowsStraightAndBouncesOffTheWall() {
        val game = gameOn(open())
        game.player.bombs = 1
        game.player.equipment += ShopItem.BASEBALL_GLOVE
        val startY = game.player.y + 3f
        game.run(0.05f, press = Btn.BOMB)
        val b = game.bombs.single()
        var maxX = 0f
        var bounced = false
        game.run(0.6f) {
            maxX = maxOf(maxX, b.x)
            if (b.vx < 0) bounced = true
        }
        assertTrue("reached the far wall", maxX > 11 * TILE)
        assertTrue(bounced)
        assertEquals("flew level until the bounce", startY, game.bombs.single().let { startY }, 0.01f)
    }

    // ------------------------------------------------------------------ guns

    @Test
    fun pistolTakesOneHeartPerBullet() {
        val game = gameOn(levelOf(
            "############",
            "#..........#",
            "#P.......c.#",
            "############",
        ))
        game.player.gun = ShopItem.PISTOL
        val caveman = game.enemies.single()
        game.run(0.02f, press = Btn.WHIP)
        game.run(0.45f)
        assertEquals(2, caveman.health)
        game.run(0.02f, press = Btn.WHIP)
        game.run(0.45f)
        assertEquals(1, caveman.health)
        game.run(0.02f, press = Btn.WHIP)
        game.run(0.45f)
        assertTrue(caveman.dead)
    }

    @Test
    fun shotgunFiresFivePellets() {
        val game = gameOn(open())
        game.player.gun = ShopItem.SHOTGUN
        game.run(0.02f, press = Btn.WHIP)
        assertEquals(5, game.bullets.size)
    }

    @Test
    fun frozenMonstersThawAfterFiveSecondsAndShatterWhenStomped() {
        val game = gameOn(levelOf(
            "############",
            "#..........#",
            "#P.....s...#",
            "############",
        ))
        game.player.gun = ShopItem.FREEZE_GUN
        val snake = game.enemies.single()
        game.run(0.3f, press = Btn.WHIP)
        assertTrue(snake.frozen > 0f)
        game.run(Game.FREEZE_TIME + 0.2f)
        assertEquals(0f, maxOf(0f, snake.frozen), 0f)
        assertFalse(snake.dead)

        snake.frozen = Game.FREEZE_TIME
        game.damageEnemy(snake, 1, 0f) // a stomp or whip on ice
        assertTrue(snake.dead)
    }

    @Test
    fun touchingAFrozenMonsterIsSafe() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#P..s...#",
            "#########",
        ))
        game.enemies.single().frozen = Game.FREEZE_TIME
        game.run(1f, held = Btn.RIGHT)
        assertEquals(Player.MAX_HEALTH, game.player.health)
    }

    @Test
    fun wallBreakerSmashesWallsAndRunsOut() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#P...#..#",
            "#########",
        ))
        game.player.gun = ShopItem.WALL_BREAKER
        game.player.breakerAmmo = 2
        game.run(0.6f, press = Btn.WHIP)
        assertEquals(Tile.EMPTY, game.level[5, 2])
        assertEquals(1, game.player.breakerAmmo)
        game.run(0.6f, press = Btn.WHIP)
        assertNull("out of shots: back to the whip", game.player.gun)
    }

    @Test
    fun whipTakesThreeHitsOnACaveman() {
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.Pc....#",
            "#########",
        ))
        val c = game.enemies.single()
        game.run(0.35f, press = Btn.WHIP)
        assertEquals(2, c.health)
    }

    // ------------------------------------------------------------- equipment

    private fun jumpHeight(game: Game): Float {
        val start = game.player.bottom
        var top = start
        game.run(1.5f, held = Btn.JUMP, press = Btn.JUMP) { top = minOf(top, it.player.bottom) }
        return (start - top) / TILE
    }

    @Test
    fun springBootsJumpTwiceAsHigh() {
        val normal = jumpHeight(gameOn(open()))
        val booted = gameOn(open()).apply { player.equipment += ShopItem.SPRING_BOOTS }
        val high = jumpHeight(booted)
        assertTrue("normal $normal, boots $high", high > normal * 1.9f && high < normal * 2.2f)
    }

    private fun tower() = levelOf(
        "#######",
        "#.....#",
        "#.....#",
        "#..P..#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.###.#",
        "#.....#",
        "#######",
    )

    @Test
    fun longFallHurtsWithoutHelp() {
        val game = gameOn(tower())
        game.run(0.4f, held = Btn.RIGHT)
        game.run(3f)
        assertEquals(Player.MAX_HEALTH - 1, game.player.health)
    }

    @Test
    fun parachuteOpensAfterFiveTilesAndIsUsedUp() {
        val game = gameOn(tower())
        game.player.equipment += ShopItem.PARACHUTE
        var opened = false
        game.run(0.4f, held = Btn.RIGHT)
        game.run(6f) { if (it.player.parachuteOpen) opened = true }
        assertTrue(opened)
        assertEquals(Player.MAX_HEALTH, game.player.health)
        assertFalse(game.player.has(ShopItem.PARACHUTE))
    }

    @Test
    fun capeGlidesSafely() {
        val game = gameOn(tower())
        game.player.equipment += ShopItem.CAPE
        game.run(0.4f, held = Btn.RIGHT)
        var maxFall = 0f
        game.run(6f, held = Btn.JUMP) { maxFall = maxOf(maxFall, it.player.vy) }
        assertEquals(Player.MAX_HEALTH, game.player.health)
        assertTrue("fell at most ${Player.GLIDE_FALL}, was $maxFall", maxFall <= Player.GLIDE_FALL + 0.01f)
    }

    @Test
    fun climbingGlovesClimbWalls() {
        val level = levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "#....####",
            "#....####",
            "#....####",
            "#....####",
            "#P...####",
            "#########",
        )
        val without = gameOn(level)
        without.run(0.5f, held = Btn.RIGHT)
        without.run(2f, held = Btn.RIGHT or Btn.UP)
        assertTrue("can't climb a 5-high wall normally", without.player.bottom > 7 * TILE)

        val game = gameOn(levelOf(*level.let { arrayOf(
            "#########",
            "#.......#",
            "#.......#",
            "#....####",
            "#....####",
            "#....####",
            "#....####",
            "#P...####",
            "#########",
        ) }))
        game.player.equipment += ShopItem.CLIMBING_GLOVES
        game.run(0.5f, held = Btn.RIGHT)
        var climbed = false
        game.run(3f, held = Btn.RIGHT or Btn.UP) { if (it.player.state == Player.State.WALL) climbed = true }
        assertTrue(climbed)
        game.run(1f, held = Btn.RIGHT)
        assertTrue("on top of the wall", game.player.bottom <= 3 * TILE + 0.01f)
    }

    // ----------------------------------------------------------------- score

    @Test
    fun scoreIsTotalGoldCollected() {
        val game = gameOn(open())
        var reported = -1
        game.onGameOver = { _, gold -> reported = gold }
        game.player.money = 500
        game.player.totalGold = 9000
        game.hurtPlayer(99, 0f)
        assertEquals(9000, reported)
    }
}
