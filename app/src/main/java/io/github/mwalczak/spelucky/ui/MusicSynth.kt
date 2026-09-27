package io.github.mwalczak.spelucky.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Writes the background music: an 8-bar chiptune loop in A minor with a square-wave
 * melody, a triangle-wave bass and noise drums. Pure Kotlin, no Android code.
 *
 * To change the tune, edit [MELODY] and [CHORDS]. Notes are written like "A4" or "C#5",
 * "-" is a rest, and the number after the colon is the length in 16th notes.
 * Every bar must add up to 16.
 */
object MusicSynth {

    const val RATE = 22050
    const val BPM = 132
    const val STEPS_PER_BAR = 16

    private val MELODY = listOf(
        "A4:2 C5:2 E5:2 A5:4 G5:2 E5:2 D5:2",
        "C5:4 B4:2 A4:2 E4:4 -:4",
        "F4:2 A4:2 C5:2 F5:4 E5:2 C5:2 A4:2",
        "B4:4 D5:2 G5:4 F5:2 D5:4",
        "E5:2 E5:2 A5:2 E5:2 C5:2 E5:2 A4:4",
        "C5:2 D5:2 E5:4 D5:2 C5:2 B4:4",
        "A4:2 C5:2 F5:4 E5:2 D5:2 C5:4",
        "B4:4 G#4:4 E4:4 -:4",
    )

    /** Root note of each bar for the bass line. */
    private val CHORDS = listOf("A2", "A2", "F2", "G2", "A2", "A2", "F2", "E2")

    private val stepSeconds = 60.0 / BPM / 4
    private val samplesPerStep = (RATE * stepSeconds).toInt()
    val bars get() = MELODY.size
    val totalSamples get() = samplesPerStep * STEPS_PER_BAR * bars

    fun noteFrequency(name: String): Double {
        val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = name.last().digitToInt()
        val semitone = names.indexOf(name.dropLast(1))
        require(semitone >= 0) { "Unknown note $name" }
        val midi = (octave + 1) * 12 + semitone
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    private class Note(val freq: Double?, val startStep: Int, val steps: Int)

    private fun parseMelody(): List<Note> {
        val notes = mutableListOf<Note>()
        for ((bar, line) in MELODY.withIndex()) {
            var step = bar * STEPS_PER_BAR
            val barStart = step
            for (token in line.trim().split(Regex("\\s+"))) {
                val (name, len) = token.split(":")
                val steps = len.toInt()
                notes += Note(if (name == "-") null else noteFrequency(name), step, steps)
                step += steps
            }
            require(step - barStart == STEPS_PER_BAR) { "Bar ${bar + 1} of the melody is ${step - barStart} steps long, not 16" }
        }
        return notes
    }

    fun render(): ShortArray {
        val mix = FloatArray(totalSamples)
        val rng = Random(3)

        // Melody: square wave with 25% duty, a little vibrato and a soft decay.
        for (n in parseMelody()) {
            val f = n.freq ?: continue
            val start = n.startStep * samplesPerStep
            val len = n.steps * samplesPerStep
            var phase = 0.0
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                val vib = 1 + 0.004 * sin(2 * PI * 5.5 * t) * (if (t > 0.12) 1 else 0)
                phase += f * vib / RATE
                val v = if (phase % 1.0 < 0.25) 1f else -1f
                val env = envelope(i, len, attack = 0.005, release = 0.03) * (0.65 + 0.35 * exp(-t * 4)).toFloat()
                mix[start + i] += v * env * 0.11f
            }
        }

        // Bass: triangle wave in eighth notes, jumping up an octave on the off-beats.
        for ((bar, root) in CHORDS.withIndex()) {
            val f = noteFrequency(root)
            for (eighth in 0 until 8) {
                val freq = if (eighth % 2 == 1) f * 2 else f
                val start = (bar * STEPS_PER_BAR + eighth * 2) * samplesPerStep
                val len = samplesPerStep * 2
                var phase = 0.0
                for (i in 0 until len) {
                    phase += freq / RATE
                    val p = phase % 1.0
                    val tri = (4 * abs(p - 0.5) - 1).toFloat()
                    mix[start + i] += tri * envelope(i, len, attack = 0.003, release = 0.02) * 0.2f
                }
            }
        }

        // Drums: kick on 1 and 3, snare on 2 and 4, soft hi-hat on every 8th.
        for (step in 0 until STEPS_PER_BAR * bars) {
            val start = step * samplesPerStep
            val beat = step % STEPS_PER_BAR
            when {
                beat == 0 || beat == 8 -> {
                    var phase = 0.0
                    val len = (RATE * 0.12).toInt()
                    for (i in 0 until len) {
                        val t = i.toDouble() / RATE
                        phase += (50 + 110 * exp(-t * 35)) / RATE
                        mix[start + i] += (sin(2 * PI * phase) * exp(-t * 22)).toFloat() * 0.35f
                    }
                }
                beat == 4 || beat == 12 -> addNoise(mix, start, 0.1, 0.13f, 30.0, rng)
            }
            if (step % 2 == 0) addNoise(mix, start, 0.03, 0.04f, 120.0, rng, bright = true)
        }

        val out = ShortArray(totalSamples)
        for (i in mix.indices) out[i] = (mix[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
        return out
    }

    private fun addNoise(mix: FloatArray, start: Int, seconds: Double, volume: Float, decay: Double, rng: Random, bright: Boolean = false) {
        val len = (RATE * seconds).toInt()
        var prev = 0f
        for (i in 0 until len) {
            if (start + i >= mix.size) break
            val t = i.toDouble() / RATE
            val white = rng.nextFloat() * 2 - 1
            // A crude high-pass for the hi-hat, low-pass for the snare.
            val v = if (bright) white - prev else (white + prev) / 2
            prev = white
            mix[start + i] += v * exp(-t * decay).toFloat() * volume
        }
    }

    /** Short fade in and out so notes never click. */
    private fun envelope(i: Int, len: Int, attack: Double, release: Double): Float {
        val a = (i / (RATE * attack)).coerceAtMost(1.0)
        val r = ((len - i) / (RATE * release)).coerceAtMost(1.0)
        return minOf(a, r).toFloat()
    }
}
