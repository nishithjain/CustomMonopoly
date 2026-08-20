package com.boardbanker.core.validation

import com.boardbanker.core.error.GameError

object PlayerNameRules {
    const val MAX_LENGTH: Int = 10

    fun normalize(raw: String): String = raw.trim()

    fun validate(raw: String): ValidationResult {
        val trimmed = normalize(raw)
        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(
                GameError.InvalidPlayerName("Please enter a player name."),
            )
        }
        if (trimmed.length > MAX_LENGTH) {
            return ValidationResult.Invalid(GameError.PlayerNameTooLong(MAX_LENGTH))
        }
        return ValidationResult.Valid(trimmed)
    }

    sealed class ValidationResult {
        data class Valid(val name: String) : ValidationResult()
        data class Invalid(val error: GameError) : ValidationResult()
    }
}
