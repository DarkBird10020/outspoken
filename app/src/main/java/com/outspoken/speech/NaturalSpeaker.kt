package com.outspoken.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.outspoken.log.EventLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

sealed interface NaturalState {
    data object Off : NaturalState
    data object Loading : NaturalState
    data class Ready(val loadMs: Long) : NaturalState
    data class Failed(val reason: String) : NaturalState
}

/**
 * Says sentences with the Kokoro voice. All model work runs on one background thread, so it never
 * holds up the camera or the screen; playing runs on another. [prepare] makes the cards' audio
 * ahead of time, so the card chosen plays at once.
 */
class NaturalSpeaker(private val log: EventLog) {

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "natural-voice").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val player = Executors.newSingleThreadExecutor { Thread(it, "natural-play") }

    @Volatile
    private var tts: OfflineTts? = null
    private val cache = AudioCache<FloatArray>()

    /** Bumped by every [prepare], so cards that are no longer on screen are not made. */
    private val prepareRound = AtomicInteger()

    @Volatile
    private var playing: AudioTrack? = null

    private val _state = MutableStateFlow<NaturalState>(NaturalState.Off)
    val state: StateFlow<NaturalState> = _state.asStateFlow()

    val ready: Boolean get() = _state.value is NaturalState.Ready

    fun load(folder: File) {
        if (_state.value is NaturalState.Loading || ready) return
        _state.value = NaturalState.Loading
        worker.execute {
            val started = SystemClock.elapsedRealtime()
            try {
                fun path(name: String) = File(folder, name).absolutePath
                val kokoro = OfflineTtsKokoroModelConfig().apply {
                    model = path("model.onnx")
                    voices = path("voices.bin")
                    tokens = path("tokens.txt")
                    dataDir = path("espeak-ng-data")
                    lexicon = listOf("lexicon-us-en.txt", "lexicon-zh.txt").joinToString(",") { path(it) }
                    dictDir = path("dict")
                }
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig().apply {
                        this.kokoro = kokoro
                        numThreads = THREADS
                        provider = "cpu"
                    },
                )
                tts = OfflineTts(null, config)
                val ms = SystemClock.elapsedRealtime() - started
                log.write("speech", "natural voice loaded in $ms ms, ${tts?.numSpeakers()} voices, ${tts?.sampleRate()} Hz, $THREADS threads, ${memory()}")
                _state.value = NaturalState.Ready(ms)
            } catch (e: Throwable) {
                log.write("speech", "natural voice failed to load: ${e.message ?: e.javaClass.simpleName}")
                _state.value = NaturalState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Frees the model's memory, for when the phone's own voice is picked again. */
    fun unload() {
        prepareRound.incrementAndGet()
        worker.execute {
            tts?.release()
            tts = null
            cache.clear()
            log.write("speech", "natural voice unloaded")
        }
        _state.value = NaturalState.Off
    }

    /** Makes the audio for [texts] in the background, first card first, skipping what is made. */
    fun prepare(texts: List<String>, voice: KokoroVoice, speed: Float) {
        if (!ready) return
        val round = prepareRound.incrementAndGet()
        val keys = cache.missing(texts.filter { it.isNotBlank() }.map { AudioCache.Key(it, voice.id, speed) })
        if (keys.isEmpty()) return
        worker.execute {
            var made = 0
            val started = SystemClock.elapsedRealtime()
            for (key in keys) {
                if (prepareRound.get() != round) break
                if (!cache.contains(key)) make(key)?.let { made++ }
            }
            if (made > 0) log.write("speech", "natural voice made $made cards ahead in ${SystemClock.elapsedRealtime() - started} ms")
        }
    }

    /**
     * Says [text]; [onDone] runs once it has finished or been cut short, with false when nothing
     * could be said (the caller then uses the phone's own voice). Audio made ahead plays at once;
     * otherwise it is made now, after whatever the voice is making.
     */
    fun speak(text: String, voice: KokoroVoice, speed: Float, onDone: (spoken: Boolean) -> Unit) {
        val key = AudioCache.Key(text, voice.id, speed)
        val ready = cache.get(key)
        if (ready != null) {
            log.write("speech", "natural voice, made ahead: \"$text\"")
            play(ready, onDone)
            return
        }
        val asked = SystemClock.elapsedRealtime()
        worker.execute {
            val audio = cache.get(key) ?: make(key)
            if (audio == null) {
                onDone(false)
                return@execute
            }
            log.write("speech", "natural voice, made on demand ${SystemClock.elapsedRealtime() - asked} ms after the choice: \"$text\"")
            play(audio, onDone)
        }
    }

    fun stop() {
        playing?.runCatching { pause(); flush() }
    }

    fun shutdown() {
        stop()
        worker.execute {
            tts?.release()
            tts = null
        }
        worker.shutdown()
        player.shutdown()
    }

    /** Model thread only. */
    private fun make(key: AudioCache.Key): FloatArray? {
        val engine = tts ?: return null
        val started = SystemClock.elapsedRealtime()
        return try {
            val audio = engine.generate(key.text, key.voiceId, key.speed)
            val ms = SystemClock.elapsedRealtime() - started
            val seconds = audio.samples.size.toFloat() / audio.sampleRate
            sampleRate = audio.sampleRate
            log.write(
                "speech",
                String.format(Locale.US, "natural voice made %.1f s of speech in %d ms (%.2f of real time): \"%s\"", seconds, ms, ms / 1000f / seconds, key.text),
            )
            cache.put(key, audio.samples)
            audio.samples
        } catch (e: Throwable) {
            log.write("speech", "natural voice could not say \"${key.text}\": ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }

    @Volatile
    private var sampleRate = DEFAULT_RATE

    private fun play(samples: FloatArray, onDone: (spoken: Boolean) -> Unit) {
        player.execute {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT) * 2)
                    .build()
                playing = track
                track.play()
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                // Writing ends when the last samples are queued; wait until they have been heard.
                val end = SystemClock.elapsedRealtime() + samples.size * 1000L / sampleRate + 500
                while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < samples.size && SystemClock.elapsedRealtime() < end) {
                    Thread.sleep(20)
                }
            } catch (e: Throwable) {
                log.write("speech", "natural voice could not play: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                playing = null
                track?.runCatching { stop(); release() }
                onDone(true)
            }
        }
    }

    private fun memory(): String {
        val rt = Runtime.getRuntime()
        val native = android.os.Debug.getNativeHeapAllocatedSize() / 1_000_000
        return "native heap $native MB, java heap ${(rt.totalMemory() - rt.freeMemory()) / 1_000_000} MB"
    }

    private companion object {
        /** Two of the phone's cores; the camera, face tracker and listener keep the rest. */
        const val THREADS = 2
        const val DEFAULT_RATE = 24_000
    }
}
