package com.boardbanker.app.ui

import com.boardbanker.app.AppTestSupport
import com.boardbanker.app.banking.BankingResultMapper
import com.boardbanker.app.game.ActiveGamePresentation
import com.boardbanker.core.model.PropertyDisplayNames
import com.boardbanker.core.model.displayNameWithNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyDisplayNamesUiTest {
    private val definitions = AppTestSupport.definitions

    @Test
    fun activeGameOwnedPropertiesUseFormattedNames() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            com.boardbanker.core.command.GameCommand.PurchaseProperty("USR_01", "PRP_01"),
        ).session

        val owned = ActiveGamePresentation.buildOwnedProperties(session, "USR_01", definitions)

        assertEquals("[1] Old Kent Road", owned.single().propertyName)
        assertEquals("Old Kent Road", definitions.properties["PRP_01"]!!.name)
    }

    @Test
    fun bankingAuctionWinMessageUsesFormattedPropertyName() {
        var session = AppTestSupport.newGame()
        session = AppTestSupport.engine.process(
            session,
            com.boardbanker.core.command.GameCommand.PurchaseProperty("USR_01", "PRP_15"),
        ).session
        val result = com.boardbanker.core.engine.GameResult(
            session = session,
            outcome = com.boardbanker.core.engine.GameOutcome.SUCCESS,
            transactions = listOf(
                com.boardbanker.core.model.Transaction(
                    transactionId = "TX_1",
                    gameId = session.gameId,
                    timestamp = 1L,
                    transactionType = com.boardbanker.core.model.TransactionType.AUCTION_WIN,
                    fromEntity = "USR_01",
                    toEntity = com.boardbanker.core.model.EntityRef.BANK,
                    playerId = "USR_01",
                    propertyId = "PRP_15",
                    amount = 100,
                ),
            ),
        )
        val ui = BankingResultMapper(definitions).mapAuctionWin(result, "PRP_15", "USR_01")

        assertTrue(ui.primaryMessage.contains("[15] Leicester Square"))
        assertFalse(ui.primaryMessage.contains("[015]"))
    }

    @Test
    fun propertyDisplayNamesLookupMatchesExtension() {
        val property = definitions.properties["PRP_15"]!!
        assertEquals("[15] Leicester Square", property.displayNameWithNumber())
        assertEquals(
            property.displayNameWithNumber(),
            PropertyDisplayNames.displayNameWithNumber("PRP_15", definitions),
        )
    }
}
