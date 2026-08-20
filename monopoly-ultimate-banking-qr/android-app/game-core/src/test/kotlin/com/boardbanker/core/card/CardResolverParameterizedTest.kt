package com.boardbanker.core.card

import com.boardbanker.core.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CardResolverParameterizedTest(
    private val cardId: String,
    private val qrPayload: String,
    private val cardType: CardType,
    private val displayName: String,
) {
  private val resolver = DefaultCardResolver(TestFixtures.definitions)

  @Test
  fun registeredCardResolvesToExpectedIdentity() {
    val result = resolver.resolve(qrPayload)
    require(result is CardResolution.Success) { "Expected success for $cardId" }
    assertEquals(cardId, result.cardId)
    assertEquals(cardType, result.cardType)
    assertEquals(displayName, result.displayName)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cards(): List<Array<Any>> =
      TestFixtures.definitions.cards.values
        .sortedBy { it.cardId }
        .map { card ->
          arrayOf(card.cardId, card.qrPayload, card.cardType, card.name)
        }
  }
}
