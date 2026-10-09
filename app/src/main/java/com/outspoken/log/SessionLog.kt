package com.outspoken.log

import com.outspoken.eye.EyeSample
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Writes what the app saw and did this session to a CSV file on the phone, for finding out why
 * a blink was missed or a card was picked. Writes happen off the calling thread. Only the
 * newest [keep] files are kept.
 */
class SessionLog(dir: File, startedAt: Date = Date(), private val keep: Int = 5) {

    private val executor = Executors.newSingleThreadExecutor()
    val file = File(dir, "session-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(startedAt)}.csv")
    private val writer: BufferedWriter

    init {
        dir.mkdirs()
        writer = file.bufferedWriter()
        writer.write("time_ms,kind,face,left,right,yaw,pitch,text\n")
        dir.listFiles { f -> f.name.startsWith("session-") && f.name.endsWith(".csv") }
            ?.sortedByDescending { it.name }
            ?.drop(keep)
            ?.forEach { it.delete() }
    }

    fun sample(sample: EyeSample) = write(
        listOf(
            sample.timeMs,
            "eye",
            if (sample.faceFound) 1 else 0,
            sample.leftOpen.fixed(),
            sample.rightOpen.fixed(),
            sample.yawDeg.fixed(1),
            sample.pitchDeg.fixed(1),
            "",
        ).joinToString(","),
    )

    fun event(timeMs: Long, text: String) =
        write("$timeMs,event,,,,,,\"${text.replace("\"", "\"\"").replace('\n', ' ')}\"")

    /** Writes out what is left and closes the file. Waits briefly so nothing is lost. */
    fun close() {
        if (executor.isShutdown) return
        executor.execute { writer.close() }
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun write(line: String) {
        if (executor.isShutdown) return
        executor.execute {
            writer.write(line)
            writer.write("\n")
        }
    }

    /** Pushes buffered lines to the file. */
    fun flush() {
        if (!executor.isShutdown) executor.execute { writer.flush() }
    }

    private fun Float?.fixed(decimals: Int = 3) = this?.let { "%.${decimals}f".format(Locale.US, it) } ?: ""
}
