package com.boardbanker.app.persistence.repository

import com.boardbanker.core.persistence.RawSavedGameLoadResult
import com.boardbanker.core.persistence.RawSavedGameReader
import kotlinx.coroutines.flow.Flow

interface ObservableRawSavedGameReader : RawSavedGameReader {
    fun observeLatestRaw(): Flow<RawSavedGameLoadResult>
}
