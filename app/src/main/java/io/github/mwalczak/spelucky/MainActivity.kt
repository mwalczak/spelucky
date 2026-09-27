package io.github.mwalczak.spelucky

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import io.github.mwalczak.spelucky.ui.GameView
import io.github.mwalczak.spelucky.ui.SoundFx
import io.github.mwalczak.spelucky.game.Btn
import io.github.mwalczak.spelucky.game.Game

class MainActivity : Activity() {

    private lateinit var view: GameView
    private lateinit var sfx: SoundFx

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        sfx = SoundFx(this)
        view = GameView(this, sfx)
        setContentView(view)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        super.onPause()
        view.pauseGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        sfx.release()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val game = view.game
        if (game.state == Game.State.PLAYING && !game.paused) {
            game.paused = true
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    // Keyboard and game controller support (e.g. the tablet's keyboard cover or a Bluetooth pad).

    private fun buttonFor(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> Btn.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> Btn.RIGHT
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> Btn.UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> Btn.DOWN
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_BUTTON_A -> Btn.JUMP
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_B -> Btn.WHIP
        KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_BUTTON_Y -> Btn.ROPE
        KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_START -> Btn.PAUSE
        KeyEvent.KEYCODE_ENTER -> Btn.TAP
        else -> 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val b = buttonFor(keyCode)
        if (b == 0) return super.onKeyDown(keyCode, event)
        view.input.setKey(b, true)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val b = buttonFor(keyCode)
        if (b == 0) return super.onKeyUp(keyCode, event)
        view.input.setKey(b, false)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X) + event.getAxisValue(MotionEvent.AXIS_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y) + event.getAxisValue(MotionEvent.AXIS_Y)
            view.input.setKey(Btn.LEFT, x < -0.5f)
            view.input.setKey(Btn.RIGHT, x > 0.5f)
            view.input.setKey(Btn.UP, y < -0.5f)
            view.input.setKey(Btn.DOWN, y > 0.5f)
            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
