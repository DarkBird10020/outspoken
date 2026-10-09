package com.outspoken.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file(name: String, bytes: Int) = folder.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun `the largest model file is picked`() {
        file("gemma3-1b.litertlm", 10)
        val big = file("gemma-4-E2B-it.litertlm", 20)
        assertEquals(big, findModelFile(folder.root))
    }

    @Test
    fun `unfinished copies and other files are skipped`() {
        file("gemma-4-E4B-it.litertlm.part", 50)
        file("notes.txt", 60)
        val model = file("gemma3-1b.litertlm", 10)
        assertEquals(model, findModelFile(folder.root))
    }

    @Test
    fun `no model file and no folder give nothing`() {
        file("notes.txt", 5)
        assertNull(findModelFile(folder.root))
        assertNull(findModelFile(null))
    }
}
