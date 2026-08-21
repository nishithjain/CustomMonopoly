package com.boardbanker.app.data

import android.content.Context
import com.boardbanker.core.edition.EditionFileSource
import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.model.EditionIds
import com.boardbanker.core.model.GameDefinitions

class AndroidEditionFileSource(
    private val context: Context,
) : EditionFileSource {
    override fun readCommon(fileName: String): String =
        readAsset("game/common/$fileName")

    override fun readEdition(editionId: String, fileName: String): String =
        readAsset("game/editions/$editionId/$fileName")

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}

/**
 * Reads generated runtime assets and delegates parsing to :game-core.
 */
class AndroidGameDataLoader(
    private val context: Context,
) {
    private val repository = EditionRepository(AndroidEditionFileSource(context))

    fun load(editionId: String = EditionIds.DEFAULT): GameDefinitions = repository.load(editionId)
}
