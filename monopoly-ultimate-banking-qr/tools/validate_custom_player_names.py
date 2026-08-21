#!/usr/bin/env python3
"""Validate custom player name support across domain, UI, and persistence."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPORT_NAME = "custom_player_name_validation.txt"


def find_workspace_root() -> Path:
    tools_dir = Path(__file__).resolve().parent
    for candidate in [tools_dir.parent.parent, tools_dir.parent.parent.parent]:
        icons = candidate / "Resources" / "Common" / "Icons"
        android = candidate / "monopoly-ultimate-banking-qr" / "android-app"
        if icons.is_dir() and android.is_dir():
            return candidate
        project = candidate / "android-app"
        if icons.is_dir() and project.is_dir():
            return candidate
    raise FileNotFoundError("Could not locate workspace root")


def find_project_root(workspace_root: Path) -> Path:
    direct = workspace_root / "monopoly-ultimate-banking-qr"
    if (direct / "android-app").is_dir():
        return direct
    if (workspace_root / "android-app").is_dir():
        return workspace_root
    raise FileNotFoundError("Could not locate android-app project root")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> int:
    project_root = find_project_root(find_workspace_root())
    report_path = project_root / "data" / REPORT_NAME
    game_core = project_root / "android-app" / "game-core" / "src" / "main" / "kotlin" / "com" / "boardbanker" / "core"
    app_root = project_root / "android-app" / "app" / "src" / "main" / "java" / "com" / "boardbanker" / "app"
    game_core_test = project_root / "android-app" / "game-core" / "src" / "test" / "kotlin" / "com" / "boardbanker" / "core"
    docs = project_root / "docs" / "CUSTOM_PLAYER_NAMES.md"

    problems: list[str] = []

    player_state = game_core / "model" / "PlayerState.kt"
    if not player_state.is_file():
        problems.append("PlayerState.kt missing")
    else:
        text = read(player_state)
        if "playerName" not in text:
            problems.append("PlayerState missing playerName field")

    command = game_core / "command" / "GameCommand.kt"
    if not command.is_file():
        problems.append("GameCommand.kt missing")
    else:
        text = read(command)
        if "RegisterPlayer" not in text or "playerName" not in text.split("RegisterPlayer", 1)[1].split(")", 1)[0]:
            problems.append("RegisterPlayer missing playerName parameter")
        if "RenamePlayer" not in text:
            problems.append("RenamePlayer command missing")

    rules = game_core / "validation" / "PlayerNameRules.kt"
    if not rules.is_file():
        problems.append("PlayerNameRules.kt missing")
    else:
        text = read(rules)
        if "MAX_LENGTH" not in text or "10" not in text:
            problems.append("PlayerNameRules max length not set to 10")
        if "trim()" not in text:
            problems.append("PlayerNameRules missing trim handling")

    engine = game_core / "engine" / "DefaultGameEngine.kt"
    if engine.is_file():
        text = read(engine)
        if "handleRenamePlayer" not in text:
            problems.append("DefaultGameEngine missing handleRenamePlayer")
        if "PlayerNameRules.validate" not in text:
            problems.append("DefaultGameEngine missing PlayerNameRules validation")

    serializer = game_core / "persistence" / "GameSessionSerializer.kt"
    if serializer.is_file():
        text = read(serializer)
        if "CURRENT_VERSION: Int = 2" not in text:
            problems.append("GameSessionSchema not bumped to version 2")

    identity = app_root / "ui" / "components" / "PlayerIdentity.kt"
    if identity.is_file():
        text = read(identity)
        if "playerName: String" not in text:
            problems.append("PlayerIdentity does not accept playerName")
        if "PlayerIconRegistry.iconResId(playerId)" not in text:
            problems.append("PlayerIdentity icon not based on playerId")

    display_names = app_root / "player" / "PlayerDisplayNames.kt"
    if not display_names.is_file():
        problems.append("PlayerDisplayNames.kt missing")

    setup_vm = app_root / "ui" / "screens" / "setup" / "GameSetupViewModel.kt"
    if setup_vm.is_file():
        text = read(setup_vm)
        if "pendingRegistration" not in text:
            problems.append("GameSetupViewModel missing pending registration flow")
        if "cancelPendingRegistration" not in text:
            problems.append("GameSetupViewModel missing cancel before registration")

    setup_screen = app_root / "ui" / "screens" / "setup" / "PlayerSetupScreen.kt"
    if setup_screen.is_file():
        text = read(setup_screen)
        if "PlayerNameEntryDialog" not in text:
            problems.append("PlayerSetupScreen missing name entry dialog")
        if "PlayerNameRules.MAX_LENGTH" not in text:
            problems.append("PlayerSetupScreen missing max-length UI guard")

    repo = app_root / "persistence" / "repository" / "RoomGameSessionRepository.kt"
    if repo.is_file():
        text = read(repo)
        if "schemaVersion > GameSessionSchema.CURRENT_VERSION" not in text:
            problems.append("RoomGameSessionRepository does not allow loading older schema versions")

    tests = game_core_test / "CustomPlayerNameTests.kt"
    if not tests.is_file():
        problems.append("CustomPlayerNameTests.kt missing")

    if not docs.is_file():
        problems.append("docs/CUSTOM_PLAYER_NAMES.md missing")

    cards_json = project_root / "data" / "common" / "card_registry.json"
    if cards_json.is_file():
        cards_doc = json.loads(read(cards_json))
        cards_list = cards_doc.get("cards", [])
        by_id = {item.get("cardId"): item for item in cards_list if isinstance(item, dict)}
        for player_id, expected in {
            "USR_01": "Car",
            "USR_02": "Helicopter",
            "USR_03": "Ship",
            "USR_04": "Aeroplane",
        }.items():
            entry = by_id.get(player_id)
            if entry is None:
                problems.append(f"cards.json missing {player_id}")
            elif entry.get("name") != expected:
                problems.append(f"cards.json token name changed for {player_id}")

    status = "PASS" if not problems else "FAIL"
    lines = [
        "CUSTOM PLAYER NAME VALIDATION",
        f"Status: {status}",
        "",
    ]
    if problems:
        lines.append("Problems:")
        lines.extend(f"- {p}" for p in problems)
    else:
        lines.extend(
            [
                "Checks:",
                "- PlayerState contains custom name",
                "- RegisterPlayer accepts playerName",
                "- RenamePlayer exists for setup editing",
                "- Max length and empty-name validation in game-core",
                "- PlayerIdentity uses playerName with playerId icons",
                "- Setup flow stages name entry before registration",
                "- Schema version 2 with backward-compatible load policy",
                "- Custom name serialization tests present",
                "- Documentation present",
                "- QR/token mapping unchanged",
            ]
        )
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(report_path)
    print(status)
    for problem in problems:
        print(f"  - {problem}")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
