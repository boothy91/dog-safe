package com.dogsafe.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dogsafe.app.db.RouteEntity
import com.dogsafe.app.routes.RoutesFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var mapFragment: MapFragment? = null
    private var routesFragment: RoutesFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }

        bottomNav = findViewById(R.id.bottomNav)

        // Show map by default
        showMap()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map    -> { showMap();    true }
                R.id.nav_routes -> { showRoutes(); true }
                else -> false
            }
        }
    }

    private fun showMap() {
        if (mapFragment == null) mapFragment = MapFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, mapFragment!!)
            .commit()
    }

    private fun showRoutes() {
        if (routesFragment == null) routesFragment = RoutesFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, routesFragment!!)
            .commit()
    }

    fun showRouteOnMap(route: RouteEntity) {
        // Switch to map tab and show route
        bottomNav.selectedItemId = R.id.nav_map
        showMap()
        // Wait for fragment to be ready then show route
        supportFragmentManager.executePendingTransactions()
        mapFragment?.showRouteOnMap(route)
    }
}
