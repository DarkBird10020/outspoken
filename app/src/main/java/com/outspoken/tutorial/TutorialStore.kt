package com.outspoken.tutorial

import android.content.Context

/** Which lessons are finished, by [Lesson.id], kept on this phone. */
class TutorialStore(context: Context) {

    private val prefs = context.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    fun finished(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun markFinished(id: String) {
        prefs.edit().putStringSet(KEY, finished() + id).apply()
    }

    private companion object {
        const val KEY = "finished"
    }
}
