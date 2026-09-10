package com.boardbanker.app.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CommonIconRegistryTest {
    @Test
    fun bankIconMapsToCommonBankDrawable() {
        assertEquals(com.boardbanker.app.R.drawable.common_bank, CommonIconRegistry.bankIconResId())
    }

    @Test
    fun jailIconMapsToCommonJailDrawable() {
        assertEquals(com.boardbanker.app.R.drawable.common_jail, CommonIconRegistry.jailIconResId())
    }

    @Test
    fun allCommonUiIconsMapToDrawables() {
        CommonUiIcon.entries.forEach { icon ->
            val resId = CommonIconRegistry.iconResId(icon)
            assertTrue("Expected non-zero drawable for $icon", resId != 0)
        }
    }

    @Test
    fun startGameAndReturnHomeMapToExpectedDrawables() {
        assertEquals(
            com.boardbanker.app.R.drawable.common_start_game,
            CommonIconRegistry.iconResId(CommonUiIcon.START_GAME),
        )
        assertEquals(
            com.boardbanker.app.R.drawable.common_return_home,
            CommonIconRegistry.iconResId(CommonUiIcon.RETURN_HOME),
        )
    }

    @Test
    fun resumeGameAndAuctionMapToExpectedDrawables() {
        assertEquals(
            com.boardbanker.app.R.drawable.common_resume_game,
            CommonIconRegistry.iconResId(CommonUiIcon.RESUME_GAME),
        )
        assertEquals(
            com.boardbanker.app.R.drawable.common_auction,
            CommonIconRegistry.iconResId(CommonUiIcon.AUCTION),
        )
    }

    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
