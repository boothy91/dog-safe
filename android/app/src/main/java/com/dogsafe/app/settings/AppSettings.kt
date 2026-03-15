package com.dogsafe.app.settings

import android.content.Context
import android.content.SharedPreferences

object AppSettings {

    private const val PREFS_NAME = "dogsafe_settings"

    // Keys
    private const val KEY_LAST_LAT         = "last_lat"
    private const val KEY_LAST_LON         = "last_lon"
    private const val KEY_LAST_ZOOM        = "last_zoom"
    private const val KEY_REMEMBER_POSITION = "remember_position"
    private const val KEY_ACTIVE_ONLY      = "active_only"
    private const val KEY_SHOW_WALES       = "show_wales"
    private const val KEY_SEASON_BANNER    = "season_banner"
    private const val KEY_DISTANCE_UNITS   = "distance_units"
    private const val KEY_AUTO_ANALYSE     = "auto_analyse"
    private const val KEY_MAP_STYLE        = "map_style"

    // Defaults
    const val DEFAULT_LAT  = 54.1
    const val DEFAULT_LON  = -2.1
    const val DEFAULT_ZOOM = 11.0

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Last position
    var rememberPosition: Boolean
        get() = _rememberPosition
        set(value) { _rememberPosition = value }
    private var _rememberPosition = true

    fun saveLastPosition(context: Context, lat: Double, lon: Double, zoom: Double) {
        if (!getRememberPosition(context)) return
        prefs(context).edit()
            .putFloat(KEY_LAST_LAT, lat.toFloat())
            .putFloat(KEY_LAST_LON, lon.toFloat())
            .putFloat(KEY_LAST_ZOOM, zoom.toFloat())
            .apply()
    }

    fun getLastLat(context: Context)  = prefs(context).getFloat(KEY_LAST_LAT,  DEFAULT_LAT.toFloat()).toDouble()
    fun getLastLon(context: Context)  = prefs(context).getFloat(KEY_LAST_LON,  DEFAULT_LON.toFloat()).toDouble()
    fun getLastZoom(context: Context) = prefs(context).getFloat(KEY_LAST_ZOOM, DEFAULT_ZOOM.toFloat()).toDouble()

    fun getRememberPosition(context: Context) = prefs(context).getBoolean(KEY_REMEMBER_POSITION, true)
    fun setRememberPosition(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_REMEMBER_POSITION, value).apply()

    // Restrictions
    fun getActiveOnly(context: Context)   = prefs(context).getBoolean(KEY_ACTIVE_ONLY, true)
    fun setActiveOnly(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_ACTIVE_ONLY, value).apply()

    fun getShowWales(context: Context)    = prefs(context).getBoolean(KEY_SHOW_WALES, true)
    fun setShowWales(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SHOW_WALES, value).apply()

    fun getSeasonBanner(context: Context) = prefs(context).getBoolean(KEY_SEASON_BANNER, true)
    fun setSeasonBanner(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SEASON_BANNER, value).apply()

    // Routes
    fun getAutoAnalyse(context: Context)  = prefs(context).getBoolean(KEY_AUTO_ANALYSE, true)
    fun setAutoAnalyse(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO_ANALYSE, value).apply()

    fun getDistanceUnits(context: Context) = prefs(context).getString(KEY_DISTANCE_UNITS, "km") ?: "km"
    fun setDistanceUnits(context: Context, value: String) = prefs(context).edit().putString(KEY_DISTANCE_UNITS, value).apply()

    // Map style
    fun getMapStyle(context: Context) = prefs(context).getString(KEY_MAP_STYLE, "standard") ?: "standard"
    fun setMapStyle(context: Context, value: String) = prefs(context).edit().putString(KEY_MAP_STYLE, value).apply()

    // Format distance based on units setting
    fun formatDistance(context: Context, km: Double): String {
        return if (getDistanceUnits(context) == "miles") {
            String.format("%.1f mi", km * 0.621371)
        } else {
            String.format("%.1f km", km)
        }
    }
}
