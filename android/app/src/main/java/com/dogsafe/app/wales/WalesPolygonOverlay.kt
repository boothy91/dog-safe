package com.dogsafe.app.wales

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class WalesPolygonOverlay(
    private val land: WalesAccessLand,
    private val onTap: (WalesAccessLand) -> Unit
) : Overlay() {

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.argb(60, 34, 139, 34) // semi-transparent green
    }

    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeWidth = 2f
        color = Color.argb(180, 34, 139, 34) // green border
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection

        land.rings.forEach { ring ->
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

        land.rings.forEach { ring ->
            if (ring.size < 3) return@forEach
            if (isPointInPolygon(tapPoint.latitude, tapPoint.longitude, ring)) {
                onTap(land)
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
            val (xi, yi) = ring[i]
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
