"""Validate edition JSON and generator templates before generation."""

from __future__ import annotations

from dataclasses import dataclass, field

from monopoly_edition_generator.paths import (
    BOARD_SPACES_DIR,
    BOARD_TEMPLATE,
    DEFAULT_THEME_ID,
    ENERGY_GRID_ASSET_FILES,
    ENERGY_GRID_ASSETS_DIR,
    ENERGY_GRIDS_JSON,
    EVENT_CARD_TEMPLATE,
    PROPERTY_CARD_TEMPLATE,
    REQUIRED_BOARD_ASSETS,
    THEMES_DIR,
    GeneratorError,
    board_tile_colors,
    currency_symbol,
    edition_dir,
    inner_box_display_path,
    inner_box_status,
    load_edition_config,
    load_edition_json,
    load_theme,
    numeric_banking_value,
    property_card_base_colors,
    theme_id_for_edition,
)
from monopoly_edition_generator.energy_grids import load_energy_grids

REQUIRED_TEMPLATES = (
    ("board/board.html", BOARD_TEMPLATE, ("const boardSpaces", "const GO_DATA")),
    ("cards/property-card.html", PROPERTY_CARD_TEMPLATE, ("const CARD_DATA",)),
    ("cards/event-card.html", EVENT_CARD_TEMPLATE, ("@@PAGE_TITLE@@", "@@EVENT_NAME@@")),
)

PROPERTY_REQUIRED_FIELDS = (
    "propertyId",
    "name",
    "sequence",
    "colorGroup",
    "purchasePrice",
    "rentLevels",
)

EVENT_REQUIRED_FIELDS = (
    "eventId",
    "sequence",
    "name",
    "eventSubtitle",
    "eventDescription",
)


@dataclass
class ValidationIssue:
    source: str
    entity: str
    field: str
    reason: str
    level: str = "error"

    def format_block(self) -> str:
        tag = "ERROR" if self.level == "error" else "WARN"
        return (
            f"[{tag}] {self.source}\n\n"
            f"{self.entity}\n"
            f"Field: {self.field}\n"
            f"Reason: {self.reason}"
        )


@dataclass
class ValidationResult:
    edition_id: str
    ok: bool
    issues: list[ValidationIssue] = field(default_factory=list)
    property_count: int = 0
    event_count: int = 0
    notes: list[str] = field(default_factory=list)
    inner_box: dict | None = None

    @property
    def errors(self) -> list[ValidationIssue]:
        return [item for item in self.issues if item.level == "error"]

    @property
    def warnings(self) -> list[ValidationIssue]:
        return [item for item in self.issues if item.level == "warning"]


def _error(result: ValidationResult, source: str, entity: str, field: str, reason: str) -> None:
    result.issues.append(ValidationIssue(source, entity, field, reason, "error"))
    result.ok = False


def _warn(result: ValidationResult, source: str, entity: str, field: str, reason: str) -> None:
    result.issues.append(ValidationIssue(source, entity, field, reason, "warning"))


def validate_edition(edition_id: str) -> ValidationResult:
    result = ValidationResult(edition_id=edition_id, ok=True)

    try:
        folder = edition_dir(edition_id)
    except GeneratorError as exc:
        _error(result, "edition", f"Edition: {edition_id}", "editionId", str(exc))
        return result

    try:
        edition_config = load_edition_config(edition_id)
        declared = str(edition_config.get("editionId") or "").strip()
        if declared and declared != edition_id:
            _error(
                result,
                "edition.json",
                f"Edition: {edition_id}",
                "editionId",
                f"edition.json editionId is {declared!r}, expected {edition_id!r}",
            )
        result.notes.append("edition.json")
    except GeneratorError as exc:
        _error(result, "edition.json", f"Edition: {edition_id}", "edition.json", str(exc))
        edition_config = {}

    theme_id = theme_id_for_edition(edition_config) if edition_config else DEFAULT_THEME_ID
    try:
        theme = load_theme(theme_id)
        property_colors = property_card_base_colors(theme)
        tile_colors = board_tile_colors(theme)
    except GeneratorError as exc:
        _error(result, "themes", f"Theme: {theme_id}", "theme", str(exc))
        property_colors = {}
        tile_colors = {}

    _validate_properties(result, edition_id, property_colors, tile_colors)
    _validate_events(result, edition_id)
    _validate_banking(result, edition_id)
    _validate_board_relationships(result, edition_id)
    _validate_templates(result)
    _validate_assets(result)
    _validate_energy_grid_assets(result, edition_id)
    _validate_inner_box(result, edition_id)

    missing_files = []
    for name in ("edition.json", "properties.json", "events.json", "banking_values.json", "board_relationships.json"):
        if not (folder / name).is_file():
            missing_files.append(name)
    if missing_files:
        _error(
            result,
            "edition",
            f"Edition: {edition_id}",
            "files",
            "Missing required file(s): " + ", ".join(missing_files),
        )

    return result


def _validate_properties(
    result: ValidationResult,
    edition_id: str,
    property_colors: dict[str, str],
    tile_colors: dict[str, dict[str, str]],
) -> None:
    source = "properties.json"
    try:
        data = load_edition_json(edition_id, source)
    except GeneratorError as exc:
        _error(result, source, "Properties", source, str(exc))
        return

    properties = data.get("properties") if isinstance(data, dict) else None
    if not isinstance(properties, list):
        _error(result, source, "Properties", "properties", "Must be a JSON array.")
        return

    result.property_count = len(properties)
    sequences: dict[int, str] = {}
    ids: dict[str, str] = {}

    if len(properties) != 22:
        _error(
            result,
            source,
            "Properties",
            "properties",
            f"Expected exactly 22 properties for the current board layout, found {len(properties)}.",
        )

    for index, prop in enumerate(properties, start=1):
        if not isinstance(prop, dict):
            _error(result, source, f"Property entry #{index}", "entry", "Must be an object.")
            continue

        label = str(prop.get("propertyId") or prop.get("name") or f"entry #{index}")
        entity = f"Property: {label}"

        missing = [field for field in PROPERTY_REQUIRED_FIELDS if field not in prop]
        for field in missing:
            _error(result, source, entity, field, "Missing required value")

        sequence = prop.get("sequence")
        if not isinstance(sequence, int) or sequence < 1:
            _error(result, source, entity, "sequence", "Must be a positive integer.")
        else:
            previous = sequences.get(sequence)
            if previous:
                _error(result, source, entity, "sequence", f"Duplicate sequence {sequence} (also {previous}).")
            else:
                sequences[sequence] = label
            if not 1 <= sequence <= 22:
                _error(result, source, entity, "sequence", "Expected an integer from 1 to 22.")

        property_id = prop.get("propertyId")
        if isinstance(property_id, str) and property_id.strip():
            previous = ids.get(property_id)
            if previous:
                _error(result, source, entity, "propertyId", f"Duplicate property ID (also {previous}).")
            else:
                ids[property_id] = label
        elif "propertyId" in prop:
            _error(result, source, entity, "propertyId", "Must be a non-empty string.")

        if not str(prop.get("name") or "").strip():
            if "name" in prop:
                _error(result, source, entity, "name", "Must be a non-empty string.")

        color_group = str(prop.get("colorGroup") or "").strip().upper()
        if color_group:
            if property_colors and color_group not in property_colors:
                supported = ", ".join(sorted(property_colors))
                _error(result, source, entity, "colorGroup", f"Unsupported value {color_group!r}. Supported: {supported}")
            elif tile_colors and color_group not in tile_colors:
                supported = ", ".join(sorted(tile_colors))
                _error(result, source, entity, "colorGroup", f"No board tile colors for {color_group!r}. Supported: {supported}")

        if "purchasePrice" in prop:
            price = prop["purchasePrice"]
            if not isinstance(price, (int, float)) or isinstance(price, bool):
                _error(result, source, entity, "purchasePrice", "Must be numeric.")

        rent_levels = prop.get("rentLevels")
        if "rentLevels" in prop:
            if not isinstance(rent_levels, list) or not rent_levels:
                _error(result, source, entity, "rentLevels", "Must be a non-empty list.")
            else:
                for rent_index, rent_level in enumerate(rent_levels):
                    if not isinstance(rent_level, dict):
                        _error(result, source, entity, f"rentLevels[{rent_index}]", "Must be an object.")
                        continue
                    if "level" not in rent_level:
                        _error(result, source, entity, f"rentLevels[{rent_index}].level", "Missing required value")
                    if "amount" not in rent_level:
                        _error(result, source, entity, f"rentLevels[{rent_index}].amount", "Missing required value")

    if sequences:
        expected = set(range(1, 23))
        actual = set(sequences)
        if actual != expected and result.property_count == 22:
            missing = sorted(expected - actual)
            extra = sorted(actual - expected)
            _error(
                result,
                source,
                "Properties",
                "sequence",
                f"Sequences must be exactly 1..22. Missing={missing}, extra={extra}",
            )


def _validate_events(result: ValidationResult, edition_id: str) -> None:
    source = "events.json"
    try:
        data = load_edition_json(edition_id, source)
    except GeneratorError as exc:
        _error(result, source, "Events", source, str(exc))
        return

    events = data.get("events") if isinstance(data, dict) else None
    if not isinstance(events, list):
        _error(result, source, "Events", "events", "Must be a JSON array.")
        return

    result.event_count = len(events)
    sequences: dict[int, str] = {}
    ids: dict[str, str] = {}

    for index, event in enumerate(events, start=1):
        if not isinstance(event, dict):
            _error(result, source, f"Event entry #{index}", "entry", "Must be an object.")
            continue

        label = str(event.get("eventId") or event.get("name") or f"entry #{index}")
        entity = f"Event: {label}"

        for field in EVENT_REQUIRED_FIELDS:
            if field not in event:
                _error(result, source, entity, field, "Missing required value")
            elif field in {"eventId", "name", "eventSubtitle", "eventDescription"}:
                if not str(event.get(field) or "").strip() and field != "eventSubtitle":
                    _error(result, source, entity, field, "Must be a non-empty string.")

        sequence = event.get("sequence")
        if not isinstance(sequence, int) or sequence < 1:
            _error(result, source, entity, "sequence", "Must be a positive integer.")
        else:
            previous = sequences.get(sequence)
            if previous:
                _error(result, source, entity, "sequence", f"Duplicate sequence {sequence} (also {previous}).")
            else:
                sequences[sequence] = label

        event_id = event.get("eventId")
        if isinstance(event_id, str) and event_id.strip():
            previous = ids.get(event_id)
            if previous:
                _error(result, source, entity, "eventId", f"Duplicate event ID (also {previous}).")
            else:
                ids[event_id] = label


def _validate_banking(result: ValidationResult, edition_id: str) -> None:
    source = "banking_values.json"
    try:
        data = load_edition_json(edition_id, source)
    except GeneratorError as exc:
        _error(result, source, "Banking values", source, str(exc))
        return

    if not isinstance(data, dict):
        _error(result, source, "Banking values", source, "Must be a JSON object.")
        return

    try:
        currency_symbol(data)
    except GeneratorError as exc:
        _error(result, source, "Banking values", "currency.symbol", str(exc))

    for key in ("goSalary", "locationFee"):
        try:
            numeric_banking_value(data, key)
        except GeneratorError as exc:
            _error(result, source, "Banking values", key, str(exc))


def _validate_board_relationships(result: ValidationResult, edition_id: str) -> None:
    source = "board_relationships.json"
    try:
        data = load_edition_json(edition_id, source)
    except GeneratorError as exc:
        _error(result, source, "Board relationships", source, str(exc))
        return

    if not isinstance(data, dict):
        _error(result, source, "Board relationships", source, "Must be a JSON object.")
        return

    color_groups = data.get("colorGroups")
    if not isinstance(color_groups, dict) or not color_groups:
        _error(result, source, "Board relationships", "colorGroups", "Missing required value")


def _validate_templates(result: ValidationResult) -> None:
    for name, path, markers in REQUIRED_TEMPLATES:
        if not path.is_file():
            _error(result, "templates", f"Template: {name}", name, f"Missing template at {path}")
            continue
        text = path.read_text(encoding="utf-8")
        for marker in markers:
            if marker not in text:
                _error(result, "templates", f"Template: {name}", marker, f"Missing placeholder in template.")
        if name.endswith("event-card.html"):
            for token in ("@@EVENT_SUBTITLE@@", "@@EVENT_DESCRIPTION@@"):
                if token not in text:
                    _error(result, "templates", f"Template: {name}", token, "Missing placeholder in event template.")


def _validate_assets(result: ValidationResult) -> None:
    for name in REQUIRED_BOARD_ASSETS:
        path = BOARD_SPACES_DIR / name
        if not path.is_file():
            _error(result, "assets", f"Asset: {name}", name, f"Missing board-space asset at {path}")

    theme_path = THEMES_DIR / f"{DEFAULT_THEME_ID}.json"
    if not theme_path.is_file():
        _error(result, "themes", f"Theme: {DEFAULT_THEME_ID}", "theme", f"Missing {theme_path}")


def _validate_energy_grid_assets(result: ValidationResult, edition_id: str) -> None:
    if edition_id != "india":
        return

    if not ENERGY_GRIDS_JSON.is_file():
        _error(
            result,
            "energy_grids.json",
            "Energy Grids",
            "energy_grids.json",
            f"Missing authoritative Energy Grid data at {ENERGY_GRIDS_JSON}",
        )
        return

    try:
        load_energy_grids()
        result.notes.append("energy_grids.json")
    except GeneratorError as exc:
        _error(result, "energy_grids.json", "Energy Grids", "energy_grids.json", str(exc))
        return

    for grid_id, filename in ENERGY_GRID_ASSET_FILES.items():
        path = ENERGY_GRID_ASSETS_DIR / filename
        if not path.is_file():
            _error(
                result,
                "assets",
                f"Energy Grid: {grid_id}",
                filename,
                f"Missing Energy Grid board-space PNG at {path}. Run scripts/render_energy_grid_board_spaces.py",
            )


def _validate_inner_box(result: ValidationResult, edition_id: str) -> None:
    status = inner_box_status(edition_id)
    result.inner_box = status
    if status["found"]:
        result.notes.append("InnerBox.png")
        return
    _warn(
        result,
        "InnerBox.png",
        "Inner artwork",
        "InnerBox.png",
        f"{inner_box_display_path(edition_id)} not found. Center board area will remain empty.",
    )
