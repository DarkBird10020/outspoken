package com.outspoken.stats

import java.io.File

/**
 * The phone's own thermal sensors (`/sys/class/thermal/thermal_zone*`), read as plain files. On
 * the vivo I2501 all 101 can be read by the app (08:48 logs).
 */
object ThermalZones {

    internal const val ROOT = "/sys/class/thermal"

    /** One line for the log: each readable sensor's name and raw value. */
    fun describe(root: File = File(ROOT)): String {
        val zones = zones(root) ?: return "thermal sensors: the folder cannot be read"
        if (zones.isEmpty()) return "thermal sensors: none found"
        val readable = zones.mapNotNull { zone -> read(File(zone, "temp"))?.let { "${read(File(zone, "type")) ?: zone.name} $it" } }
        val values = if (readable.isEmpty()) "" else " (raw values): " + readable.joinToString(", ")
        return "thermal sensors: ${readable.size} of ${zones.size} readable$values"
    }

    /** The zone folders in number order, or null when the folder cannot be read. */
    internal fun zones(root: File): List<File>? =
        root.listFiles { file -> file.name.startsWith(ZONE) }
            ?.sortedBy { it.name.removePrefix(ZONE).toIntOrNull() ?: Int.MAX_VALUE }

    internal fun read(file: File): String? =
        try {
            file.readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

    private const val ZONE = "thermal_zone"
}

/**
 * One thermal sensor, found by its type, in °C. Values are thousandths of a degree: on the phone
 * the "battery" sensor read 34200 while Android gave the battery as 34.2 °C (08:48:22). The zone
 * is looked up once. Null when it is missing or unreadable, or reads outside 0 to 120 °C (unused
 * sensors read -273000 or -40960).
 */
class ThermalSensor(private val type: String, private val root: File = File(ThermalZones.ROOT)) {

    private val zone: File? by lazy {
        ThermalZones.zones(root)?.firstOrNull { ThermalZones.read(File(it, "type")) == type }
    }

    fun celsius(): Float? =
        zone?.let { ThermalZones.read(File(it, "temp")) }
            ?.toFloatOrNull()
            ?.div(1000f)
            ?.takeIf { it in 0f..120f }
}
