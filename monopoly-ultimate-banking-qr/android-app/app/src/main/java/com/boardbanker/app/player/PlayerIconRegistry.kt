package com.boardbanker.app.player

import android.util.Log
import androidx.annotation.DrawableRes
import com.boardbanker.app.R

object PlayerIconRegistry {
    private const val TAG = "PlayerIconRegistry"

    private val iconByPlayerId: Map<String, Int> = mapOf(
        "USR_01" to R.drawable.player_car,
        "USR_02" to R.drawable.player_helicopter,
        "USR_03" to R.drawable.player_ship,
        "USR_04" to R.drawable.player_aeroplane,
    )

    private val runtimeNameByPlayerId: Map<String, String> = mapOf(
        "USR_01" to "player_car",
        "USR_02" to "player_helicopter",
        "USR_03" to "player_ship",
        "USR_04" to "player_aeroplane",
    )

    @DrawableRes
    fun iconResId(playerId: String?): Int? {
        if (playerId == null) return null
        val icon = iconByPlayerId[playerId]
        if (icon == null) {
            Log.d(TAG, "No player icon registered for $playerId")
        }
        return icon
    }

    fun runtimeResourceName(playerId: String?): String? = playerId?.let { runtimeNameByPlayerId[it] }
}
