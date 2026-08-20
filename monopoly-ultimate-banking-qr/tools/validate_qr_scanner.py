#!/usr/bin/env python3
"""Validate Step 7 QR scanner implementation."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ANDROID_IMPORT_PATTERN = re.compile(r"^\s*import\s+(android\.|androidx\.)")


def find_project_root() -> Path:
    current = Path(__file__).resolve().parent
    for candidate in [current.parent, current.parent.parent]:
        if (candidate / "android-app").is_dir() and (candidate / "data").is_dir():
            return candidate
    raise FileNotFoundError("Could not locate project root")


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.is_file() else ""


def check_game_core_pure_kotlin(game_core_dir: Path) -> tuple[bool, list[str]]:
    violations: list[str] = []
    kotlin_root = game_core_dir / "src" / "main" / "kotlin"
    for file_path in kotlin_root.rglob("*.kt"):
        for line_number, line in enumerate(file_path.read_text(encoding="utf-8").splitlines(), start=1):
            if ANDROID_IMPORT_PATTERN.match(line):
                violations.append(f"{file_path}:{line_number}: {line.strip()}")
    return len(violations) == 0, violations


def main() -> int:
    project_root = find_project_root()
    android_root = project_root / "android-app"
    app_dir = android_root / "app"
    game_core_dir = android_root / "game-core"
    output_path = project_root / "data" / "qr_scanner_validation.txt"

    checks: list[tuple[str, bool, str]] = []

    def record(name: str, passed: bool, detail: str = "") -> None:
        checks.append((name, passed, detail))

    manifest = read_text(app_dir / "src" / "main" / "AndroidManifest.xml")
    libs = read_text(android_root / "gradle" / "libs.versions.toml")
    app_build = read_text(app_dir / "build.gradle.kts")
    game_core_build = read_text(game_core_dir / "build.gradle.kts")
    package_root = app_dir / "src" / "main" / "java" / "com" / "boardbanker" / "app"

    record("CAMERA permission exists", "android.permission.CAMERA" in manifest)
    internet_removed = (
        "android.permission.INTERNET" not in manifest
        or 'tools:node="remove"' in manifest
    )
    record("no INTERNET permission", internet_removed)
    record("CameraX dependency exists", "androidx-camera-core" in libs and "androidx.camera" in app_build)
    record("ML Kit Barcode dependency exists", "mlkit-barcode-scanning" in libs and "mlkit.barcode.scanning" in app_build)
    record(
        "QR-only scanner configuration exists",
        "FORMAT_QR_CODE" in read_text(package_root / "scanner" / "camera" / "QrImageAnalyzer.kt"),
    )
    record("scanner package exists", (package_root / "scanner").is_dir())
    record("CardResolver exists", (game_core_dir / "src" / "main" / "kotlin" / "com" / "boardbanker" / "core" / "card" / "CardResolver.kt").is_file())
    record("ScanGate exists", (game_core_dir / "src" / "main" / "kotlin" / "com" / "boardbanker" / "core" / "scanner" / "ScanGate.kt").is_file())
    record("ScannerScreen exists", (package_root / "scanner" / "ui" / "ScannerScreen.kt").is_file())
    record("scanner ViewModel/controller exists", (package_root / "scanner" / "ScannerViewModel.kt").is_file() and (package_root / "scanner" / "ScannerController.kt").is_file())
    record("QrCodeSource abstraction exists", (package_root / "scanner" / "QrCodeSource.kt").is_file())
    record("AndroidGameDataLoader exists", (package_root / "data" / "AndroidGameDataLoader.kt").is_file())

    resolver_test = read_text(game_core_dir / "src" / "test" / "kotlin" / "com" / "boardbanker" / "core" / "card" / "CardResolverParameterizedTest.kt")
    record("49-card resolver test exists", "registeredCardResolvesToExpectedIdentity" in resolver_test)
    record("ScanGate tests exist", (game_core_dir / "src" / "test" / "kotlin" / "com" / "boardbanker" / "core" / "scanner" / "ScanGateTest.kt").is_file())
    record(
        "Fake QR source test exists",
        (app_dir / "src" / "test" / "java" / "com" / "boardbanker" / "app" / "scanner" / "FakeQrCodeSourceTest.kt").is_file(),
    )

    scanner_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (package_root / "scanner").rglob("*.kt")
    )
    record("Room not in game-core", "room" not in game_core_build.lower())
    record("scanner does not depend on Room", "androidx.room" not in scanner_sources and "Room" not in scanner_sources)
    record("game-core does not depend on app", "project(\":app\")" not in game_core_build)

    pure_ok, violations = check_game_core_pure_kotlin(game_core_dir)
    record("game-core still has no Android imports", pure_ok, "; ".join(violations))

    record("QR scanner architecture doc exists", (project_root / "docs" / "QR_SCANNER_ARCHITECTURE.md").is_file())
    record("physical QR checklist exists", (project_root / "docs" / "PHYSICAL_QR_TEST_CHECKLIST.md").is_file())

    passed = sum(1 for _, ok, _ in checks if ok)
    total = len(checks)
    lines = [
        "QR Scanner Validation",
        "=====================",
        f"Passed: {passed} / {total}",
        "",
    ]
    for name, ok, detail in checks:
        status = "PASS" if ok else "FAIL"
        lines.append(f"[{status}] {name}")
        if detail:
            lines.append(f"       {detail}")
    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("\n".join(lines))
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
