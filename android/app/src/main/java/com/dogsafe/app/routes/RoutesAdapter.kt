package com.dogsafe.app.routes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dogsafe.app.R
import com.dogsafe.app.db.RouteEntity

class RoutesAdapter(
    private var routes: List<RouteEntity>,
    private val onVisibilityToggle: (RouteEntity) -> Unit,
    private val onDelete: (RouteEntity) -> Unit,
    private val onRowClick: (RouteEntity) -> Unit
) : RecyclerView.Adapter<RoutesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val visibilityDot: View     = view.findViewById(R.id.visibilityDot)
        val routeName: TextView     = view.findViewById(R.id.routeName)
        val routeDistance: TextView = view.findViewById(R.id.routeDistance)
        val routeRestrictions: TextView = view.findViewById(R.id.routeRestrictions)
        val safetyDot: View         = view.findViewById(R.id.safetyDot)
        val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val route = routes[position]

        holder.routeName.text = route.name
        holder.routeDistance.text = String.format("%.1fkm", route.distanceKm)
        holder.routeRestrictions.text = "${route.restrictionCount} restriction${if (route.restrictionCount != 1) "s" else ""}"

        // Visibility dot — green = visible, red = hidden
        holder.visibilityDot.setBackgroundResource(
            if (route.isVisible) R.drawable.dot_green else R.drawable.dot_red
        )

        // Safety dot
        holder.safetyDot.setBackgroundResource(
            when (route.safetyStatus) {
                "RED"   -> R.drawable.dot_red
                "AMBER" -> R.drawable.dot_amber
                else    -> R.drawable.dot_green
            }
        )

        holder.visibilityDot.setOnClickListener { onVisibilityToggle(route) }
        holder.deleteButton.setOnClickListener { onDelete(route) }
        holder.itemView.setOnClickListener { onRowClick(route) }
    }

    override fun getItemCount() = routes.size

    fun updateRoutes(newRoutes: List<RouteEntity>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
}
