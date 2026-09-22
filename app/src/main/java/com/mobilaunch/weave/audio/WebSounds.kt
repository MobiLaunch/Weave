package com.mobilaunch.weave.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Tiny synthesized sound kit – no audio assets. A taut-thread "snap" for re-attaching
 * a thought, and a soft rising chime when a new thought is woven in.
 */
class WebSounds {
    private var snapTrack: AudioTrack? = null
    private var weaveTrack: AudioTrack? = null

    fun playSnap() {
        val track = snapTrack ?: build(Synth.snap()).also { snapTrack = it }
        play(track)
    }

    fun playWeave() {
        val track = weaveTrack ?: build(Synth.weave()).also { weaveTrack = it }
        play(track)
    }

    fun release() {
        snapTrack?.release()
        weaveTrack?.release()
        snapTrack = null
        weaveTrack = null
    }

    private fun play(track: AudioTrack?) {
        track ?: return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Sound skipped", e)
        }
    }

    private fun build(samples: ShortArray): AudioTrack? = try {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Synth.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(samples, 0, samples.size) }
    } catch (e: Exception) {
        Log.w(TAG, "Audio unavailable", e)
        null
    }

    private companion object {
        const val TAG = "WebSounds"
    }
}

internal object Synth {
    const val RATE = 44_100
    private const val TAU = 2.0 * PI

    /** A sharp click, a low thump and a plucked, falling string. */
    fun snap(): ShortArray {
        val n = (RATE * 0.42).toInt()
        val out = FloatArray(n)
        val rnd = Random(3)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val click = if (t < 0.006) (rnd.nextDouble() * 2 - 1) * (1 - t / 0.006) * 0.9 else 0.0
            val thump = sin(TAU * 95 * t) * exp(-t * 38) * 0.55
            val freq = 520 + 1500 * exp(-t * 9)
            phase += TAU * freq / RATE
            val twang = sin(phase) * exp(-t * 11) * 0.42 + sin(phase * 2 + 0.3) * exp(-t * 19) * 0.16
            out[i] = (click + thump + twang).toFloat()
        }
        return toPcm(out)
    }

    /** Three staggered bell tones, G5 – C6 – G6. */
    fun weave(): ShortArray {
        val n = (RATE * 1.1).toInt()
        val out = FloatArray(n)
        val notes = doubleArrayOf(783.99, 1046.5, 1567.98)
        for ((k, f) in notes.withIndex()) {
            val start = k * 0.075
            for (i in 0 until n) {
                val t = i.toDouble() / RATE - start
                if (t < 0) continue
                val attack = (t / 0.006).coerceAtMost(1.0)
                val env = attack * exp(-t * 5.5)
                val vibrato = 1 + 0.003 * sin(TAU * 5 * t)
                out[i] += (env * 0.22 * (sin(TAU * f * vibrato * t) + 0.3 * sin(TAU * f * 2 * t))).toFloat()
            }
        }
        return toPcm(out)
    }

    private fun toPcm(buffer: FloatArray): ShortArray {
        val peak = buffer.maxOf { abs(it) }.coerceAtLeast(1e-6f)
        val gain = 0.85f / peak
        val fade = (RATE * 0.02).toInt()
        return ShortArray(buffer.size) { i ->
            val tail = ((buffer.size - i).toFloat() / fade).coerceAtMost(1f)
            val v = tanh((buffer[i] * gain * tail).toDouble()).toFloat()
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
