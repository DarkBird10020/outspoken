package com.outspoken.log

import java.io.File
import java.io.OutputStream

/**
 * Writes every run log in [dir] into one stream, oldest run first, each under a header line, so
 * the owner can save it somewhere they can reach (like Downloads) and send it on.
 */
fun exportLogs(dir: File, out: OutputStream): Int {
    val files = dir.listFiles { file -> file.extension == "log" }?.sortedBy { it.name }.orEmpty()
    out.bufferedWriter().use { writer ->
        files.forEach { file ->
            writer.write("===== ${file.name} =====\n")
            file.bufferedReader().use { it.copyTo(writer) }
            writer.write("\n")
        }
    }
    return files.size
}
