package com.boardbanker.core.engine

import com.boardbanker.core.command.GameCommand
import com.boardbanker.core.model.GameSession

interface GameEngine {
    fun process(session: GameSession, command: GameCommand): GameResult
}
