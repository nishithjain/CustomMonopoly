"""Tests for path resolution and asset rewriting."""

from __future__ import annotations

from pathlib import Path

from monopoly_edition_generator.paths import (
    BOARD_ASSET_PREFIX,
    BOARD_SPACES_DIR,
    BOARD_TEMPLATE,
    ENERGY_GRID_ASSET_PREFIX,
    ENERGY_GRID_ASSETS_DIR,
    EVENT_CARD_TEMPLATE,
    PROPERTY_CARD_TEMPLATE,
    PROJECT_ROOT,
    REQUIRED_BOARD_ASSETS,
    WORKSPACE_ENV_VAR,
    discover_workspace_root,
    edition_output_dir,
    editions_root,
    find_data_root,
    inner_box_path,
    posix_relative,
    rewrite_board_asset_paths,
    resources_root,
)


def test_project_paths_exist() -> None:
    assert PROJECT_ROOT.is_dir()
    assert BOARD_TEMPLATE.is_file()
    assert EVENT_CARD_TEMPLATE.is_file()
    assert PROPERTY_CARD_TEMPLATE.is_file()
    for name in REQUIRED_BOARD_ASSETS:
        assert (BOARD_SPACES_DIR / name).is_file(), name


def test_discover_workspace_root_from_parent_search() -> None:
    root = discover_workspace_root(PROJECT_ROOT)
    assert (root / "monopoly-ultimate-banking-qr" / "data" / "editions").is_dir()
    assert (root / "Resources" / "Editions").is_dir()


def test_workspace_env_override(tmp_path: Path, monkeypatch) -> None:
    data = tmp_path / "monopoly-ultimate-banking-qr" / "data" / "editions" / "demo"
    resources = tmp_path / "Resources" / "Editions" / "demo"
    data.mkdir(parents=True)
    resources.mkdir(parents=True)
    monkeypatch.setenv(WORKSPACE_ENV_VAR, str(tmp_path))
    assert discover_workspace_root() == tmp_path.resolve()


def test_data_root_points_at_game_editions() -> None:
    root = find_data_root()
    assert (root / "editions" / "india").is_dir()


def test_inner_box_path_is_under_workspace_resources() -> None:
    path = inner_box_path("india")
    assert path.name.startswith("InnerBox.")
    assert path.suffix.lower() in {".png", ".jpg", ".jpeg"}
    assert path.is_file()
    assert path.parent.name == "Board"
    assert path.parent.parent.name == "india"
    assert path.parent.parent.parent.name == "Editions"
    assert path.parent.parent.parent.parent == resources_root()


def test_rewrite_board_asset_paths() -> None:
    html = f'<img src="{BOARD_ASSET_PREFIX}/go.png">'
    output = PROJECT_ROOT / "output" / "india" / "board" / "Board_India.html"
    rewritten = rewrite_board_asset_paths(html, output)
    expected = posix_relative(output.parent, BOARD_SPACES_DIR)
    assert rewritten == f'<img src="{expected}/go.png">'


def test_rewrite_board_energy_grid_paths_do_not_double_replace() -> None:
    html = f'"{ENERGY_GRID_ASSET_PREFIX}/eng_01_solar.png"'
    output = PROJECT_ROOT / "output" / "india" / "board" / "Board_India.html"
    rewritten = rewrite_board_asset_paths(html, output)
    expected = posix_relative(output.parent, ENERGY_GRID_ASSETS_DIR)
    assert rewritten == f'"{expected}/eng_01_solar.png"'
    assert rewritten.count("../") == 3


def test_edition_output_dir_scoped_to_edition() -> None:
    india = edition_output_dir("india")
    uk = edition_output_dir("uk")
    assert india != uk
    assert india.name == "india"
    assert editions_root().name == "editions"

