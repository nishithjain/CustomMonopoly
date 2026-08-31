package com.boardbanker.core.edition

import java.nio.file.Path
import kotlin.io.path.readText

class FileEditionFileSource(private val dataRoot: Path) : EditionFileSource {
    override fun readCommon(fileName: String): String =
        dataRoot.resolve("common").resolve(fileName).readText()

    override fun readEdition(editionId: String, fileName: String): String =
        dataRoot.resolve("editions").resolve(editionId).resolve(fileName).readText()

    override fun readCatalogIndex(): String =
        dataRoot.resolve("editions").resolve("index.json").readText()
}
