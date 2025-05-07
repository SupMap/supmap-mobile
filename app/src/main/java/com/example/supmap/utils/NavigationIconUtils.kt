package com.example.supmap.utils

import com.example.supmap.R

object NavigationIconUtils {


    fun getNavigationIconResource(sign: Int): Int {
        return when (sign) {
            // U-turns
            -98 -> R.drawable.u_ic_turn_left // U-turn (sens inconnu)
            -8 -> R.drawable.u_ic_turn_left  // U-turn gauche
            8 -> R.drawable.u_ic_turn_right  // U-turn droite

            // Keep left / right
            -7 -> R.drawable.u_ic_turn_slight_left // keep left
            7 -> R.drawable.u_ic_turn_slight_right // keep right

            // Sharp / normal / slight LEFT
            -3 -> R.drawable.u_ic_turn_sharp_left  // sharp left
            -2 -> R.drawable.u_ic_turn_left        // left
            -1 -> R.drawable.u_ic_turn_slight_left // slight left

            // Continue straight
            0 -> R.drawable.u_ic_turn_straight    // continue on street

            // Slight / normal / sharp RIGHT
            1 -> R.drawable.u_ic_turn_slight_right // slight right
            2 -> R.drawable.u_ic_turn_right        // right
            3 -> R.drawable.u_ic_turn_sharp_right  // sharp right

            // Special points
            4 -> R.drawable.ic_flag             // finish instruction
            5 -> R.drawable.u_ic_place         // via point

            // Roundabout
            6 -> R.drawable.u_ic_sync // enter roundabout

            // Default
            else -> R.drawable.ic_navigation     // default icon
        }
    }
}