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
}
