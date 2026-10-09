package com.outspoken.log

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class LogExportTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `all run logs go out oldest first under headers`() {
        folder.newFile("outspoken-20261009-120000.log").writeText("second run\n")
        folder.newFile("outspoken-20261009-110000.log").writeText("first run\n")
        folder.newFile("notes.txt").writeText("not a log\n")
        val out = ByteArrayOutputStream()
        assertEquals(2, exportLogs(folder.root, out))
        assertEquals(
            "===== outspoken-20261009-110000.log =====\nfirst run\n\n" +
                "===== outspoken-20261009-120000.log =====\nsecond run\n\n",
            out.toString(),
        )
    }

    @Test
    fun `no logs writes nothing`() {
        val out = ByteArrayOutputStream()
        assertEquals(0, exportLogs(folder.root, out))
        assertEquals("", out.toString())
    }
}
