package com.boardbanker.app.audio

/**
 * Stable player ID to Android raw resource name mapping.
 *
 * Mapping is by card ID only — never by QR payload, filename, or display name.
 */
object UserCardSoundRegistry {
    const val ERROR_SOUND = "error"

    val PLAYER_SOUND_RESOURCE_NAMES: Map<String, String> = mapOf(
        "USR_01" to GameSoundRegistry.resourceNameFor(GameSound.USER_CAR),
        "USR_02" to GameSoundRegistry.resourceNameFor(GameSound.USER_HELICOPTER),
        "USR_03" to GameSoundRegistry.resourceNameFor(GameSound.USER_SHIP),
        "USR_04" to GameSoundRegistry.resourceNameFor(GameSound.USER_AEROPLANE),
    )

    fun soundResourceNameFor(playerId: String): String? =
        GameSoundRegistry.userSoundFor(playerId)?.let { GameSoundRegistry.resourceNameFor(it) }
}
