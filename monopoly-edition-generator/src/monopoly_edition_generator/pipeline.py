"""Shared generation pipeline used by the CLI and GUI."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from monopoly_edition_generator.generators.board import generate_board
from monopoly_edition_generator.generators.event_cards import generate_event_cards
from monopoly_edition_generator.generators.property_cards import generate_property_cards
from monopoly_edition_generator.paths import (
    INNER_BOARD_SIZE_CM,
    OUTER_BOARD_SIZE_CM,
    GeneratorError,
    clean_stale_outputs,
    currency_symbol,
    edition_display_token,
    edition_output_dir,
    inner_box_status,
    load_edition_config,
    load_edition_json,
    write_json,
)
from monopoly_edition_generator.renderer import render_board_png, render_card_pngs
from monopoly_edition_generator.validator import ValidationResult, validate_edition

ProgressCallback = Callable[[str, int], None]


@dataclass
class GenerationOptions:
    edition_id: str
    generate_properties: bool = True
    generate_events: bool = True
    generate_board: bool = True
    generate_pngs: bool = True
    dpi: float = 300.0
    validate_only: bool = False


@dataclass
class GenerationOutcome:
    ok: bool
    validation: ValidationResult
    generated: dict[str, int]
    output_dir: Path
    status: str
    inner_box: dict
    report_path: Path | None = None
    error: str | None = None
    png_notes: list[str] = field(default_factory=list)
    artifacts: dict[str, list[Path]] = field(default_factory=dict)

    def written_files(self) -> list[Path]:
        files: list[Path] = []
        for paths in self.artifacts.values():
            files.extend(paths)
        return files


def _emit(progress: ProgressCallback | None, message: str, percent: int) -> None:
    if progress:
        progress(message, max(0, min(100, percent)))
    else:
        print(message)


def _png_jobs(html_paths: list[Path], png_dir: Path) -> list[tuple[Path, Path]]:
    png_dir.mkdir(parents=True, exist_ok=True)
    return [(html_path, png_dir / (html_path.stem + ".png")) for html_path in html_paths]


def board_report_payload(inner_box: dict) -> dict:
    return {
        "outerSizeCm": OUTER_BOARD_SIZE_CM,
        "innerSizeCm": INNER_BOARD_SIZE_CM,
        "innerBox": {
            "found": bool(inner_box.get("found")),
            "path": inner_box.get("path"),
            "action": inner_box.get("action"),
        },
    }


def write_generation_report(
    edition_id: str,
    result: ValidationResult,
    generated: dict[str, int],
    status: str,
    extra: dict | None = None,
) -> Path:
    inner = result.inner_box or inner_box_status(edition_id)
    payload = {
        "edition": edition_id,
        "status": status,
        "board": board_report_payload(inner),
        "generated": generated,
        "validation": {
            "errors": len(result.errors),
            "warnings": len(result.warnings),
        },
    }
    if extra:
        payload.update(extra)
    path = edition_output_dir(edition_id) / "generation_report.json"
    write_json(path, payload)
    return path


def run_generation(
    options: GenerationOptions,
    progress: ProgressCallback | None = None,
    after_validate=None,
) -> GenerationOutcome:
    edition_id = options.edition_id
    output_dir = edition_output_dir(edition_id)
    generated = {"propertyCards": 0, "eventCards": 0, "boards": 0}
    inner = inner_box_status(edition_id)

    _emit(progress, "Validating edition...", 5)
    try:
        result = validate_edition(edition_id)
    except GeneratorError as exc:
        dummy = ValidationResult(edition_id=edition_id, ok=False)
        dummy.inner_box = inner
        return GenerationOutcome(
            ok=False,
            validation=dummy,
            generated=generated,
            output_dir=output_dir,
            status="error",
            inner_box=inner,
            error=str(exc),
        )

    inner = result.inner_box or inner
    if after_validate:
        after_validate(result)

    if not result.ok:
        report = write_generation_report(
            edition_id, result, generated, "validation_failed"
        )
        return GenerationOutcome(
            ok=False,
            validation=result,
            generated=generated,
            output_dir=output_dir,
            status="validation_failed",
            inner_box=inner,
            report_path=report,
            error="Validation failed.",
        )

    if options.validate_only:
        report = write_generation_report(edition_id, result, generated, "validated")
        if progress:
            _emit(progress, "Validation successful.", 100)
        elif not after_validate:
            print("Validation successful.")
        return GenerationOutcome(
            ok=True,
            validation=result,
            generated=generated,
            output_dir=output_dir,
            status="validated",
            inner_box=inner,
            report_path=report,
        )

    clean_stale_outputs(
        edition_id,
        properties=options.generate_properties,
        events=options.generate_events,
        board=options.generate_board,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    banking = load_edition_json(edition_id, "banking_values.json")
    currency = currency_symbol(banking)
    edition_config = load_edition_config(edition_id)
    token = edition_display_token(edition_id, edition_config)

    property_html: list[Path] = []
    event_html: list[Path] = []
    board_html: Path | None = None
    png_notes: list[str] = []
    artifacts: dict[str, list[Path]] = {}

    try:
        if options.generate_properties:
            _emit(progress, "Generating Property Cards...", 20)
            property_html = generate_property_cards(edition_id, currency)
            generated["propertyCards"] = len(property_html)
            artifacts["propertyHtml"] = list(property_html)
            _emit(progress, f"{len(property_html)} property cards generated.", 35)

        if options.generate_events:
            _emit(progress, "Generating Event Cards...", 40)
            event_html = generate_event_cards(edition_id)
            generated["eventCards"] = len(event_html)
            artifacts["eventHtml"] = list(event_html)
            _emit(progress, f"{len(event_html)} event cards generated.", 55)

        if options.generate_board:
            _emit(progress, "Generating Board...", 60)
            board_html = generate_board(edition_id)
            generated["boards"] = 1
            artifacts["boardHtml"] = [board_html]
            if inner.get("found"):
                _emit(progress, "InnerBox.png added to board.", 70)
            else:
                _emit(progress, "InnerBox.png not found. Empty center used.", 70)
            _emit(progress, "Board generated.", 72)

        if options.generate_pngs:
            _emit(progress, "Rendering PNGs...", 75)

            def property_progress(done: int, total: int, _path: Path) -> None:
                _emit(progress, f"Rendering property card PNG {done}/{total}...", 75 + int(8 * done / max(total, 1)))

            def event_progress(done: int, total: int, _path: Path) -> None:
                _emit(progress, f"Rendering event card PNG {done}/{total}...", 83 + int(8 * done / max(total, 1)))

            try:
                if property_html:
                    created = render_card_pngs(
                        _png_jobs(property_html, output_dir / "property_cards" / "png"),
                        ".property-card",
                        dpi=options.dpi,
                        on_progress=property_progress if progress else None,
                    )
                    artifacts["propertyPng"] = [Path(item) for item in created]
                if event_html:
                    created = render_card_pngs(
                        _png_jobs(event_html, output_dir / "event_cards" / "png"),
                        ".event-card",
                        dpi=options.dpi,
                        on_progress=event_progress if progress else None,
                    )
                    artifacts["eventPng"] = [Path(item) for item in created]
                if board_html is not None:
                    _emit(progress, "Rendering board PNG...", 92)
                    board_png = render_board_png(
                        board_html,
                        board_html.with_name(f"Board_{token}_{int(options.dpi)}DPI.png"),
                        dpi=options.dpi,
                    )
                    artifacts["boardPng"] = [Path(board_png)]
            except SystemExit:
                png_notes.append("PNG rendering skipped because Playwright or Pillow is not installed.")
                _emit(progress, "PNG rendering skipped (Playwright/Pillow not installed).", 95)
            except Exception as exc:
                png_notes.append(str(exc))
                _emit(progress, f"PNG rendering failed: {exc}", 95)
        elif property_html or event_html or board_html is not None:
            png_notes.append(
                "PNG rendering was not requested (GUI: Generate PNGs unchecked, CLI: --no-png)."
            )
            _emit(progress, "PNG rendering not requested. HTML only.", 95)

        generated["pngs"] = sum(
            len(artifacts.get(key, ())) for key in ("propertyPng", "eventPng", "boardPng")
        )
        extra = {"pngNotes": png_notes} if png_notes else None
        report = write_generation_report(edition_id, result, generated, "success", extra)
        _emit(progress, "Generation completed successfully.", 100)
        return GenerationOutcome(
            ok=True,
            validation=result,
            generated=generated,
            output_dir=output_dir,
            status="success",
            inner_box=inner,
            report_path=report,
            png_notes=png_notes,
            artifacts=artifacts,
        )
    except GeneratorError as exc:
        report = write_generation_report(edition_id, result, generated, "error")
        return GenerationOutcome(
            ok=False,
            validation=result,
            generated=generated,
            output_dir=output_dir,
            status="error",
            inner_box=inner,
            report_path=report,
            error=str(exc),
            artifacts=artifacts,
        )
