package com.dogsafe.app.routes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dogsafe.app.R
import com.dogsafe.app.routes.RouteAnalyser.isNestingBirdSeason
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class RoutesFragment : Fragment() {

    private lateinit var viewModel: RoutesViewModel
    private lateinit var adapter: RoutesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var seasonBanner: View

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = getFileName(uri) ?: "route.gpx"
                viewModel.importGpx(requireContext(), uri, fileName)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_routes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[RoutesViewModel::class.java]

        recyclerView = view.findViewById(R.id.routesList)
        emptyState   = view.findViewById(R.id.emptyState)
        progressBar  = view.findViewById(R.id.routesProgress)
        seasonBanner = view.findViewById(R.id.seasonBanner)

        // Show season banner if applicable
        if (isNestingBirdSeason()) seasonBanner.visibility = View.VISIBLE

        // Setup RecyclerView
        adapter = RoutesAdapter(
            routes             = emptyList(),
            onVisibilityToggle = { route -> viewModel.toggleVisibility(requireContext(), route) },
            onDelete           = { route -> confirmDelete(route) },
            onRowClick         = { route -> onRouteSelected(route) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Import button
        view.findViewById<MaterialButton>(R.id.importButton).setOnClickListener {
            openFilePicker()
        }

        // Observe
        viewModel.routes.observe(viewLifecycleOwner) { routes ->
            adapter.updateRoutes(routes)
            recyclerView.visibility = if (routes.isEmpty()) View.GONE else View.VISIBLE
            emptyState.visibility   = if (routes.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(view, it, Snackbar.LENGTH_LONG).show()
            }
        }

        viewModel.importSuccess.observe(viewLifecycleOwner) { message ->
            message?.let {
                Snackbar.make(view, it, Snackbar.LENGTH_LONG).show()
            }
        }

        viewModel.loadRoutes(requireContext())
    }

    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePicker.launch(Intent.createChooser(intent, "Select GPX file"))
    }

    private fun confirmDelete(route: com.dogsafe.app.db.RouteEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Route")
            .setMessage("Delete \"${route.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteRoute(requireContext(), route)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun onRouteSelected(route: com.dogsafe.app.db.RouteEntity) {
        // Notify MainActivity to switch to map tab and show route
        (activity as? com.dogsafe.app.MainActivity)?.showRouteOnMap(route)
    }

    private fun getFileName(uri: android.net.Uri): String? {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        }
    }
}
