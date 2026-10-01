package io.github.mwalczak.spelucky.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.mwalczak.spelucky.game.Bomb
import io.github.mwalczak.spelucky.game.Bullet
import io.github.mwalczak.spelucky.game.BulletKind
import io.github.mwalczak.spelucky.game.Caveman
import io.github.mwalczak.spelucky.game.Explosion
import io.github.mwalczak.spelucky.game.ShopItem
import io.github.mwalczak.spelucky.game.TILE
import kotlin.math.sin

/** Drawings for the shop items, the shopkeeper, bombs, bullets and the caveman. */
class ItemArt {
    private val p = Paint().apply { isAntiAlias = false }
    private val aa = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val oval = RectF()

    companion object {
        const val GUN_GREY = 0xFF5F6670.toInt()
        const val GUN_DARK = 0xFF33373D.toInt()
        const val WOOD = 0xFF8B5A2B.toInt()
        const val ICE = 0xFF4FC3F7.toInt()
        const val ORANGE = 0xFFFF8F00.toInt()
        const val CAPE_RED = 0xFFC62828.toInt()
        const val CHUTE = 0xFFFF7043.toInt()
        const val GLOVE_BROWN = 0xFFA0522D.toInt()
        const val GLOVE_BLUE = 0xFF607D8B.toInt()
    }

    /** Draws an item's icon in a [size] x [size] square with its top-left at (x, y). */
    fun icon(c: Canvas, item: ShopItem, x: Float, y: Float, size: Float) {
        c.save()
        c.translate(x, y)
        c.scale(size / 16f, size / 16f)
        when (item) {
            ShopItem.BOMBS -> {
                aa.color = 0xFF222222.toInt()
                c.drawCircle(8f, 10f, 5f, aa)
                aa.color = 0xFF777777.toInt()
                c.drawCircle(6.3f, 8.3f, 1.3f, aa)
                p.color = WOOD
                c.drawRect(7.5f, 3f, 8.8f, 5.5f, p)
                p.color = 0xFFFFEB3B.toInt()
                c.drawRect(8f, 1.2f, 9.6f, 2.8f, p)
            }
            ShopItem.ROPES -> {
                p.color = 0xFFD2A262.toInt()
                c.drawRect(2f, 5f, 14f, 14f, p)
                p.color = 0xFF9A7040.toInt()
                for (i in 0..2) c.drawRect(2f, 7f + i * 2.5f, 14f, 7.8f + i * 2.5f, p)
            }
            ShopItem.BASEBALL_GLOVE -> {
                aa.color = GLOVE_BROWN
                oval.set(3f, 3f, 14f, 14f)
                c.drawRoundRect(oval, 4f, 4f, aa)
                oval.set(0.5f, 7f, 5f, 12f)
                c.drawRoundRect(oval, 2f, 2f, aa)
                line.color = 0xFF5D2E0C.toInt()
                line.strokeWidth = 0.8f
                c.drawLine(6f, 5f, 6f, 11f, line)
                c.drawLine(9f, 5f, 9f, 11f, line)
                c.drawLine(12f, 5f, 12f, 11f, line)
            }
            ShopItem.CLIMBING_GLOVES -> {
                aa.color = GLOVE_BLUE
                oval.set(3f, 4f, 13f, 14f)
                c.drawRoundRect(oval, 3f, 3f, aa)
                p.color = 0xFFECEFF1.toInt()
                for (i in 0..3) {
                    path.reset()
                    path.moveTo(3.5f + i * 2.5f, 4.5f)
                    path.lineTo(4.7f + i * 2.5f, 1f)
                    path.lineTo(5.9f + i * 2.5f, 4.5f)
                    path.close()
                    c.drawPath(path, p)
                }
            }
            ShopItem.PISTOL -> {
                p.color = GUN_GREY
                c.drawRect(3f, 5f, 14f, 8.5f, p)
                p.color = GUN_DARK
                c.drawRect(3f, 8.5f, 7f, 14f, p)
                c.drawRect(12.5f, 4f, 14f, 5f, p)
            }
            ShopItem.SHOTGUN -> {
                p.color = GUN_DARK
                c.drawRect(1f, 6f, 15.5f, 8f, p)
                p.color = GUN_GREY
                c.drawRect(5f, 8f, 11f, 9.5f, p)
                p.color = WOOD
                c.drawRect(0.5f, 7.5f, 6f, 11.5f, p)
            }
            ShopItem.FREEZE_GUN -> {
                p.color = ICE
                c.drawRect(2f, 5f, 12f, 9.5f, p)
                p.color = 0xFFE1F5FE.toInt()
                c.drawRect(12f, 6f, 15.5f, 8.5f, p)
                p.color = 0xFF0277BD.toInt()
                c.drawRect(3f, 9.5f, 6.5f, 14f, p)
                c.drawRect(4f, 6f, 9f, 7f, p)
            }
            ShopItem.WALL_BREAKER -> {
                p.color = ORANGE
                c.drawRect(2f, 4f, 12f, 10f, p)
                p.color = GUN_DARK
                c.drawRect(12f, 5f, 15.5f, 9f, p)
                c.drawRect(3f, 10f, 6.5f, 14.5f, p)
                p.color = 0xFFFFE082.toInt()
                c.drawRect(4f, 5.5f, 10f, 6.5f, p)
            }
            ShopItem.CAPE -> {
                path.reset()
                path.moveTo(5f, 2f)
                path.lineTo(11f, 2f)
                path.lineTo(14.5f, 14.5f)
                path.lineTo(1.5f, 14.5f)
                path.close()
                p.color = CAPE_RED
                c.drawPath(path, p)
                p.color = 0xFFFFD54F.toInt()
                c.drawRect(5f, 2f, 11f, 3.5f, p)
            }
            ShopItem.PARACHUTE -> {
                aa.color = CHUTE
                oval.set(1f, 1f, 15f, 13f)
                c.drawArc(oval, 180f, 180f, true, aa)
                aa.color = 0xFFFFFFFF.toInt()
                oval.set(5.5f, 1f, 10.5f, 13f)
                c.drawArc(oval, 180f, 180f, true, aa)
                line.color = 0xFFEEEEEE.toInt()
                line.strokeWidth = 0.6f
                c.drawLine(1.5f, 7f, 8f, 15f, line)
                c.drawLine(14.5f, 7f, 8f, 15f, line)
                c.drawLine(8f, 7f, 8f, 15f, line)
            }
            ShopItem.SPRING_BOOTS -> {
                p.color = 0xFF6D4C41.toInt()
                c.drawRect(4f, 2f, 10f, 9f, p)
                c.drawRect(4f, 7f, 13f, 10f, p)
                line.color = 0xFFB0BEC5.toInt()
                line.strokeWidth = 1.2f
                path.reset()
                path.moveTo(6f, 10.5f)
                path.lineTo(11f, 11.7f)
                path.lineTo(6f, 12.9f)
                path.lineTo(11f, 14.1f)
                path.lineTo(6f, 15.3f)
                c.drawPath(path, line)
            }
        }
        c.restore()
    }

    /** The shopkeeper: a big friendly fellow with a moustache. Standing on tile (tx, ty). */
    fun shopkeeper(c: Canvas, tx: Int, ty: Int, facing: Int, t: Float) {
        val x = tx * TILE
        val y = ty * TILE
        c.save()
        c.translate(x, y - 4f)
        if (facing < 0) c.scale(-1f, 1f, 8f, 0f)
        val bob = if (sin(t * 3f) > 0) 0f else 0.5f
        p.color = 0xFF3E2723.toInt() // trousers
        c.drawRect(3f, 15f, 7f, 20f, p)
        c.drawRect(9f, 15f, 13f, 20f, p)
        p.color = 0xFF2E7D32.toInt() // green vest
        c.drawRect(2f, 8f + bob, 14f, 16f, p)
        p.color = 0xFFF5F5F5.toInt() // shirt
        c.drawRect(6f, 8f + bob, 10f, 16f, p)
        p.color = 0xFFF1C27D.toInt() // head
        c.drawRect(4f, 1f + bob, 12f, 8f + bob, p)
        p.color = 0xFF1A1A1A.toInt()
        c.drawRect(9f, 3f + bob, 10f, 4.2f + bob, p) // eye
        p.color = 0xFF4E342E.toInt()
        c.drawRect(7f, 5.5f + bob, 13f, 6.8f + bob, p) // moustache
        c.drawRect(4f, 0f + bob, 12f, 1.5f + bob, p) // hair
        p.color = 0xFFF1C27D.toInt()
        c.drawRect(12f, 10f + bob, 15f, 12f + bob, p) // waving hand
        c.restore()
    }

    /** Wooden display stand with a price tag above. */
    fun pedestal(c: Canvas, tx: Int, ty: Int) {
        val x = tx * TILE
        val y = ty * TILE
        p.color = 0xFF6B4423.toInt()
        c.drawRect(x + 2f, y + 12f, x + 14f, y + 16f, p)
        p.color = 0xFF8D6E63.toInt()
        c.drawRect(x + 1f, y + 11f, x + 15f, y + 12.5f, p)
    }

    fun bomb(c: Canvas, b: Bomb, t: Float) {
        val cx = b.centerX
        val cy = b.centerY
        val blink = b.fuse < 1f && ((t * 12f).toInt() % 2 == 0)
        aa.color = if (blink) 0xFFE53935.toInt() else 0xFF222222.toInt()
        c.drawCircle(cx, cy, 3.4f, aa)
        aa.color = 0xFF888888.toInt()
        c.drawCircle(cx - 1.2f, cy - 1.2f, 0.9f, aa)
        p.color = 0xFFFFEB3B.toInt()
        val s = if ((t * 20f).toInt() % 2 == 0) 1.2f else 0.7f
        c.drawRect(cx + 0.5f - s, cy - 5f - s, cx + 0.5f + s, cy - 5f + s, p)
    }

    fun bullet(c: Canvas, b: Bullet) {
        when (b.kind) {
            BulletKind.BULLET -> {
                p.color = 0xFFFFE082.toInt()
                c.drawRect(b.x - 2f, b.y - 0.8f, b.x + 2f, b.y + 0.8f, p)
            }
            BulletKind.PELLET -> {
                p.color = 0xFFFFCC80.toInt()
                c.drawRect(b.x - 1f, b.y - 1f, b.x + 1f, b.y + 1f, p)
            }
            BulletKind.ICE -> {
                aa.color = 0xCC81D4FA.toInt()
                c.drawCircle(b.x, b.y, 2.5f, aa)
                aa.color = 0xFFFFFFFF.toInt()
                c.drawCircle(b.x, b.y, 1f, aa)
            }
            BulletKind.BREAKER -> {
                aa.color = ORANGE
                c.drawCircle(b.x, b.y, 2.2f, aa)
                aa.color = 0xFFFFF59D.toInt()
                c.drawCircle(b.x, b.y, 1f, aa)
            }
        }
    }

    fun explosion(c: Canvas, e: Explosion) {
        val k = (e.time / 0.4f).coerceIn(0f, 1f)
        val r = Bomb.HURT_RADIUS * TILE * (0.4f + 0.6f * k)
        aa.color = 0xFFFF9800.toInt()
        aa.alpha = (200 * (1f - k)).toInt()
        c.drawCircle(e.x, e.y, r, aa)
        aa.color = 0xFFFFF59D.toInt()
        aa.alpha = (230 * (1f - k)).toInt()
        c.drawCircle(e.x, e.y, r * 0.55f, aa)
        aa.alpha = 255
    }

    /** Drawn in the caveman's own 10x14 box, facing right. */
    fun caveman(c: Canvas, m: Caveman) {
        val step = if (m.stunned <= 0f && (m.animTime * (if (m.charging) 12f else 6f)).toInt() % 2 == 0) 1f else 0f
        p.color = 0xFF5D4037.toInt() // legs
        c.drawRect(1.5f, 11f, 4f, 14f - step, p)
        c.drawRect(6f, 11f, 8.5f, 13f + step, p)
        p.color = 0xFFA1887F.toInt() // fur tunic
        c.drawRect(1f, 6f, 9f, 11.5f, p)
        p.color = 0xFF8D6E63.toInt()
        c.drawRect(1f, 10.5f, 9f, 11.5f, p)
        p.color = 0xFFE0A872.toInt() // head
        c.drawRect(2f, 1.5f, 8f, 6.5f, p)
        p.color = 0xFF3E2723.toInt() // wild hair and brow
        c.drawRect(1f, 0f, 8.5f, 2.2f, p)
        c.drawRect(1f, 0f, 2.5f, 5f, p)
        c.drawRect(5f, 2.8f, 8f, 3.4f, p)
        p.color = if (m.charging) 0xFFE53935.toInt() else 0xFF1A1A1A.toInt()
        c.drawRect(6f, 3.4f, 7f, 4.4f, p) // eye
        // Club.
        p.color = 0xFF6D4C41.toInt()
        c.drawRect(8f, 4f + step, 9.5f, 10f + step, p)
        c.drawRect(7.5f, 2f + step, 10.5f, 5f + step, p)
        p.color = 0xFFE0A872.toInt()
        c.drawRect(7.5f, 8f, 9.5f, 10f, p) // hand
    }

    /** A see-through ice block over a frozen monster. */
    fun ice(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        p.color = 0x9981D4FA.toInt()
        c.drawRect(x - 1.5f, y - 1.5f, x + w + 1.5f, y + h + 1f, p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawRect(x - 0.5f, y - 0.5f, x + 2f, y + 1f, p)
        c.drawRect(x + w - 2f, y + h - 3f, x + w, y + h - 2f, p)
    }
}
