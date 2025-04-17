package com.example.supmap.ui.map

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.supmap.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

class RouteOptionsAdapter(
    private var routes: List<RouteOption>,
    private var selectedIndex: Int = 0,
    private val onRouteSelected: (Int) -> Unit
) : RecyclerView.Adapter<RouteOptionsAdapter.RouteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.route_option_item, parent, false)
        return RouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        val route = routes[position]
        holder.bind(route, position == selectedIndex)

        holder.itemView.setOnClickListener {
            val previousSelected = selectedIndex
            selectedIndex = position
            onRouteSelected(position)
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedIndex)
        }
    }

    override fun getItemCount(): Int = routes.size

    fun updateData(newRoutes: List<RouteOption>, newSelectedIndex: Int) {
        routes = newRoutes
        selectedIndex = newSelectedIndex
        notifyDataSetChanged()
    }

    class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView
        private val durationText: TextView = itemView.findViewById(R.id.routeDurationText)
        private val arrivalText: TextView = itemView.findViewById(R.id.routeArrivalText)
        private val typeLabel: TextView = itemView.findViewById(R.id.routeTypeLabel)
        private val descriptionText: TextView = itemView.findViewById(R.id.routeDescriptionText)
        private val distanceText: TextView = itemView.findViewById(R.id.routeDistanceText)

        fun bind(route: RouteOption, isSelected: Boolean) {
            // Formater la durée
            val timeMin = (route.path.time / 60000).toInt()
            val hours = timeMin / 60
            val minutes = timeMin % 60
            val formattedDuration = if (hours > 0) {
                "${hours}h ${minutes}min"
            } else {
                "${minutes}min"
            }
            durationText.text = formattedDuration

            // Calculer l'heure d'arrivée
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MILLISECOND, route.path.time.toInt())
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            arrivalText.text = "Arrivée à ${formatter.format(calendar.time)}"

            // Définir le badge du type d'itinéraire
            typeLabel.text = when (route.type) {
                "fastest" -> "⭐️ ${route.label}"
                "noToll" -> "🚫🧾 ${route.label}"
                "economical" -> "💰 ${route.label}"
                else -> route.label
            }

            // Définir la description (texte de la première instruction ou vide)
            descriptionText.text = route.path.instructions?.firstOrNull()?.text ?: ""

            // Formater la distance
            val distanceKm = (route.path.distance / 1000).toDouble()
            distanceText.text = String.format("%.1f km", distanceKm)

            // Mettre en évidence l'itinéraire sélectionné
            if (isSelected) {
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        R.color.bleu
                    )
                )
                durationText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
                arrivalText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
                descriptionText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
                distanceText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
                typeLabel.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.route_type_badge_selected_background
                )
                typeLabel.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
            } else {
                cardView.setCardBackgroundColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.white
                    )
                )
                durationText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.black
                    )
                )
                arrivalText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.darker_gray
                    )
                )
                descriptionText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.darker_gray
                    )
                )
                distanceText.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.darker_gray
                    )
                )
                typeLabel.background = ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.route_type_badge_background
                )
                typeLabel.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        android.R.color.black
                    )
                )
            }
        }
    }
}