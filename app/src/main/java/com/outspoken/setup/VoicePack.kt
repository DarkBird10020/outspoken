package com.outspoken.setup

/**
 * The natural voice's files: k2-fsa's sherpa-onnx build of Kokoro v1.0 (Apache 2.0), full
 * precision, all 53 voices. Like Gemma, the phone's browser downloads the archive into Downloads
 * (the app has no internet permission); the app then unpacks it into a folder of its own.
 */
object VoicePack {
    const val TITLE = "Kokoro natural voice"
    const val NOTE = "more human speech for the cards, English"
    const val ARCHIVE = "kokoro-multi-lang-v1_0.tar.bz2"
    const val FOLDER = "kokoro-multi-lang-v1_0"
    const val SIZE_BYTES = 349_906_910L
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$ARCHIVE"

    /** The files the voice needs, inside [FOLDER]; when any is missing the folder is unpacked again. */
    val NEEDED = listOf("model.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "espeak-ng-data/phontab")

    val sizeMb: String get() = "${SIZE_BYTES / 1_000_000} MB"

    /**
     * The finished download among [files] (name and size), or null. The browser adds " (1)" to a
     * repeated name and keeps an unfinished download under another name, so a match needs the
     * archive's name, its ending and the exact size.
     */
    fun findDownload(files: List<Pair<String, Long>>): String? {
        val stem = ARCHIVE.removeSuffix(".tar.bz2")
        return files.firstOrNull { (name, size) -> name.startsWith(stem) && name.endsWith(".tar.bz2") && size == SIZE_BYTES }?.first
    }

    /**
     * Where an archive entry goes inside the unpack folder, or null for an entry to skip: one
     * outside [FOLDER], or one that tries to climb out of it.
     */
    fun entryPath(entryName: String): String? {
        val name = entryName.removePrefix("./")
        if (!name.startsWith("$FOLDER/")) return null
        val inside = name.removePrefix("$FOLDER/")
        if (inside.isEmpty() || inside.split('/').any { it == ".." }) return null
        return inside
    }
}
