package com.dogsafe.app.routes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogsafe.app.db.AppDatabase
import com.dogsafe.app.db.RouteEntity
import kotlinx.coroutines.launch
import java.io.File

class RoutesViewModel : ViewModel() {

    private val _routes = MutableLiveData<List<RouteEntity>>()
    val routes: LiveData<List<RouteEntity>> = _routes

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _importSuccess = MutableLiveData<String?>()
    val importSuccess: LiveData<String?> = _importSuccess

    fun loadRoutes(context: Context) {
        viewModelScope.launch {
            _routes.postValue(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppDatabase.getInstance(context).routeDao().getAll() })
        }
    }

    fun importGpx(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _loading.postValue(true)
            _error.postValue(null)

            try {
                // Parse GPX
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Could not open file")

                val name = fileName.removeSuffix(".gpx").removeSuffix(".GPX")
                val route = GpxParser.parse(stream, name)
                stream.close()

                if (route.points.isEmpty()) {
                    _error.postValue("No route points found in GPX file")
                    _loading.postValue(false)
                    return@launch
                }

                // Save GPX file to app cache
                val gpxFile = File(context.cacheDir, "routes/$fileName")
                gpxFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    gpxFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Analyse against restrictions
                val analysis = RouteAnalyser.analyse(route)

                // Save to database
                val entity = RouteEntity(
                    name                  = route.name,
                    gpxFileName           = fileName,
                    distanceKm            = route.distanceKm,
                    pointCount            = route.points.size,
                    restrictionCount      = analysis.restrictions.size,
                    safetyStatus          = analysis.safetyStatus,
                    isVisible             = true,
                    minLat                = route.minLat,
                    maxLat                = route.maxLat,
                    minLon                = route.minLon,
                    maxLon                = route.maxLon,
                    restrictionCaseNumbers = analysis.intersectingCaseNumbers.joinToString(",")
                )

                AppDatabase.getInstance(context).routeDao().insert(entity)
                _importSuccess.postValue("${route.name} imported — ${analysis.restrictions.size} restriction${if (analysis.restrictions.size != 1) "s" else ""} found")
                loadRoutes(context))

            } catch (e: Exception) {
                _error.postValue("Import failed: ${e.message}")
            }

            _loading.postValue(false)
        }
    }

    fun toggleVisibility(context: Context, route: RouteEntity, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            AppDatabase.getInstance(context).routeDao().setVisible(route.id, !route.isVisible)
            loadRoutes(context)
            onDone?.invoke()
        }
    }

    fun deleteRoute(context: Context, route: RouteEntity) {
        viewModelScope.launch {
            // Delete cached GPX file
            val gpxFile = File(context.cacheDir, "routes/${route.gpxFileName}")
            if (gpxFile.exists()) gpxFile.delete()

            AppDatabase.getInstance(context).routeDao().delete(route)
            loadRoutes(context)
        }
    }

    fun loadGpxPoints(context: Context, route: RouteEntity): List<GpxPoint> {
        return try {
            val gpxFile = File(context.cacheDir, "routes/${route.gpxFileName}")
            if (!gpxFile.exists()) return emptyList()
            GpxParser.parse(gpxFile.inputStream(), route.name).points
        } catch (e: Exception) {
            emptyList()
        }
    }
}
