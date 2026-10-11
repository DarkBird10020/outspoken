package com.outspoken.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePackTest {

    @Test
    fun `a finished download is found by name and exact size`() {
        val files = listOf("gemma-4-E2B-it.litertlm" to 2_588_147_712L, VoicePack.ARCHIVE to VoicePack.SIZE_BYTES)
        assertEquals(VoicePack.ARCHIVE, VoicePack.findDownload(files))
    }

    @Test
    fun `the browser's second copy is found too`() {
        val name = "kokoro-multi-lang-v1_0 (1).tar.bz2"
        assertEquals(name, VoicePack.findDownload(listOf(name to VoicePack.SIZE_BYTES)))
    }

    @Test
    fun `an unfinished or wrong-sized download is not used`() {
        assertNull(VoicePack.findDownload(listOf(VoicePack.ARCHIVE to 1_000_000L)))
        assertNull(VoicePack.findDownload(listOf("${VoicePack.ARCHIVE}.crdownload" to VoicePack.SIZE_BYTES)))
    }

    @Test
    fun `archive entries unpack inside the voice folder only`() {
        assertEquals("model.onnx", VoicePack.entryPath("kokoro-multi-lang-v1_0/model.onnx"))
        assertEquals("espeak-ng-data/phontab", VoicePack.entryPath("./kokoro-multi-lang-v1_0/espeak-ng-data/phontab"))
        assertNull(VoicePack.entryPath("kokoro-multi-lang-v1_0/"))
        assertNull(VoicePack.entryPath("other/model.onnx"))
        assertNull(VoicePack.entryPath("kokoro-multi-lang-v1_0/../../evil.txt"))
    }
}
