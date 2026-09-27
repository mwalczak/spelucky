package io.github.mwalczak.spelucky.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import io.github.mwalczak.spelucky.game.Btn
import io.github.mwalczak.spelucky.game.Input
import kotlin.math.abs
import kotlin.math.hypot

/**
 * On-screen controls: a d-pad that covers the whole left side of the screen
 * (put your thumb anywhere and slide) and big round buttons on the right.
 */
class TouchControls(private val density: Float) {

    class Button(val bit: Int, val label: String, val color: Int) {
        var cx = 0f
        var cy = 0f
        var r = 0f
        fun hit(x: Float, y: Float, slack: Float) = hypot(x - cx, y - cy) <= r * slack
    }

    val jump = Button(Btn.JUMP, "JUMP", 0xFF66BB6A.toInt())
    val whip = Button(Btn.WHIP, "WHIP", 0xFFFFA726.toInt())
    val rope = Button(Btn.ROPE, "ROPE", 0xFFD7A86E.toInt())
    val pause = Button(Btn.PAUSE, "", 0xFFFFFFFF.toInt())
    val music = Button(Btn.MUSIC, "", 0xFFFFFFFF.toInt())
    private val actionButtons = listOf(jump, whip, rope)

    var padX = 0f
    var padY = 0f
    var padR = 0f
    private var screenW = 0f

    private val roles = HashMap<Int, Int>()
    private val posX = HashMap<Int, Float>()
    private val posY = HashMap<Int, Float>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val path = Path()

    private fun dp(v: Float) = v * density

    fun layout(w: Int, h: Int) {
        screenW = w.toFloat()
        padR = dp(78f)
        padX = dp(36f) + padR
        padY = h - dp(36f) - padR

        jump.r = dp(58f)
        jump.cx = w - dp(40f) - jump.r
        jump.cy = h - dp(40f) - jump.r
        whip.r = dp(48f)
        whip.cx = jump.cx - jump.r - dp(34f) - whip.r
        whip.cy = h - dp(30f) - whip.r
        rope.r = dp(40f)
        rope.cx = jump.cx - dp(12f)
        rope.cy = jump.cy - jump.r - dp(36f) - rope.r
        pause.r = dp(26f)
        pause.cx = w - dp(46f)
        pause.cy = dp(46f)
        music.r = dp(26f)
        music.cx = pause.cx - dp(70f)
        music.cy = pause.cy
    }

    private fun buttonAt(x: Float, y: Float): Button? =
        actionButtons.firstOrNull { it.hit(x, y, 1.25f) }

    private fun roleAt(x: Float, y: Float): Int = when {
        pause.hit(x, y, 1.3f) -> Btn.PAUSE
        music.hit(x, y, 1.3f) -> Btn.MUSIC
        buttonAt(x, y) != null -> buttonAt(x, y)!!.bit
        x < screenW * 0.45f -> DPAD
        else -> 0
    }

    private fun padMask(x: Float, y: Float): Int {
        val dx = x - padX
        val dy = y - padY
        val len = hypot(dx, dy)
        if (len < dp(16f)) return 0
        var m = 0
        // Horizontal is generous and vertical is strict, so running doesn't accidentally climb.
        if (abs(dx) > len * 0.5f) m = m or (if (dx > 0) Btn.RIGHT else Btn.LEFT)
        if (abs(dy) > len * 0.7f) m = m or (if (dy > 0) Btn.DOWN else Btn.UP)
        return m
    }

    fun onTouch(e: MotionEvent, input: Input) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                posX[id] = e.getX(i)
                posY[id] = e.getY(i)
                val role = roleAt(e.getX(i), e.getY(i))
                roles[id] = role
                if (role and Btn.NOT_A_TAP == 0) input.tap()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val x = e.getX(i)
                    val y = e.getY(i)
                    posX[id] = x
                    posY[id] = y
                    val role = roles[id] ?: continue
                    // Let a thumb slide from one action button to another.
                    if (role != DPAD && role and Btn.NOT_A_TAP == 0) roles[id] = buttonAt(x, y)?.bit ?: 0
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                roles.remove(id)
            }
            MotionEvent.ACTION_CANCEL -> roles.clear()
        }
        var mask = 0
        for ((id, role) in roles) {
            mask = mask or if (role == DPAD) padMask(posX[id] ?: 0f, posY[id] ?: 0f) else role
        }
        input.setTouch(mask)
    }

    fun draw(c: Canvas, mask: Int) {
        // D-pad.
        fill.color = 0x22FFFFFF
        c.drawCircle(padX, padY, padR, fill)
        stroke.strokeWidth = dp(2f)
        stroke.color = 0x55FFFFFF
        c.drawCircle(padX, padY, padR, stroke)
        drawArrow(c, padX + padR * 0.62f, padY, 0f, mask and Btn.RIGHT != 0)
        drawArrow(c, padX - padR * 0.62f, padY, 180f, mask and Btn.LEFT != 0)
        drawArrow(c, padX, padY - padR * 0.62f, 270f, mask and Btn.UP != 0)
        drawArrow(c, padX, padY + padR * 0.62f, 90f, mask and Btn.DOWN != 0)

        for (b in actionButtons) {
            val down = mask and b.bit != 0
            fill.color = (b.color and 0x00FFFFFF) or (if (down) 0x99000000.toInt() else 0x44000000)
            c.drawCircle(b.cx, b.cy, b.r, fill)
            stroke.color = (b.color and 0x00FFFFFF) or 0xCC000000.toInt()
            stroke.strokeWidth = dp(3f)
            c.drawCircle(b.cx, b.cy, b.r, stroke)
            text.color = 0xEEFFFFFF.toInt()
            text.textSize = b.r * 0.42f
            c.drawText(b.label, b.cx, b.cy + text.textSize * 0.36f, text)
        }

        // Pause button.
        fill.color = 0x44000000
        c.drawCircle(pause.cx, pause.cy, pause.r, fill)
        fill.color = 0xCCFFFFFF.toInt()
        val bw = pause.r * 0.22f
        val bh = pause.r * 0.5f
        c.drawRect(pause.cx - bw * 2f, pause.cy - bh, pause.cx - bw * 0.7f, pause.cy + bh, fill)
        c.drawRect(pause.cx + bw * 0.7f, pause.cy - bh, pause.cx + bw * 2f, pause.cy + bh, fill)
    }

    /** The music on/off button; shown on every screen. */
    fun drawMusicButton(c: Canvas, on: Boolean) {
        val b = music
        fill.color = 0x44000000
        c.drawCircle(b.cx, b.cy, b.r, fill)
        fill.color = if (on) 0xCCFFFFFF.toInt() else 0x66FFFFFF
        val s = b.r / 26f
        // A pair of eighth notes.
        c.drawCircle(b.cx - 8 * s, b.cy + 8 * s, 5 * s, fill)
        c.drawCircle(b.cx + 7 * s, b.cy + 5 * s, 5 * s, fill)
        c.drawRect(b.cx - 5 * s, b.cy - 11 * s, b.cx - 2.5f * s, b.cy + 8 * s, fill)
        c.drawRect(b.cx + 10 * s, b.cy - 14 * s, b.cx + 12.5f * s, b.cy + 5 * s, fill)
        path.reset()
        path.moveTo(b.cx - 5 * s, b.cy - 11 * s)
        path.lineTo(b.cx + 12.5f * s, b.cy - 14 * s)
        path.lineTo(b.cx + 12.5f * s, b.cy - 9 * s)
        path.lineTo(b.cx - 5 * s, b.cy - 6 * s)
        path.close()
        c.drawPath(path, fill)
        if (!on) {
            stroke.color = 0xEEFF5252.toInt()
            stroke.strokeWidth = dp(3f)
            c.drawLine(b.cx - b.r * 0.6f, b.cy + b.r * 0.6f, b.cx + b.r * 0.6f, b.cy - b.r * 0.6f, stroke)
        }
    }

    private fun drawArrow(c: Canvas, x: Float, y: Float, angle: Float, down: Boolean) {
        val s = padR * 0.22f
        c.save()
        c.rotate(angle, x, y)
        path.reset()
        path.moveTo(x + s, y)
        path.lineTo(x - s * 0.6f, y - s)
        path.lineTo(x - s * 0.6f, y + s)
        path.close()
        fill.color = if (down) 0xEEFFFFFF.toInt() else 0x77FFFFFF
        c.drawPath(path, fill)
        c.restore()
    }

    companion object {
        private const val DPAD = -1
    }
}
