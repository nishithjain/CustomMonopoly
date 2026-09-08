package com.boardbanker.app.audio

/**
 * Authoritative semantic-sound to Android raw resource name mapping.
 */
object GameSoundRegistry {
    private val resourceNames: Map<GameSound, String> = mapOf(
        GameSound.USER_CAR to "user_car",
        GameSound.USER_HELICOPTER to "user_helicopter",
        GameSound.USER_SHIP to "user_ship",
        GameSound.USER_AEROPLANE to "user_aeroplane",
        GameSound.AUCTION_BEGINS to "auction_begins",
        GameSound.AUCTION_ENDING to "auction_ending",
        GameSound.ERROR to "error",
        GameSound.GAME_STARTS to "game_starts",
        GameSound.GO to "go",
        GameSound.LOCATION to "location",
        GameSound.GO_TO_JAIL to "go_to_jail",
        GameSound.JAIL_RELEASE to "jail",
        GameSound.JAIL_PASS to "jail_pass",
        GameSound.BANK_CREDIT to "ka_ching",
        GameSound.BANK_DEBIT to "someone_just_took_your_money",
        GameSound.MONEY_TRANSFER to "money_transfer",
        GameSound.LOST_GAME to "lost_game",
        GameSound.PROPERTY_PURCHASED to "property_purchased",
        GameSound.ENERGY_GRID_PURCHASED to "energy_grid_purchased",
        GameSound.RENT_TRANSFER to "rent_transfer",
        GameSound.RENT_RELIEF to "rent_relief",
        GameSound.RENT_LEVEL_DECREASED to "rent_level_decreased",
        GameSound.RENT_LEVEL_INCREASED to "rent_level_increased",
        GameSound.PROPERTY_SOLD to "property_sold",
        GameSound.SCAN_CARD to "scan_card",
        GameSound.EVENT_APPLIED to "event_applied",
        GameSound.TURN_CHANGED to "turn_changed",
        GameSound.TURN_SKIPPED to "turn_skipped",
        GameSound.EXTRA_TURN to "extra_turn",
        GameSound.DICE_ROLL to "dice_roll",
        GameSound.MOVE_PLAYER to "move_player",
        GameSound.LUCKY_DRAW to "lucky_draw",
        GameSound.UNDO to "undo",
        GameSound.UNDO_LAST_ACTION to "undo_last_action",
        GameSound.WINNER to "winner",
        GameSound.COLOR_SET_COMPLETE to "color_set_complete",
    )

    private val playerSoundById: Map<String, GameSound> = mapOf(
        "USR_01" to GameSound.USER_CAR,
        "USR_02" to GameSound.USER_HELICOPTER,
        "USR_03" to GameSound.USER_SHIP,
        "USR_04" to GameSound.USER_AEROPLANE,
    )

    val allSounds: Set<GameSound> = resourceNames.keys

    fun resourceNameFor(sound: GameSound): String = resourceNames.getValue(sound)

    fun userSoundFor(playerId: String): GameSound? = playerSoundById[playerId]
}
