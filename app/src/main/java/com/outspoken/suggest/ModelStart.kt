package com.outspoken.suggest

import com.outspoken.log.EventLog

/** One way to start the model: on which chip, and whether its multi-token drafter (MTP) is asked for. */
data class StartPlan(val backend: String, val speculative: Boolean) {
    val label: String get() = if (speculative) "$backend with MTP" else backend
}

/**
 * Fastest first. Gemma 4 files carry a multi-token drafter that LiteRT-LM leaves off unless asked;
 * Google reports up to 3x faster writing with it. A model without one fails to start with it on,
 * so the plain GPU and then the CPU follow.
 */
val START_PLANS = listOf(StartPlan("GPU", true), StartPlan("GPU", false), StartPlan("CPU", false))

/** Starts with the first plan that works, logging why each earlier one failed. */
fun <T> startFirst(plans: List<StartPlan>, log: EventLog, start: (StartPlan) -> T): Pair<StartPlan, T> {
    var failure: Exception? = null
    for (plan in plans) {
        try {
            return plan to start(plan)
        } catch (e: Exception) {
            log.write("model", "could not start on ${plan.label}: ${e.message ?: e.javaClass.simpleName}")
            failure = e
        }
    }
    throw failure ?: IllegalArgumentException("no way to start the model")
}
