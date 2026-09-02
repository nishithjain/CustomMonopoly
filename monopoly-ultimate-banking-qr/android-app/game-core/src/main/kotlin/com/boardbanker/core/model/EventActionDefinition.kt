package com.boardbanker.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class EventActionType {
    MOVE_THEN_PROPERTY_CHOICE,
    INCREASE_COLOR_SET_RENT_LEVEL,
    DECREASE_COLOR_SET_RENT_LEVEL,
    RESET_PROPERTY_RENT_LEVEL,
    SET_PROPERTY_RENT_LEVEL,
    SWAP_PROPERTIES,
    PAY_PER_OWNED_PROPERTY,
    CREDIT_BOTH_PLAYERS,
    TEMPORARY_RENT_CAP,
    SEND_PLAYER_TO_JAIL,
    ADJUST_SELECTED_AND_NEIGHBOUR_RENT_LEVELS,
    DECREASE_BOARD_SIDE_RENT_LEVEL,
    INCREASE_BOARD_SIDE_RENT_LEVEL,
    TOTAL_GRIDLOCK_V1,
    MOVE_TO_SPACE,
    MOVE_BACKWARD,
    BANK_CREDIT,
    BANK_DEBIT,
    PAY_EACH_PLAYER,
    COLLECT_FROM_EACH_PLAYER,
    DEBIT_PER_OWNED_PROPERTY,
    CREDIT_PER_OWNED_PROPERTY,
    NEXT_RENT_WAIVER,
    GET_OUT_OF_JAIL_PASS,
    MOVE_TO_JAIL,
    INCREASE_SELECTED_PROPERTY_RENT_LEVEL,
    DECREASE_SELECTED_PROPERTY_RENT_LEVEL,
    DRAW_ANOTHER_EVENT,
    COOPERATIVE_PROPERTY_UPGRADE,
    GAMBLE_ON_DICE_ROLL,
    SKIP_NEXT_TURN,
    FORCED_PROPERTY_SELLBACK,
    TOP_UP_BALANCE_TO_THRESHOLD,
    MOVE_TO_NEAREST_STATION,
    EXTRA_TURN,
    COMPLETE_COLOR_SET_BONUS_CREDIT,
}

@Serializable
enum class EventTargetType {
    NONE,
    PROPERTY,
    COLOR_GROUP,
    BOARD_SIDE,
    PLAYER,
    BOTH_PLAYERS,
    SELECTED_PROPERTY_AND_NEIGHBOURS,
    SELECTED_PROPERTY,
    OWNED_PROPERTY,
    ANY_PROPERTY,
    TWO_PLAYERS_AND_PROPERTIES,
    CURRENT_PLAYER,
    NEIGHBOURS_OF_SELECTED_PROPERTY,
    TWO_PLAYERS,
    ALL_PLAYERS,
    OTHER_PLAYER,
    BOARD_SIDE_OF_SELECTED_PROPERTY,
}

@Serializable
data class EventActionDefinition(
    val actionType: String,
    val targetType: String = EventTargetType.NONE.name,
    val requiresPlayerScan: Boolean = false,
    val requiresPropertyScan: Boolean = false,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val amount: Int? = null,
    val temporaryEffect: Boolean = false,
    val physicalActionRequired: Boolean = false,
    val ownedPropertiesOnly: Boolean = true,
) {
    fun parsedActionType(): EventActionType =
        EventActionType.valueOf(actionType)

    fun parsedTargetType(): EventTargetType =
        runCatching { EventTargetType.valueOf(targetType) }
            .getOrDefault(EventTargetType.NONE)
}
