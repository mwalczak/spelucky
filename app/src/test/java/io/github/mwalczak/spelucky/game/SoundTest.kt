package io.github.mwalczak.spelucky.game

import io.github.mwalczak.spelucky.ui.SfxSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoundTest {

    @Test
    fun everySoundIsAudibleAndDoesNotClip() {
        for (s in Sound.values()) {
            val pcm = SfxSynth.render(s)
            val seconds = pcm.size.toFloat() / SfxSynth.RATE
            assertTrue("$s is $seconds s", seconds in 0.05f..3f)
            val peak = pcm.maxOf { abs(it) }
            assertEquals("$s peak", SfxSynth.loudness(s), peak, 0.01f)
            assertTrue("$s ends silently", abs(pcm.last()) < 0.05f)
        }
    }

    @Test
    fun importantSoundsAreLoud() {
        for (s in listOf(Sound.COIN, Sound.GEM, Sound.WHIP, Sound.HURT, Sound.DEATH)) {
            assertTrue("$s should be loud", SfxSynth.loudness(s) >= 0.85f)
        }
    }

    @Test
    fun gameMakesTheRightSounds() {
        val sounds = mutableListOf<Sound>()
        val game = gameOn(levelOf(
            "#########",
            "#.......#",
            "#.......#",
            "#.Ps....#",
            "#########",
        ))
        game.onSound = { sounds += it }
        game.run(0.5f, press = Btn.WHIP)
        assertTrue(Sound.WHIP in sounds)
        assertTrue(Sound.KILL in sounds)

        game.hurtPlayer(1, 0f)
        assertTrue(Sound.HURT in sounds)
        game.run(1.5f)
        game.hurtPlayer(99, 0f)
        assertTrue(Sound.DEATH in sounds)
    }
}
