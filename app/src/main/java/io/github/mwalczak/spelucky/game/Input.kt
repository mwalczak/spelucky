package io.github.mwalczak.spelucky.game

import java.util.concurrent.atomic.AtomicInteger

/** Bit flags for every "button" the game understands. */
object Btn {
    const val LEFT = 1
    const val RIGHT = 2
    const val UP = 4
    const val DOWN = 8
    const val JUMP = 16
    const val WHIP = 32
    const val ROPE = 64
    const val PAUSE = 128
    /** Any tap on the screen (used for menus). Only ever "pressed", never held. */
    const val TAP = 256
}

/** What the game sees during one update step. */
class InputState {
    var held = 0
    var pressed = 0

    fun isHeld(b: Int) = held and b != 0
    fun isPressed(b: Int) = pressed and b != 0
    val dirX get() = (if (isHeld(Btn.RIGHT)) 1 else 0) - (if (isHeld(Btn.LEFT)) 1 else 0)
}

/**
 * Collects input from the UI thread (touch, keyboard, gamepad) and hands it to the
 * game thread. Presses are latched so a very quick tap between two game steps is
 * never lost.
 */
class Input {
    @Volatile private var touch = 0
    @Volatile private var keys = 0
    private val latch = AtomicInteger(0)
    private var prevHeld = 0

    fun setTouch(mask: Int) {
        val newly = mask and touch.inv()
        touch = mask
        if (newly != 0) latch.getAndUpdate { it or newly }
    }

    fun tap() {
        latch.getAndUpdate { it or Btn.TAP }
    }

    fun setKey(button: Int, down: Boolean) {
        synchronized(this) {
            val old = keys
            keys = if (down) old or button else old and button.inv()
            if (down && old and button == 0) latch.getAndUpdate { it or button or Btn.TAP }
        }
    }

    /** Current touch mask, for drawing pressed buttons. */
    val touchMask get() = touch

    fun poll(out: InputState) {
        val held = touch or keys
        val latched = latch.getAndSet(0)
        out.held = held
        out.pressed = (held and prevHeld.inv()) or latched
        prevHeld = held
    }
}
