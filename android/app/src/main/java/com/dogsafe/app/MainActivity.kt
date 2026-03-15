package com.dogsafe.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.dogsafe.app.db.RouteEntity
import com.dogsafe.app.routes.RoutesFragment
import com.dogsafe.app.settings.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var mapFragment: MapFragment
    private lateinit var routesFragment: RoutesFragment
    private lateinit var settingsFragment: SettingsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        // Create fragments once
        mapFragment      = MapFragment()
        routesFragment   = RoutesFragment()
        settingsFragment = SettingsFragment()

        // Add all fragments, show map by default
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, mapFragment,      "map")
            .add(R.id.fragmentContainer, routesFragment,   "routes")
            .add(R.id.fragmentContainer, settingsFragment, "settings")
            .hide(routesFragment)
            .hide(settingsFragment)
            .commit()

        bottomNav = findViewById(R.id.bottomNav)

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map      -> { showFragment(mapFragment);      mapFragment.refreshSettings(); true }
                R.id.nav_routes   -> { showFragment(routesFragment);   true }
                R.id.nav_settings -> { showFragment(settingsFragment); true }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .hide(mapFragment)
            .hide(routesFragment)
            .hide(settingsFragment)
            .show(fragment)
            .commit()
    }

    fun showRouteOnMap(route: RouteEntity) {
        bottomNav.selectedItemId = R.id.nav_map
        showFragment(mapFragment)
        mapFragment.refreshSettings()
        supportFragmentManager.executePendingTransactions()
        mapFragment.showRouteOnMap(route)
    }
}
