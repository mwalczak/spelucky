package io.github.mwalczak.spelucky.game

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Sound { JUMP, LAND, WHIP, COIN, GEM, HURT, KILL, ROPE, DOOR, DEATH, BAT, HIT, THROW, EXPLOSION, SHOOT, SHOTGUN, FREEZE, SHATTER, BUY, DENIED }

/** All game rules. Knows nothing about Android; see the android package for drawing and input. */
class Game(seed: Long = System.nanoTime()) {

    enum class State { TITLE, INTRO, PLAYING, DEAD }

    val rng = Random(seed)
    var state = State.TITLE
        private set
    var paused = false
    var stateTime = 0f
        private set
    var time = 0f
        private set

    var depth = 1
        private set
    lateinit var level: Level
        private set
    var player = Player(0f, 0f)
        private set
    val enemies = mutableListOf<Enemy>()
    val pickups = mutableListOf<Pickup>()
    val particles = mutableListOf<Particle>()
    val texts = mutableListOf<FloatingText>()
    val bombs = mutableListOf<Bomb>()
    val bullets = mutableListOf<Bullet>()
    val explosions = mutableListOf<Explosion>()
    val offers = mutableListOf<ShopOffer>()

    /** The shop item the player is standing on, if any. */
    var offerHere: ShopOffer? = null
        private set
    /** Seconds of screen shake left (after explosions). */
    var shake = 0f
        private set
    private var welcomed = false

    /** True while the player stands in front of the exit door. */
    var atExit = false
        private set

    // Camera (top-left corner of the view, in world units). The renderer sets the view size.
    var camX = 0f
    var camY = 0f
    var viewW = 20 * TILE
    var viewH = 11.5f * TILE

    var bestDepth = 0
    var bestMoney = 0
    var newRecord = false
        private set

    var musicOn = true
    /** Whether background music should be playing right now. */
    val wantsMusic get() = musicOn && !paused && state != State.DEAD

    var onSound: (Sound) -> Unit = {}
    var onMusicSetting: (on: Boolean) -> Unit = {}
    var onRecords: (depth: Int, money: Int) -> Unit = { _, _ -> }
    /** Called once when a run ends, to send the score to the online leaderboard. */
    var onGameOver: (depth: Int, money: Int) -> Unit = { _, _ -> }

    companion object {
        const val FREEZE_TIME = 5f
        const val BREAKER_SHOTS = 10
    }

    val scores = ScoreBoard()
    val update = UpdateState()

    init {
        buildLevel()
    }

    fun sound(s: Sound) = onSound(s)

    fun startRun() {
        depth = 1
        player = Player(0f, 0f)
        newRecord = false
        scores.status = SubmitStatus.None
        buildLevel()
        setState(State.INTRO)
    }

    /** Plays on a hand-made level (used by tests). */
    fun startWithLevel(custom: Level) {
        level = custom
        clearLevelObjects()
        player = Player(0f, 0f)
        player.placeAt(level.entranceX, level.entranceY)
        stockShop()
        setState(State.PLAYING)
    }

    private fun clearLevelObjects() {
        enemies.clear()
        pickups.clear()
        particles.clear()
        texts.clear()
        bombs.clear()
        bullets.clear()
        explosions.clear()
        offers.clear()
        offerHere = null
        welcomed = false
        shake = 0f
    }

    /** Fills the shop with 4 different random items the player doesn't already own. */
    private fun stockShop() {
        val shop = level.shop ?: return
        val choices = ShopItem.values().filter { !(it.equipment && player.has(it)) && player.gun != it }.shuffled(rng)
        for ((i, slot) in shop.slots.withIndex()) {
            if (i >= choices.size) break
            offers += ShopOffer(choices[i], slot.first, slot.second)
        }
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
    }

    private fun buildLevel() {
        level = LevelGenerator(rng).generate(depth)
        clearLevelObjects()
        for (s in level.spawns) {
            val px = s.tx * TILE
            val py = s.ty * TILE
            when (s.kind) {
                SpawnKind.SNAKE -> enemies += Snake(px + 2f, py + 8f).also { it.facing = if (rng.nextBoolean()) 1 else -1 }
                SpawnKind.BAT -> enemies += Bat(px + 3f, py)
                SpawnKind.CAVEMAN -> enemies += Caveman(px + 3f, py + 2f).also { it.facing = if (rng.nextBoolean()) 1 else -1 }
                SpawnKind.NUGGET -> pickups += Pickup(px + 4f, py + 9f, Treasure.NUGGET)
                SpawnKind.GOLD_BAR -> pickups += Pickup(px + 4f, py + 9f, Treasure.GOLD_BAR)
                SpawnKind.EMERALD -> pickups += Pickup(px + 4f, py + 9f, Treasure.EMERALD)
                SpawnKind.SAPPHIRE -> pickups += Pickup(px + 4f, py + 9f, Treasure.SAPPHIRE)
                SpawnKind.RUBY -> pickups += Pickup(px + 4f, py + 9f, Treasure.RUBY)
                SpawnKind.ROPE_PILE -> pickups += Pickup(px + 4f, py + 9f, Treasure.ROPE_PILE)
            }
        }
        player.placeAt(level.entranceX, level.entranceY)
        stockShop()
        updateCamera(1f, snap = true)
    }

    fun update(dt: Float, input: InputState) {
        time += dt
        stateTime += dt
        if (input.isPressed(Btn.MUSIC)) {
            musicOn = !musicOn
            onMusicSetting(musicOn)
        }
        when (state) {
            State.TITLE -> {
                val span = max(0f, level.pixelWidth - viewW)
                camX = (sin(time * 0.15f) * 0.5f + 0.5f) * span
                camY = clampCamY(level.entranceY * TILE - viewH / 2)
                if (stateTime > 0.3f && input.isPressed(Btn.TAP or Btn.JUMP)) startRun()
            }

            State.INTRO -> {
                if (stateTime > 1.8f || (stateTime > 0.5f && input.isPressed(Btn.TAP))) setState(State.PLAYING)
            }

            State.PLAYING -> {
                if (paused) {
                    if (input.isPressed(Btn.PAUSE or Btn.TAP)) paused = false
                    return
                }
                if (input.isPressed(Btn.PAUSE)) {
                    paused = true
                    return
                }
                updateWorld(dt, input)
            }

            State.DEAD -> {
                updateWorld(dt, input)
                if (stateTime > 1.5f && input.isPressed(Btn.TAP or Btn.JUMP)) startRun()
            }
        }
    }

    private fun updateWorld(dt: Float, input: InputState) {
        val p = player
        p.update(dt, input, this)

        for (e in enemies) {
            e.tick(dt)
            if (e.frozen > 0f) e.updateFrozen(dt, level) else e.update(dt, this)
        }

        if (p.state != Player.State.DEAD) {
            if (p.whipActive) {
                val b = p.whipBox()
                for (e in enemies) if (!e.dead && e.overlaps(b[0], b[1], b[2], b[3])) damageEnemy(e, 1, p.centerX)
            }
            for (e in enemies) {
                if (e.dead || !p.overlaps(e)) continue
                val stomp = p.vy > 0 && p.prevBottom <= e.y + 4f && p.state == Player.State.NORMAL
                if (stomp) {
                    damageEnemy(e, 1, p.centerX)
                    p.bounce()
                } else if (p.invulnerable <= 0f && e.frozen <= 0f) {
                    hurtPlayer(1, e.centerX)
                }
            }
            for (item in pickups) if (!item.dead && p.overlaps(item)) collect(item)
        }
        updateBombs(dt)
        updateBullets(dt)
        enemies.removeAll { it.dead }
        pickups.removeAll { it.dead }
        for (item in pickups) item.update(dt, level)

        updateShop(input)

        atExit = p.state == Player.State.NORMAL && p.onGround &&
            level.isExit(Entity.tile(p.centerX), Entity.tile(p.centerY))
        if (atExit && state == State.PLAYING && input.isPressed(Btn.UP)) {
            nextLevel()
            return
        }

        updateEffects(dt)
        updateCamera(dt)
    }

    private fun updateEffects(dt: Float) {
        if (shake > 0) shake -= dt
        explosions.removeAll { it.time += dt; it.time > 0.4f }
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val pt = pit.next()
            pt.life -= dt
            if (pt.life <= 0) {
                pit.remove()
                continue
            }
            if (pt.gravity) pt.vy += 500f * dt
            pt.x += pt.vx * dt
            pt.y += pt.vy * dt
        }
        val tit = texts.iterator()
        while (tit.hasNext()) {
            val t = tit.next()
            t.life -= dt
            t.y -= 18f * dt
            if (t.life <= 0) tit.remove()
        }
    }

    private fun nextLevel() {
        sound(Sound.DOOR)
        depth++
        buildLevel()
        saveRecords()
        setState(State.INTRO)
    }

    fun hurtPlayer(amount: Int, fromX: Float, knockback: Boolean = true) {
        val p = player
        if (state != State.PLAYING || p.state == Player.State.DEAD) return
        if (amount < 99 && p.invulnerable > 0f) return
        p.hurt(amount, fromX, knockback)
        burst(p.centerX, p.centerY, 0xFFD03030.toInt(), 10)
        if (p.state == Player.State.DEAD) {
            sound(Sound.DEATH)
            setState(State.DEAD)
            saveRecords()
            onGameOver(depth, p.totalGold)
        } else {
            sound(Sound.HURT)
        }
    }

    private fun saveRecords() {
        if (depth > bestDepth || player.totalGold > bestMoney) {
            newRecord = true
            bestDepth = max(bestDepth, depth)
            bestMoney = max(bestMoney, player.totalGold)
            onRecords(bestDepth, bestMoney)
        }
    }

    private fun kill(e: Enemy) {
        e.dead = true
        val color = when (e) {
            is Bat -> 0xFF7E5CA8.toInt()
            is Caveman -> 0xFFB5651D.toInt()
            else -> 0xFF4CAF50.toInt()
        }
        burst(e.centerX, e.centerY, color, 12)
        sound(Sound.KILL)
    }

    /**
     * Hurts a monster. A frozen monster shatters whatever hits it.
     * [ignoreCooldown] lets every bullet of a shotgun blast count.
     */
    fun damageEnemy(e: Enemy, amount: Int, fromX: Float, ignoreCooldown: Boolean = false) {
        if (e.dead) return
        if (e.frozen > 0f) {
            e.dead = true
            burst(e.centerX, e.centerY, 0xFFB3E5FC.toInt(), 16)
            sound(Sound.SHATTER)
            return
        }
        if (e.hitCooldown > 0f && !ignoreCooldown) return
        e.health -= amount
        e.hitCooldown = 0.3f
        if (e.health <= 0) {
            kill(e)
            return
        }
        e.flash = 0.15f
        e.stunned = 0.6f
        e.vx = (if (e.centerX < fromX) -1f else 1f) * 90f
        sound(Sound.HIT)
    }

    // ------------------------------------------------------------------ shop

    private fun updateShop(input: InputState) {
        val p = player
        val shop = level.shop
        offerHere = null
        if (shop == null || p.state == Player.State.DEAD) return
        val tx = Entity.tile(p.centerX)
        val ty = Entity.tile(p.centerY)
        if (!welcomed && shop.contains(tx, ty)) {
            welcomed = true
            texts += FloatingText(shop.keeperX * TILE + TILE / 2, shop.keeperY * TILE - 6f, "Welcome!", 0xFFFFFFFF.toInt())
        }
        if (p.state != Player.State.NORMAL || !p.onGround) return
        offerHere = offers.firstOrNull { !it.sold && it.tx == tx && it.ty == ty }
        val offer = offerHere ?: return
        if (state == State.PLAYING && input.isPressed(Btn.UP)) buy(offer)
    }

    /** Buys an item if the player can afford it. Returns true if bought. */
    fun buy(offer: ShopOffer): Boolean {
        val p = player
        val item = offer.item
        val x = offer.tx * TILE + TILE / 2
        val y = offer.ty * TILE
        if (offer.sold) return false
        if (p.money < item.price) {
            texts += FloatingText(x, y - 4f, "Not enough gold!", 0xFFFF8A80.toInt())
            sound(Sound.DENIED)
            return false
        }
        p.money -= item.price
        offer.sold = true
        when (item) {
            ShopItem.BOMBS -> p.bombs += 4
            ShopItem.ROPES -> p.ropes += 4
            ShopItem.PISTOL, ShopItem.SHOTGUN, ShopItem.FREEZE_GUN, ShopItem.WALL_BREAKER -> {
                p.gun = item
                p.breakerAmmo = if (item == ShopItem.WALL_BREAKER) BREAKER_SHOTS else 0
            }
            else -> p.equipment += item
        }
        texts += FloatingText(x, y - 4f, "Thank you!", 0xFFFFE082.toInt())
        burst(x, y + 8f, 0xFFFFE680.toInt(), 10, gravity = false)
        sound(Sound.BUY)
        return true
    }

    // ----------------------------------------------------------------- bombs

    fun throwBomb(dropAtFeet: Boolean) {
        val p = player
        if (p.bombs <= 0) return
        p.bombs--
        val b = Bomb(p.centerX - 3f, p.y + 3f)
        when {
            dropAtFeet -> b.y = p.bottom - b.h
            p.has(ShopItem.BASEBALL_GLOVE) -> {
                b.vx = p.facing * 330f
                b.straight = true
            }
            else -> {
                b.vx = p.facing * 150f + p.vx * 0.5f
                b.vy = -150f
            }
        }
        bombs += b
        sound(Sound.THROW)
    }

    private fun updateBombs(dt: Float) {
        val it = bombs.iterator()
        val boom = mutableListOf<Bomb>()
        while (it.hasNext()) {
            val b = it.next()
            b.update(dt, level)
            if (b.fuse <= 0f) {
                it.remove()
                boom += b
            }
        }
        for (b in boom) explode(b.centerX, b.centerY)
    }

    /** A bomb blast: destroys dirt nearby and hurts everything close by. */
    fun explode(cx: Float, cy: Float) {
        explosions += Explosion(cx, cy)
        shake = 0.35f
        sound(Sound.EXPLOSION)
        val r = Bomb.BREAK_RADIUS * TILE
        val t0x = Entity.tile(cx - r)
        val t1x = Entity.tile(cx + r)
        val t0y = Entity.tile(cy - r)
        val t1y = Entity.tile(cy + r)
        for (ty in t0y..t1y) for (tx in t0x..t1x) {
            val dx = tx * TILE + TILE / 2 - cx
            val dy = ty * TILE + TILE / 2 - cy
            if (dx * dx + dy * dy > r * r) continue
            if (level.destroy(tx, ty)) burst(tx * TILE + TILE / 2, ty * TILE + TILE / 2, 0xFF8A5A2E.toInt(), 5)
        }
        repeat(18) {
            val a = rng.nextFloat() * 6.283f
            val s = 60f + rng.nextFloat() * 160f
            particles += Particle(cx, cy, cos(a) * s, sin(a) * s, 0.3f + rng.nextFloat() * 0.4f,
                if (rng.nextBoolean()) 0xFFFFB300.toInt() else 0xFFFF5722.toInt(), size = 2f + rng.nextFloat() * 2f)
        }
        val hurt = Bomb.HURT_RADIUS * TILE
        for (e in enemies) {
            if (hypot(e.centerX - cx, e.centerY - cy) <= hurt) {
                e.frozen = 0f
                damageEnemy(e, 10, cx, ignoreCooldown = true)
            }
        }
        val p = player
        if (p.state != Player.State.DEAD && hypot(p.centerX - cx, p.centerY - cy) <= hurt) hurtPlayer(2, cx)
        // Things lying around get thrown about, and the floor under them may be gone.
        for (item in pickups) {
            if (hypot(item.centerX - cx, item.centerY - cy) <= hurt + TILE) {
                item.onGround = false
                item.vy = -150f
            }
        }
        // Other bombs nearby go off too.
        for (b in bombs) if (hypot(b.centerX - cx, b.centerY - cy) <= hurt) b.fuse = minOf(b.fuse, 0.1f)
    }

    // ------------------------------------------------------------------ guns

    fun fire() {
        val p = player
        val gun = p.gun ?: return
        val hx = if (p.facing > 0) p.right + 2f else p.x - 2f
        val hy = p.y + 8f
        when (gun) {
            ShopItem.PISTOL -> {
                bullets += Bullet(hx, hy, p.facing * 420f, 0f, BulletKind.BULLET, 0.7f)
                p.gunCooldown = 0.4f
                sound(Sound.SHOOT)
            }
            ShopItem.SHOTGUN -> {
                for (i in -2..2) {
                    val a = i * 0.12f
                    bullets += Bullet(hx, hy, p.facing * 380f * cos(a), 380f * sin(a), BulletKind.PELLET, 0.28f)
                }
                p.gunCooldown = 0.9f
                if (p.onGround) p.vx -= p.facing * 60f // kick
                sound(Sound.SHOTGUN)
            }
            ShopItem.FREEZE_GUN -> {
                bullets += Bullet(hx, hy, p.facing * 300f, 0f, BulletKind.ICE, 0.8f)
                p.gunCooldown = 0.6f
                sound(Sound.FREEZE)
            }
            ShopItem.WALL_BREAKER -> {
                bullets += Bullet(hx, hy, p.facing * 400f, 0f, BulletKind.BREAKER, 1.2f)
                p.gunCooldown = 0.5f
                p.breakerAmmo--
                if (p.breakerAmmo <= 0) {
                    p.gun = null // out of shots: back to the whip
                    texts += FloatingText(p.centerX, p.y - 4f, "Out of shots!", 0xFFFFFFFF.toInt())
                }
                sound(Sound.SHOOT)
            }
            else -> Unit
        }
    }

    private fun updateBullets(dt: Float) {
        for (b in bullets) {
            b.life -= dt
            if (b.life <= 0f) {
                b.dead = true
                continue
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
            val tx = Entity.tile(b.x)
            val ty = Entity.tile(b.y)
            if (level.isSolid(tx, ty)) {
                b.dead = true
                if (b.kind == BulletKind.BREAKER && level.destroy(tx, ty)) {
                    burst(tx * TILE + TILE / 2, ty * TILE + TILE / 2, 0xFF8A5A2E.toInt(), 10)
                    sound(Sound.EXPLOSION)
                    shake = 0.15f
                } else {
                    burst(b.x, b.y, 0xFFFFE082.toInt(), 3, gravity = false)
                }
                continue
            }
            val hit = enemies.firstOrNull { !it.dead && it.overlaps(b.x - 2f, b.y - 2f, 4f, 4f) } ?: continue
            b.dead = true
            if (b.kind == BulletKind.ICE) {
                if (hit.frozen <= 0f) sound(Sound.FREEZE)
                hit.frozen = FREEZE_TIME
                hit.vx = 0f
                burst(hit.centerX, hit.centerY, 0xFFB3E5FC.toInt(), 8, gravity = false)
            } else {
                damageEnemy(hit, 1, b.x - b.vx, ignoreCooldown = true)
            }
        }
        bullets.removeAll { it.dead }
    }

    private fun collect(item: Pickup) {
        item.dead = true
        val p = player
        if (item.kind == Treasure.ROPE_PILE) {
            p.ropes += 3
            texts += FloatingText(item.centerX, item.y, "+3 ropes", 0xFFE0C090.toInt())
            sound(Sound.COIN)
            return
        }
        p.money += item.kind.value
        p.totalGold += item.kind.value
        val gem = item.kind == Treasure.EMERALD || item.kind == Treasure.SAPPHIRE || item.kind == Treasure.RUBY
        texts += FloatingText(item.centerX, item.y, "$" + item.kind.value, if (gem) 0xFF9FE8FF.toInt() else 0xFFFFD54A.toInt())
        burst(item.centerX, item.centerY, 0xFFFFE680.toInt(), 6, gravity = false)
        sound(if (gem) Sound.GEM else Sound.COIN)
    }

    /** Throws a rope straight up from where the player is; it hangs down to the floor. */
    fun throwRope() {
        val p = player
        if (p.ropes <= 0) return
        val tx = Entity.tile(p.centerX)
        val ty = Entity.tile(p.centerY)
        if (level.isSolid(tx, ty)) return
        var top = ty
        var n = 0
        while (n < 8 && !level.isSolid(tx, top - 1)) {
            top--
            n++
        }
        var r = top
        var len = 0
        while (!level.isSolid(tx, r) && len < 16) {
            level.setRope(tx, r)
            r++
            len++
        }
        p.ropes--
        sound(Sound.ROPE)
        burst(tx * TILE + TILE / 2, top * TILE + 2f, 0xFFC89A58.toInt(), 6)
    }

    private fun burst(x: Float, y: Float, color: Int, count: Int, gravity: Boolean = true) {
        repeat(count) {
            particles += Particle(
                x, y,
                (rng.nextFloat() - 0.5f) * 160f,
                -rng.nextFloat() * 140f,
                0.4f + rng.nextFloat() * 0.4f,
                color,
                size = 1.5f + rng.nextFloat() * 1.5f,
                gravity = gravity,
            )
        }
    }

    private fun clampCamY(y: Float) = if (level.pixelHeight <= viewH) (level.pixelHeight - viewH) / 2
    else y.coerceIn(0f, level.pixelHeight - viewH)

    private fun clampCamX(x: Float) = if (level.pixelWidth <= viewW) (level.pixelWidth - viewW) / 2
    else x.coerceIn(0f, level.pixelWidth - viewW)

    fun updateCamera(dt: Float, snap: Boolean = false) {
        val tx = clampCamX(player.centerX - viewW / 2)
        val ty = clampCamY(player.centerY - viewH / 2)
        if (snap) {
            camX = tx
            camY = ty
        } else {
            val k = min(1f, dt * 7f)
            camX += (tx - camX) * k
            camY += (ty - camY) * k
        }
    }
}
