package io.github.mwalczak.spelucky.ui

import io.github.mwalczak.spelucky.game.Sound
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Designs the sound effects. Pure Kotlin, no Android code.
 * Every sound is normalized to its [loudness] so none of them get lost under the music.
 */
object SfxSynth {

    const val RATE = 22050

    /** Peak level of each sound (1.0 = as loud as possible). */
    fun loudness(s: Sound): Float = when (s) {
        Sound.LAND, Sound.BAT -> 0.45f
        Sound.JUMP, Sound.ROPE, Sound.THROW -> 0.6f
        Sound.HIT, Sound.SHOOT, Sound.FREEZE -> 0.75f
        else -> 0.9f // collecting, whip, hurt, death, kills, door: the important ones
    }

    fun render(s: Sound): FloatArray = normalize(design(s), loudness(s))

    private enum class Wave { SQUARE, PULSE, TRIANGLE, SINE, NOISE }

    private val rng = Random(7)

    /**
     * One note sliding from [from] to [to] Hz. [decay] shapes the fade: 0 = hold full volume
     * then release at the end, higher = fades out faster.
     */
    private fun tone(from: Float, to: Float, seconds: Float, wave: Wave, decay: Float = 0f, volume: Float = 1f): FloatArray {
        val n = (seconds * RATE).toInt()
        val out = FloatArray(n)
        var phase = 0.0
        var noise = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = from + (to - from) * t
            phase += f / RATE
            val p = phase % 1.0
            val v = when (wave) {
                Wave.SQUARE -> if (p < 0.5) 1f else -1f
                Wave.PULSE -> if (p < 0.25) 1f else -1f
                Wave.TRIANGLE -> (4 * abs(p - 0.5) - 1).toFloat()
                Wave.SINE -> sin(2 * PI * phase).toFloat()
                Wave.NOISE -> {
                    val k = (f / 4000f).coerceIn(0.05f, 1f)
                    noise += (rng.nextFloat() * 2f - 1f - noise) * k
                    noise * 1.6f
                }
            }
            val attack = (i / (RATE * 0.003f)).coerceAtMost(1f)
            val release = ((n - i) / (RATE * 0.015f)).coerceAtMost(1f)
            val fade = exp(-decay * t)
            out[i] = v * volume * attack * release * fade
        }
        return out
    }

    private fun silence(seconds: Float) = FloatArray((seconds * RATE).toInt())

    private fun concat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }

    private fun mix(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.maxOf { it.size })
        for (p in parts) for (i in p.indices) out[i] += p[i]
        return out
    }

    private fun normalize(x: FloatArray, peak: Float): FloatArray {
        val max = x.maxOfOrNull { abs(it) } ?: 0f
        if (max < 1e-6f) return x
        val k = peak / max
        return FloatArray(x.size) { x[it] * k }
    }

    private fun design(s: Sound): FloatArray = when (s) {
        Sound.JUMP -> tone(250f, 620f, 0.13f, Wave.PULSE, decay = 1.5f)

        Sound.LAND -> tone(500f, 150f, 0.08f, Wave.NOISE, decay = 3f)

        // A swoosh followed by a sharp crack.
        Sound.WHIP -> concat(
            tone(600f, 3500f, 0.09f, Wave.NOISE, volume = 0.45f),
            mix(tone(4000f, 2000f, 0.05f, Wave.NOISE, decay = 4f), tone(1800f, 900f, 0.05f, Wave.SQUARE, decay = 5f, volume = 0.5f)),
        )

        // The classic two-note "ding-ding!".
        Sound.COIN -> concat(
            tone(988f, 988f, 0.07f, Wave.SQUARE),
            tone(1319f, 1319f, 0.28f, Wave.SQUARE, decay = 3f),
        )

        // A sparkling arpeggio up, with a shimmer on top.
        Sound.GEM -> mix(
            concat(
                tone(1047f, 1047f, 0.06f, Wave.PULSE), tone(1319f, 1319f, 0.06f, Wave.PULSE),
                tone(1568f, 1568f, 0.06f, Wave.PULSE), tone(2093f, 2093f, 0.35f, Wave.PULSE, decay = 3f),
            ),
            concat(silence(0.18f), tone(3136f, 3136f, 0.3f, Wave.SINE, decay = 4f, volume = 0.35f)),
        )

        // "Ouch!": a quick falling buzz with a thump.
        Sound.HURT -> mix(
            concat(tone(700f, 500f, 0.06f, Wave.SQUARE), tone(500f, 160f, 0.22f, Wave.SQUARE, decay = 1.5f)),
            tone(900f, 200f, 0.12f, Wave.NOISE, decay = 3f, volume = 0.7f),
        )

        Sound.KILL -> mix(
            tone(1600f, 300f, 0.16f, Wave.NOISE, decay = 2f),
            tone(300f, 70f, 0.16f, Wave.SQUARE, decay = 2f, volume = 0.5f),
        )

        Sound.ROPE -> tone(180f, 950f, 0.24f, Wave.PULSE, decay = 1f)

        Sound.DOOR -> concat(
            tone(523f, 523f, 0.09f, Wave.SQUARE), tone(659f, 659f, 0.09f, Wave.SQUARE),
            tone(784f, 784f, 0.09f, Wave.SQUARE), tone(1047f, 1047f, 0.4f, Wave.SQUARE, decay = 2.5f),
        )

        // A sad falling tune: "wah wah wah waaaah".
        Sound.DEATH -> mix(
            concat(
                tone(494f, 466f, 0.28f, Wave.SQUARE, decay = 0.8f), silence(0.04f),
                tone(440f, 415f, 0.28f, Wave.SQUARE, decay = 0.8f), silence(0.04f),
                tone(415f, 392f, 0.28f, Wave.SQUARE, decay = 0.8f), silence(0.04f),
                tone(392f, 196f, 0.9f, Wave.SQUARE, decay = 1.5f),
            ),
            concat(silence(0.96f), tone(98f, 60f, 0.9f, Wave.TRIANGLE, decay = 1.5f, volume = 0.8f)),
        )

        // A thud and a grunt.
        Sound.HIT -> mix(
            tone(300f, 120f, 0.12f, Wave.SQUARE, decay = 3f),
            tone(800f, 200f, 0.08f, Wave.NOISE, decay = 4f, volume = 0.6f),
        )

        Sound.THROW -> tone(400f, 900f, 0.1f, Wave.NOISE, decay = 2f)

        // A big boom: low rumble plus crackling noise.
        Sound.EXPLOSION -> mix(
            tone(1200f, 100f, 0.9f, Wave.NOISE, decay = 3f),
            tone(90f, 35f, 0.8f, Wave.TRIANGLE, decay = 2.5f, volume = 0.9f),
            tone(3000f, 800f, 0.15f, Wave.NOISE, decay = 6f, volume = 0.5f),
        )

        Sound.SHOOT -> mix(
            tone(2500f, 600f, 0.12f, Wave.NOISE, decay = 5f),
            tone(900f, 200f, 0.1f, Wave.SQUARE, decay = 5f, volume = 0.5f),
        )

        Sound.SHOTGUN -> mix(
            tone(1800f, 300f, 0.3f, Wave.NOISE, decay = 4f),
            tone(150f, 50f, 0.25f, Wave.SQUARE, decay = 4f, volume = 0.7f),
        )

        // A wobbly "pew" going up, like ice crystals.
        Sound.FREEZE -> mix(
            tone(1200f, 2400f, 0.25f, Wave.SINE, decay = 2f),
            tone(1800f, 3600f, 0.25f, Wave.SINE, decay = 2f, volume = 0.5f),
        )

        // Glass breaking.
        Sound.SHATTER -> mix(
            tone(4000f, 2000f, 0.25f, Wave.NOISE, decay = 4f),
            concat(tone(2637f, 2637f, 0.05f, Wave.SINE), tone(3136f, 3136f, 0.05f, Wave.SINE), tone(3951f, 3951f, 0.15f, Wave.SINE, decay = 4f)),
        )

        // Cash register: "ka-ching!".
        Sound.BUY -> concat(
            tone(300f, 200f, 0.05f, Wave.NOISE),
            tone(1568f, 1568f, 0.08f, Wave.SQUARE), tone(2093f, 2093f, 0.35f, Wave.SQUARE, decay = 3f),
        )

        // "Uh-uh": two low buzzes.
        Sound.DENIED -> concat(
            tone(180f, 170f, 0.12f, Wave.SQUARE), silence(0.05f), tone(150f, 140f, 0.18f, Wave.SQUARE, decay = 1f),
        )

        Sound.BAT -> concat(
            tone(1500f, 1000f, 0.05f, Wave.SQUARE), silence(0.03f), tone(1500f, 1000f, 0.05f, Wave.SQUARE),
        )
    }
}
