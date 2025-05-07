package com.example.supmap.data.api

import androidx.annotation.DrawableRes
import com.example.supmap.R

data class IncidentTypeDto(
    val id: Long,
    val name: String,
    @DrawableRes val iconRes: Int
)

object IncidentTypeProvider {
    val allTypes = listOf(
        IncidentTypeDto(1, "Collision entre véhicules", R.drawable.ic_inc_collision),
        IncidentTypeDto(3, "Accident avec blessés", R.drawable.ic_inc_blesses),
        IncidentTypeDto(4, "Embouteillage majeur", R.drawable.ic_inc_embouteillage),
        IncidentTypeDto(6, "Route bloquée", R.drawable.ic_inc_bloquee),
        IncidentTypeDto(7, "Travaux en cours", R.drawable.ic_inc_travaux),
        IncidentTypeDto(8, "Radar fixe", R.drawable.ic_inc_radar),
        IncidentTypeDto(9, "Contrôle mobile", R.drawable.ic_inc_police),
        IncidentTypeDto(10, "Débris sur la route", R.drawable.ic_inc_debris),
        IncidentTypeDto(11, "Animal sur la chaussée", R.drawable.ic_inc_animal),
    )
}
