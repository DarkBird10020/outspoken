package com.outspoken.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelCatalogTest {

    private val e2b = ModelCatalog.E2B

    @Test
    fun `a finished download is found`() {
        val files = listOf("photo.jpg" to 10L, "gemma-4-E2B-it.litertlm" to e2b.sizeBytes)
        assertEquals("gemma-4-E2B-it.litertlm", ModelCatalog.findDownload(e2b, files))
    }

    @Test
    fun `a repeated download with a number added is found`() {
        val files = listOf("gemma-4-E2B-it (1).litertlm" to e2b.sizeBytes)
        assertEquals("gemma-4-E2B-it (1).litertlm", ModelCatalog.findDownload(e2b, files))
    }

    @Test
    fun `an unfinished download is not found`() {
        val files = listOf(
            "gemma-4-E2B-it.litertlm" to e2b.sizeBytes / 2,
            "gemma-4-E2B-it.litertlm.crdownload" to e2b.sizeBytes,
        )
        assertNull(ModelCatalog.findDownload(e2b, files))
    }

    @Test
    fun `the other model is not mistaken for this one`() {
        val files = listOf("gemma-4-E4B-it.litertlm" to ModelCatalog.E4B.sizeBytes)
        assertNull(ModelCatalog.findDownload(e2b, files))
    }

    @Test
    fun `download links point at the model files`() {
        assertEquals(
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true",
            ModelCatalog.E4B.url,
        )
    }

    @Test
    fun `at start the picked model wins when it is on the phone`() {
        assertEquals(ModelCatalog.E4B, ModelCatalog.atStart(ModelCatalog.E4B) { true })
    }

    @Test
    fun `with nothing picked E2B loads, not the larger E4B`() {
        assertEquals(ModelCatalog.E2B, ModelCatalog.atStart(null) { true })
    }

    @Test
    fun `a picked model that is gone falls back to E2B`() {
        assertEquals(ModelCatalog.E2B, ModelCatalog.atStart(ModelCatalog.E4B) { it == ModelCatalog.E2B })
    }

    @Test
    fun `with neither model on the phone there is no pick`() {
        assertNull(ModelCatalog.atStart(null) { false })
    }

    @Test
    fun `every finished copy is listed, so a damaged first copy can be skipped`() {
        val size = ModelCatalog.E2B.sizeBytes
        val files = listOf("gemma-4-E2B-it.litertlm" to size, "gemma-4-E2B-it (1).litertlm" to size, "gemma-4-E2B-it (2).litertlm" to 10L)
        assertEquals(listOf("gemma-4-E2B-it.litertlm", "gemma-4-E2B-it (1).litertlm"), ModelCatalog.findDownloads(ModelCatalog.E2B, files))
    }
}
