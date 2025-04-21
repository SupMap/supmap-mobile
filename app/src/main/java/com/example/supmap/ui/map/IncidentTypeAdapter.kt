package com.example.supmap.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.supmap.R
import com.example.supmap.data.api.IncidentTypeDto

class IncidentTypeAdapter(
    private val items: List<IncidentTypeDto>,
    private val onClick: (IncidentTypeDto) -> Unit
) : RecyclerView.Adapter<IncidentTypeAdapter.VH>() {

    inner class VH(parent: ViewGroup) :
        RecyclerView.ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.incident_type_item, parent, false)
        ) {
        private val icon = itemView.findViewById<ImageView>(R.id.incidentIcon)
        private val label = itemView.findViewById<TextView>(R.id.incidentLabel)

        fun bind(item: IncidentTypeDto) {
            icon.setImageResource(item.iconRes)
            label.text = item.name
            itemView.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(parent)
    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size
}
