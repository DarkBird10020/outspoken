package com.outspoken.log

import com.outspoken.eye.EyeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.text.SimpleDateFormat
import java.util.Locale

class SessionLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun date(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(text)!!

    @Test
    fun `writes samples and events as CSV`() {
        val log = SessionLog(folder.root, date("2026-10-09 10:00:00"))
        log.sample(EyeSample(1_000, true, 0.912f, 0.88f, yawDeg = 3.04f, pitchDeg = -1f))
        log.sample(EyeSample(1_033, false))
        log.event(1_040, "say \"I need water\", now")
        log.close()
        val lines = log.file.readLines()
        assertEquals("session-20261009-100000.csv", log.file.name)
        assertEquals("time_ms,kind,face,left,right,yaw,pitch,text", lines[0])
        assertEquals("1000,eye,1,0.912,0.880,3.0,-1.0,", lines[1])
        assertEquals("1033,eye,0,,,0.0,0.0,", lines[2])
        assertEquals("1040,event,,,,,,\"say \"\"I need water\"\", now\"", lines[3])
    }

    @Test
    fun `keeps only the newest files`() {
        (1..7).forEach { SessionLog(folder.root, date("2026-10-0$it 10:00:00"), keep = 3).close() }
        val names = folder.root.list()!!.sorted()
        assertEquals(listOf("session-20261005-100000.csv", "session-20261006-100000.csv", "session-20261007-100000.csv"), names)
    }

    @Test
    fun `writes after close are dropped`() {
        val log = SessionLog(folder.root)
        log.close()
        log.event(1, "late")
        assertTrue(log.file.readLines().size == 1)
    }
}
