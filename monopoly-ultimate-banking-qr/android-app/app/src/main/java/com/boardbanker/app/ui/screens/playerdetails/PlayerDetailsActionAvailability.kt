package com.boardbanker.app.ui.screens.playerdetails

/**
 * Central policy for which Player Details bank actions are enabled for the displayed player.
 */
data class PlayerDetailsActionAvailability(
    val collectGoEnabled: Boolean,
    val locationEnabled: Boolean,
    val goToJailEnabled: Boolean,
    val getOutOfJailEnabled: Boolean,
    val actionsDisabledReason: String? = null,
) {
    companion object {
        fun forPlayer(
            isCurrentPlayer: Boolean,
            activePlayerName: String?,
            inJail: Boolean,
            commandInFlight: Boolean,
            step: PlayerDetailsStep,
        ): PlayerDetailsActionAvailability {
            if (commandInFlight || step != PlayerDetailsStep.Hub) {
                return allDisabled()
            }
            if (!isCurrentPlayer) {
                val reason = activePlayerName?.let { name ->
                    "Actions are disabled because it is $name's turn."
                } ?: "Actions are disabled because it is another player's turn."
                return PlayerDetailsActionAvailability(
                    collectGoEnabled = false,
                    locationEnabled = false,
                    goToJailEnabled = false,
                    getOutOfJailEnabled = false,
                    actionsDisabledReason = reason,
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

        private fun allDisabled() = PlayerDetailsActionAvailability(
            collectGoEnabled = false,
            locationEnabled = false,
            goToJailEnabled = false,
            getOutOfJailEnabled = false,
        )
    }
}
