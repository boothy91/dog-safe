package com.dogsafe.app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogsafe.app.api.ApiClient
import com.dogsafe.app.model.Restriction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox

class MapViewModel : ViewModel() {

    private val _restrictions = MutableLiveData<List<Restriction>>()
    val restrictions: LiveData<List<Restriction>> = _restrictions

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var activeOnly = true
    private var fetchJob: Job? = null
    private var lastBounds: BoundingBox? = null

    fun onMapMoved(bounds: BoundingBox) {
        // Debounce — wait 500ms after map stops moving
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(500)
            if (bounds.diagonalLengthInMeters > 500_000) {
                // Too zoomed out — don't query
                _error.value = "Zoom in to see restrictions"
                _restrictions.value = emptyList()
                return@launch
            }
            fetchRestrictions(bounds)
        }
    }

    private suspend fun fetchRestrictions(bounds: BoundingBox) {
        _loading.value = true
        _error.value = null

        try {
            val geometry = "${bounds.lonWest},${bounds.latSouth},${bounds.lonEast},${bounds.latNorth}"
            val where = ApiClient.DOG_RESTRICTION_TYPES +
                    if (activeOnly) ApiClient.ACTIVE_FILTER else ""

            val json = ApiClient.api.getRestrictions(
                geometry = geometry,
                where    = where
            )

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
}
