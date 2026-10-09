package com.outspoken.setup

/**
 * A Gemma model the app offers. The files are litert-community's LiteRT-LM builds on Hugging Face
 * (Apache 2.0, no sign-in). The app has no internet permission, so the phone's browser downloads
 * them and the app picks the finished file up from Downloads.
 */
data class ModelChoice(
    val title: String,
    val note: String,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    /** The file's SHA-256 as Hugging Face lists it (the LFS object id). */
    val sha256: String,
) {
    val url: String get() = "https://huggingface.co/litert-community/$repo/resolve/main/$fileName?download=true"
    val sizeGb: String get() = "%.1f GB".format(java.util.Locale.US, sizeBytes / 1_000_000_000.0)
}

object ModelCatalog {
    // Speeds from the model cards (S26 Ultra GPU, the same chip family as the iQOO 15): E2B decodes
    // about 52 tokens a second, E4B about 22.
    val E2B = ModelChoice(
        title = "Gemma 4 E2B",
        note = "fastest, about 1 s a reply",
        repo = "gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_588_147_712,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )
    val E4B = ModelChoice(
        title = "Gemma 4 E4B",
        note = "more accurate, about 2 to 3 s a reply",
        repo = "gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_659_530_240,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
    )
    val all = listOf(E2B, E4B)

    /**
     * The model to load at start: the one picked in the app if it is on the phone, otherwise E2B.
     * Null means neither; the caller then loads whatever model file is there. Loading the largest
     * file made E4B the default, and it misses the PRD's 2 s reply target (2 to 3 s) and shares
     * the GPU with the face tracker, which can slow the camera frames the blinks are read from.
     */
    fun atStart(chosen: ModelChoice?, isHere: (ModelChoice) -> Boolean): ModelChoice? =
        chosen?.takeIf(isHere) ?: E2B.takeIf(isHere)

    fun byFileName(name: String?): ModelChoice? = all.firstOrNull { it.fileName == name }

    /**
     * The name of a finished download of [choice] among [files] (name and size), or null. The
     * browser adds " (1)" to a repeated name and keeps an unfinished download under another name,
     * so a match needs the model's name, the .litertlm ending and the exact size.
     */
    fun findDownload(choice: ModelChoice, files: List<Pair<String, Long>>): String? = findDownloads(choice, files).firstOrNull()

    /** Every finished download of [choice] among [files], in the order given. */
    fun findDownloads(choice: ModelChoice, files: List<Pair<String, Long>>): List<String> {
        val stem = choice.fileName.removeSuffix(".litertlm")
        return files.filter { (name, size) ->
            name.startsWith(stem) && name.endsWith(".litertlm") && size == choice.sizeBytes
        }.map { it.first }
    }
}
