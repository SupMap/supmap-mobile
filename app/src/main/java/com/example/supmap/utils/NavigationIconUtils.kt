package com.example.supmap.utils

import com.example.supmap.R

object NavigationIconUtils {


    fun getNavigationIconResource(sign: Int): Int {
        return when (sign) {
            -98 -> R.drawable.u_ic_turn_left
            -8 -> R.drawable.u_ic_turn_left
            8 -> R.drawable.u_ic_turn_right

            -7 -> R.drawable.u_ic_turn_slight_left
            7 -> R.drawable.u_ic_turn_slight_right

            -3 -> R.drawable.u_ic_turn_sharp_left
            -2 -> R.drawable.u_ic_turn_left
            -1 -> R.drawable.u_ic_turn_slight_left

            0 -> R.drawable.u_ic_turn_straight

            1 -> R.drawable.u_ic_turn_slight_right
            2 -> R.drawable.u_ic_turn_right
            3 -> R.drawable.u_ic_turn_sharp_right

            4 -> R.drawable.ic_flag
            5 -> R.drawable.u_ic_place

            6 -> R.drawable.u_ic_sync

            else -> R.drawable.ic_navigation
        }
    }
}