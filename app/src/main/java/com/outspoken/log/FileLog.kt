package com.outspoken.log

import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Writes each line to logcat (tag `Outspoken`) and to one file per app start under
 * `Android/data/com.outspoken/files/logs`, so a run on the phone can be read back with adb.
 */
class FileLog(filesDir: File?) : EventLog {

    val file: File? = filesDir?.let { File(it, "logs") }?.let { dir ->
        dir.mkdirs()
        dir.listFiles { f -> f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP_FILES - 1)
            ?.forEach { it.delete() }
        File(dir, "outspoken-${LocalDateTime.now().format(FILE_STAMP)}.log")
    }

    private val writer = Executors.newSingleThreadExecutor()

    override fun write(area: String, message: String) {
        Log.i(TAG, "$area: $message")
        val line = line(area, message)
        writer.execute { append(line) }
    }

    /** Records any crash in the file before the app dies, then lets Android handle it as usual. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val text = "on ${thread.name}: ${error.stackTraceToString()}"
            Log.e(TAG, "crash $text")
            // Written here, not on the writer thread: the process may be gone before it runs.
            append(line("crash", text))
            previous?.uncaughtException(thread, error)
        }
    }

    private fun line(area: String, message: String) =
        "${LocalDateTime.now().format(LINE_STAMP)} ${area.padEnd(AREA_WIDTH)} $message\n"

    @Synchronized
    private fun append(line: String) {
        try {
            file?.appendText(line)
        } catch (e: Exception) {
            Log.w(TAG, "could not write log file", e)
        }
    }

    private companion object {
        const val TAG = "Outspoken"
        const val KEEP_FILES = 10
        const val AREA_WIDTH = 7
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        val LINE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    }
}
