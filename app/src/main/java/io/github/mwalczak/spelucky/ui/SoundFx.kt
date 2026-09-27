package io.github.mwalczak.spelucky.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.mwalczak.spelucky.game.Sound
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Retro sound effects, synthesized when the app starts (no audio files needed).
 * Each sound is written to a small WAV file in the cache and played with SoundPool.
 */
class SoundFx(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = IntArray(Sound.values().size)
    private val loaded = BooleanArray(Sound.values().size)

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) {
                val i = ids.indexOf(id)
                if (i >= 0) loaded[i] = true
            }
        }
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        for (s in Sound.values()) {
            val file = File(dir, "${s.name.lowercase()}.wav")
            file.writeBytes(wav(synth(s)))
            ids[s.ordinal] = pool.load(file.path, 1)
        }
    }

    fun play(s: Sound) {
        if (!loaded[s.ordinal]) return
        val vol = if (s == Sound.LAND) 0.5f else 0.9f
        pool.play(ids[s.ordinal], vol, vol, 1, 0, 1f)
    }

    fun release() = pool.release()

    // ------------------------------------------------------------- synthesis

    private val rate = 22050
    private val rng = Random(7)

    private enum class Wave { SQUARE, SINE, NOISE }

    private fun tone(from: Float, to: Float, seconds: Float, wave: Wave, volume: Float): FloatArray {
        val n = (seconds * rate).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        var noise = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = from + (to - from) * t
            phase += f / rate
            val v = when (wave) {
                Wave.SQUARE -> if (phase % 1.0 < 0.5) 1f else -1f
                Wave.SINE -> sin(2 * PI * phase).toFloat()
                Wave.NOISE -> {
                    // Smoothed noise; higher "frequency" means brighter noise.
                    val k = (f / 4000f).coerceIn(0.05f, 1f)
                    noise += (rng.nextFloat() * 2f - 1f - noise) * k
                    noise * 1.6f
                }
            }
            val attack = (i / (rate * 0.004f)).coerceAtMost(1f)
            out[i] = v * volume * attack * (1f - t)
        }
        return out
    }

    private fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }

    private fun mix(a: FloatArray, b: FloatArray): FloatArray =
        FloatArray(maxOf(a.size, b.size)) { (if (it < a.size) a[it] else 0f) + (if (it < b.size) b[it] else 0f) }

    private fun synth(s: Sound): FloatArray = when (s) {
        Sound.JUMP -> tone(260f, 560f, 0.12f, Wave.SQUARE, 0.22f)
        Sound.LAND -> tone(600f, 200f, 0.07f, Wave.NOISE, 0.35f)
        Sound.WHIP -> concat(tone(800f, 3000f, 0.07f, Wave.NOISE, 0.15f), tone(4000f, 1500f, 0.08f, Wave.NOISE, 0.4f))
        Sound.COIN -> concat(tone(988f, 988f, 0.06f, Wave.SQUARE, 0.18f), tone(1319f, 1319f, 0.2f, Wave.SQUARE, 0.18f))
        Sound.GEM -> concat(
            tone(880f, 880f, 0.06f, Wave.SINE, 0.4f), tone(1109f, 1109f, 0.06f, Wave.SINE, 0.4f),
            tone(1319f, 1319f, 0.06f, Wave.SINE, 0.4f), tone(1760f, 1760f, 0.25f, Wave.SINE, 0.4f),
        )
        Sound.HURT -> mix(tone(420f, 110f, 0.25f, Wave.SQUARE, 0.25f), tone(900f, 300f, 0.12f, Wave.NOISE, 0.3f))
        Sound.KILL -> mix(tone(1500f, 300f, 0.15f, Wave.NOISE, 0.45f), tone(220f, 60f, 0.15f, Wave.SQUARE, 0.2f))
        Sound.ROPE -> tone(180f, 900f, 0.22f, Wave.SQUARE, 0.13f)
        Sound.DOOR -> concat(
            tone(523f, 523f, 0.08f, Wave.SQUARE, 0.18f), tone(659f, 659f, 0.08f, Wave.SQUARE, 0.18f),
            tone(784f, 784f, 0.08f, Wave.SQUARE, 0.18f), tone(1047f, 1047f, 0.3f, Wave.SQUARE, 0.18f),
        )
        Sound.DEATH -> concat(
            tone(392f, 370f, 0.2f, Wave.SQUARE, 0.22f), tone(330f, 311f, 0.2f, Wave.SQUARE, 0.22f),
            tone(262f, 70f, 0.6f, Wave.SQUARE, 0.22f),
        )
        Sound.BAT -> concat(tone(1400f, 1000f, 0.05f, Wave.SQUARE, 0.08f), tone(1400f, 1000f, 0.05f, Wave.SQUARE, 0.08f))
    }

    private fun wav(samples: FloatArray): ByteArray {
        val dataLen = samples.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen)
        for (s in samples) b.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        return b.array()
    }
}
