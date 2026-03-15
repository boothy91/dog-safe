package com.dogsafe.app.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.dogsafe.app.BuildConfig
import com.dogsafe.app.R
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        // --- Map settings ---
        val switchRememberPosition = view.findViewById<SwitchMaterial>(R.id.switchRememberPosition)
        switchRememberPosition.isChecked = AppSettings.getRememberPosition(ctx)
        switchRememberPosition.setOnCheckedChangeListener { _, checked ->
            AppSettings.setRememberPosition(ctx, checked)
        }

        val spinnerMapStyle = view.findViewById<Spinner>(R.id.spinnerMapStyle)
        val mapStyles = listOf("Standard", "Topo (hills & contours)", "Satellite")
        val mapStyleAdapter = ArrayAdapter(ctx, R.layout.item_spinner, mapStyles)
        mapStyleAdapter.setDropDownViewResource(R.layout.item_spinner)
        spinnerMapStyle.adapter = mapStyleAdapter
        spinnerMapStyle.setSelection(when (AppSettings.getMapStyle(ctx)) { "topo" -> 1; "satellite" -> 2; else -> 0 })
        spinnerMapStyle.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) {
                AppSettings.setMapStyle(ctx, when (pos) { 1 -> "topo"; 2 -> "satellite"; else -> "standard" })
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // --- Restriction settings ---
        val switchActiveOnly = view.findViewById<SwitchMaterial>(R.id.switchActiveOnly)
        switchActiveOnly.isChecked = AppSettings.getActiveOnly(ctx)
        switchActiveOnly.setOnCheckedChangeListener { _, checked ->
            AppSettings.setActiveOnly(ctx, checked)
        }

        val switchShowWales = view.findViewById<SwitchMaterial>(R.id.switchShowWales)
        switchShowWales.isChecked = AppSettings.getShowWales(ctx)
        switchShowWales.setOnCheckedChangeListener { _, checked ->
            AppSettings.setShowWales(ctx, checked)
        }

        val switchSeasonBanner = view.findViewById<SwitchMaterial>(R.id.switchSeasonBanner)
        switchSeasonBanner.isChecked = AppSettings.getSeasonBanner(ctx)
        switchSeasonBanner.setOnCheckedChangeListener { _, checked ->
            AppSettings.setSeasonBanner(ctx, checked)
        }

        // --- Route settings ---
        val switchAutoAnalyse = view.findViewById<SwitchMaterial>(R.id.switchAutoAnalyse)
        switchAutoAnalyse.isChecked = AppSettings.getAutoAnalyse(ctx)
        switchAutoAnalyse.setOnCheckedChangeListener { _, checked ->
            AppSettings.setAutoAnalyse(ctx, checked)
        }

        val spinnerDistanceUnits = view.findViewById<Spinner>(R.id.spinnerDistanceUnits)
        val units = listOf("Kilometres (km)", "Miles")
        val unitsAdapter = ArrayAdapter(ctx, R.layout.item_spinner, units)
        unitsAdapter.setDropDownViewResource(R.layout.item_spinner)
        spinnerDistanceUnits.adapter = unitsAdapter
        spinnerDistanceUnits.setSelection(if (AppSettings.getDistanceUnits(ctx) == "miles") 1 else 0)
        spinnerDistanceUnits.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, pos: Int, id: Long) {
                AppSettings.setDistanceUnits(ctx, if (pos == 1) "miles" else "km")
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // --- About ---
        view.findViewById<TextView>(R.id.textVersion).text = BuildConfig.VERSION_NAME

        view.findViewById<View>(R.id.linkNaturalEngland).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.gov.uk/right-of-way-open-access-land/use-your-right-to-roam")))
        }

        view.findViewById<View>(R.id.linkLicence).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.nationalarchives.gov.uk/doc/open-government-licence/")))
        }
    }
}
