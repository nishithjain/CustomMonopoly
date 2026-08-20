#!/usr/bin/env python3
"""Master V1 release readiness validator."""

from __future__ import annotations

import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ANDROID_IMPORT_PATTERN = re.compile(r"^\s*import\s+(android\.|androidx\.)")

VALIDATORS = [
    "validate_android_foundation.py",
    "validate_game_engine.py",
    "validate_qr_scanner.py",
    "validate_persistence.py",
    "validate_player_setup_integration.py",
    "validate_gameplay_integration.py",
    "validate_advanced_banking.py",
    "validate_card_registry.py",
    "validate_game_data.py",
    "validate_rule_spec.py",
]

REQUIRED_DOCS = [
    "docs/V1_FEATURE_FREEZE.md",
    "docs/V1_RELEASE_NOTES.md",
    "docs/V1_TEST_REPORT.md",
    "docs/V1_KNOWN_LIMITATIONS.md",
    "docs/V1_RELEASE_STATUS.md",
    "docs/USER_GUIDE.md",
    "docs/ORIGINAL_UNIT_COMPATIBILITY_TEST.md",
    "docs/PHYSICAL_QR_TEST_CHECKLIST.md",
    "docs/PHYSICAL_GAMEPLAY_TEST_CHECKLIST.md",
    "docs/FULL_GAME_TEST_LOG.md",
]


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "android-app").is_dir() and (candidate / "data").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def run_validator(tools_dir: Path, name: str) -> tuple[bool, str]:
    result = subprocess.run(
        [sys.executable, str(tools_dir / name)],
        capture_output=True,
        text=True,
        cwd=tools_dir.parent,
    )
    output = (result.stdout or "") + (result.stderr or "")
    passed = result.returncode == 0 or "PASS" in output.splitlines()[-3:]
  # validators exit 0 on pass; some print PASS in body
    if result.returncode != 0:
        passed = "PASS" in output and "FAIL" not in output.split("RESULT:")[-1]
    return result.returncode == 0, output.strip().splitlines()[-1] if output else "no output"


def count_physical_qr_passes(checklist_path: Path) -> dict[str, int]:
    text = read_text(checklist_path)
    counts = Counter()
    for line in text.splitlines():
        if not line.startswith("|") or line.startswith("| Card ID") or line.startswith("|---"):
            continue
        parts = [part.strip() for part in line.strip("|").split("|")]
        if len(parts) < 6:
            continue
        card_type = parts[2]
        pass_fail = parts[5].upper()
        if pass_fail == "PASS":
            counts[card_type] += 1
            counts["TOTAL"] += 1
    return dict(counts)


def check_game_core_pure(game_core_dir: Path) -> tuple[bool, list[str]]:
    violations: list[str] = []
    for file_path in (game_core_dir / "src" / "main" / "kotlin").rglob("*.kt"):
        for line_number, line in enumerate(file_path.read_text(encoding="utf-8").splitlines(), start=1):
            if ANDROID_IMPORT_PATTERN.match(line):
                violations.append(f"{file_path}:{line_number}")
    return len(violations) == 0, violations


def main() -> int:
    project_root = find_project_root()
    tools_dir = project_root / "tools"
    android_root = project_root / "android-app"
    app_dir = android_root / "app"
    output_path = project_root / "data" / "v1_release_validation.txt"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    cards = json.loads((project_root / "data" / "cards.json").read_text(encoding="utf-8"))["cards"]
    type_counts = Counter(card["cardType"] for card in cards)
    record("4 User cards", type_counts["USER"] == 4, f"count={type_counts['USER']}")
    record("22 Properties", type_counts["PROPERTY"] == 22, f"count={type_counts['PROPERTY']}")
    record("23 Events", type_counts["EVENT"] == 23, f"count={type_counts['EVENT']}")
    record("49 total cards", len(cards) == 49, f"count={len(cards)}")

    properties = json.loads((project_root / "data" / "properties.json").read_text(encoding="utf-8"))["properties"]
    events = json.loads((project_root / "data" / "events.json").read_text(encoding="utf-8"))["events"]
    event_rules = json.loads((project_root / "data" / "event_engine_rules.json").read_text(encoding="utf-8"))["events"]
    record("22 Property definitions", len(properties) == 22)
    record("23 Event definitions", len(events) == 23)
    record("23 Event Engine rules", len(event_rules) == 23)

    pure_ok, violations = check_game_core_pure(android_root / "game-core")
    record("game-core pure Kotlin", pure_ok, "; ".join(violations[:3]))

    manifest = read_text(app_dir / "src" / "main" / "AndroidManifest.xml")
    internet_removed = (
        "android.permission.INTERNET" not in manifest
        or 'tools:node="remove"' in manifest
    )
    record("no INTERNET in app manifest", internet_removed)
    record("CAMERA permission present", "android.permission.CAMERA" in manifest)
    record("portrait lock configured", 'screenOrientation="portrait"' in manifest)

    build_gradle = read_text(app_dir / "build.gradle.kts")
    record("versionName 1.0.0", 'versionName = "1.0.0"' in build_gradle)
    record("release minify disabled (documented)", "isMinifyEnabled = false" in build_gradle)

    home_screen = read_text(app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app" / "ui" / "screens" / "home" / "HomeScreen.kt")
    record("debug-only screens gated", "BuildConfig.DEBUG" in home_screen)

    for doc in REQUIRED_DOCS:
        record(f"doc exists: {doc}", (project_root / doc).is_file())

    debug_apk = app_dir / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    release_apk = app_dir / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk"
    release_apk_signed = app_dir / "build" / "outputs" / "apk" / "release" / "app-release.apk"
    record("debug APK exists", debug_apk.is_file(), str(debug_apk))
    record(
        "release APK exists",
        release_apk.is_file() or release_apk_signed.is_file(),
        str(release_apk if release_apk.is_file() else release_apk_signed),
    )

    physical = count_physical_qr_passes(project_root / "docs" / "PHYSICAL_QR_TEST_CHECKLIST.md")
    record(
        "physical QR checklist tracked separately",
        True,
        f"PASS counts USER={physical.get('USER', 0)} PROPERTY={physical.get('PROPERTY', 0)} "
        f"EVENT={physical.get('EVENT', 0)} TOTAL={physical.get('TOTAL', 0)}",
    )

    for validator in VALIDATORS:
        ok, detail = run_validator(tools_dir, validator)
        record(f"validator {validator}", ok, detail)

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "V1 RELEASE VALIDATION",
        f"Result: {'PASS' if passed == total else 'PARTIAL'} ({passed}/{total})",
        "",
        "Physical QR and full-game results are recorded in docs/ and are NOT auto-marked PASS.",
        "",
    ]
    for name, ok, detail in checks:
        status = "PASS" if ok else "FAIL"
        line = f"[{status}] {name}"
        if detail:
            line += f" — {detail}"
        lines.append(line)

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))
    # Allow partial pass when only APKs missing before build
    critical_failures = [name for name, ok, _ in checks if not ok and "APK" not in name and "validator" not in name]
    validator_failures = [name for name, ok, _ in checks if not ok and name.startswith("validator")]
    return 0 if not critical_failures and not validator_failures else 1


if __name__ == "__main__":
    sys.exit(main())
