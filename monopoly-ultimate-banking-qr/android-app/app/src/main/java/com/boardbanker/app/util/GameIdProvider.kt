package com.boardbanker.app.util

import java.util.UUID

object GameIdProvider {
    fun newGameId(): String = UUID.randomUUID().toString()
}
