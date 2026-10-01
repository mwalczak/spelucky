package io.github.mwalczak.spelucky.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import io.github.mwalczak.spelucky.game.Bat
import io.github.mwalczak.spelucky.game.Enemy
import io.github.mwalczak.spelucky.game.Game
import io.github.mwalczak.spelucky.game.Level
import io.github.mwalczak.spelucky.game.Pickup
import io.github.mwalczak.spelucky.game.Player
import io.github.mwalczak.spelucky.game.ScoreBoard
import io.github.mwalczak.spelucky.game.SubmitStatus
import io.github.mwalczak.spelucky.game.Snake
import io.github.mwalczak.spelucky.game.TILE
import io.github.mwalczak.spelucky.game.Tile
import io.github.mwalczak.spelucky.game.Treasure
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Draws everything with simple shapes; there are no image files in the game. */
class Renderer(private val density: Float) {

    private object C {
        const val BG = 0xFF24170E.toInt()
        const val BG_BRICK = 0xFF2C1D12.toInt()
        const val DIRT = 0xFF8A5A2E.toInt()
        const val DIRT_TOP = 0xFFB07A42.toInt()
        const val DIRT_DARK = 0xFF63401F.toInt()
        const val DIRT_SPECK = 0xFF6E4624.toInt()
        const val ROCK = 0xFF8C837A.toInt()
        const val STONE = 0xFF4E4A50.toInt()
        const val STONE_DARK = 0xFF3A373C.toInt()
        const val LADDER = 0xFFA8733A.toInt()
        const val RUNG = 0xFFC99656.toInt()
        const val SPIKE = 0xFFD8D8E0.toInt()
        const val SPIKE_BASE = 0xFF6D6D78.toInt()
        const val ROPE = 0xFFD2A262.toInt()
        const val ROPE_DARK = 0xFF9A7040.toInt()
        const val DOOR_FRAME = 0xFF5E3C1E.toInt()
        const val DOOR_INSIDE = 0xFF0E0805.toInt()
        const val SKIN = 0xFFF1C27D.toInt()
        const val SKIN_DARK = 0xFFD9A462.toInt()
        const val HAT = 0xFF8B5A2B.toInt()
        const val HAT_BRIM = 0xFF6B4423.toInt()
        const val HAT_BAND = 0xFF3B2412.toInt()
        const val SHIRT = 0xFF3B82F6.toInt()
        const val SCARF = 0xFFE53935.toInt()
        const val PANTS = 0xFF7A5230.toInt()
        const val BOOTS = 0xFF3B2412.toInt()
        const val WHIP = 0xFF5A3A1C.toInt()
        const val SNAKE = 0xFF43A047.toInt()
        const val SNAKE_BELLY = 0xFF9CCC65.toInt()
        const val BAT = 0xFF5E4A86.toInt()
        const val BAT_WING = 0xFF48386A.toInt()
        const val GOLD = 0xFFF5C542.toInt()
        const val GOLD_LIGHT = 0xFFFFF2A8.toInt()
        const val GOLD_DARK = 0xFFC99A1A.toInt()
        const val HEART = 0xFFE53935.toInt()
        const val HEART_EMPTY = 0x55FFFFFF
    }

    private val p = Paint().apply { isAntiAlias = false }
    private val aa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt()
    }
    private val path = Path()
    private val rect = RectF()

    private fun dp(v: Float) = v * density

    fun draw(c: Canvas, g: Game, controls: TouchControls, touchMask: Int) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val zoom = h / (11.5f * TILE)
        g.viewW = w / zoom
        g.viewH = h / zoom

        c.drawColor(C.BG)
        c.save()
        c.scale(zoom, zoom)
        // Snap the camera to whole screen pixels so tiles never show seams.
        c.translate(-(g.camX * zoom).roundToInt() / zoom, -(g.camY * zoom).roundToInt() / zoom)
        drawTiles(c, g)
        drawDoors(c, g)
        for (item in g.pickups) drawPickup(c, item, g.time)
        for (e in g.enemies) drawEnemy(c, e)
        if (g.state != Game.State.TITLE) drawPlayer(c, g.player)
        for (pt in g.particles) {
            p.color = pt.color
            p.alpha = (255 * (pt.life / pt.maxLife).coerceIn(0f, 1f)).toInt()
            c.drawRect(pt.x, pt.y, pt.x + pt.size, pt.y + pt.size, p)
        }
        p.alpha = 255
        for (t in g.texts) {
            val a = (255 * (t.life / 0.4f).coerceIn(0f, 1f)).toInt()
            drawLabel(c, t.text, t.x, t.y, 7f, t.color, a)
        }
        c.restore()

        when (g.state) {
            Game.State.TITLE -> drawTitle(c, g, w, h)
            Game.State.INTRO -> {
                drawHud(c, g, w)
                drawIntro(c, g, w, h)
            }
            Game.State.PLAYING -> {
                drawHud(c, g, w)
                controls.draw(c, touchMask)
                if (g.paused) drawPaused(c, w, h)
            }
            Game.State.DEAD -> {
                drawHud(c, g, w)
                drawGameOver(c, g, w, h)
            }
        }
        controls.drawMusicButton(c, g.musicOn)
    }

    // ------------------------------------------------------------------ world

    private fun hash(x: Int, y: Int): Int {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        return (n xor (n ushr 16)) and 0x7FFFFFFF
    }

    private fun drawTiles(c: Canvas, g: Game) {
        val level = g.level
        val x0 = floor(g.camX / TILE).toInt() - 1
        val y0 = floor(g.camY / TILE).toInt() - 1
        val x1 = ceil((g.camX + g.viewW) / TILE).toInt() + 1
        val y1 = ceil((g.camY + g.viewH) / TILE).toInt() + 1
        for (ty in y0..y1) for (tx in x0..x1) {
            val x = tx * TILE
            val y = ty * TILE
            val hsh = hash(tx, ty)
            when (level[tx, ty]) {
                Tile.DIRT -> drawDirt(c, level, tx, ty, x, y, hsh)
                Tile.BORDER -> drawStone(c, x, y, hsh)
                else -> {
                    if (hsh % 5 == 0) {
                        p.color = C.BG_BRICK
                        val bx = x + (hsh / 5 % 6)
                        val by = y + (hsh / 30 % 8)
                        c.drawRect(bx, by, bx + 8f, by + 5f, p)
                    }
                    when (level[tx, ty]) {
                        Tile.LADDER -> drawLadder(c, x, y)
                        Tile.SPIKES -> drawSpikes(c, x, y)
                    }
                }
            }
            if (level.hasRope(tx, ty)) drawRope(c, level, tx, ty, x, y)
        }
    }

    private fun drawDirt(c: Canvas, level: Level, tx: Int, ty: Int, x: Float, y: Float, hsh: Int) {
        p.color = C.DIRT
        c.drawRect(x, y, x + TILE, y + TILE, p)
        p.color = C.DIRT_SPECK
        c.drawRect(x + hsh % 12 + 1, y + hsh / 12 % 12 + 2, x + hsh % 12 + 3, y + hsh / 12 % 12 + 4, p)
        c.drawRect(x + hsh / 144 % 12 + 1, y + hsh / 1728 % 10 + 3, x + hsh / 144 % 12 + 4, y + hsh / 1728 % 10 + 5, p)
        if (hsh % 7 == 0) {
            p.color = C.ROCK
            val rx = x + hsh / 7 % 9 + 2
            val ry = y + hsh / 63 % 8 + 4
            c.drawRect(rx, ry, rx + 5f, ry + 3f, p)
        }
        p.color = C.DIRT_DARK
        if (!level.isSolid(tx, ty + 1)) c.drawRect(x, y + TILE - 3f, x + TILE, y + TILE, p)
        if (!level.isSolid(tx - 1, ty)) c.drawRect(x, y, x + 1.5f, y + TILE, p)
        if (!level.isSolid(tx + 1, ty)) c.drawRect(x + TILE - 1.5f, y, x + TILE, y + TILE, p)
        if (!level.isSolid(tx, ty - 1)) {
            p.color = C.DIRT_TOP
            c.drawRect(x, y, x + TILE, y + 3f, p)
            // Little pebbles along the top.
            p.color = C.DIRT
            c.drawRect(x + hsh % 11 + 2, y + 2f, x + hsh % 11 + 5, y + 3f, p)
        }
    }

    private fun drawStone(c: Canvas, x: Float, y: Float, hsh: Int) {
        p.color = C.STONE
        c.drawRect(x, y, x + TILE, y + TILE, p)
        p.color = C.STONE_DARK
        c.drawRect(x, y + 7f, x + TILE, y + 8f, p)
        val off = if (hsh % 2 == 0) 4f else 11f
        c.drawRect(x + off, y, x + off + 1f, y + 7f, p)
        c.drawRect(x + (15f - off), y + 8f, x + (16f - off), y + TILE, p)
    }

    private fun drawLadder(c: Canvas, x: Float, y: Float) {
        p.color = C.LADDER
        c.drawRect(x + 3f, y, x + 5f, y + TILE, p)
        c.drawRect(x + 11f, y, x + 13f, y + TILE, p)
        p.color = C.RUNG
        for (i in 0..3) c.drawRect(x + 4f, y + 2f + i * 4f, x + 12f, y + 3.5f + i * 4f, p)
    }

    private fun drawSpikes(c: Canvas, x: Float, y: Float) {
        p.color = C.SPIKE_BASE
        c.drawRect(x, y + TILE - 2f, x + TILE, y + TILE, p)
        path.reset()
        for (i in 0..2) {
            val sx = x + 0.5f + i * 5f
            path.moveTo(sx, y + TILE - 2f)
            path.lineTo(sx + 2.5f, y + 6f)
            path.lineTo(sx + 5f, y + TILE - 2f)
            path.close()
        }
        p.color = C.SPIKE
        c.drawPath(path, p)
    }

    private fun drawRope(c: Canvas, level: Level, tx: Int, ty: Int, x: Float, y: Float) {
        p.color = C.ROPE
        c.drawRect(x + 7f, y, x + 9f, y + TILE, p)
        p.color = C.ROPE_DARK
        c.drawRect(x + 7f, y + 3f, x + 9f, y + 4f, p)
        c.drawRect(x + 7f, y + 11f, x + 9f, y + 12f, p)
        if (!level.hasRope(tx, ty - 1)) {
            p.color = C.STONE
            c.drawRect(x + 5f, y - 1f, x + 11f, y + 1.5f, p)
        }
    }

    private fun drawDoors(c: Canvas, g: Game) {
        val lv = g.level
        drawDoor(c, lv.entranceX * TILE, lv.entranceY * TILE, exit = false, t = g.time)
        drawDoor(c, lv.exitX * TILE, lv.exitY * TILE, exit = true, t = g.time)
        if (g.atExit && g.state == Game.State.PLAYING) {
            // Bouncing arrow: "press up to go in".
            val ax = lv.exitX * TILE + TILE / 2
            val ay = lv.exitY * TILE - 22f + sin(g.time * 6f) * 2f
            path.reset()
            path.moveTo(ax, ay - 5f)
            path.lineTo(ax - 5f, ay + 1f)
            path.lineTo(ax + 5f, ay + 1f)
            path.close()
            aa.color = 0xFFFFFFFF.toInt()
            c.drawPath(path, aa)
        }
    }

    private fun drawDoor(c: Canvas, x: Float, y: Float, exit: Boolean, t: Float) {
        p.color = C.DOOR_FRAME
        c.drawRect(x - 2f, y - 9f, x + TILE + 2f, y + TILE, p)
        p.color = C.DOOR_INSIDE
        c.drawRect(x + 1f, y - 6f, x + TILE - 1f, y + TILE, p)
        if (exit) {
            val glow = 0.55f + 0.25f * sin(t * 3f)
            p.color = 0xFFFFC857.toInt()
            p.alpha = (glow * 150).toInt()
            c.drawRect(x + 3f, y - 3f, x + TILE - 3f, y + TILE, p)
            p.alpha = 255
            p.color = C.HAT_BRIM
            c.drawRect(x - 1f, y - 16f, x + TILE + 1f, y - 9f, p)
            drawLabel(c, "EXIT", x + TILE / 2, y - 10.6f, 5.5f, 0xFFFFE082.toInt(), 255)
        } else {
            p.color = C.STONE
            c.drawRect(x - 3f, y - 10f, x + TILE + 3f, y - 8f, p)
        }
    }

    private fun drawPickup(c: Canvas, item: Pickup, t: Float) {
        c.save()
        c.translate(item.x, item.y)
        when (item.kind) {
            Treasure.NUGGET -> {
                p.color = C.GOLD_DARK
                c.drawRect(1f, 3f, 7f, 7f, p)
                p.color = C.GOLD
                c.drawRect(1f, 3f, 6f, 6f, p)
                c.drawRect(2f, 2f, 5f, 3f, p)
                p.color = C.GOLD_LIGHT
                c.drawRect(2f, 3f, 3.5f, 4.5f, p)
            }
            Treasure.GOLD_BAR -> {
                p.color = C.GOLD_DARK
                c.drawRect(0f, 4f, 8f, 7f, p)
                p.color = C.GOLD
                c.drawRect(1f, 2.5f, 7f, 5.5f, p)
                p.color = C.GOLD_LIGHT
                c.drawRect(1.5f, 3f, 6.5f, 3.8f, p)
            }
            Treasure.EMERALD, Treasure.SAPPHIRE, Treasure.RUBY -> {
                val col = when (item.kind) {
                    Treasure.EMERALD -> 0xFF2ECC71.toInt()
                    Treasure.SAPPHIRE -> 0xFF3D8BFF.toInt()
                    else -> 0xFFE8384F.toInt()
                }
                path.reset()
                path.moveTo(4f, 0.5f)
                path.lineTo(8f, 3.5f)
                path.lineTo(4f, 7f)
                path.lineTo(0f, 3.5f)
                path.close()
                p.color = col
                c.drawPath(path, p)
                p.color = 0xCCFFFFFF.toInt()
                c.drawRect(2.5f, 2.5f, 4f, 3.5f, p)
            }
            Treasure.ROPE_PILE -> {
                p.color = C.ROPE
                c.drawRect(0f, 2f, 8f, 7f, p)
                p.color = C.ROPE_DARK
                c.drawRect(0f, 3.5f, 8f, 4.2f, p)
                c.drawRect(0f, 5.3f, 8f, 6f, p)
            }
        }
        // Occasional sparkle so treasure stands out.
        val phase = (t * 0.7f + item.x * 0.37f) % 2f
        if (item.kind != Treasure.ROPE_PILE && phase < 0.18f) {
            p.color = 0xFFFFFFFF.toInt()
            c.drawRect(5.5f, -1.5f, 6.5f, 2.5f, p)
            c.drawRect(4f, 0f, 8f, 1f, p)
        }
        c.restore()
    }

    private fun drawEnemy(c: Canvas, e: Enemy) {
        c.save()
        c.translate(e.x, e.y)
        if (e.facing < 0) c.scale(-1f, 1f, e.w / 2, 0f)
        when (e) {
            is Snake -> drawSnake(c, e)
            is Bat -> drawBat(c, e)
        }
        c.restore()
    }

    private fun drawSnake(c: Canvas, s: Snake) {
        val wig = if ((s.animTime * 6f).toInt() % 2 == 0) 0f else 1f
        p.color = C.SNAKE
        c.drawRect(0f, 5f, 11f, 8f, p)
        c.drawRect(1f + wig, 3f, 6f + wig, 5f, p)
        c.drawRect(7f, 1f, 10f, 5f, p)
        c.drawRect(8f, 0f, 12f, 3.5f, p)
        p.color = C.SNAKE_BELLY
        c.drawRect(0f, 7f, 11f, 8f, p)
        p.color = 0xFF000000.toInt()
        c.drawRect(10f, 1f, 11f, 2f, p)
        if ((s.animTime * 2.5f) % 1f < 0.25f) {
            p.color = 0xFFE53935.toInt()
            c.drawRect(12f, 2.2f, 14.5f, 3f, p)
        }
    }

    private fun drawBat(c: Canvas, b: Bat) {
        if (!b.awake) {
            // Asleep, hanging upside down with wings folded.
            p.color = C.BAT_WING
            c.drawRect(2f, 0.5f, 8f, 6.5f, p)
            p.color = C.BAT
            c.drawRect(3f, 0f, 7f, 6f, p)
            return
        }
        val up = sin(b.animTime * 24f) > 0
        path.reset()
        path.moveTo(3.5f, 3f)
        path.lineTo(-3f, if (up) -1f else 7f)
        path.lineTo(3.5f, 6f)
        path.close()
        path.moveTo(6.5f, 3f)
        path.lineTo(13f, if (up) -1f else 7f)
        path.lineTo(6.5f, 6f)
        path.close()
        p.color = C.BAT_WING
        c.drawPath(path, p)
        p.color = C.BAT
        c.drawRect(3f, 2f, 7f, 7f, p)
        c.drawRect(3f, 1f, 4f, 2f, p)
        c.drawRect(6f, 1f, 7f, 2f, p)
        p.color = 0xFFFF5252.toInt()
        c.drawRect(4f, 3f, 5f, 4f, p)
        c.drawRect(5.5f, 3f, 6.5f, 4f, p)
    }

    private fun drawPlayer(c: Canvas, pl: Player) {
        val dead = pl.state == Player.State.DEAD
        if (!dead && pl.invulnerable > 0 && (pl.invulnerable * 14).toInt() % 2 == 0) return
        c.save()
        if (dead) c.rotate(-90f * pl.facing, pl.centerX, pl.bottom - 5f)
        c.translate(pl.x, pl.y)
        if (pl.facing < 0) c.scale(-1f, 1f, pl.w / 2, 0f)

        val climbing = pl.state == Player.State.CLIMB
        val hanging = pl.state == Player.State.HANG
        val step = (pl.animTime * 10f).toInt() % 2

        // Legs and boots.
        p.color = C.PANTS
        when {
            pl.walking -> if (step == 0) {
                c.drawRect(1f, 11f, 3.5f, 14f, p)
                c.drawRect(6f, 11f, 8.5f, 13f, p)
            } else {
                c.drawRect(2f, 11f, 4.5f, 13f, p)
                c.drawRect(6.5f, 11f, 9f, 14f, p)
            }
            climbing -> if (step == 0) {
                c.drawRect(2f, 11f, 4f, 14f, p)
                c.drawRect(6f, 11f, 8f, 12.5f, p)
            } else {
                c.drawRect(2f, 11f, 4f, 12.5f, p)
                c.drawRect(6f, 11f, 8f, 14f, p)
            }
            !pl.onGround -> {
                c.drawRect(2f, 11f, 4f, 13f, p)
                c.drawRect(6f, 11f, 8f, 13f, p)
            }
            else -> {
                c.drawRect(2f, 11f, 4f, 14f, p)
                c.drawRect(6f, 11f, 8f, 14f, p)
            }
        }
        // Body.
        p.color = C.SHIRT
        c.drawRect(1.5f, 7f, 8.5f, 11f, p)
        p.color = C.HAT_BAND
        c.drawRect(1.5f, 10f, 8.5f, 11f, p)
        p.color = C.SCARF
        c.drawRect(2f, 7f, 8f, 8f, p)
        // Head.
        p.color = C.SKIN
        c.drawRect(2f, 3f, 8f, 7f, p)
        p.color = C.SKIN_DARK
        c.drawRect(8f, 4.5f, 9f, 6f, p)
        p.color = 0xFF1A1A1A.toInt()
        if (dead) {
            c.drawRect(5.5f, 3.8f, 7.5f, 4.4f, p)
        } else if (!climbing) {
            c.drawRect(6f, 4f, 7f, 5.2f, p)
        }
        // Hat.
        p.color = C.HAT
        c.drawRect(2.5f, 0f, 7.5f, 2.5f, p)
        p.color = C.HAT_BAND
        c.drawRect(2.5f, 1.8f, 7.5f, 2.6f, p)
        p.color = C.HAT_BRIM
        c.drawRect(0f, 2.5f, 10f, 3.5f, p)

        // Arms.
        p.color = C.SKIN
        when {
            hanging -> {
                c.drawRect(7.5f, -2f, 9.5f, 7f, p)
                c.drawRect(0.5f, -2f, 2.5f, 7f, p)
            }
            climbing -> {
                val a = if (step == 0) 2f else -2f
                c.drawRect(0f, 4f + a, 2f, 8f + a, p)
                c.drawRect(8f, 4f - a, 10f, 8f - a, p)
            }
            pl.whipTimer > 0 -> c.drawRect(7.5f, 7f, 10.5f, 9f, p)
            else -> c.drawRect(7f, 8f, 9f, 10.5f, p)
        }

        // Whip.
        if (pl.whipTimer > 0 && !dead) {
            val phase = 1f - pl.whipTimer / Player.WHIP_TIME
            line.color = C.WHIP
            line.strokeWidth = 1.3f
            if (phase < 0.25f) {
                c.drawLine(8f, 7f, -5f, -5f, line)
            } else {
                path.reset()
                path.moveTo(9.5f, 8f)
                path.quadTo(18f, 4f, 27f, 9f)
                c.drawPath(path, line)
            }
        }
        c.restore()

        // Dizzy stars after a big fall.
        if (pl.stunned > 0 && !dead) {
            p.color = 0xFFFFEB3B.toInt()
            for (i in 0..2) {
                val a = pl.animTime * 6f + i * 2.1f
                val sx = pl.centerX + sin(a) * 6f
                val sy = pl.y - 3f + sin(a + 1.6f) * 1.5f
                c.drawRect(sx - 1f, sy - 1f, sx + 1f, sy + 1f, p)
            }
        }
    }

    private fun drawLabel(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, alpha: Int) {
        outline.textSize = size
        outline.strokeWidth = size * 0.22f
        outline.alpha = alpha
        c.drawText(s, x, y, outline)
        text.textSize = size
        text.color = color
        text.alpha = alpha
        c.drawText(s, x, y, text)
    }

    // -------------------------------------------------------------------- HUD

    private fun drawHeart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.6f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.6f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
        path.close()
        aa.color = color
        c.drawPath(path, aa)
    }

    private fun drawHud(c: Canvas, g: Game, w: Float) {
        val pl = g.player
        val top = dp(18f)
        aa.color = 0x66000000
        rect.set(dp(12f), dp(10f), dp(12f) + dp(40f) * Player.MAX_HEALTH + dp(230f), dp(62f))
        c.drawRoundRect(rect, dp(12f), dp(12f), aa)

        var x = dp(38f)
        val cy = top + dp(18f)
        for (i in 0 until Player.MAX_HEALTH) {
            drawHeart(c, x, cy, dp(13f), if (i < pl.health) C.HEART else C.HEART_EMPTY)
            x += dp(38f)
        }
        text.textAlign = Paint.Align.LEFT
        // Ropes.
        x += dp(4f)
        aa.color = C.ROPE
        rect.set(x - dp(9f), cy - dp(12f), x + dp(9f), cy + dp(12f))
        c.drawRoundRect(rect, dp(6f), dp(6f), aa)
        aa.color = C.ROPE_DARK
        c.drawRect(x - dp(9f), cy - dp(3f), x + dp(9f), cy, aa)
        text.textSize = dp(26f)
        text.color = 0xFFFFFFFF.toInt()
        c.drawText("${pl.ropes}", x + dp(16f), cy + dp(9f), text)
        // Money.
        x += dp(64f)
        text.color = C.GOLD
        c.drawText("$${pl.money}", x, cy + dp(9f), text)
        text.textAlign = Paint.Align.CENTER

        // Level number.
        text.textSize = dp(24f)
        text.color = 0xFFFFFFFF.toInt()
        aa.color = 0x66000000
        rect.set(w / 2 - dp(80f), dp(10f), w / 2 + dp(80f), dp(56f))
        c.drawRoundRect(rect, dp(12f), dp(12f), aa)
        c.drawText("LEVEL ${g.depth}", w / 2, dp(42f), text)
    }

    private fun dim(c: Canvas, alpha: Int, color: Int = 0) {
        aa.color = color
        aa.alpha = alpha.coerceIn(0, 255)
        c.drawPaint(aa)
        aa.alpha = 255
    }

    /** Where the "your name" line is on the title screen, so a tap there can change it. */
    val nameRect = RectF()
    /** Where the "update available" banner is, so a tap there installs it. */
    val updateRect = RectF()

    private fun drawTitle(c: Canvas, g: Game, w: Float, h: Float) {
        dim(c, 120)
        val board = g.scores
        // With the online leaderboard on the right, the title moves to the left.
        val cx = if (board.enabled) w * 0.35f else w / 2
        drawLabel(c, "SPELUCKY", cx, h * 0.36f, dp(110f), C.GOLD, 255)
        drawLabel(c, "Dig deep. Grab the gold. Find the exit.", cx, h * 0.36f + dp(56f), dp(26f), 0xFFFFFFFF.toInt(), 230)
        if (g.bestDepth > 0) {
            drawLabel(c, "Deepest level: ${g.bestDepth}     Most loot: $${g.bestMoney}", cx, h * 0.56f, dp(24f), 0xFFFFE082.toInt(), 255)
        }
        val a = (170 + 85 * sin(g.time * 4f)).toInt()
        drawLabel(c, "Tap to start", cx, h * 0.7f, dp(40f), 0xFFFFFFFF.toInt(), a)
        if (board.enabled) drawBoard(c, board, w, h) else nameRect.setEmpty()
        drawUpdate(c, g, w, h)
        drawLabel(
            c, "Left side: move, climb, ▲ at a door to go in     Right side: JUMP  WHIP  ROPE",
            w / 2, h - dp(28f), dp(18f), 0xFFCCCCCC.toInt(), 220,
        )
    }

    private fun drawUpdate(c: Canvas, g: Game, w: Float, h: Float) {
        val u = g.update
        if (u.currentBuild > 0) {
            text.textAlign = Paint.Align.RIGHT
            text.textSize = dp(16f)
            text.color = 0x99FFFFFF.toInt()
            c.drawText("build ${u.currentBuild}", w - dp(16f), h - dp(12f), text)
            text.textAlign = Paint.Align.CENTER
        }
        if (!u.available) {
            updateRect.setEmpty()
            return
        }
        val label = when {
            u.message != null -> u.message!!
            u.progress >= 1f -> "Installing update..."
            u.progress >= 0f -> "Downloading update... ${(u.progress * 100).toInt()}%"
            else -> "New version available (build ${u.latestBuild}): tap to update!"
        }
        text.textSize = dp(22f)
        val tw = text.measureText(label)
        val left = dp(24f)
        val top = dp(24f)
        updateRect.set(left, top, left + tw + dp(48f), top + dp(56f))
        val pulse = if (u.busy) 1f else 0.85f + 0.15f * sin(g.time * 4f)
        aa.color = 0xFF2E7D32.toInt()
        aa.alpha = (230 * pulse).toInt()
        c.drawRoundRect(updateRect, dp(14f), dp(14f), aa)
        aa.alpha = 255
        if (u.progress in 0f..1f) {
            // Progress bar along the bottom of the banner.
            aa.color = 0xFFA5D6A7.toInt()
            c.drawRect(updateRect.left + dp(12f), updateRect.bottom - dp(8f),
                updateRect.left + dp(12f) + (updateRect.width() - dp(24f)) * u.progress, updateRect.bottom - dp(4f), aa)
        }
        text.color = 0xFFFFFFFF.toInt()
        c.drawText(label, updateRect.centerX(), updateRect.centerY() + dp(8f), text)
    }

    private fun drawBoard(c: Canvas, board: ScoreBoard, w: Float, h: Float) {
        val left = w * 0.66f
        val right = w - dp(24f)
        val top = dp(90f)
        val rowH = dp(40f)
        val bottom = top + dp(70f) + rowH * LeaderboardClient.TOP_COUNT + dp(70f)
        aa.color = 0x99000000.toInt()
        rect.set(left, top, right, bottom)
        c.drawRoundRect(rect, dp(16f), dp(16f), aa)
        val mid = (left + right) / 2
        drawLabel(c, "TOP EXPLORERS", mid, top + dp(46f), dp(28f), C.GOLD, 255)

        val rows = board.top
        var y = top + dp(70f) + rowH * 0.7f
        if (rows.isEmpty()) {
            val msg = when {
                board.loading -> "Loading..."
                board.offline -> "No internet connection"
                else -> "No scores yet. Be the first!"
            }
            drawLabel(c, msg, mid, y + rowH, dp(20f), 0xFFCCCCCC.toInt(), 255)
        }
        val me = board.playerName?.lowercase()
        text.textSize = dp(22f)
        for (r in rows) {
            val mine = r.player.lowercase() == me
            text.color = if (mine) C.GOLD else 0xFFFFFFFF.toInt()
            text.textAlign = Paint.Align.LEFT
            c.drawText("${r.rank}.", left + dp(20f), y, text)
            c.drawText(r.player, left + dp(58f), y, text)
            text.textAlign = Paint.Align.RIGHT
            c.drawText("$${r.score}", right - dp(20f), y, text)
            y += rowH
        }
        text.textAlign = Paint.Align.CENTER

        // "Playing as ..." line, tappable to change the name.
        val footerY = bottom - dp(28f)
        val label = board.playerName?.let { "Playing as $it  ✎" } ?: "Tap here to enter your name"
        drawLabel(c, label, mid, footerY, dp(20f), 0xFFB3E5FC.toInt(), 255)
        nameRect.set(left, footerY - dp(34f), right, bottom)
    }

    private fun drawIntro(c: Canvas, g: Game, w: Float, h: Float) {
        val fade = if (g.stateTime < 1.3f) 1f else (1f - (g.stateTime - 1.3f) / 0.5f).coerceIn(0f, 1f)
        dim(c, (255 * fade).toInt())
        val a = (255 * fade).toInt()
        drawLabel(c, "LEVEL ${g.depth}", w / 2, h * 0.45f, dp(80f), C.GOLD, a)
        val hint = if (g.depth == 1) "Find the EXIT door and press ▲ to go in" else "Deeper and more dangerous..."
        drawLabel(c, hint, w / 2, h * 0.45f + dp(56f), dp(26f), 0xFFFFFFFF.toInt(), a)
    }

    private fun drawPaused(c: Canvas, w: Float, h: Float) {
        dim(c, 150)
        drawLabel(c, "PAUSED", w / 2, h * 0.45f, dp(80f), 0xFFFFFFFF.toInt(), 255)
        drawLabel(c, "Tap to continue", w / 2, h * 0.45f + dp(56f), dp(30f), 0xFFCCCCCC.toInt(), 255)
    }

    private fun drawGameOver(c: Canvas, g: Game, w: Float, h: Float) {
        val k = (g.stateTime / 1f).coerceIn(0f, 1f)
        dim(c, (170 * k).toInt(), 0xFF300808.toInt())
        val a = (255 * k).toInt()
        drawLabel(c, "GAME OVER", w / 2, h * 0.36f, dp(90f), 0xFFFF5252.toInt(), a)
        drawLabel(c, "You reached level ${g.depth} with $${g.player.money}", w / 2, h * 0.36f + dp(60f), dp(30f), 0xFFFFFFFF.toInt(), a)
        if (g.newRecord) {
            drawLabel(c, "New record!", w / 2, h * 0.36f + dp(104f), dp(30f), C.GOLD, a)
        }
        val online = when (val s = g.scores.status) {
            is SubmitStatus.Done ->
                (if (s.personalBest) "Personal best! " else "") + "You are #${s.rank} on the leaderboard" to C.GOLD
            SubmitStatus.Sending -> "Sending your score..." to 0xFFCCCCCC.toInt()
            SubmitStatus.Queued -> "No internet: your score will be sent later" to 0xFFCCCCCC.toInt()
            is SubmitStatus.Rejected -> "Leaderboard: ${s.message}" to 0xFFFF8A80.toInt()
            SubmitStatus.AskingName, SubmitStatus.None -> null
        }
        if (online != null) drawLabel(c, online.first, w / 2, h * 0.36f + dp(148f), dp(28f), online.second, a)
        if (g.stateTime > 1.5f) {
            val pulse = (170 + 85 * sin(g.time * 4f)).toInt()
            drawLabel(c, "Tap to play again", w / 2, h * 0.72f, dp(40f), 0xFFFFFFFF.toInt(), pulse)
        }
    }
}
