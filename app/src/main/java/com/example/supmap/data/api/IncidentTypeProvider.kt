package com.example.supmap.data.api

import androidx.annotation.DrawableRes
import com.example.supmap.R

/** Pour ton BottomSheet, on propose ici tous les types “hard‑codés” */
data class IncidentTypeDto(
    val id: Long,
    val name: String,
    @DrawableRes val iconRes: Int
)

object IncidentTypeProvider {
    val allTypes = listOf(
        // catégorie Accident (id category=1)
        IncidentTypeDto(1, "Collision entre véhicules", R.drawable.ic_incident_collision),
        IncidentTypeDto(2, "Accident multiple", R.drawable.ic_incident_multiple),
        IncidentTypeDto(3, "Accident avec blessés", R.drawable.ic_incident_blesses),

        // catégorie Embouteillage (id category=2)
        IncidentTypeDto(4, "Embouteillage majeur", R.drawable.ic_incident_embouteillage),
        IncidentTypeDto(5, "Circulation ralentie", R.drawable.ic_incident_ralentie),

        // catégorie Route fermée (id category=3)
        IncidentTypeDto(6, "Route bloquée", R.drawable.ic_incident_bloquee),
        IncidentTypeDto(7, "Travaux en cours", R.drawable.ic_incident_travaux),

        // catégorie Contrôle policier (id category=4)
        IncidentTypeDto(8, "Radar fixe", R.drawable.ic_incident_radar),
        IncidentTypeDto(9, "Contrôle mobile", R.drawable.ic_incident_controle),

        // catégorie Obstacle sur la route (id category=5)
        IncidentTypeDto(10, "Débris sur la route", R.drawable.ic_incident_debris),
        IncidentTypeDto(11, "Animal sur la chaussée", R.drawable.ic_incident_animal),
        IncidentTypeDto(12, "Objet sur la route", R.drawable.ic_incident_objet)
    )
}
