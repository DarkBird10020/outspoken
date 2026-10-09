package com.outspoken.suggest

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.outspoken.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemma (or any `.litertlm` model) running on the phone through LiteRT-LM. Tries the GPU first
 * and falls back to the CPU. One request at a time.
 */
class LiteRtLmModel(private val modelPath: String, private val cacheDir: String) : TextModel {

    private val lock = Mutex()
    private var engine: Engine? = null

    var backendName: String = ""
        private set

    @OptIn(ExperimentalApi::class)
    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (engine != null) return@withLock
            ExperimentalFlags.enableBenchmark = true
            engine = try {
                start(Backend.GPU()).also { backendName = "GPU" }
            } catch (gpu: Exception) {
                AppLog.write("model", "could not start on the GPU: ${reason(gpu)}")
                try {
                    start(Backend.CPU()).also { backendName = "CPU" }
                } catch (cpu: Exception) {
                    throw IllegalStateException("GPU: ${reason(gpu)}; CPU: ${reason(cpu)}", cpu)
                }
            }
        }
    }

    @OptIn(ExperimentalApi::class)
    override suspend fun generate(prompt: String): Generation = withContext(Dispatchers.Default) {
        lock.withLock {
            val engine = checkNotNull(engine) { "Model is not loaded" }
            engine.createConversation(ConversationConfig(samplerConfig = SAMPLER)).use { conversation ->
                val text = conversation.sendMessage(prompt, maxOutputToken = MAX_OUTPUT_TOKENS).toString()
                val tokensPerSecond = runCatching {
                    conversation.getBenchmarkInfo().lastDecodeTokensPerSecond.toFloat()
                }.getOrNull()
                Generation(text, tokensPerSecond)
            }
        }
    }

    /** Frees the model, after any reply being written finishes. */
    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            engine?.close()
            engine = null
        }
    }

    private fun start(backend: Backend): Engine {
        val engine = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir))
        try {
            engine.initialize()
        } catch (e: Exception) {
            // Closing an engine that never started throws "Engine is not initialized", which
            // used to replace the real reason the start failed.
            runCatching { engine.close() }
            throw e
        }
        return engine
    }

    private fun reason(e: Exception): String = e.message ?: e.javaClass.simpleName

    private companion object {
        // Low temperature: small Gemma models at 0.8 drifted from the four-item JSON format, and
        // every malformed answer cost a second generation.
        val SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)

        // Four short replies as JSON fit well inside this; it caps the worst-case reply time.
        const val MAX_OUTPUT_TOKENS = 96
    }
}
