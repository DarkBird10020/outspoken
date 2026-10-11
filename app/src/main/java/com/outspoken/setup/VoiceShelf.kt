package com.outspoken.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.outspoken.speech.NaturalVoices
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream

/**
 * The natural voice on this phone: downloading it, unpacking it, and which voice is chosen. The
 * archive stays in Downloads like the Gemma models; it is unpacked next to the model choice in
 * Documents/Outspoken, so it survives a reinstall. The choices are kept apart from the eye tuning,
 * so Reset in Settings does not switch the voice.
 */
class VoiceShelf(private val context: Context) {

    private val prefs = context.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val downloadsDir: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** The unpacked voice. In the app's own folder when Downloads cannot be seen, as nothing else can be. */
    val folder: File
        get() = if (canSeeFiles()) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Outspoken/voices/${VoicePack.FOLDER}")
        } else {
            File(context.getExternalFilesDir(null), "voices/${VoicePack.FOLDER}")
        }

    /** The natural voice is picked on the Model screen (the phone's own voice otherwise). */
    var useNatural: Boolean
        get() = prefs.getBoolean(USE, false)
        set(value) = prefs.edit().putBoolean(USE, value).apply()

    var voiceId: Int
        get() = prefs.getInt(VOICE, NaturalVoices.default.id)
        set(value) = prefs.edit().putInt(VOICE, value).apply()

    /** 1 is the model's own pace; above is faster. */
    var speed: Float
        get() = prefs.getFloat(SPEED, 1f)
        set(value) = prefs.edit().putFloat(SPEED, value).apply()

    /** The browser was asked for the archive; it is unpacked by itself when it arrives. */
    var waiting: Boolean
        get() = prefs.getBoolean(WAITING, false)
        set(value) = prefs.edit().putBoolean(WAITING, value).apply()

    fun canSeeFiles(): Boolean = Environment.isExternalStorageManager()

    fun unpacked(): Boolean = VoicePack.NEEDED.all { File(folder, it).isFile }

    /** The finished archive in Downloads, or null. */
    fun archive(): File? {
        if (!canSeeFiles()) return null
        val files = downloadsDir.listFiles()?.sortedBy { it.name }?.map { it.name to it.length() }.orEmpty()
        return VoicePack.findDownload(files)?.let { File(downloadsDir, it) }
    }

    /** Opens the download in the phone's browser. False when no app can open the link. */
    fun download(): Boolean {
        waiting = true
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VoicePack.URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /**
     * Unpacks [archive] into [folder], through a folder beside it that is renamed only when every
     * file is out, so a half-unpacked voice is never loaded. [onPercent] hears how much of the
     * archive has been read. Blocking: call off the main thread. Returns the number of files.
     */
    fun unpack(archive: File, onPercent: (Int) -> Unit): Int {
        val target = folder
        val partial = File(target.parentFile, "${target.name}.partial")
        partial.deleteRecursively()
        partial.mkdirs()
        var files = 0
        var lastPercent = -1
        CountingStream(archive.inputStream()) { read ->
            val percent = (read * 100 / archive.length()).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                onPercent(percent)
            }
        }.use { counted ->
            // The decompressor reads a byte at a time; the buffer keeps the counting to large reads.
            TarArchiveInputStream(BZip2CompressorInputStream(counted.buffered(BUFFER))).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    val inside = VoicePack.entryPath(entry.name) ?: continue
                    val out = File(partial, inside)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered(BUFFER).use { tar.copyTo(it, BUFFER) }
                        files++
                    }
                }
            }
        }
        target.deleteRecursively()
        check(partial.renameTo(target)) { "could not move the unpacked voice into place" }
        return files
    }

    private fun interface ReadCount {
        fun onRead(total: Long)
    }

    private class CountingStream(input: InputStream, private val onRead: ReadCount) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) onRead.onRead(++count) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
            if (it > 0) {
                count += it
                onRead.onRead(count)
            }
        }
    }

    private companion object {
        const val USE = "useNatural"
        const val VOICE = "voiceId"
        const val SPEED = "speed"
        const val WAITING = "waiting"
        const val BUFFER = 1 shl 16
    }
}
