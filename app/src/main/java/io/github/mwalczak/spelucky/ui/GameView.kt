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
import io.github.mwalczak.spelucky.game.SubmitStatus
import io.github.mwalczak.spelucky.BuildConfig

/** Runs the game loop on its own thread and draws to a hardware-accelerated surface. */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    private val sfx: SoundFx,
    private val music: Music,
    /** Shows the "enter your name" dialog and reports the cleaned name, or null if skipped. */
    private val askName: (current: String?, done: (String?) -> Unit) -> Unit,
) : SurfaceView(context), SurfaceHolder.Callback {

    val input = Input()
    val game = Game()
    private val controls = TouchControls(resources.displayMetrics.density)
    private val renderer = Renderer(resources.displayMetrics.density)
    private val prefs = context.getSharedPreferences("spelucky", Context.MODE_PRIVATE)
    private val leaderboard = LeaderboardClient(
        BuildConfig.SCORES_URL, BuildConfig.SCORES_KEY, "spelucky", prefs, game.scores,
    )

    @Volatile private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        game.bestDepth = prefs.getInt("bestDepth", 0)
        game.bestMoney = prefs.getInt("bestMoney", 0)
        game.musicOn = prefs.getBoolean("music", true)
        game.onSound = { sfx.play(it) }
        game.onMusicSetting = { prefs.edit().putBoolean("music", it).apply() }
        game.onRecords = { depth, money ->
            prefs.edit().putInt("bestDepth", depth).putInt("bestMoney", money).apply()
        }
        game.onGameOver = { depth, money -> post { sendScore(money, depth) } }
        leaderboard.refresh()
    }

    private fun sendScore(money: Int, depth: Int) {
        if (!game.scores.enabled || money <= 0) return
        if (game.scores.playerName != null) {
            leaderboard.submit(money, depth)
            return
        }
        game.scores.status = SubmitStatus.AskingName
        askName(null) { name ->
            if (name == null) {
                game.scores.status = SubmitStatus.None
            } else {
                leaderboard.setName(name)
                leaderboard.submit(money, depth)
            }
        }
    }

    private fun changeName() {
        askName(game.scores.playerName) { name ->
            if (name != null) {
                leaderboard.setName(name)
                leaderboard.refresh()
            }
        }
    }

    /** Called when the app comes back to the front. */
    fun refreshLeaderboard() = leaderboard.refresh()

    /** Called when the "update available" banner is tapped. */
    var onUpdateTap: () -> Unit = {}

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
        music.setPlaying(false)
    }

    /** Called when the app goes to the background. */
    fun pauseGame() {
        if (game.state == Game.State.PLAYING) game.paused = true
        music.setPlaying(false)
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
            music.setPlaying(game.wantsMusic)
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
        if (event.actionMasked == MotionEvent.ACTION_DOWN && game.state == Game.State.TITLE) {
            if (renderer.nameRect.contains(event.x, event.y)) {
                changeName()
                return true
            }
            if (renderer.updateRect.contains(event.x, event.y)) {
                onUpdateTap()
                return true
            }
        }
        controls.onTouch(event, input)
        return true
    }

    companion object {
        /** Physics runs at a fixed 120 steps per second, whatever the screen refresh rate. */
        private const val STEP = 1.0 / 120.0
    }
}
