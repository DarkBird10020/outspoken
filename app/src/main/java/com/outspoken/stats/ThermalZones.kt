package com.outspoken.stats

import java.io.File

/**
 * The phone's own thermal sensors (`/sys/class/thermal/thermal_zone*`), read as plain files.
 * The battery temperature the stats screen shows changes only about once a minute (phone logs,
 * 07:31 to 07:36), and Android may or may not let an app read these faster sensors. This writes
 * down which ones it can, with their raw values, so one is only shown once the logs prove it works.
 */
object ThermalZones {

    fun describe(root: File = File("/sys/class/thermal")): String {
        val zones = root.listFiles { file -> file.name.startsWith(ZONE) }
            ?: return "thermal sensors: the folder cannot be read"
        if (zones.isEmpty()) return "thermal sensors: none found"
        val readable = zones
            .sortedBy { it.name.removePrefix(ZONE).toIntOrNull() ?: Int.MAX_VALUE }
            .mapNotNull { zone -> read(File(zone, "temp"))?.let { "${read(File(zone, "type")) ?: zone.name} $it" } }
        val values = if (readable.isEmpty()) "" else " (raw values): " + readable.joinToString(", ")
        return "thermal sensors: ${readable.size} of ${zones.size} readable$values"
    }

    private fun read(file: File): String? =
        try {
            file.readText().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

    private const val ZONE = "thermal_zone"
}
