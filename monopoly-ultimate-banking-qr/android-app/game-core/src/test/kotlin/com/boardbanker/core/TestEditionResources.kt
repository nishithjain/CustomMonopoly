package com.boardbanker.core

import com.boardbanker.core.edition.EditionRepository
import com.boardbanker.core.edition.FileEditionFileSource
import com.boardbanker.core.model.GameDefinitions
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object TestEditionResources {
    const val CUSTOM_TEST_EDITION_ID = "custom-test"

    fun customTestDataDir(): Path {
        val tempRoot = Files.createTempDirectory("custom-test-edition").resolve("data")
        copyTree(TestFixtures.dataDir, tempRoot)
        val customEditionSource = resolveTestEditionDir(CUSTOM_TEST_EDITION_ID)
        copyTree(customEditionSource, tempRoot.resolve("editions/$CUSTOM_TEST_EDITION_ID"))
        val customCommon = customEditionSource.resolve("common_card_registry.json")
        Files.copy(
            customCommon,
            tempRoot.resolve("common/card_registry.json"),
            StandardCopyOption.REPLACE_EXISTING,
        )
        return tempRoot
    }

    fun loadCustomTestEdition(): GameDefinitions =
        EditionRepository(FileEditionFileSource(customTestDataDir())).load(CUSTOM_TEST_EDITION_ID)

    private fun resolveTestEditionDir(editionId: String): Path {
        val candidates = listOf(
            Path.of("src/test/resources/test-editions/$editionId"),
            Path.of("game-core/src/test/resources/test-editions/$editionId"),
            Path.of("../../../game-core/src/test/resources/test-editions/$editionId"),
            Path.of("../../../../monopoly-ultimate-banking-qr/android-app/game-core/src/test/resources/test-editions/$editionId"),
        )
        return candidates.firstOrNull { it.resolve("edition.json").toFile().exists() }
            ?: error("Could not locate test edition resources for $editionId")
    }

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).forEach { path ->
            val relative = source.relativize(path)
            if (relative.startsWith(Path.of("editions", CUSTOM_TEST_EDITION_ID))) {
                return@forEach
            }
            val destination = target.resolve(relative)
            if (path.toFile().isDirectory) {
                Files.createDirectories(destination)
            } else {
                destination.parent?.let { Files.createDirectories(it) }
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
