package com.outspoken.setup

import java.io.File
import java.security.MessageDigest

/**
 * Checks a downloaded model against the SHA-256 Hugging Face publishes for it. A browser download
 * on the phone had the exact size of Gemma 4 E2B but other bytes (03:51), and LiteRT-LM only said
 * "Invalid flatbuffer" on every load. Hashing 2.6 GB takes several seconds, so each file's result
 * is kept in [cacheFile] by path, size and time, and only a new or changed file is hashed again.
 * Files that are not in the catalog cannot be checked and pass.
 */
class ModelCheck(
    private val cacheFile: File,
    private val catalog: List<ModelChoice> = ModelCatalog.all,
) {
    /** Null when [file] is fine or cannot be checked; otherwise what is wrong, in plain words. */
    @Synchronized
    fun problem(file: File): String? {
        val choice = catalogEntry(file) ?: return null
        val hash = cached(file) ?: sha256(file).also { remember(file, it) }
        return if (hash.equals(choice.sha256, ignoreCase = true)) null else DAMAGED
    }

    /** True when [file] was hashed before and did not match; never hashes. */
    @Synchronized
    fun knownDamaged(file: File): Boolean {
        val choice = catalogEntry(file) ?: return false
        val hash = cached(file) ?: return false
        return !hash.equals(choice.sha256, ignoreCase = true)
    }

    private fun catalogEntry(file: File): ModelChoice? =
        catalog.firstOrNull { ModelCatalog.findDownload(it, listOf(file.name to file.length())) != null }

    private fun key(file: File) = "${file.absolutePath}|${file.length()}|${file.lastModified()}"

    private fun entries(): Map<String, String> =
        runCatching { cacheFile.readLines() }.getOrDefault(emptyList())
            .mapNotNull { line -> line.substringBeforeLast(' ', "").takeIf { it.isNotEmpty() }?.let { it to line.substringAfterLast(' ') } }
            .toMap()

    private fun cached(file: File): String? = entries()[key(file)]

    private fun remember(file: File, hash: String) {
        val kept = entries().filterKeys { !it.startsWith("${file.absolutePath}|") } + (key(file) to hash)
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(kept.entries.joinToString("\n") { "${it.key} ${it.value}" })
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DAMAGED = "the download is damaged (its checksum does not match). Delete it from Downloads and download it again"
        private const val BUFFER_BYTES = 1 shl 20

        fun at(cacheDir: File) = ModelCheck(File(cacheDir, "model-checks.txt"))
    }
}
