package io.github.mwalczak.spelucky.ui

import android.content.SharedPreferences
import io.github.mwalczak.spelucky.game.ScoreBoard
import io.github.mwalczak.spelucky.game.ScoreEntry
import io.github.mwalczak.spelucky.game.SubmitStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Talks to the mobile-scores backend. All network calls run on one background thread;
 * results are written into [board], which the renderer reads.
 *
 * Scores that can't be sent (no internet) are kept in [prefs] and retried later.
 */
class LeaderboardClient(
    private val baseUrl: String,
    private val gameKey: String,
    private val game: String,
    private val prefs: SharedPreferences,
    private val board: ScoreBoard,
) {
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "leaderboard").apply { isDaemon = true } }

    init {
        board.enabled = gameKey.isNotEmpty() && baseUrl.isNotEmpty()
        board.playerName = prefs.getString(PREF_NAME, null)
    }

    /** Reloads the top scores (and sends any scores saved while offline). */
    fun refresh() {
        if (!board.enabled) return
        board.loading = true
        worker.execute {
            sendQueued()
            try {
                val json = request("GET", "/api/games/$game/scores?limit=$TOP_COUNT", null)
                val arr = json.getJSONArray("scores")
                board.top = List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    ScoreEntry(
                        o.getInt("rank"), o.getString("player"), o.getInt("score"),
                        if (o.isNull("level")) null else o.getInt("level"),
                    )
                }
                board.offline = false
            } catch (e: Exception) {
                board.offline = true
            } finally {
                board.loading = false
            }
        }
    }

    fun setName(name: String) {
        board.playerName = name
        prefs.edit().putString(PREF_NAME, name).apply()
    }

    /** Sends a finished run's score for the player whose name is set. */
    fun submit(score: Int, level: Int) {
        val name = board.playerName ?: return
        board.status = SubmitStatus.Sending
        worker.execute {
            board.status = try {
                val res = post(name, score, level)
                SubmitStatus.Done(res.getInt("rank"), res.optBoolean("personalBest"))
            } catch (e: Rejected) {
                if (e.code == 422) {
                    // The server didn't like the name; ask for a new one next time.
                    board.playerName = null
                    prefs.edit().remove(PREF_NAME).apply()
                }
                SubmitStatus.Rejected(e.message ?: "Score not accepted")
            } catch (e: IOException) {
                queue(name, score, level)
                SubmitStatus.Queued
            }
        }
        refresh()
    }

    private fun post(name: String, score: Int, level: Int): JSONObject {
        val body = JSONObject().put("player", name).put("score", score).put("level", level)
        return request("POST", "/api/games/$game/scores", body)
    }

    private fun queue(name: String, score: Int, level: Int) {
        val arr = JSONArray(prefs.getString(PREF_QUEUE, "[]"))
        arr.put(JSONObject().put("player", name).put("score", score).put("level", level))
        while (arr.length() > MAX_QUEUE) arr.remove(0)
        prefs.edit().putString(PREF_QUEUE, arr.toString()).apply()
    }

    /** Runs on the worker thread. */
    private fun sendQueued() {
        val arr = JSONArray(prefs.getString(PREF_QUEUE, "[]"))
        if (arr.length() == 0) return
        val left = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            try {
                post(o.getString("player"), o.getInt("score"), o.getInt("level"))
            } catch (e: Rejected) {
                // Dropped: the server will never accept it.
            } catch (e: IOException) {
                left.put(o)
            }
        }
        prefs.edit().putString(PREF_QUEUE, left.toString()).apply()
    }

    private class Rejected(val code: Int, message: String) : Exception(message)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Game-Key", gameKey)
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }
            if (code in 200..299) return json
            // 4xx means "don't retry" (except rate limiting); 5xx is treated like being offline.
            if (code in 400..499 && code != 429) throw Rejected(code, json.optString("error", "HTTP $code"))
            throw IOException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val TOP_COUNT = 8
        private const val MAX_QUEUE = 20
        private const val PREF_NAME = "playerName"
        private const val PREF_QUEUE = "pendingScores"
    }
}
