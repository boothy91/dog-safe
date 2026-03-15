package com.dogsafe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dogsafe.app.db.AppDatabase
import com.dogsafe.app.db.RouteEntity
import com.dogsafe.app.model.Restriction
import com.dogsafe.app.model.RestrictionType
import com.dogsafe.app.routes.GpxParser
import com.dogsafe.app.search.GeocodingClient
import com.dogsafe.app.search.SearchResult
import com.dogsafe.app.settings.AppSettings
import com.dogsafe.app.viewmodel.MapViewModel
import com.dogsafe.app.wales.WalesAccessLand
import com.dogsafe.app.wales.WalesPolygonOverlay
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

class MapFragment : Fragment() {

    private lateinit var viewModel: MapViewModel
    private lateinit var mapView: MapView
    private lateinit var bottomSheet: BottomSheetBehavior<View>
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var legend: View
    private lateinit var locationFab: FloatingActionButton
    private lateinit var searchInput: TextInputEditText
    private lateinit var searchResultsList: ListView
    private lateinit var areaChipGroup: ChipGroup

    private var locationOverlay: MyLocationNewOverlay? = null
    private var searchResults = mutableListOf<SearchResult>()

    // Track drawn polylines by route id
    private val routePolylines = mutableMapOf<Int, Polyline>()

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) enableLocation()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Configuration.getInstance().apply {
            load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
            userAgentValue = "DogSafe/1.0 (com.dogsafe.app)"
        }

        viewModel         = ViewModelProvider(requireActivity())[MapViewModel::class.java]
        statusText        = view.findViewById(R.id.statusText)
        progressBar       = view.findViewById(R.id.progressBar)
        legend            = view.findViewById(R.id.legend)
        locationFab       = view.findViewById(R.id.locationFab)
        searchInput       = view.findViewById(R.id.searchInput)
        searchResultsList = view.findViewById(R.id.searchResults)
        areaChipGroup     = view.findViewById(R.id.areaChipGroup)

        setupMap(view)
        setupBottomSheet(view)
        setupAreaButtons()
        setupSearch()
        setupLocationFab()
        setupObservers()
        requestLocation()

        // Load all visible routes on start
        refreshRoutes()
    }

    private fun setupMap(view: View) {
        mapView = view.findViewById(R.id.map)
        mapView.setMultiTouchControls(true)
        applyMapStyle()

        val ctx  = requireContext()
        val lat  = AppSettings.getLastLat(ctx)
        val lon  = AppSettings.getLastLon(ctx)
        val zoom = AppSettings.getLastZoom(ctx)
        mapView.controller.setCenter(GeoPoint(lat, lon))
        mapView.controller.setZoom(zoom)

        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean {
                savePosition(); viewModel.onMapMoved(mapView.boundingBox); return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
                savePosition(); viewModel.onMapMoved(mapView.boundingBox); return false
            }
        })
    }

    private fun applyMapStyle() {
        val style = AppSettings.getMapStyle(requireContext())
        mapView.setTileSource(when (style) {
            "topo"      -> TileSourceFactory.OpenTopo
            "satellite" -> org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Satellite", 0, 19, 256, ".jpg",
                arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
                "© Esri"
            )
            else -> TileSourceFactory.MAPNIK
        })
    }

    fun refreshSettings() {
        if (::mapView.isInitialized) {
            applyMapStyle()
            viewModel.onMapMoved(mapView.boundingBox)
        }
    }

    // Called by RoutesFragment when visibility changes
    fun refreshRoutes() {
        lifecycleScope.launch {
            val routes = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).routeDao().getAll()
            }

            // Remove all existing route polylines
            routePolylines.values.forEach { mapView.overlays.remove(it) }
            routePolylines.clear()

            // Draw only visible routes
            routes.filter { it.isVisible }.forEach { route ->
                drawRoute(route)
            }
            mapView.invalidate()
        }
    }

    private suspend fun drawRoute(route: RouteEntity) {
        val points = withContext(Dispatchers.IO) {
            try {
                val gpxFile = File(requireContext().cacheDir, "routes/${route.gpxFileName}")
                if (!gpxFile.exists()) return@withContext emptyList()
                GpxParser.parse(gpxFile.inputStream(), route.name).points
            } catch (e: Exception) { emptyList() }
        }
        if (points.isEmpty()) return

        val polyline = Polyline().apply {
            title = "route_${route.id}"
            setPoints(points.map { GeoPoint(it.lat, it.lon) })
            outlinePaint.color = when (route.safetyStatus) {
                "RED"   -> android.graphics.Color.RED
                "AMBER" -> android.graphics.Color.parseColor("#f59e0b")
                else    -> android.graphics.Color.parseColor("#22c55e")
            }
            outlinePaint.strokeWidth = 6f
        }
        routePolylines[route.id] = polyline
        mapView.overlays.add(polyline)
    }

    fun showRouteOnMap(route: RouteEntity) {
        lifecycleScope.launch {
            // Make sure route is drawn
            if (!routePolylines.containsKey(route.id)) {
                drawRoute(route)
                mapView.invalidate()
            }

            // Zoom to route
            val points = withContext(Dispatchers.IO) {
                try {
                    val gpxFile = File(requireContext().cacheDir, "routes/${route.gpxFileName}")
                    if (!gpxFile.exists()) return@withContext emptyList()
                    GpxParser.parse(gpxFile.inputStream(), route.name).points
                } catch (e: Exception) { emptyList() }
            }
            if (points.isEmpty()) return@launch

            val center = GeoPoint(
                (points.minOf { it.lat } + points.maxOf { it.lat }) / 2,
                (points.minOf { it.lon } + points.maxOf { it.lon }) / 2
            )
            mapView.controller.animateTo(center)
            mapView.controller.setZoom(12.0)
            mapView.invalidate()
        }
    }

    private fun savePosition() {
        if (::mapView.isInitialized && AppSettings.getRememberPosition(requireContext())) {
            val center = mapView.mapCenter
            AppSettings.saveLastPosition(
                requireContext(), center.latitude, center.longitude, mapView.zoomLevelDouble
            )
        }
    }

    private fun setupBottomSheet(view: View) {
        val sheet = view.findViewById<View>(R.id.bottomSheet)
        bottomSheet = BottomSheetBehavior.from(sheet)
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

        view.findViewById<View>(R.id.bottomSheetClose).setOnClickListener {
            bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }

        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(v: View, newState: Int) {
                val vis = if (newState == BottomSheetBehavior.STATE_HIDDEN) View.VISIBLE else View.GONE
                legend.visibility      = vis
                locationFab.visibility = vis
            }
            override fun onSlide(v: View, offset: Float) {}
        })
    }

    private fun setupAreaButtons() {
        val areas = listOf(
            "Yorkshire Dales"  to GeoPoint(54.1, -2.1),
            "Peak District"    to GeoPoint(53.4, -1.8),
            "Dartmoor"         to GeoPoint(50.6, -3.9),
            "Lake District"    to GeoPoint(54.5, -3.1),
            "North York Moors" to GeoPoint(54.3, -0.9),
            "Exmoor"           to GeoPoint(51.1, -3.6),
            "Brecon Beacons"   to GeoPoint(51.9, -3.4),
            "Snowdonia"        to GeoPoint(53.1, -3.9),
        )
        areas.forEach { (name, point) ->
            val chip = Chip(requireContext()).apply {
                text = name
                isCheckable = true
                setOnClickListener {
                    mapView.controller.animateTo(point)
                    mapView.controller.setZoom(11.0)
                    hideKeyboard()
                    searchResultsList.visibility = View.GONE
                }
            }
            areaChipGroup.addView(chip)
        }
    }

    private fun setupSearch() {
        view?.findViewById<View>(R.id.searchButton)?.setOnClickListener { performSearch() }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                performSearch(); true
            } else false
        }
        searchResultsList.setOnItemClickListener { _, _, position, _ ->
            val result = searchResults.getOrNull(position) ?: return@setOnItemClickListener
            mapView.controller.animateTo(GeoPoint(result.lat, result.lon))
            mapView.controller.setZoom(13.0)
            searchResultsList.visibility = View.GONE
            searchInput.setText(result.displayName)
            hideKeyboard()
        }
    }

    private fun performSearch() {
        val query = searchInput.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return
        hideKeyboard()
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val results = GeocodingClient.search(query)
                searchResults.clear()
                searchResults.addAll(results)
                if (results.isEmpty()) {
                    statusText.text = "No results for \"$query\""
                    searchResultsList.visibility = View.GONE
                } else if (results.size == 1) {
                    mapView.controller.animateTo(GeoPoint(results[0].lat, results[0].lon))
                    mapView.controller.setZoom(13.0)
                    searchResultsList.visibility = View.GONE
                } else {
                    searchResultsList.adapter = ArrayAdapter(
                        requireContext(), R.layout.item_search_result,
                        results.map { it.displayName }
                    )
                    searchResultsList.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                statusText.text = "Search failed"
            }
            progressBar.visibility = View.GONE
        }
    }

    private fun setupLocationFab() {
        locationFab.setOnClickListener {
            locationOverlay?.myLocation?.let {
                mapView.controller.animateTo(it)
                mapView.controller.setZoom(13.0)
            } ?: requestLocation()
        }
    }

    private fun setupObservers() {
        viewModel.restrictions.observe(viewLifecycleOwner) { updateEnglandMap(it) }
        viewModel.walesLand.observe(viewLifecycleOwner)    { updateWalesMap(it)   }
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { statusText.text = "⚠️ $it" }
        }
        viewModel.countryInfo.observe(viewLifecycleOwner) {
            val engCount   = viewModel.restrictions.value?.size ?: 0
            val walesCount = viewModel.walesLand.value?.size    ?: 0
            statusText.text = when {
                engCount > 0 && walesCount > 0 -> "🐕 $engCount restrictions · 🏴󠁧󠁢󠁷󠁬󠁳󠁿 $walesCount Wales areas"
                engCount > 0   -> "🐕 $engCount restriction${if (engCount != 1) "s" else ""} found"
                walesCount > 0 -> "🏴󠁧󠁢󠁷󠁬󠁳󠁿 Wales: $walesCount access areas — no restriction data"
                else           -> "✅ No dog restrictions in this area"
            }
        }
    }

    private fun updateEnglandMap(restrictions: List<Restriction>) {
        mapView.overlays.removeAll { it is RestrictionPolygonOverlay }
        restrictions.forEach { restriction ->
            mapView.overlays.add(RestrictionPolygonOverlay(restriction) { showRestrictionDetail(it) })
        }
        mapView.invalidate()
    }

    private fun updateWalesMap(land: List<WalesAccessLand>) {
        mapView.overlays.removeAll { it is WalesPolygonOverlay }
        land.forEach { accessLand ->
            mapView.overlays.add(WalesPolygonOverlay(accessLand) { showWalesDetail(it) })
        }
        mapView.invalidate()
    }

    private fun showRestrictionDetail(restriction: Restriction) {
        val v  = view ?: return
        val rt = RestrictionType.fromCode(restriction.type)
        v.findViewById<TextView>(R.id.detailType).apply {
            text = rt.label; setTextColor(rt.color(requireContext()))
        }
        v.findViewById<TextView>(R.id.detailPurpose).text = restriction.purposeLabel()
        v.findViewById<TextView>(R.id.detailFrom).text    = restriction.formattedStartDate()
        v.findViewById<TextView>(R.id.detailUntil).text   = restriction.formattedEndDate()
        v.findViewById<TextView>(R.id.detailCase).text    = restriction.caseNumber
        val active = restriction.isActive()
        v.findViewById<TextView>(R.id.detailStatus).apply {
            text = if (active) "ACTIVE" else "EXPIRED"
            setTextColor(if (active) requireContext().getColor(android.R.color.holo_green_dark)
                         else requireContext().getColor(android.R.color.holo_red_dark))
            setBackgroundResource(if (active) R.drawable.badge_active else R.drawable.badge_expired)
        }
        v.findViewById<View>(R.id.viewPdfButton).apply {
            visibility = if (restriction.pdfUrl() != null) View.VISIBLE else View.GONE
            setOnClickListener {
                restriction.pdfUrl()?.let { url ->
                    startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                }
            }
        }
        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun showWalesDetail(land: WalesAccessLand) {
        val v = view ?: return
        v.findViewById<TextView>(R.id.detailType).apply {
            text = "🏴󠁧󠁢󠁷󠁬󠁳󠁿 Wales CROW Access Land"
            setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
        }
        v.findViewById<TextView>(R.id.detailPurpose).text =
            "${land.layerType}\n\n⚠️ No dog restriction data available for Wales."
        v.findViewById<TextView>(R.id.detailFrom).text  = "${String.format("%.1f", land.areaHa)} ha"
        v.findViewById<TextView>(R.id.detailUntil).text = "Permanent access"
        v.findViewById<TextView>(R.id.detailCase).text  = "NRW #${land.objectId}"
        v.findViewById<TextView>(R.id.detailStatus).apply {
            text = "OPEN ACCESS"
            setTextColor(requireContext().getColor(android.R.color.holo_green_dark))
            setBackgroundResource(R.drawable.badge_active)
        }
        v.findViewById<View>(R.id.viewPdfButton).visibility = View.GONE
        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) enableLocation()
        else locationPermission.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun enableLocation() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView).apply {
            enableMyLocation()
            // Blue circle instead of default person icon
            val size = 32
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.parseColor("#2979FF")
            canvas.drawCircle(size/2f, size/2f, size/2f - 2, paint)
            paint.color = android.graphics.Color.WHITE
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawCircle(size/2f, size/2f, size/2f - 2, paint)
            setPersonIcon(bmp)
        }
        mapView.overlays.add(locationOverlay)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
            applyMapStyle()
            viewModel.onMapMoved(mapView.boundingBox)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mapView.isInitialized) {
            savePosition()
            mapView.onPause()
        }
    }
}
