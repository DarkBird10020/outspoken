package com.outspoken.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `missing folder has no model`() {
        assertNull(findModelFile(null))
    }

    @Test
    fun `other file types are ignored`() {
        folder.newFile("gemma.task").writeText("x")
        folder.newFile("notes.txt").writeText("x")
        assertNull(findModelFile(folder.root))
    }

    @Test
    fun `largest model file wins`() {
        folder.newFile("small.litertlm").writeBytes(ByteArray(10))
        folder.newFile("large.litertlm").writeBytes(ByteArray(100))
        assertEquals("large.litertlm", findModelFile(folder.root)?.name)
    }
}
