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
 * Gemma (or any `.litertlm` model) running on the phone through LiteRT-LM. Starts in the first
 * of [START_PLANS] that works: GPU with the drafter, GPU, then CPU. One request at a time.
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
            val (plan, started) = startFirst(START_PLANS, AppLog, ::start)
            engine = started
            backendName = plan.label
        }
    }

    override suspend fun generate(prompt: String): Generation = withContext(Dispatchers.Default) {
        lock.withLock {
            write(checkNotNull(engine) { "Model is not loaded" }, prompt, MAX_OUTPUT_TOKENS)
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun write(engine: Engine, prompt: String, maxTokens: Int): Generation =
        engine.createConversation(ConversationConfig(samplerConfig = SAMPLER)).use { conversation ->
            val text = conversation.sendMessage(prompt, maxOutputToken = maxTokens).toString()
            val tokensPerSecond = runCatching {
                conversation.getBenchmarkInfo().lastDecodeTokensPerSecond.toFloat()
            }.getOrNull()
            Generation(text, tokensPerSecond)
        }

    /** Frees the model, after any reply being written finishes. */
    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock {
            engine?.close()
            engine = null
        }
    }

    @OptIn(ExperimentalApi::class)
    private fun start(plan: StartPlan): Engine {
        // Read when an engine is made. Null leaves it to the model, which never fails to start.
        ExperimentalFlags.enableSpeculativeDecoding = if (plan.speculative) true else null
        val backend = if (plan.backend == "GPU") Backend.GPU() else Backend.CPU()
        val engine = Engine(EngineConfig(modelPath = modelPath, backend = backend, cacheDir = cacheDir))
        try {
            engine.initialize()
            // The drafter can be set up on first use, so a short reply proves it works here.
            if (plan.speculative) write(engine, WARM_UP_PROMPT, WARM_UP_TOKENS)
        } catch (e: Exception) {
            engine.close()
            throw e
        }
        return engine
    }

    private companion object {
        // Low temperature: small Gemma models at 0.8 drifted from the four-item JSON format, and
        // every malformed answer cost a second generation.
        val SAMPLER = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)

        // Four short replies as JSON fit well inside this; it caps the worst-case reply time.
        const val MAX_OUTPUT_TOKENS = 96

        const val WARM_UP_PROMPT = "Say OK."
        const val WARM_UP_TOKENS = 4
    }
}
