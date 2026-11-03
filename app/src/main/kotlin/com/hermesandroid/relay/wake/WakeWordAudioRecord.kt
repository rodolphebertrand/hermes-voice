package com.hermesandroid.relay.wake

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Hardware belongs to one recognition attempt, including partially built resources. */
internal class WakeWordAudioRecord(
    private val context: Context,
    private val preferences: WakeWordPreferences,
) : WakeWordAudio {
    private val lock = Any()
    private val stopped = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var trigger: WakeTrigger? = null

    @SuppressLint("MissingPermission")
    override suspend fun detect(onListening: () -> Unit, onSamples: (ShortArray, Int) -> Unit): WakeDetection? {
        if (stopped.get()) return null
        // Phase 0b : primaire = assets openWakeWord ONNX (wakewords.json) ;
        // fallback = sherpa-onnx KWS installé — la décision revient à WakeTriggerFactory.
        val createdTrigger = WakeTriggerFactory.create(context, preferences)
        trigger = createdTrigger
        val minBuffer = AudioRecord.getMinBufferSize(
            16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(6_400)
        val created = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(16_000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        synchronized(lock) {
            recorder = created
            if (stopped.get()) return null
            check(created.state == AudioRecord.STATE_INITIALIZED) { "Wake-word microphone failed to initialize" }
            created.startRecording()
        }
        Log.i(TAG, "AudioRecord open: 16000Hz mono VOICE_RECOGNITION, buffer=${minBuffer * 2}B, trigger=${trigger.id}")
        onListening()
        val samples = ShortArray(1_600)
        while (!stopped.get()) {
            val count = created.read(samples, 0, samples.size)
            if (stopped.get()) return null
            check(count >= 0) { "Wake-word microphone read failed: $count" }
            onSamples(samples, count)
            if (count > 0) {
                val hit = createdTrigger.accept(samples, count)
                if (hit != null) return hit
            }
        }
        return null
    }

    override fun stop() {
        stopped.set(true)
        synchronized(lock) { runCatching { recorder?.stop() } }
    }

    override fun close() {
        stop()
        synchronized(lock) {
            runCatching { recorder?.release() }
            recorder = null
        }
        runCatching { trigger?.close() }
        trigger = null
        Log.i(TAG, "AudioRecord released")
    }

    private companion object {
        const val TAG = "WakeWordAudio"
    }
}
