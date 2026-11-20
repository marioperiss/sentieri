package com.mario.sentieri

import java.io.InputStream
import java.io.OutputStream

object Gpx {
    fun exportGpx(points: List<TrackPoint>, name: String, out: OutputStream) {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Sentieri App\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk>\n    <name>${escapeXml(name)}</name>\n    <trkseg>\n")
        points.forEach { p ->
            sb.append("      <trkpt lat=\"${p.lat}\" lon=\"${p.lng}\"><ele>${p.altitude}</ele></trkpt>\n")
        }
        sb.append("    </trkseg>\n  </trk>\n</gpx>")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    fun importGpx(input: InputStream): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val text = input.bufferedReader().use { it.readText() }
        val regex = Regex("<trkpt\\s+lat=\"([^\"]+)\"\\s+lon=\"([^\"]+)\">(?:\\s*<ele>([^<]+)</ele>)?", RegexOption.IGNORE_CASE)
        regex.findAll(text).forEach { match ->
            val lat = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val lng = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val alt = match.groupValues[3].toDoubleOrNull() ?: 0.0
            if (lat != 0.0 && lng != 0.0) {
                points.add(TrackPoint(lat, lng, alt))
            }
        }
        return points
    }

    private fun escapeXml(s: String): String {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }
}
