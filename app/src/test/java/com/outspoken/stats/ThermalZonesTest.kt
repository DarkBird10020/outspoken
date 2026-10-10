package com.outspoken.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ThermalZonesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun zone(root: File, name: String, type: String?, temp: String?) {
        val dir = File(root, name).apply { mkdirs() }
        type?.let { File(dir, "type").writeText("$it\n") }
        temp?.let { File(dir, "temp").writeText("$it\n") }
    }

    @Test
    fun `readable sensors are listed in zone order with their raw values`() {
        val root = folder.newFolder("thermal")
        zone(root, "thermal_zone10", "skin-therm", "33500")
        zone(root, "thermal_zone2", "cpu-0-0", "41200")
        zone(root, "thermal_zone3", "gpu", temp = null)
        File(root, "cooling_device0").mkdirs()
        assertEquals(
            "thermal sensors: 2 of 3 readable (raw values): cpu-0-0 41200, skin-therm 33500",
            ThermalZones.describe(root),
        )
    }

    @Test
    fun `a zone without a type uses its folder name`() {
        val root = folder.newFolder("thermal")
        zone(root, "thermal_zone0", type = null, temp = "30000")
        assertEquals("thermal sensors: 1 of 1 readable (raw values): thermal_zone0 30000", ThermalZones.describe(root))
    }

    @Test
    fun `a sensor is found by its type and read in degrees`() {
        val root = folder.newFolder("thermal")
        zone(root, "thermal_zone0", "battery", "34200")
        zone(root, "thermal_zone94", "tz_shell", "36319")
        assertEquals(36.319f, ThermalSensor("tz_shell", root).celsius()!!, 0.001f)
    }

    @Test
    fun `a missing, unreadable or unused sensor gives nothing`() {
        val root = folder.newFolder("thermal")
        zone(root, "thermal_zone0", "sdr0", "-273000")
        zone(root, "thermal_zone1", "gpu", temp = null)
        assertNull(ThermalSensor("tz_shell", root).celsius())
        assertNull(ThermalSensor("sdr0", root).celsius())
        assertNull(ThermalSensor("gpu", root).celsius())
        assertNull(ThermalSensor("tz_shell", File(root, "missing")).celsius())
    }

    @Test
    fun `nothing readable says so`() {
        val root = folder.newFolder("thermal")
        zone(root, "thermal_zone0", "cpu", temp = null)
        assertEquals("thermal sensors: 0 of 1 readable", ThermalZones.describe(root))
        assertEquals("thermal sensors: none found", ThermalZones.describe(folder.newFolder("empty")))
        assertEquals("thermal sensors: the folder cannot be read", ThermalZones.describe(File(root, "missing")))
    }
}
