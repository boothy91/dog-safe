package com.dogsafe.app.routes

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.*

data class GpxRoute(
    val name: String,
    val points: List<GpxPoint>,
    val distanceKm: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

data class GpxPoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val time: String? = null
)

object GpxParser {

    fun parse(input: InputStream, fallbackName: String = "Imported Route"): GpxRoute {
        val points = mutableListOf<GpxPoint>()
        var routeName = fallbackName

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var eventType = parser.eventType
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null
        var currentTime: String? = null
        var inTrkpt = false
        var inRtept = false
        var inWpt  = false
        var inName = false
        var inEle  = false
        var inTime = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "trkpt", "rtept", "wpt" -> {
                            inTrkpt = parser.name.lowercase() == "trkpt"
                            inRtept = parser.name.lowercase() == "rtept"
                            inWpt   = parser.name.lowercase() == "wpt"
                            currentLat  = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon  = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle  = null
                            currentTime = null
                        }
                        "name" -> inName = true
                        "ele"  -> inEle  = true
                        "time" -> inTime = true
                    }
                }
                XmlPullParser.TEXT -> {
                    when {
                        inName && routeName == fallbackName -> {
                            val text = parser.text.trim()
                            if (text.isNotEmpty()) routeName = text
                        }
                        inEle  -> currentEle  = parser.text.trim().toDoubleOrNull()
                        inTime -> currentTime = parser.text.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name.lowercase()) {
                        "trkpt", "rtept", "wpt" -> {
                            if ((inTrkpt || inRtept || inWpt) &&
                                currentLat != null && currentLon != null) {
                                points.add(GpxPoint(currentLat!!, currentLon!!, currentEle, currentTime))
                            }
                            inTrkpt = false; inRtept = false; inWpt = false
                        }
                        "name" -> inName = false
                        "ele"  -> inEle  = false
                        "time" -> inTime = false
                    }
                }
            }
            eventType = parser.next()
        }

        // Calculate bounding box
        val minLat = points.minOfOrNull { it.lat } ?: 0.0
        val maxLat = points.maxOfOrNull { it.lat } ?: 0.0
        val minLon = points.minOfOrNull { it.lon } ?: 0.0
        val maxLon = points.maxOfOrNull { it.lon } ?: 0.0

        // Calculate distance
        val distanceKm = calculateDistance(points)

        return GpxRoute(
            name        = routeName,
            points      = points,
            distanceKm  = distanceKm,
            minLat      = minLat,
            maxLat      = maxLat,
            minLon      = minLon,
            maxLon      = maxLon
        )
    }

    private fun calculateDistance(points: List<GpxPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversine(
                points[i-1].lat, points[i-1].lon,
                points[i].lat,   points[i].lon
            )
        }
        return total
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        return r * 2 * asin(sqrt(a))
    }
}
