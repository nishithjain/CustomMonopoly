package com.boardbanker.app.cards

import com.boardbanker.core.card.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path

class CardFrontRegistryTest {
    private lateinit var registry: CardFrontRegistry

    @Before
    fun setUp() {
        val assetsRoot = listOf(
            Path.of("src/main/assets"),
            Path.of("app/src/main/assets"),
            Path.of("../app/src/main/assets"),
            Path.of("android-app/app/src/main/assets"),
            Path.of("c:/Personal/Monopoly/monopoly-ultimate-banking-qr/android-app/app/src/main/assets"),
        ).first { it.resolve("cards/common/android_card_front_manifest.json").toFile().exists() }
        registry = CardFrontRegistry.loadFromAssetsDirectory(assetsRoot)
    }

    @Test
    fun ukPropertyArtworkResolvesFromUkPath() {
        val result = registry.resolve("uk", CardType.PROPERTY, "PRP_01")
        assertTrue(result is CardFrontResolveResult.Found)
        val assetPath = (result as CardFrontResolveResult.Found).image.assetPath
        assertEquals("cards/editions/uk/property/prp_01.png", assetPath)
    }

    @Test
    fun ukEventArtworkResolvesFromUkPath() {
        val result = registry.resolve("uk", CardType.EVENT, "EVT_01")
        assertTrue(result is CardFrontResolveResult.Found)
        assertEquals(
            "cards/editions/uk/event/evt_01.png",
            (result as CardFrontResolveResult.Found).image.assetPath,
        )
    }

    @Test
    fun indiaPropertyResolvesFromIndiaPathWithoutUkFallback() {
        val result = registry.resolve("india", CardType.PROPERTY, "PRP_01")
        assertTrue(result is CardFrontResolveResult.Found)
        val assetPath = (result as CardFrontResolveResult.Found).image.assetPath
        assertEquals("cards/editions/india/property/prp_01.png", assetPath)
        assertNotEquals("cards/editions/uk/property/prp_01.png", assetPath)
    }

    @Test
    fun commonUserCardResolvesExplicitlyForAnyEdition() {
        val ukResult = registry.resolve("uk", CardType.USER, "USR_01")
        val indiaResult = registry.resolve("india", CardType.USER, "USR_01")
        assertTrue(ukResult is CardFrontResolveResult.Found)
        assertTrue(indiaResult is CardFrontResolveResult.Found)
        val assetPath = (ukResult as CardFrontResolveResult.Found).image.assetPath
        assertEquals("cards/common/user/usr_01.png", assetPath)
        assertEquals(assetPath, (indiaResult as CardFrontResolveResult.Found).image.assetPath)
    }

    @Test
    fun missingArtworkReportsEditionTypeAndCardId() {
        val result = registry.resolve("india", CardType.EVENT, "EVT_99")
        assertTrue(result is CardFrontResolveResult.Missing)
        val missing = result as CardFrontResolveResult.Missing
        assertEquals("india", missing.editionId)
        assertEquals(CardType.EVENT, missing.cardType)
        assertEquals("EVT_99", missing.cardId)
        assertTrue(missing.reason.isNotBlank())
    }

    @Test
    fun ukAndIndiaPropertyLookupKeysAreDistinct() {
        val uk = registry.resolve("uk", CardType.PROPERTY, "PRP_01")
        val india = registry.resolve("india", CardType.PROPERTY, "PRP_01")
        assertTrue(uk is CardFrontResolveResult.Found)
        assertTrue(india is CardFrontResolveResult.Found)
        assertNotEquals(
            (uk as CardFrontResolveResult.Found).image.assetPath,
            (india as CardFrontResolveResult.Found).image.assetPath,
        )
    }
}
