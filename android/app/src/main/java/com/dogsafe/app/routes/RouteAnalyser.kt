package com.dogsafe.app.routes

import com.dogsafe.app.api.ApiClient
import com.dogsafe.app.model.Restriction
import java.util.Calendar

data class RouteAnalysis(
    val restrictions: List<Restriction>,
    val safetyStatus: String, // RED, AMBER, GREEN
    val intersectingCaseNumbers: List<String>
)

object RouteAnalyser {

    // Dog restriction type codes
    private val NO_DOG_TYPES   = setOf("02", "03", "04")
    private val LEAD_TYPES     = setOf("05", "09", "10")

    suspend fun analyse(route: GpxRoute): RouteAnalysis {
        if (route.points.isEmpty()) {
            return RouteAnalysis(emptyList(), "GREEN", emptyList())
        }

        // Add small padding to bounding box
        val pad = 0.01
        val geometry = "${route.minLon - pad},${route.latSouth(route) - pad}," +
                       "${route.maxLon + pad},${route.latNorth(route) + pad}"

        val where = "(TYPE='02' OR TYPE='03' OR TYPE='04' OR TYPE='05' OR TYPE='09' OR TYPE='10')" +
                    ApiClient.ACTIVE_FILTER

        return try {
            val json = ApiClient.api.getRestrictions(
                geometry = geometry,
                where    = where
            )
            val restrictions = ApiClient.parseRestrictions(json)

            // Find which restrictions the route actually passes through
            val intersecting = restrictions.filter { restriction ->
                routeIntersectsRestriction(route.points, restriction)
            }

            val status = calculateStatus(intersecting)
            val caseNumbers = intersecting.map { it.caseNumber }

            RouteAnalysis(intersecting, status, caseNumbers)
        } catch (e: Exception) {
            RouteAnalysis(emptyList(), "GREEN", emptyList())
        }
    }

    private fun routeIntersectsRestriction(
        points: List<GpxPoint>,
        restriction: Restriction
    ): Boolean {
        // Check if any route point falls inside any restriction ring
        restriction.rings.forEach { ring ->
            if (ring.size < 3) return@forEach
            points.forEach { point ->
                if (isPointInPolygon(point.lat, point.lon, ring)) return true
            }
        }
        return false
    }

    private fun isPointInPolygon(
        lat: Double, lon: Double,
        ring: List<Pair<Double, Double>>
    ): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (xi, yi) = ring[i] // xi=lon, yi=lat
            val (xj, yj) = ring[j]
            if ((yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun calculateStatus(restrictions: List<Restriction>): String {
        if (restrictions.isEmpty()) {
            // Check seasonal rule
            return if (isNestingBirdSeason()) "AMBER" else "GREEN"
        }
        return when {
            restrictions.any { it.type in NO_DOG_TYPES } -> "RED"
            restrictions.any { it.type in LEAD_TYPES }   -> "AMBER"
            else -> "GREEN"
        }
    }

    fun isNestingBirdSeason(): Boolean {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) // 0-indexed
        val day   = cal.get(Calendar.DAY_OF_MONTH)
        // 1 March to 31 July
        return when (month) {
            2 -> day >= 1   // March
            3, 4, 5 -> true // April, May, June
            6 -> day <= 31  // July
            else -> false
        }
    }

    private fun GpxRoute.latSouth(r: GpxRoute) = r.minLat
    private fun GpxRoute.latNorth(r: GpxRoute) = r.maxLat
}
