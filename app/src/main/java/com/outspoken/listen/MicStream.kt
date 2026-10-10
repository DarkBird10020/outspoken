package com.outspoken.listen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

/**
 * The microphone, recorded by the app and handed to the speech recogniser through a pipe
 * ([source] goes in `RecognizerIntent.EXTRA_AUDIO_SOURCE`). Android ends such a session "when and
 * only when the audio is closed", so one session lasts until [close], with no gap between
 * sentences. While [muted], silence goes down the pipe instead, so the phone does not hear its own
 * voice and the session does not have to stop.
 */
class MicStream private constructor(
    private val record: AudioRecord,
    val source: ParcelFileDescriptor,
    private val sink: ParcelFileDescriptor,
) {
    @Volatile var muted = false

    @Volatile private var running = true

    /** Why the sound stopped early, for the log; null while it flows. */
    @Volatile var failure: String? = null
        private set

    init {
        thread(name = "mic", isDaemon = true) { pump() }
    }

    private fun pump() {
        val chunk = ByteArray(CHUNK_BYTES)
        try {
            record.startRecording()
            while (running) {
                val read = record.read(chunk, 0, chunk.size)
                if (read < 0) {
                    failure = "microphone read failed ($read)"
                    break
                }
                if (read == 0) continue
                if (muted) chunk.fill(0, 0, read)
                try {
                    // A chunk this small goes into the pipe whole or not at all. When the
                    // recogniser is not reading, it is dropped rather than blocking the microphone.
                    Os.write(sink.fileDescriptor, chunk, 0, read)
                } catch (e: ErrnoException) {
                    if (e.errno != OsConstants.EAGAIN) {
                        if (running) failure = "the recogniser closed the sound pipe"
                        break
                    }
                }
            }
        } catch (e: IllegalStateException) {
            failure = "microphone would not start: ${e.message}"
        } finally {
            runCatching { record.stop() }
            record.release()
            runCatching { sink.close() }
        }
    }

    fun close() {
        running = false
        runCatching { source.close() }
    }

    companion object {
        const val SAMPLE_RATE = 16_000

        /** 50 ms of 16-bit mono sound: below the pipe's atomic write size (4096 bytes). */
        private const val CHUNK_BYTES = SAMPLE_RATE / 20 * 2

        /** A running stream, or null when there is no microphone permission or it will not open. */
        @SuppressLint("MissingPermission")
        fun open(context: Context): MicStream? {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
            val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBytes <= 0) return null
            val record = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBytes, CHUNK_BYTES * 8),
                )
            }.getOrNull() ?: return null
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return null
            }
            val pipe = runCatching { ParcelFileDescriptor.createPipe() }.getOrNull()
            val nonBlocking = pipe != null && runCatching {
                Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
            }.isSuccess
            if (pipe == null || !nonBlocking) {
                pipe?.forEach { runCatching { it.close() } }
                record.release()
                return null
            }
            return MicStream(record, pipe[0], pipe[1])
        }
    }
}
