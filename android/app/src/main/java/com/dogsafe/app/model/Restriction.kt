package com.dogsafe.app.model

import android.content.Context
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Restriction(
    val objectId: Int,
    val caseNumber: String,
    val type: String,
    val purpose: String?,
    val startDate: Long?,
    val endDate: Long?,
    val viewMap: String?,
    val rings: List<List<Pair<Double, Double>>> // lon, lat pairs
) {
    fun isActive(): Boolean {
        val now = System.currentTimeMillis()
        return (startDate ?: 0) <= now && (endDate ?: 0) >= now
    }

    fun pdfUrl(): String? {
        val match = Regex("""href="([^"]+)"""").find(viewMap ?: "") ?: return null
        return match.groupValues[1]
    }

    fun formattedStartDate(): String = formatDate(startDate)
    fun formattedEndDate(): String   = formatDate(endDate)

    private fun formatDate(ts: Long?): String {
        if (ts == null) return "Unknown"
        return SimpleDateFormat("d MMM yyyy", Locale.UK).format(Date(ts))
    }

    fun purposeLabel(): String = when (purpose) {
        "16" -> "🪶 Grouse disturbance"
        "17" -> "🐑 Lambing"
        "14" -> "🦎 Sensitive wildlife"
        "03" -> "🐄 Livestock disturbance"
        "05" -> "🔥 Fire prevention"
        "01" -> "⚠️ Public safety"
        "07" -> "🌿 Land management"
        else -> "Restriction"
    }
}

enum class RestrictionType(val code: String, val label: String, private val hex: String) {
    NO_DOGS              ("02", "No Dogs",                             "#ef4444"),
    NO_DOGS_ASSISTANCE   ("04", "No Dogs (except assistance dogs)",    "#f97316"),
    NO_DOGS_GUIDE        ("03", "No Dogs (except guide/hearing dogs)", "#f97316"),
    DOGS_ON_LEADS        ("05", "Dogs on Leads",                       "#eab308"),
    DOGS_FENCED_ROUTES   ("09", "Dogs on Fenced Routes Only",          "#eab308"),
    MARKED_ROUTES_LEADS  ("10", "Marked Routes, Dogs on Leads",        "#eab308"),
    UNKNOWN              ("99", "Restriction",                         "#888888");

    fun color(context: Context): Int = Color.parseColor(hex)
    fun colorInt(): Int = Color.parseColor(hex)

    companion object {
        fun fromCode(code: String) = values().find { it.code == code } ?: UNKNOWN
    }
}
