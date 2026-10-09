package com.outspoken.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelCheckTest {

    @get:Rule
    val folder = TemporaryFolder()

    // SHA-256 of the five bytes "hello".
    private val model = ModelChoice(
        title = "Test",
        note = "",
        repo = "test",
        fileName = "test-model.litertlm",
        sizeBytes = 5,
        sha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
    )
    private val check get() = ModelCheck(folder.root.resolve("checks.txt"), listOf(model))

    private fun download(name: String, text: String) = folder.newFile(name).apply { writeText(text) }

    @Test
    fun `a download with the published checksum passes`() {
        assertNull(check.problem(download("test-model.litertlm", "hello")))
    }

    @Test
    fun `a download of the right size with other bytes is damaged`() {
        val file = download("test-model.litertlm", "jello")
        assertFalse(check.knownDamaged(file))
        assertEquals(ModelCheck.DAMAGED, check.problem(file))
        assertTrue(check.knownDamaged(file))
    }

    @Test
    fun `the result is remembered across checks`() {
        val file = download("test-model.litertlm", "jello")
        check.problem(file)
        assertTrue(ModelCheck(folder.root.resolve("checks.txt"), listOf(model)).knownDamaged(file))
    }

    @Test
    fun `a model that is not in the catalog cannot be checked and passes`() {
        val file = download("gemma3-1b.litertlm", "anything")
        assertNull(check.problem(file))
        assertFalse(check.knownDamaged(file))
    }
}
