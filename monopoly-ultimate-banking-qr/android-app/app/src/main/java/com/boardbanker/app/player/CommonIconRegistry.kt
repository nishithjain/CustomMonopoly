package com.boardbanker.app.player

import androidx.annotation.DrawableRes
import com.boardbanker.app.R

enum class CommonUiIcon {
    ABANDON_GAME,
    ACCEPT_CARD,
    AUCTION,
    BACK,
    BANK,
    BUY_PROPERTY,
    CAMERA,
    CHECK,
    COLLECT_GO,
    COLLECT,
    CANCEL,
    COLOR_SET_COMPLETE,
    CURRENT_TURN,
    DEBT,
    DICE,
    DO_NOTHING,
    END_GAME,
    END_TURN,
    ENERGY_GRID,
    ERROR,
    EVENT_CARD,
    GAME_STATUS,
    JAIL,
    LOCATION,
    LUCKY_DRAW,
    MONEY_TRANSFER,
    PLAYER_DETAILS,
    PROPERTY,
    RECENT_BANKING,
    RENT,
    RENT_DECREASE,
    RENT_INCREASE,
    RESUME_GAME,
    RETURN_HOME,
    SCAN_CARD,
    START_GAME,
    UNDO_LAST_ACTION,
}

object CommonIconRegistry {
    @DrawableRes
    fun iconResId(icon: CommonUiIcon): Int = when (icon) {
        CommonUiIcon.ABANDON_GAME -> R.drawable.common_abandon_game
        CommonUiIcon.ACCEPT_CARD -> R.drawable.common_accept_card
        CommonUiIcon.AUCTION -> R.drawable.common_auction
        CommonUiIcon.BACK -> R.drawable.common_back
        CommonUiIcon.BANK -> R.drawable.common_bank
        CommonUiIcon.BUY_PROPERTY -> R.drawable.common_buy_property
        CommonUiIcon.CAMERA -> R.drawable.common_camera
        CommonUiIcon.CHECK -> R.drawable.common_check
        CommonUiIcon.COLLECT_GO -> R.drawable.common_collect_go
        CommonUiIcon.COLLECT -> R.drawable.common_collect
        CommonUiIcon.CANCEL -> R.drawable.common_cancel
        CommonUiIcon.COLOR_SET_COMPLETE -> R.drawable.common_color_set_complete
        CommonUiIcon.CURRENT_TURN -> R.drawable.common_current_turn
        CommonUiIcon.DEBT -> R.drawable.common_debt
        CommonUiIcon.DICE -> R.drawable.common_dice
        CommonUiIcon.DO_NOTHING -> R.drawable.common_do_nothing
        CommonUiIcon.END_GAME -> R.drawable.common_end_game
        CommonUiIcon.END_TURN -> R.drawable.common_end_turn
        CommonUiIcon.ENERGY_GRID -> R.drawable.common_energy_grid
        CommonUiIcon.ERROR -> R.drawable.common_error
        CommonUiIcon.EVENT_CARD -> R.drawable.common_event_card
        CommonUiIcon.GAME_STATUS -> R.drawable.common_game_status
        CommonUiIcon.JAIL -> R.drawable.common_jail
        CommonUiIcon.LOCATION -> R.drawable.common_location
        CommonUiIcon.LUCKY_DRAW -> R.drawable.common_lucky_draw
        CommonUiIcon.MONEY_TRANSFER -> R.drawable.common_money_transfer
        CommonUiIcon.PLAYER_DETAILS -> R.drawable.common_player_details
        CommonUiIcon.PROPERTY -> R.drawable.common_property
        CommonUiIcon.RECENT_BANKING -> R.drawable.common_recent_banking
        CommonUiIcon.RENT -> R.drawable.common_rent
        CommonUiIcon.RENT_DECREASE -> R.drawable.common_rent_decrease
        CommonUiIcon.RENT_INCREASE -> R.drawable.common_rent_increase
        CommonUiIcon.RESUME_GAME -> R.drawable.common_resume_game
        CommonUiIcon.RETURN_HOME -> R.drawable.common_return_home
        CommonUiIcon.SCAN_CARD -> R.drawable.common_scan_card
        CommonUiIcon.START_GAME -> R.drawable.common_start_game
        CommonUiIcon.UNDO_LAST_ACTION -> R.drawable.common_undo_last_action
    }

    @DrawableRes
    fun bankIconResId(): Int = iconResId(CommonUiIcon.BANK)

    @DrawableRes
    fun jailIconResId(): Int = iconResId(CommonUiIcon.JAIL)
}
