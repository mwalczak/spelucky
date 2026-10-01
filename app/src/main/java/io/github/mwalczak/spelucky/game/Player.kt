package io.github.mwalczak.spelucky.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Player(x: Float, y: Float) : Entity(x, y, 10f, 14f) {

    companion object {
        const val GRAVITY = 1100f
        const val JUMP_SPEED = 310f // ~2.7 tiles high
        const val MAX_FALL = 420f
        const val RUN_SPEED = 100f
        const val CLIMB_SPEED = 70f
        const val GROUND_ACCEL = 1400f
        const val AIR_ACCEL = 900f
        const val COYOTE_TIME = 0.1f
        const val JUMP_BUFFER = 0.12f
        const val WHIP_TIME = 0.3f
        const val INVULNERABLE_TIME = 1.2f
        const val FALL_DAMAGE_TILES = 9f
        const val MAX_HEALTH = 4
        const val GLIDE_FALL = 60f
        const val PARACHUTE_FALL = 50f
        const val PARACHUTE_OPENS_TILES = 5f
    }

    /** WALL = climbing a wall with the climbing gloves. */
    enum class State { NORMAL, CLIMB, HANG, WALL, DEAD }

    var state = State.NORMAL
    var facing = 1
    var health = MAX_HEALTH
    var ropes = 4
    var bombs = 0
    /** Gold in the pocket, for shopping. */
    var money = 0
    /** All gold ever picked up this run (the leaderboard score; shopping doesn't lower it). */
    var totalGold = 0

    /** Gloves, cape, parachute, boots... kept until the run ends. */
    val equipment = mutableSetOf<ShopItem>()
    /** The gun in hand (replaces the whip), or null. */
    var gun: ShopItem? = null
    var breakerAmmo = 0
    var gunCooldown = 0f
    var parachuteOpen = false
    var gliding = false

    fun has(item: ShopItem) = item in equipment || gun == item

    private val jumpSpeed get() = if (has(ShopItem.SPRING_BOOTS)) JUMP_SPEED * 1.42f else JUMP_SPEED

    var whipTimer = 0f
    var invulnerable = 0f
    var stunned = 0f
    var animTime = 0f
    var walking = false

    private var coyote = 0f
    private var jumpBuffer = 0f
    private var whipCooldown = 0f
    private var hangCooldown = 0f
    private var highestY = y
    private var wasOnGround = false
    var prevBottom = bottom
        private set

    /** True while the whip is out far enough to hit things. */
    val whipActive get() = whipTimer in 0.04f..0.22f

    fun whipBox(): FloatArray {
        val bx = if (facing > 0) right else x - 18f
        return floatArrayOf(bx, y + 2f, 18f, 9f)
    }

    fun placeAt(tx: Int, ty: Int) {
        x = tx * TILE + (TILE - w) / 2
        y = (ty + 1) * TILE - h
        vx = 0f
        vy = 0f
        state = State.NORMAL
        highestY = y
        onGround = true
        wasOnGround = true
        whipTimer = 0f
        stunned = 0f
    }

    fun update(dt: Float, input: InputState, game: Game) {
        val level = game.level
        prevBottom = bottom
        animTime += dt
        if (invulnerable > 0) invulnerable -= dt
        if (whipTimer > 0) whipTimer -= dt
        if (whipCooldown > 0) whipCooldown -= dt
        if (hangCooldown > 0) hangCooldown -= dt
        if (gunCooldown > 0) gunCooldown -= dt
        if (state == State.DEAD) {
            // Still fall to the ground.
            vx *= 0.9f
            vy = min(vy + GRAVITY * dt, MAX_FALL)
            moveX(level, vx * dt)
            if (moveY(level, vy * dt, true)) vy = 0f
            return
        }

        val canControl = stunned <= 0f
        if (!canControl) stunned -= dt
        val dirX = if (canControl) input.dirX else 0
        fun pressed(b: Int) = canControl && input.isPressed(b)
        fun held(b: Int) = canControl && input.isHeld(b)

        if (pressed(Btn.JUMP)) jumpBuffer = JUMP_BUFFER else if (jumpBuffer > 0) jumpBuffer -= dt

        when (state) {
            State.HANG -> {
                vx = 0f
                vy = 0f
                walking = false
                if (dirX != 0) facing = dirX
                if (jumpBuffer > 0) {
                    jumpBuffer = 0f
                    state = State.NORMAL
                    vy = -jumpSpeed
                    hangCooldown = 0.25f
                    highestY = y
                    game.sound(Sound.JUMP)
                } else if (held(Btn.DOWN)) {
                    state = State.NORMAL
                    hangCooldown = 0.3f
                    highestY = y
                }
            }

            State.CLIMB -> updateClimb(dt, dirX, ::held, game)

            State.WALL -> updateWall(dt, dirX, ::held, game)

            State.NORMAL -> updateNormal(dt, dirX, ::pressed, ::held, game)

            State.DEAD -> Unit
        }

        // Parachutes and capes only work while falling freely.
        if (state != State.NORMAL && parachuteOpen) closeParachute()
        if (state != State.NORMAL) gliding = false

        // Actions available while standing, jumping or climbing.
        if (state != State.HANG && state != State.WALL) {
            if (pressed(Btn.WHIP) && state == State.NORMAL) {
                if (gun != null) {
                    if (gunCooldown <= 0f) game.fire()
                } else if (whipCooldown <= 0f) {
                    whipTimer = WHIP_TIME
                    whipCooldown = WHIP_TIME + 0.05f
                    game.sound(Sound.WHIP)
                }
            }
            if (pressed(Btn.ROPE)) game.throwRope()
            if (pressed(Btn.BOMB)) game.throwBomb(dropAtFeet = held(Btn.DOWN) && onGround)
        }
    }

    private fun updateClimb(dt: Float, dirX: Int, held: (Int) -> Boolean, game: Game) {
        val level = game.level
        walking = false
        vx = 0f
        val tx = tile(centerX)
        x = tx * TILE + (TILE - w) / 2
        if (dirX != 0) facing = dirX
        if (jumpBuffer > 0) {
            jumpBuffer = 0f
            state = State.NORMAL
            vy = -jumpSpeed * 0.85f
            vx = dirX * RUN_SPEED * 0.5f
            highestY = y
            hangCooldown = 0.2f
            game.sound(Sound.JUMP)
            return
        }
        vy = when {
            held(Btn.UP) -> -CLIMB_SPEED
            held(Btn.DOWN) -> CLIMB_SPEED
            else -> 0f
        }
        if (vy != 0f) animTime += dt
        if (vy < 0) {
            val newCenter = centerY + vy * dt
            if (!level.isClimbableAt(centerX, newCenter)) {
                // Reached the top. Step onto the ladder if it ends here; ropes just stop.
                val top = listOf(tile(centerY), tile(bottom - 1f)).firstOrNull { level.isLadderTop(tx, it) }
                if (top != null) {
                    y = top * TILE - h
                    state = State.NORMAL
                    onGround = true
                }
                vy = 0f
                return
            }
            moveY(level, vy * dt)
        } else if (vy > 0) {
            val hit = moveY(level, vy * dt)
            if (hit) {
                state = State.NORMAL
                onGround = true
            } else if (!level.isClimbableAt(centerX, bottom - 1f)) {
                state = State.NORMAL
            }
        }
        highestY = y
    }

    /** Which side a wall is touching us on, if we push towards it. */
    private fun wallBeside(level: Level, dir: Int): Boolean {
        if (dir == 0) return false
        val handX = if (dir > 0) right + 0.5f else x - 0.5f
        return level.isSolidAt(handX, centerY)
    }

    private fun updateWall(dt: Float, dirX: Int, held: (Int) -> Boolean, game: Game) {
        val level = game.level
        walking = false
        vx = 0f
        highestY = y
        if (jumpBuffer > 0) {
            // Kick off the wall.
            jumpBuffer = 0f
            state = State.NORMAL
            vy = -jumpSpeed * 0.9f
            vx = -facing * RUN_SPEED * 0.8f
            facing = -facing
            hangCooldown = 0.2f
            game.sound(Sound.JUMP)
            return
        }
        if (dirX == -facing) {
            state = State.NORMAL
            return
        }
        vy = when {
            held(Btn.UP) -> -CLIMB_SPEED
            held(Btn.DOWN) -> CLIMB_SPEED
            else -> 0f
        }
        if (vy != 0f) animTime += dt
        if (vy < 0) {
            val handX = if (facing > 0) right + 0.5f else x - 0.5f
            if (!level.isSolidAt(handX, centerY + vy * dt)) {
                // Reached the top of the wall: pull up onto it if there's room.
                val tx = tile(handX)
                val row = tile(centerY)
                if (level.isSolid(tx, row) && !level.isSolid(tx, row - 1)) {
                    y = row * TILE - h
                    x = if (facing > 0) tx * TILE else (tx + 1) * TILE - w
                    state = State.NORMAL
                    vy = 0f
                    onGround = true
                    highestY = y
                }
                vy = 0f
                return
            }
            moveY(level, vy * dt)
        } else if (vy > 0) {
            if (moveY(level, vy * dt)) {
                state = State.NORMAL
                onGround = true
            } else if (!wallBeside(level, facing)) {
                state = State.NORMAL
            }
        }
    }

    private fun openParachute(game: Game) {
        parachuteOpen = true
        game.sound(Sound.ROPE)
    }

    private fun closeParachute() {
        // A parachute only works once.
        parachuteOpen = false
        equipment -= ShopItem.PARACHUTE
    }

    private fun updateNormal(dt: Float, dirX: Int, pressed: (Int) -> Boolean, held: (Int) -> Boolean, game: Game) {
        val level = game.level

        // Climb a wall with the climbing gloves.
        if (has(ShopItem.CLIMBING_GLOVES) && hangCooldown <= 0f && held(Btn.UP) && wallBeside(level, dirX)) {
            state = State.WALL
            facing = dirX
            vx = 0f
            vy = 0f
            return
        }

        // Grab a ladder or rope.
        if (held(Btn.UP) && level.isClimbableAt(centerX, centerY)) {
            state = State.CLIMB
            vy = 0f
            return
        }
        if (held(Btn.DOWN) && onGround && level.isClimbableAt(centerX, bottom + 2f)) {
            state = State.CLIMB
            y += 3f
            vy = 0f
            onGround = false
            return
        }

        // Run.
        val target = dirX * RUN_SPEED
        val accel = (if (onGround) GROUND_ACCEL else AIR_ACCEL) * dt
        vx = if (vx < target) min(vx + accel, target) else max(vx - accel, target)
        if (dirX != 0 && stunned <= 0f) facing = dirX
        walking = onGround && abs(vx) > 5f

        // Jump.
        coyote = if (onGround) COYOTE_TIME else coyote - dt
        if (jumpBuffer > 0 && coyote > 0) {
            vy = -jumpSpeed
            jumpBuffer = 0f
            coyote = 0f
            onGround = false
            highestY = y
            game.sound(Sound.JUMP)
        }

        // Gravity; falls faster when the jump button is let go early.
        val g = if (vy < 0 && !held(Btn.JUMP)) GRAVITY * 2.4f else GRAVITY
        if (has(ShopItem.PARACHUTE) && !parachuteOpen && !onGround && vy > 0 &&
            (y - highestY) / TILE >= PARACHUTE_OPENS_TILES
        ) {
            openParachute(game)
        }
        gliding = !parachuteOpen && has(ShopItem.CAPE) && held(Btn.JUMP) && vy > 0 && !onGround
        val maxFall = when {
            parachuteOpen -> PARACHUTE_FALL
            gliding -> GLIDE_FALL
            else -> MAX_FALL
        }
        vy = min(vy + g * dt, maxFall)
        // Floating down safely never counts as a fall.
        if (parachuteOpen || gliding) highestY = y

        if (moveX(level, vx * dt)) vx = 0f
        val topBefore = y
        val hitY = moveY(level, vy * dt, ladderTopIsFloor = !held(Btn.DOWN))
        if (hitY) {
            if (vy > 0) onGround = true
            vy = 0f
        } else {
            onGround = standingOnSomething(level, !held(Btn.DOWN)) && vy >= 0f
        }
        if (!onGround) highestY = min(highestY, y)

        if (onGround && !wasOnGround) land(game)
        wasOnGround = onGround

        // Grab a ledge while falling past it.
        if (!onGround && vy > 0 && dirX != 0 && hangCooldown <= 0 && !held(Btn.DOWN)) {
            tryLedgeGrab(level, dirX, topBefore)
        }
    }

    private fun land(game: Game) {
        val level = game.level
        if (parachuteOpen) closeParachute()
        val fallTiles = (y - highestY) / TILE
        highestY = y
        val feetTile = level.tileAt(centerX, bottom - 1f)
        if (feetTile == Tile.SPIKES && fallTiles > 0.2f) {
            game.hurtPlayer(99, 0f)
            return
        }
        if (fallTiles >= FALL_DAMAGE_TILES) {
            stunned = 0.8f
            game.hurtPlayer(1, 0f, knockback = false)
        } else if (fallTiles > 1.5f) {
            game.sound(Sound.LAND)
        }
    }

    private fun tryLedgeGrab(level: Level, dirX: Int, topBefore: Float) {
        val handX = if (dirX > 0) right + 1f else x - 1f
        val tx = tile(handX)
        val ty = tile(y)
        val edge = ty * TILE
        // Only when our head just passed the top edge of a wall with free space above it.
        if (topBefore > edge + 0.01f) return
        if (!level.isSolid(tx, ty) || level.isSolid(tx, ty - 1)) return
        if (level.isSolid(tile(centerX), ty - 1)) return
        state = State.HANG
        facing = dirX
        y = edge
        vx = 0f
        vy = 0f
        x = if (dirX > 0) tx * TILE - w else (tx + 1) * TILE
    }

    fun hurt(amount: Int, fromX: Float, knockback: Boolean) {
        health = max(0, health - amount)
        invulnerable = INVULNERABLE_TIME
        if (state == State.HANG || state == State.CLIMB || state == State.WALL) state = State.NORMAL
        if (knockback) {
            val dir = if (centerX < fromX) -1f else 1f
            vx = dir * 140f
            vy = -180f
            onGround = false
            stunned = max(stunned, 0.25f)
        }
        highestY = y
        if (health == 0) {
            state = State.DEAD
            whipTimer = 0f
        }
    }

    /** Bounce after jumping on an enemy. */
    fun bounce() {
        vy = -JUMP_SPEED * 0.6f
        highestY = y
        onGround = false
    }
}
