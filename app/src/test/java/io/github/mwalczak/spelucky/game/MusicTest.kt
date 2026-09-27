package io.github.mwalczak.spelucky.game

import io.github.mwalczak.spelucky.ui.MusicSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MusicTest {

    @Test
    fun noteFrequencies() {
        assertEquals(440.0, MusicSynth.noteFrequency("A4"), 0.01)
        assertEquals(261.63, MusicSynth.noteFrequency("C4"), 0.01)
        assertEquals(415.30, MusicSynth.noteFrequency("G#4"), 0.01)
    }

    @Test
    fun loopRendersWithoutClippingAndLoopsCleanly() {
        val pcm = MusicSynth.render()
        assertEquals(MusicSynth.totalSamples, pcm.size)
        val seconds = pcm.size.toFloat() / MusicSynth.RATE
        assertTrue("loop is $seconds s", seconds in 10f..30f)
        val clipped = pcm.count { abs(it.toInt()) >= 32767 }
        assertTrue("$clipped clipped samples", clipped < pcm.size / 1000)
        // The loop point should not click: start and end are close to silence.
        assertTrue(abs(pcm.first().toInt()) < 2000)
        assertTrue(abs(pcm.last().toInt()) < 2000)
    }

    @Test
    fun musicButtonTogglesWithoutStartingTheGame() {
        val game = Game(seed = 1)
        var saved: Boolean? = null
        game.onMusicSetting = { saved = it }
        val input = Input()
        val state = InputState()
        input.setKey(Btn.MUSIC, true)
        input.poll(state)
        game.update(0.5f, state)
        assertEquals(false, game.musicOn)
        assertEquals(false, saved)
        assertEquals(Game.State.TITLE, game.state)
        assertTrue(!game.wantsMusic)
    }
}
