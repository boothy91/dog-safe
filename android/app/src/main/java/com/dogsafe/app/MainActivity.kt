package com.dogsafe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dogsafe.app.databinding.ActivityMainBinding
import com.dogsafe.app.model.Restriction
import com.dogsafe.app.model.RestrictionType
import com.dogsafe.app.search.GeocodingClient
import com.dogsafe.app.search.SearchResult
import com.dogsafe.app.viewmodel.MapViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MapViewModel
    private lateinit var mapView: MapView
    private lateinit var bottomSheet: BottomSheetBehavior<View>
    private var locationOverlay: MyLocationNewOverlay? = null
    private var searchResults = mutableListOf<SearchResult>()

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            enableLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            load(this@MainActivity, PreferenceManager.getDefaultSharedPreferences(this@MainActivity))
            userAgentValue = "DogSafe/1.0 (com.dogsafe.app)"
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MapViewModel::class.java]

        setupMap()
        setupBottomSheet()
        setupAreaButtons()
        setupSearch()
        setupLocationFab()
        setupObservers()
        requestLocation()
    }

    private fun setupMap() {
        mapView = binding.map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(11.0)
        mapView.controller.setCenter(GeoPoint(54.1, -2.1))

        mapView.addMapListener(object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent): Boolean {
                viewModel.onMapMoved(mapView.boundingBox)
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
                viewModel.onMapMoved(mapView.boundingBox)
                return false
            }
        })
    }

    private fun setupBottomSheet() {
        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

        binding.bottomSheetClose.setOnClickListener {
            bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }

        bottomSheet.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(view: View, newState: Int) {
                binding.legend.visibility = when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> View.VISIBLE
                    else -> View.GONE
                }
            }
            override fun onSlide(view: View, slideOffset: Float) {}
        })
    }

    private fun setupAreaButtons() {
        val areas = listOf(
            "Yorkshire Dales" to GeoPoint(54.1, -2.1),
            "Peak District"   to GeoPoint(53.4, -1.8),
            "Dartmoor"        to GeoPoint(50.6, -3.9),
            "Lake District"   to GeoPoint(54.5, -3.1),
            "North York Moors" to GeoPoint(54.3, -0.9),
            "Exmoor"          to GeoPoint(51.1, -3.6),
        )
        areas.forEach { (name, point) ->
            val btn = com.google.android.material.chip.Chip(this).apply {
                text = name
                isCheckable = true
                setOnClickListener {
                    mapView.controller.animateTo(point)
                    mapView.controller.setZoom(11.0)
                    hideKeyboard()
                    binding.searchResults.visibility = View.GONE
                }
            }
            binding.areaChipGroup.addView(btn)
        }
    }

    private fun setupSearch() {
        // Search button click
        binding.searchButton.setOnClickListener { performSearch() }

        // Keyboard search action
        binding.searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch()
                true
            } else false
        }

        // Search result tapped
        binding.searchResults.setOnItemClickListener { _, _, position, _ ->
            val result = searchResults.getOrNull(position) ?: return@setOnItemClickListener
            mapView.controller.animateTo(GeoPoint(result.lat, result.lon))
            mapView.controller.setZoom(13.0)
            binding.searchResults.visibility = View.GONE
            binding.searchInput.setText(result.displayName)
            hideKeyboard()
        }
    }

    private fun performSearch() {
        val query = binding.searchInput.text?.toString()?.trim() ?: return
        if (query.isEmpty()) return

        hideKeyboard()
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val results = GeocodingClient.search(query)
                searchResults.clear()
                searchResults.addAll(results)

                if (results.isEmpty()) {
                    binding.statusText.text = "No results found for \"$query\""
                    binding.searchResults.visibility = View.GONE
                } else if (results.size == 1) {
                    // Single result — go straight there
                    mapView.controller.animateTo(GeoPoint(results[0].lat, results[0].lon))
                    mapView.controller.setZoom(13.0)
                    binding.searchResults.visibility = View.GONE
                } else {
                    // Multiple results — show dropdown
                    val adapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_list_item_1,
                        results.map { it.displayName }
                    )
                    binding.searchResults.adapter = adapter
                    binding.searchResults.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.statusText.text = "Search failed — check connection"
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupLocationFab() {
        binding.locationFab.setOnClickListener {
            locationOverlay?.myLocation?.let { location ->
                mapView.controller.animateTo(location)
                mapView.controller.setZoom(13.0)
            } ?: run {
                // Permission not granted yet — request it
                requestLocation()
            }
        }
    }

    private fun setupObservers() {
        viewModel.restrictions.observe(this) { restrictions ->
            updateMap(restrictions)
            binding.statusText.text = if (restrictions.isEmpty())
                "✅ No dog restrictions in this area"
            else
                "🐕 ${restrictions.size} restriction${if (restrictions.size != 1) "s" else ""} found"
        }

        viewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let { binding.statusText.text = "⚠️ $it" }
        }
    }

    private fun updateMap(restrictions: List<Restriction>) {
        mapView.overlays.removeAll { it is RestrictionPolygonOverlay }
        restrictions.forEach { restriction ->
            val overlay = RestrictionPolygonOverlay(restriction) { selected ->
                showRestrictionDetail(selected)
            }
            mapView.overlays.add(overlay)
        }
        mapView.invalidate()
    }

    private fun showRestrictionDetail(restriction: Restriction) {
        binding.detailType.text = RestrictionType.fromCode(restriction.type).label
        binding.detailType.setTextColor(RestrictionType.fromCode(restriction.type).color(this))
        binding.detailPurpose.text = restriction.purposeLabel()
        binding.detailFrom.text = restriction.formattedStartDate()
        binding.detailUntil.text = restriction.formattedEndDate()
        binding.detailCase.text = restriction.caseNumber
        binding.detailStatus.text = if (restriction.isActive()) "ACTIVE" else "EXPIRED"
        binding.detailStatus.setTextColor(
            if (restriction.isActive())
                getColor(android.R.color.holo_green_dark)
            else
                getColor(android.R.color.holo_red_dark)
        )
        binding.detailStatus.setBackgroundResource(
            if (restriction.isActive()) R.drawable.badge_active else R.drawable.badge_expired
        )
        binding.viewPdfButton.setOnClickListener {
            restriction.pdfUrl()?.let { url ->
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)
                )
                startActivity(intent)
            }
        }
        binding.viewPdfButton.visibility =
            if (restriction.pdfUrl() != null) View.VISIBLE else View.GONE

        bottomSheet.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            enableLocation()
        } else {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun enableLocation() {
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(locationOverlay)
        locationOverlay?.runOnFirstFix {
            runOnUiThread {
                locationOverlay?.myLocation?.let {
                    mapView.controller.animateTo(it)
                    mapView.controller.setZoom(12.0)
                    locationOverlay?.disableFollowLocation()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
