package io.github.mwalczak.spelucky.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.github.mwalczak.spelucky.game.Game
import io.github.mwalczak.spelucky.game.Input
import io.github.mwalczak.spelucky.game.InputState

/** Runs the game loop on its own thread and draws to a hardware-accelerated surface. */
@SuppressLint("ViewConstructor")
class GameView(context: Context, private val sfx: SoundFx) : SurfaceView(context), SurfaceHolder.Callback {

    val input = Input()
    val game = Game()
    private val controls = TouchControls(resources.displayMetrics.density)
    private val renderer = Renderer(resources.displayMetrics.density)
    private val prefs = context.getSharedPreferences("spelucky", Context.MODE_PRIVATE)

    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        game.bestDepth = prefs.getInt("bestDepth", 0)
        game.bestMoney = prefs.getInt("bestMoney", 0)
        game.onSound = { sfx.play(it) }
        game.onRecords = { depth, money ->
            prefs.edit().putInt("bestDepth", depth).putInt("bestMoney", money).apply()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(::loop, "game-loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        controls.layout(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        thread?.join(1000)
        thread = null
    }

    /** Called when the app goes to the background. */
    fun pauseGame() {
        if (game.state == Game.State.PLAYING) game.paused = true
    }

    private fun loop() {
        val state = InputState()
        var last = SystemClock.elapsedRealtimeNanos()
        var acc = 0.0
        while (running) {
            val now = SystemClock.elapsedRealtimeNanos()
            acc += ((now - last) / 1e9).coerceAtMost(0.1)
            last = now
            while (acc >= STEP) {
                input.poll(state)
                game.update(STEP.toFloat(), state)
                acc -= STEP
            }
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                null
            }
            if (canvas == null) {
                SystemClock.sleep(16)
                continue
            }
            try {
                renderer.draw(canvas, game, controls, input.touchMask)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        controls.onTouch(event, input)
        return true
    }

    companion object {
        /** Physics runs at a fixed 120 steps per second, whatever the screen refresh rate. */
        private const val STEP = 1.0 / 120.0
    }
}
