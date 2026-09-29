package io.github.mwalczak.spelucky.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Sound { JUMP, LAND, WHIP, COIN, GEM, HURT, KILL, ROPE, DOOR, DEATH, BAT }

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

    val scores = ScoreBoard()

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
        enemies.clear()
        pickups.clear()
        player = Player(0f, 0f)
        player.placeAt(level.entranceX, level.entranceY)
        setState(State.PLAYING)
    }

    private fun setState(s: State) {
        state = s
        stateTime = 0f
    }

    private fun buildLevel() {
        level = LevelGenerator(rng).generate(depth)
        enemies.clear()
        pickups.clear()
        particles.clear()
        texts.clear()
        for (s in level.spawns) {
            val px = s.tx * TILE
            val py = s.ty * TILE
            when (s.kind) {
                SpawnKind.SNAKE -> enemies += Snake(px + 2f, py + 8f).also { it.facing = if (rng.nextBoolean()) 1 else -1 }
                SpawnKind.BAT -> enemies += Bat(px + 3f, py)
                SpawnKind.NUGGET -> pickups += Pickup(px + 4f, py + 9f, Treasure.NUGGET)
                SpawnKind.GOLD_BAR -> pickups += Pickup(px + 4f, py + 9f, Treasure.GOLD_BAR)
                SpawnKind.EMERALD -> pickups += Pickup(px + 4f, py + 9f, Treasure.EMERALD)
                SpawnKind.SAPPHIRE -> pickups += Pickup(px + 4f, py + 9f, Treasure.SAPPHIRE)
                SpawnKind.RUBY -> pickups += Pickup(px + 4f, py + 9f, Treasure.RUBY)
                SpawnKind.ROPE_PILE -> pickups += Pickup(px + 4f, py + 9f, Treasure.ROPE_PILE)
            }
        }
        player.placeAt(level.entranceX, level.entranceY)
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

        for (e in enemies) e.update(dt, this)

        if (p.state != Player.State.DEAD) {
            if (p.whipActive) {
                val b = p.whipBox()
                for (e in enemies) if (!e.dead && e.overlaps(b[0], b[1], b[2], b[3])) kill(e)
            }
            for (e in enemies) {
                if (e.dead || !p.overlaps(e)) continue
                val stomp = p.vy > 0 && p.prevBottom <= e.y + 4f && p.state == Player.State.NORMAL
                if (stomp) {
                    kill(e)
                    p.bounce()
                } else if (p.invulnerable <= 0f) {
                    hurtPlayer(1, e.centerX)
                }
            }
            for (item in pickups) if (!item.dead && p.overlaps(item)) collect(item)
        }
        enemies.removeAll { it.dead }
        pickups.removeAll { it.dead }
        for (item in pickups) item.update(dt, level)

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
            onGameOver(depth, p.money)
        } else {
            sound(Sound.HURT)
        }
    }

    private fun saveRecords() {
        if (depth > bestDepth || player.money > bestMoney) {
            newRecord = true
            bestDepth = max(bestDepth, depth)
            bestMoney = max(bestMoney, player.money)
            onRecords(bestDepth, bestMoney)
        }
    }

    private fun kill(e: Enemy) {
        e.dead = true
        val color = if (e is Bat) 0xFF7E5CA8.toInt() else 0xFF4CAF50.toInt()
        burst(e.centerX, e.centerY, color, 12)
        sound(Sound.KILL)
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
