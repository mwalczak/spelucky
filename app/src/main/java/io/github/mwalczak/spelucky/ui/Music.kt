package io.github.mwalczak.spelucky.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Plays the background music loop. The tune is rendered once on a background thread
 * and then looped seamlessly by AudioTrack.
 */
class Music {
    private var track: AudioTrack? = null
    private var wanted = false
    private var playing = false

    init {
        thread(name = "music-synth", isDaemon = true) {
            val pcm = MusicSynth.render()
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(MusicSynth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                    .also {
                        it.write(pcm, 0, pcm.size)
                        it.setLoopPoints(0, pcm.size, -1)
                        it.setVolume(0.5f)
                    }
            } catch (e: Exception) {
                null // No music is better than a crash.
            }
            synchronized(this) {
                track = t
                apply()
            }
        }
    }

    /** Safe to call every frame; only does something when the wish changes. */
    fun setPlaying(on: Boolean) = synchronized(this) {
        wanted = on
        apply()
    }

    private fun apply() {
        val t = track ?: return
        if (wanted == playing) return
        try {
            if (wanted) t.play() else t.pause()
            playing = wanted
        } catch (e: IllegalStateException) {
            // Track not ready; try again next frame.
        }
    }

    fun release() = synchronized(this) {
        track?.release()
        track = null
        playing = false
    }
}
