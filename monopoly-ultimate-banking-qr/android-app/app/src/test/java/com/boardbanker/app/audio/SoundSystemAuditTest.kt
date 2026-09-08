package com.boardbanker.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Complete sound-system audit for Batches 1–5.
 */
class SoundSystemAuditTest {
    private val expectedOtherSounds = mapOf(
        "AuctionBegins.mp3" to GameSound.AUCTION_BEGINS,
        "AuctionEnding.mp3" to GameSound.AUCTION_ENDING,
        "ColorSetComplete.mp3" to GameSound.COLOR_SET_COMPLETE,
        "DiceRoll.mp3" to GameSound.DICE_ROLL,
        "EnergyGridPurchased.mp3" to GameSound.ENERGY_GRID_PURCHASED,
        "Error.mp3" to GameSound.ERROR,
        "EventApplied.mp3" to GameSound.EVENT_APPLIED,
        "ExtraTurn.mp3" to GameSound.EXTRA_TURN,
        "GameStarts.mp3" to GameSound.GAME_STARTS,
        "Go.mp3" to GameSound.GO,
        "GoToJail.mp3" to GameSound.GO_TO_JAIL,
        "Jail.mp3" to GameSound.JAIL_RELEASE,
        "JailPass.mp3" to GameSound.JAIL_PASS,
        "KaChing.mp3" to GameSound.BANK_CREDIT,
        "Location.mp3" to GameSound.LOCATION,
        "LostGame.mp3" to GameSound.LOST_GAME,
        "LuckyDraw.mp3" to GameSound.LUCKY_DRAW,
        "MoneyTransfer.mp3" to GameSound.MONEY_TRANSFER,
        "MovePlayer.mp3" to GameSound.MOVE_PLAYER,
        "PropertyPurchased.mp3" to GameSound.PROPERTY_PURCHASED,
        "PropertySold.mp3" to GameSound.PROPERTY_SOLD,
        "RentLevelDecreased.mp3" to GameSound.RENT_LEVEL_DECREASED,
        "RentLevelIncreased.mp3" to GameSound.RENT_LEVEL_INCREASED,
        "RentRelief.mp3" to GameSound.RENT_RELIEF,
        "RentTransfer.mp3" to GameSound.RENT_TRANSFER,
        "ScanCard.mp3" to GameSound.SCAN_CARD,
        "SomeoneJustTookYourMoney.mp3" to GameSound.BANK_DEBIT,
        "TurnChanged.mp3" to GameSound.TURN_CHANGED,
        "TurnSkipped.mp3" to GameSound.TURN_SKIPPED,
        "Undo.mp3" to GameSound.UNDO,
        "UndoLastAction.mp3" to GameSound.UNDO_LAST_ACTION,
        "Winner.mp3" to GameSound.WINNER,
    )

    @Test
    fun everyBatchSoundIdentifierMapsToExactlyOneRawResource() {
        val mappedSounds = GameSound.entries.filter { it !in USER_CARD_SOUNDS }
        assertEquals(expectedOtherSounds.size, mappedSounds.size)
        expectedOtherSounds.values.forEach { sound ->
            val resourceName = GameSoundRegistry.resourceNameFor(sound)
            assertTrue("Missing mapping for $sound", resourceName.isNotBlank())
            val duplicates = GameSound.entries.filter { GameSoundRegistry.resourceNameFor(it) == resourceName }
            assertEquals("Duplicate raw resource for $resourceName", 1, duplicates.size)
        }
    }

    @Test
    fun synchronizedAndroidAssetsExistForEveryBatchSound() {
        val rawDir = locateRawDirectory()
        expectedOtherSounds.forEach { (sourceName, sound) ->
            val rawName = "${GameSoundRegistry.resourceNameFor(sound)}.mp3"
            val rawPath = rawDir.resolve(rawName)
            assertTrue("Missing Android asset $rawName for $sourceName", Files.exists(rawPath))
        }
    }

    @Test
    fun userCardSoundsRemainMappedSeparately() {
        assertEquals("user_car", UserCardSoundRegistry.soundResourceNameFor("USR_01"))
        assertEquals("user_helicopter", UserCardSoundRegistry.soundResourceNameFor("USR_02"))
        assertEquals("user_ship", UserCardSoundRegistry.soundResourceNameFor("USR_03"))
        assertEquals("user_aeroplane", UserCardSoundRegistry.soundResourceNameFor("USR_04"))
    }

    private fun locateRawDirectory(): Path {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val candidates = listOf(
            cwd.resolve("src/main/res/raw"),
            cwd.resolve("app/src/main/res/raw"),
            cwd.resolve("android-app/app/src/main/res/raw"),
            cwd.parent?.resolve("src/main/res/raw"),
            cwd.parent?.resolve("app/src/main/res/raw"),
            cwd.parent?.resolve("android-app/app/src/main/res/raw"),
        ).filterNotNull()
        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error("Could not locate res/raw from $cwd")
    }

    private companion object {
        val USER_CARD_SOUNDS = setOf(
            GameSound.USER_CAR,
            GameSound.USER_HELICOPTER,
            GameSound.USER_SHIP,
            GameSound.USER_AEROPLANE,
        )
    }
}
