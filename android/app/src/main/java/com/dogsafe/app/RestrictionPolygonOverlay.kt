package com.dogsafe.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import com.dogsafe.app.model.Restriction
import com.dogsafe.app.model.RestrictionType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class RestrictionPolygonOverlay(
    private val restriction: Restriction,
    private val onTap: (Restriction) -> Unit
) : Overlay() {

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        val baseColor = RestrictionType.fromCode(restriction.type).colorInt()
        color = Color.argb(
            if (restriction.isActive()) 80 else 40,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
    }

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = if (restriction.isActive()) 3f else 1.5f
        color = RestrictionType.fromCode(restriction.type).colorInt()
        if (!restriction.isActive()) {
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection

        restriction.rings.forEach { ring ->
            if (ring.isEmpty()) return@forEach
            val path = Path()
            ring.forEachIndexed { index, (lon, lat) ->
                val point = projection.toPixels(GeoPoint(lat, lon), null)
                if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                else path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
            path.close()
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val tapPoint   = projection.fromPixels(e.x.toInt(), e.y.toInt())

        // Check if tap is inside any ring
        restriction.rings.forEach { ring ->
            if (ring.size < 3) return@forEach
            if (isPointInPolygon(tapPoint.latitude, tapPoint.longitude, ring)) {
                onTap(restriction)
                return true
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
            val (xi, yi) = ring[i]  // xi=lon, yi=lat
            val (xj, yj) = ring[j]
            if ((yi > lat) != (yj > lat) &&
                lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}
