package com.dogsafe.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.dogsafe.app.databinding.ActivityMainBinding
import com.dogsafe.app.model.RestrictionType
import com.dogsafe.app.viewmodel.MapViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            enableLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid config
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
        setupObservers()
        requestLocation()
    }

    private fun setupMap() {
        mapView = binding.map
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(11.0)
        mapView.controller.setCenter(GeoPoint(54.1, -2.1)) // Yorkshire Dales default

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
                }
            }
            binding.areaChipGroup.addView(btn)
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

    private fun updateMap(restrictions: List<com.dogsafe.app.model.Restriction>) {
        mapView.overlays.removeAll { it is RestrictionPolygonOverlay }

        restrictions.forEach { restriction ->
            val overlay = RestrictionPolygonOverlay(restriction) { selected ->
                showRestrictionDetail(selected)
            }
            mapView.overlays.add(overlay)
        }
        mapView.invalidate()
    }

    private fun showRestrictionDetail(restriction: com.dogsafe.app.model.Restriction) {
        binding.detailType.text = RestrictionType.fromCode(restriction.type).label
        binding.detailType.setTextColor(RestrictionType.fromCode(restriction.type).color(this))
        binding.detailPurpose.text = restriction.purposeLabel()
        binding.detailFrom.text = restriction.formattedStartDate()
        binding.detailUntil.text = restriction.formattedEndDate()
        binding.detailCase.text = restriction.caseNumber
        binding.detailStatus.text = if (restriction.isActive()) "ACTIVE" else "EXPIRED"
        binding.detailStatus.setBackgroundResource(
            if (restriction.isActive()) R.drawable.badge_active else R.drawable.badge_expired
        )
        binding.viewPdfButton.setOnClickListener {
            restriction.pdfUrl()?.let { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url))
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
