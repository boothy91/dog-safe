package com.dogsafe.app.routes

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogsafe.app.db.AppDatabase
import com.dogsafe.app.db.RouteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val list = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).routeDao().getAll()
            }
            _routes.postValue(list)
        }
    }

    fun importGpx(context: Context, uri: Uri, fileName: String) {
        viewModelScope.launch {
            _loading.postValue(true)
            _error.postValue(null)
            try {
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

                // Save GPX file to cache
                val gpxFile = File(context.cacheDir, "routes/$fileName")
                gpxFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    gpxFile.outputStream().use { output -> input.copyTo(output) }
                }

                // Analyse against restrictions
                val analysis = withContext(Dispatchers.IO) {
                    RouteAnalyser.analyse(route)
                }

                // Save to database
                val entity = RouteEntity(
                    name                   = route.name,
                    gpxFileName            = fileName,
                    distanceKm             = route.distanceKm,
                    pointCount             = route.points.size,
                    restrictionCount       = analysis.restrictions.size,
                    safetyStatus           = analysis.safetyStatus,
                    isVisible              = true,
                    minLat                 = route.minLat,
                    maxLat                 = route.maxLat,
                    minLon                 = route.minLon,
                    maxLon                 = route.maxLon,
                    restrictionCaseNumbers = analysis.intersectingCaseNumbers.joinToString(",")
                )

                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(context).routeDao().insert(entity)
                }

                val msg = "${route.name} imported — ${analysis.restrictions.size} restriction${if (analysis.restrictions.size != 1) "s" else ""} found"
                _importSuccess.postValue(msg)
                loadRoutes(context)

            } catch (e: Exception) {
                _error.postValue("Import failed: ${e.message}")
            }
            _loading.postValue(false)
        }
    }

    fun toggleVisibility(context: Context, route: RouteEntity, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(context).routeDao().setVisible(route.id, !route.isVisible)
            }
            loadRoutes(context)
            onDone?.invoke()
        }
    }

    fun deleteRoute(context: Context, route: RouteEntity, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val gpxFile = File(context.cacheDir, "routes/${route.gpxFileName}")
                if (gpxFile.exists()) gpxFile.delete()
                AppDatabase.getInstance(context).routeDao().delete(route)
            }
            loadRoutes(context)
            onDone?.invoke()
        }
    }
}
