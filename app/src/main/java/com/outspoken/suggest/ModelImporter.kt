package com.outspoken.suggest

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a model file the person picked on the phone (for example from Downloads) into the
 * app's own folder, so no cable or adb is needed. Only one model is kept.
 */
class ModelImporter(private val context: Context) {

    suspend fun import(uri: Uri, targetDir: File, onProgress: (percent: Int) -> Unit): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "model.litertlm"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { row ->
            if (row.moveToFirst()) {
                row.getString(0)?.let { name = it }
                if (!row.isNull(1)) size = row.getLong(1)
            }
        }
        require(name.endsWith(".litertlm")) { "$name is not a .litertlm model file" }

        targetDir.mkdirs()
        val partial = File(targetDir, "$name.part")
        val input = checkNotNull(resolver.openInputStream(uri)) { "could not open $name" }
        input.use { source ->
            partial.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_BYTES)
                var copied = 0L
                var lastPercent = -1
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    copied += read
                    val percent = if (size > 0) (copied * 100 / size).toInt() else -1
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                }
            }
        }
        targetDir.listFiles { file -> file.extension == "litertlm" }?.forEach { it.delete() }
        val target = File(targetDir, name)
        check(partial.renameTo(target)) { "could not save $name" }
        target
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 20
    }
}
