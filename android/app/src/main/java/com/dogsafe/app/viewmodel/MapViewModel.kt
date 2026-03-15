package com.dogsafe.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.dogsafe.app.api.ApiClient
import com.dogsafe.app.model.Restriction
import com.dogsafe.app.settings.AppSettings
import com.dogsafe.app.wales.WalesAccessLand
import com.dogsafe.app.wales.WalesApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>().applicationContext

    private val _restrictions = MutableLiveData<List<Restriction>>()
    val restrictions: LiveData<List<Restriction>> = _restrictions

    private val _walesLand = MutableLiveData<List<WalesAccessLand>>()
    val walesLand: LiveData<List<WalesAccessLand>> = _walesLand

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _countryInfo = MutableLiveData<String?>()
    val countryInfo: LiveData<String?> = _countryInfo

    private var fetchJob: Job? = null

    fun onMapMoved(bounds: BoundingBox) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(500)

            if (bounds.diagonalLengthInMeters > 500_000) {
                _error.value = "Zoom in to see restrictions"
                _restrictions.value = emptyList()
                _walesLand.value = emptyList()
                return@launch
            }

            val inEngland = WalesApiClient.isInEngland(
                bounds.latSouth, bounds.latNorth, bounds.lonWest, bounds.lonEast)
            val inWales   = WalesApiClient.isInWales(
                bounds.latSouth, bounds.latNorth, bounds.lonWest, bounds.lonEast)
            val showWales = AppSettings.getShowWales(ctx)

            if (inEngland) fetchEnglandRestrictions(bounds)
            else _restrictions.value = emptyList()

            if (inWales && showWales) fetchWalesLand(bounds)
            else _walesLand.value = emptyList()

            _countryInfo.value = when {
                inWales && !inEngland && showWales -> "Wales: Access land shown. No restriction data available."
                inWales && inEngland  -> null
                !inEngland && !inWales -> null
                else -> null
            }
        }
    }

    private suspend fun fetchEnglandRestrictions(bounds: BoundingBox) {
        _loading.value = true
        _error.value   = null
        try {
            val activeOnly = AppSettings.getActiveOnly(ctx)
            val geometry   = "${bounds.lonWest},${bounds.latSouth},${bounds.lonEast},${bounds.latNorth}"
            val where      = ApiClient.DOG_RESTRICTION_TYPES +
                             if (activeOnly) ApiClient.ACTIVE_FILTER else ""

            val json         = ApiClient.api.getRestrictions(geometry = geometry, where = where)
            val restrictions = ApiClient.parseRestrictions(json)
            _restrictions.value = restrictions

            if (json.get("exceededTransferLimit")?.asBoolean == true) {
                _error.value = "Too many results — zoom in for complete data"
            }
        } catch (e: Exception) {
            _error.value = "Could not load data: ${e.message}"
        }
        _loading.value = false
    }

    private suspend fun fetchWalesLand(bounds: BoundingBox) {
        try {
            val land = WalesApiClient.getAccessLand(
                bounds.lonWest, bounds.latSouth,
                bounds.lonEast, bounds.latNorth
            )
            _walesLand.value = land
        } catch (e: Exception) { }
    }
}
