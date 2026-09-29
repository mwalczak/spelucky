package io.github.mwalczak.spelucky.game

/** One row of the online leaderboard. */
class ScoreEntry(val rank: Int, val player: String, val score: Int, val level: Int?)

/** What happened to the score from the last game. */
sealed class SubmitStatus {
    object None : SubmitStatus()
    object AskingName : SubmitStatus()
    object Sending : SubmitStatus()
    class Done(val rank: Int, val personalBest: Boolean) : SubmitStatus()
    /** Couldn't reach the server; the score is saved and sent later. */
    object Queued : SubmitStatus()
    class Rejected(val message: String) : SubmitStatus()
}

/**
 * Online leaderboard state, shared between the network code (which writes it) and the
 * renderer (which reads it). Every field is replaced as a whole, so reading is thread-safe.
 */
class ScoreBoard {
    @Volatile var enabled = false
    @Volatile var top: List<ScoreEntry> = emptyList()
    @Volatile var loading = false
    @Volatile var offline = false
    @Volatile var playerName: String? = null
    @Volatile var status: SubmitStatus = SubmitStatus.None

    companion object {
        const val MAX_NAME = 16
        private val ALLOWED = Regex("^[\\p{L}\\p{N} _.\\-]+$")

        /** Tidies a typed name; returns null if it can't be used (same rules as the server). */
        fun cleanName(raw: String): String? {
            val name = raw.trim().replace(Regex("\\s+"), " ")
            if (name.isEmpty() || name.codePointCount(0, name.length) > MAX_NAME) return null
            return if (ALLOWED.matches(name)) name else null
        }
    }
}
