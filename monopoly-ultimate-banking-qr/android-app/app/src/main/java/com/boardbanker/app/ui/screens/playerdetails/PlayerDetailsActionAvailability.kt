package com.boardbanker.app.ui.screens.playerdetails

/**
 * Central policy for which Player Details bank actions are enabled for the displayed player.
 */
data class PlayerDetailsActionAvailability(
    val collectGoEnabled: Boolean,
    val locationEnabled: Boolean,
    val goToJailEnabled: Boolean,
    val getOutOfJailEnabled: Boolean,
) {
    companion object {
        fun forPlayer(
            inJail: Boolean,
            commandInFlight: Boolean,
            step: PlayerDetailsStep,
        ): PlayerDetailsActionAvailability {
            if (commandInFlight || step != PlayerDetailsStep.Hub) {
                return PlayerDetailsActionAvailability(
                    collectGoEnabled = false,
                    locationEnabled = false,
                    goToJailEnabled = false,
                    getOutOfJailEnabled = false,
                )
            }
            return if (inJail) {
                PlayerDetailsActionAvailability(
                    collectGoEnabled = false,
                    locationEnabled = false,
                    goToJailEnabled = false,
                    getOutOfJailEnabled = true,
                )
            } else {
                PlayerDetailsActionAvailability(
                    collectGoEnabled = true,
                    locationEnabled = true,
                    goToJailEnabled = true,
                    getOutOfJailEnabled = false,
                )
            }
        }
    }
}
