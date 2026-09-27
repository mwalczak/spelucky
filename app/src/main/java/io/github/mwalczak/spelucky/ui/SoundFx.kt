package io.github.mwalczak.spelucky.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import io.github.mwalczak.spelucky.game.Sound
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Plays the retro sound effects designed in [SfxSynth]. Each sound is written to a small
 * WAV file in the cache when the app starts and played with SoundPool.
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
            file.writeBytes(wav(SfxSynth.render(s)))
            ids[s.ordinal] = pool.load(file.path, 1)
        }
    }

    fun play(s: Sound) {
        if (!loaded[s.ordinal]) return
        pool.play(ids[s.ordinal], 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun wav(samples: FloatArray): ByteArray {
        val dataLen = samples.size * 2
        val b = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SfxSynth.RATE).putInt(SfxSynth.RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(dataLen)
        for (s in samples) b.putShort((s.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        return b.array()
    }
}
