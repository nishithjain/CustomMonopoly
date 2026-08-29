#!/usr/bin/env python3
"""Validate resource reorganization and audio feedback integration."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

OLD_PATH_PATTERNS = [
    re.compile(r"Resources/UserCards"),
    re.compile(r"Resources/PropertyCards"),
    re.compile(r"Resources/EventCards"),
]

EXPECTED_CARD_DIRS = [
    "Resources/Common/UserCards",
    "Resources/Editions/uk/PropertyCards",
    "Resources/Editions/uk/EventCards",
]

USER_SOUND_SOURCES = {
    "USR_01": ("Resources/Common/Sounds/UserCardSounds/Car.mp3", "user_car.mp3"),
    "USR_02": ("Resources/Common/Sounds/UserCardSounds/Helicopter.mp3", "user_helicopter.mp3"),
    "USR_03": ("Resources/Common/Sounds/UserCardSounds/Ship.mp3", "user_ship.mp3"),
    "USR_04": ("Resources/Common/Sounds/UserCardSounds/Aeroplane.mp3", "user_aeroplane.mp3"),
}

OTHER_SOUND_SOURCES = {
    "AUCTION_BEGINS": ("Resources/Common/Sounds/Other/AuctionBegins.mp3", "auction_begins.mp3"),
    "AUCTION_ENDING": ("Resources/Common/Sounds/Other/AuctionEnding.mp3", "auction_ending.mp3"),
    "ERROR": ("Resources/Common/Sounds/Other/Error.mp3", "error.mp3"),
    "GAME_STARTS": ("Resources/Common/Sounds/Other/GameStarts.mp3", "game_starts.mp3"),
    "GO": ("Resources/Common/Sounds/Other/Go.mp3", "go.mp3"),
    "GO_TO_JAIL": ("Resources/Common/Sounds/Other/GoToJail.mp3", "go_to_jail.mp3"),
    "JAIL": ("Resources/Common/Sounds/Other/Jail.mp3", "jail.mp3"),
    "KA_CHING": ("Resources/Common/Sounds/Other/KaChing.mp3", "ka_ching.mp3"),
    "LOST_GAME": ("Resources/Common/Sounds/Other/LostGame.mp3", "lost_game.mp3"),
    "PROPERTY_PURCHASED": ("Resources/Common/Sounds/Other/PropertyPurchased.mp3", "property_purchased.mp3"),
    "RENT_LEVEL_DECREASED": ("Resources/Common/Sounds/Other/RentLevelDecreased.mp3", "rent_level_decreased.mp3"),
    "RENT_LEVEL_INCREASED": ("Resources/Common/Sounds/Other/RentLevelIncreased.mp3", "rent_level_increased.mp3"),
    "RENT_TRANSFER": ("Resources/Common/Sounds/Other/RentTransfer.mp3", "rent_transfer.mp3"),
    "SCAN_CARD": ("Resources/Common/Sounds/Other/ScanCard.mp3", "scan_card.mp3"),
    "MONEY_LOST": ("Resources/Common/Sounds/Other/SomeoneJustTookYourMoney.mp3", "someone_just_took_your_money.mp3"),
    "UNDO": ("Resources/Common/Sounds/Other/Undo.mp3", "undo.mp3"),
    "UNDO_LAST_ACTION": ("Resources/Common/Sounds/Other/UndoLastAction.mp3", "undo_last_action.mp3"),
    "WINNER": ("Resources/Common/Sounds/Other/Winner.mp3", "winner.mp3"),
    "COLOR_SET_COMPLETE": ("Resources/Common/Sounds/Other/ColorSetComplete.mp3", "color_set_complete.mp3"),
}

REQUIRED_GAMEPLAY_METHODS = [
    "playGameStarted",
    "playPropertyPurchased",
    "playColorSetComplete",
    "playRentTransfer",
    "playRentLevelIncreased",
    "playRentLevelDecreased",
    "playGo",
    "playGoToJail",
    "playJail",
    "playAuctionBegins",
    "playAuctionEnding",
    "playKaChing",
    "playMoneyLost",
    "playUndo",
    "playUndoLastAction",
    "playLostGame",
    "playWinner",
    "playScanPrompt",
]

ANDROID_AUDIO_FILES = [
    "tools/sync_android_media.py",
    "android-app/app/src/main/java/com/boardbanker/app/audio/GameAudioFeedback.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/GameSound.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/GameSoundRegistry.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/GameplayOutcomeAudio.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/UserCardSoundRegistry.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/ScanAudioFeedback.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/InvalidUserActionAudio.kt",
    "android-app/app/src/main/java/com/boardbanker/app/audio/SoundPoolGameAudioFeedback.kt",
    "android-app/app/src/main/java/com/boardbanker/app/scanner/ScannerViewModel.kt",
    "android-app/app/src/test/java/com/boardbanker/app/audio/GameplayOutcomeAudioTest.kt",
    "docs/USER_CARD_AUDIO.md",
    "docs/AUDIO_FEEDBACK.md",
]

GAME_CORE_ANDROID_PATTERN = re.compile(r"^\s*import\s+(android\.|androidx\.)")


def find_roots() -> tuple[Path, Path]:
    tools_dir = Path(__file__).resolve().parent
    project_root = tools_dir.parent
    workspace_root = project_root.parent
    if not (workspace_root / "Resources").is_dir():
        workspace_root = project_root
    return workspace_root, project_root


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def active_tool_files(project_root: Path) -> list[Path]:
    tools_dir = project_root / "tools"
    files = sorted(tools_dir.glob("*.py"))
    return [path for path in files if path.name != "__init__.py"]


def check_old_paths_in_active_tools(project_root: Path) -> list[str]:
    problems: list[str] = []
    skip_files = {"validate_audio_feedback.py", "migrate_to_edition_layout.py"}
    for path in active_tool_files(project_root):
        if path.name in skip_files:
            continue
        text = read_text(path)
        for pattern in OLD_PATH_PATTERNS:
            if pattern.search(text):
                problems.append(f"Active tool still references old path in {path.name}: {pattern.pattern}")
    cards_json = read_text(project_root / "data" / "common" / "card_registry.json")
    for pattern in OLD_PATH_PATTERNS:
        if pattern.search(cards_json):
            problems.append(f"cards.json still references old path: {pattern.pattern}")
    return problems


def check_registry_mapping(project_root: Path) -> list[str]:
    problems: list[str] = []
    registry = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "GameSoundRegistry.kt"
    )
    user_registry = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "UserCardSoundRegistry.kt"
    )
    for player_id, (_, raw_name) in USER_SOUND_SOURCES.items():
        raw_key = raw_name.replace(".mp3", "")
        if raw_key not in registry or f"\"{player_id}\"" not in registry:
            problems.append(f"Missing GameSoundRegistry mapping for {player_id}")
        if f"\"{player_id}\"" not in user_registry or "soundResourceNameFor" not in user_registry:
            problems.append(f"Missing UserCardSoundRegistry mapping for {player_id}")
    for semantic, (_, raw_name) in OTHER_SOUND_SOURCES.items():
        raw_key = raw_name.replace(".mp3", "")
        if raw_key not in registry:
            problems.append(f"Missing GameSoundRegistry mapping for {semantic}")
    if "ERROR" not in registry and "\"error\"" not in registry:
        problems.append("Missing error sound mapping in GameSoundRegistry")
    return problems


def check_scan_trigger(project_root: Path) -> list[str]:
    problems: list[str] = []
    scanner_vm = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "scanner" / "ScannerViewModel.kt"
    )
    scan_audio = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "ScanAudioFeedback.kt"
    )
    if "ScanAudioFeedback.onScanProcessed" not in scanner_vm:
        problems.append("ScannerViewModel does not call ScanAudioFeedback.onScanProcessed")
    if "CardType.USER" not in scan_audio or "playUserCard" not in scan_audio:
        problems.append("ScanAudioFeedback missing USER card trigger")
    if "playUserCardThenError" not in scan_audio:
        problems.append("ScanAudioFeedback missing wrong USER sequencing")
    if "UnknownCard" not in scan_audio or "playError" not in scan_audio:
        problems.append("ScanAudioFeedback missing unknown QR error trigger")
    return problems


def check_gameplay_router(project_root: Path) -> list[str]:
    problems: list[str] = []
    feedback = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "GameAudioFeedback.kt"
    )
    router = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "GameplayOutcomeAudio.kt"
    )
    for method in REQUIRED_GAMEPLAY_METHODS:
        if method not in feedback:
            problems.append(f"GameAudioFeedback missing {method}")
        if method.replace("play", "GameplayAudioCue.")[:20] and method not in router:
            pass
    required_router_tokens = [
        "COLOR_SET_COMPLETE",
        "PROPERTY_PURCHASED",
        "RENT_TRANSFER",
        "RENT_LEVEL_INCREASED",
        "RENT_LEVEL_DECREASED",
        "GO_TO_JAIL",
        "KA_CHING",
        "MONEY_LOST",
        "GAME_STARTS",
        "AUCTION_BEGINS",
        "AUCTION_ENDING",
    ]
    for token in required_router_tokens:
        if token not in router:
            problems.append(f"GameplayOutcomeAudio missing cue mapping for {token}")
    if "GameplayOutcomeAudioTest" not in read_text(
        project_root / "android-app" / "app" / "src" / "test" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "GameplayOutcomeAudioTest.kt"
    ):
        problems.append("GameplayOutcomeAudioTest missing")
    return problems


def check_game_core_android_free(project_root: Path) -> list[str]:
    problems: list[str] = []
    game_core = project_root / "android-app" / "game-core" / "src" / "main" / "kotlin"
    for path in game_core.rglob("*.kt"):
        for line in read_text(path).splitlines():
            if GAME_CORE_ANDROID_PATTERN.match(line):
                problems.append(f"Android import found in game-core: {path.relative_to(project_root)}")
                break
    audio_in_core = list(game_core.rglob("*Audio*.kt"))
    if audio_in_core:
        problems.append("Audio code found in game-core")
    return problems


def validate(workspace_root: Path, project_root: Path) -> tuple[list[str], dict]:
    stats: dict = {
        "card_dirs": 0,
        "user_sources": 0,
        "other_sources": 0,
        "android_raw": 0,
    }
    problems: list[str] = []

    for rel in EXPECTED_CARD_DIRS:
        if (workspace_root / rel).is_dir():
            stats["card_dirs"] += 1
        else:
            problems.append(f"Missing card directory: {rel}")

    for player_id, (source_rel, raw_name) in USER_SOUND_SOURCES.items():
        source = workspace_root / source_rel
        raw = project_root / "android-app" / "app" / "src" / "main" / "res" / "raw" / raw_name
        if source.is_file():
            stats["user_sources"] += 1
        else:
            problems.append(f"Missing user sound source for {player_id}: {source_rel}")
        if raw.is_file():
            stats["android_raw"] += 1
        else:
            problems.append(f"Missing Android raw resource: res/raw/{raw_name}")

    for semantic, (source_rel, raw_name) in OTHER_SOUND_SOURCES.items():
        source = workspace_root / source_rel
        raw = project_root / "android-app" / "app" / "src" / "main" / "res" / "raw" / raw_name
        if source.is_file():
            stats["other_sources"] += 1
        else:
            problems.append(f"Missing other sound source for {semantic}: {source_rel}")
        if raw.is_file():
            stats["android_raw"] += 1
        else:
            problems.append(f"Missing Android raw resource: res/raw/{raw_name}")

    expected_total = len(USER_SOUND_SOURCES) + len(OTHER_SOUND_SOURCES)
    if stats["android_raw"] != expected_total:
        problems.append(f"Expected {expected_total} Android raw resources, found {stats['android_raw']}")

    for rel in ANDROID_AUDIO_FILES:
        if not (project_root / rel).is_file():
            problems.append(f"Missing required audio file: {rel}")

    problems.extend(check_old_paths_in_active_tools(project_root))
    problems.extend(check_registry_mapping(project_root))
    problems.extend(check_scan_trigger(project_root))
    problems.extend(check_gameplay_router(project_root))
    problems.extend(check_game_core_android_free(project_root))

    invalid_audio = read_text(
        project_root / "android-app" / "app" / "src" / "main" / "java"
        / "com" / "boardbanker" / "app" / "audio" / "InvalidUserActionAudio.kt"
    )
    if "InvalidUserActionAudio" not in invalid_audio:
        problems.append("InvalidUserActionAudio helper missing")

    return problems, stats


def write_report(project_root: Path, problems: list[str], stats: dict) -> Path:
    output = project_root / "data" / "audio_feedback_validation.txt"
    expected_total = 22
    result = "PASS" if not problems else "FAIL"
    lines = [
        "AUDIO FEEDBACK VALIDATION",
        "=========================",
        "",
        "Card directories found",
        "Expected: 3",
        f"Found:    {stats['card_dirs']}",
        "",
        "User sound sources",
        "Expected: 4",
        f"Found:    {stats['user_sources']}",
        "",
        "Other sound sources",
        "Expected: 18",
        f"Found:    {stats['other_sources']}",
        "",
        "Android raw resources",
        f"Expected: {expected_total}",
        f"Found:    {stats['android_raw']}",
        "",
        "Audio outside game-core",
        "Expected: YES",
        f"Found:    {'YES' if not any('game-core' in p for p in problems if 'Audio' in p or 'Android import' in p) else 'NO'}",
        "",
        f"RESULT: {result}",
    ]
    if problems:
        lines.extend(["", "Problems:", "---------"])
        lines.extend(f"- {problem}" for problem in problems)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {output}")
    return output


def main() -> int:
    workspace_root, project_root = find_roots()
    problems, stats = validate(workspace_root, project_root)
    write_report(project_root, problems, stats)

    card_registry = subprocess.run(
        [sys.executable, str(project_root / "tools" / "validate_card_registry.py")],
        cwd=project_root,
        capture_output=True,
        text=True,
    )

    if problems:
        print(f"Validation FAILED with {len(problems)} problem(s).")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    if card_registry.returncode != 0:
        print("Card registry validation failed after path update.", file=sys.stderr)
        print(card_registry.stdout)
        print(card_registry.stderr, file=sys.stderr)
        return 1

    print("Validation PASSED.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
