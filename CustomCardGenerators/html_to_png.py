#!/usr/bin/env python3
"""
Export ONLY the Monopoly board (#monopoly-board) from a local HTML file
to a print-ready square PNG.

Usage:
    python html_to_png.py
    python html_to_png.py --dpi 300
    python html_to_png.py --input Board_Template.html --output Monopoly_Board.png

Defaults:
    --input   Board_Template.html
    --output  Monopoly_Board_300DPI.png
    --dpi     300

The board is 50 cm × 50 cm. Target pixels are:

    round((50 / 2.54) * DPI)

which is 5906 × 5906 at 300 DPI.

Requires:
    pip install playwright pillow
    playwright install chromium
"""

from __future__ import annotations

import argparse
import asyncio
import math
import sys
import tempfile
from pathlib import Path

BOARD_CM = 50.0
CSS_PX_PER_INCH = 96.0
POST_RENDER_WAIT_MS = 800
MAX_DEVICE_SCALE = 8.0


INSTALL_HINT = """ERROR: Playwright is not installed.

Install with:
    pip install playwright pillow
    playwright install chromium
"""

CHROMIUM_HINT = """ERROR: Playwright Chromium is not installed.

Install with:
    playwright install chromium
"""

PILLOW_HINT = """ERROR: Pillow is not installed.

Install with:
    pip install pillow
"""


def target_pixels(dpi: float) -> int:
    """Return the print pixel size for a 50 cm board at the given DPI."""
    return round((BOARD_CM / 2.54) * dpi)


def css_board_pixels() -> float:
    """Chromium CSS pixels for 50 cm (1in = 96 CSS px)."""
    return (BOARD_CM / 2.54) * CSS_PX_PER_INCH


def require_pillow():
    try:
        from PIL import Image
    except ImportError:
        print(PILLOW_HINT, file=sys.stderr)
        raise SystemExit(1) from None
    return Image


def require_playwright():
    try:
        from playwright.async_api import Error as PlaywrightError
        from playwright.async_api import async_playwright
    except ImportError:
        print(INSTALL_HINT, file=sys.stderr)
        raise SystemExit(1) from None
    return async_playwright, PlaywrightError


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Capture #monopoly-board from a local HTML file as a print-ready PNG."
    )
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("Board_Template.html"),
        help="Input HTML file (default: Board_Template.html)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("Monopoly_Board_300DPI.png"),
        help="Output PNG path (default: Monopoly_Board_300DPI.png)",
    )
    parser.add_argument(
        "--dpi",
        type=float,
        default=300,
        help="Print DPI used to calculate the square pixel size (default: 300)",
    )
    return parser.parse_args()


def device_scale_for_target(target: int) -> float:
    """
    Choose a Chromium deviceScaleFactor that renders the 50 cm board at or
    above the requested pixel size so any later resize is a downscale.
    """
    css_px = css_board_pixels()
    if css_px <= 0:
        raise ValueError("Invalid CSS board size.")

    # Slight oversample so rounding of the element box still stays >= target.
    scale = (target / css_px) * 1.08
    scale = max(scale, target / css_px)
    return min(MAX_DEVICE_SCALE, scale)


async def wait_for_images_and_validate(page) -> None:
    """Wait for images, then fail if the board or any image is incomplete."""
    await page.wait_for_function(
        """
        () => {
            const board = document.querySelector("#monopoly-board");
            if (!board) {
                return false;
            }
            return board.querySelectorAll(".board-space").length >= 32
                && board.querySelectorAll(".board-corner").length >= 4;
        }
        """
    )

    result = await page.evaluate(
        """
        async () => {
            const board = document.querySelector("#monopoly-board");
            if (!board) {
                return { boardExists: false, failed: [] };
            }

            if (document.fonts && document.fonts.ready) {
                await document.fonts.ready;
            }

            await Promise.all(
                Array.from(document.images)
                    .filter(img => !img.complete)
                    .map(img => new Promise(resolve => {
                        img.onload = img.onerror = resolve;
                    }))
            );

            const failed = [];
            for (const img of document.images) {
                if (!img.complete || img.naturalWidth <= 0) {
                    failed.push(img.currentSrc || img.getAttribute("src") || "(unknown img)");
                }
            }

            const backgroundUrls = new Set();
            const nodes = [board, ...board.querySelectorAll("*")];
            for (const node of nodes) {
                const bg = getComputedStyle(node).backgroundImage;
                if (!bg || bg === "none") {
                    continue;
                }
                for (const match of bg.matchAll(/url\\((['"]?)(.*?)\\1\\)/g)) {
                    const url = match[2];
                    if (url && !url.startsWith("data:")) {
                        backgroundUrls.add(url);
                    }
                }
            }

            await Promise.all(
                Array.from(backgroundUrls).map(url => new Promise(resolve => {
                    const image = new Image();
                    image.onload = () => {
                        if (image.naturalWidth <= 0) {
                            failed.push(url);
                        }
                        resolve();
                    };
                    image.onerror = () => {
                        failed.push(url);
                        resolve();
                    };
                    image.src = url;
                }))
            );

            return {
                boardExists: true,
                failed,
            };
        }
        """
    )

    if not result or not result.get("boardExists"):
        print("ERROR: Could not find #monopoly-board", file=sys.stderr)
        raise SystemExit(1)

    failed = result.get("failed") or []
    if failed:
        print("ERROR: One or more local images failed to load:", file=sys.stderr)
        for src in failed:
            print(f"  {src}", file=sys.stderr)
        raise SystemExit(1)


async def capture_board_png(
    html_path: Path,
    output_path: Path,
    dpi: float,
    Image,
    async_playwright,
    PlaywrightError,
) -> None:
    target = target_pixels(dpi)
    scale = device_scale_for_target(target)
    css_px = css_board_pixels()

    # Leave room for body padding / print note so the board is fully in-view.
    viewport = int(math.ceil(css_px) + 520)

    html_uri = html_path.resolve().as_uri()
    output_path = output_path.resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"Loading:\n{html_path.resolve()}")
    print()
    print("Board size:\n50 cm × 50 cm")
    print()
    print(f"Target DPI:\n{dpi:g}")
    print()
    print(f"Target resolution:\n{target} × {target}")
    print()

    async with async_playwright() as playwright:
        try:
            browser = await playwright.chromium.launch(
                headless=True,
                args=[
                    "--allow-file-access-from-files",
                    "--hide-scrollbars",
                    "--disable-lcd-text",
                ],
            )
        except PlaywrightError as exc:
            message = str(exc)
            if "Executable doesn't exist" in message or "chromium" in message.lower():
                print(CHROMIUM_HINT, file=sys.stderr)
                raise SystemExit(1) from None
            raise

        context = await browser.new_context(
            viewport={"width": viewport, "height": viewport},
            device_scale_factor=scale,
            color_scheme="light",
            reduced_motion="reduce",
        )
        page = await context.new_page()
        await page.emulate_media(media="screen")
        page.set_default_timeout(120_000)

        print("Waiting for board rendering...")
        await page.goto(html_uri, wait_until="networkidle")
        await page.wait_for_load_state("networkidle")

        exists = await page.evaluate("() => !!document.querySelector('#monopoly-board')")
        if not exists:
            await browser.close()
            print("ERROR: Could not find #monopoly-board", file=sys.stderr)
            raise SystemExit(1)

        await wait_for_images_and_validate(page)

        # Hide page chrome that is outside the board. Do not change board size.
        await page.add_style_tag(
            content="""
            html, body {
                overflow: hidden !important;
            }
            .board-preview {
                overflow: visible !important;
                padding: 0 !important;
            }
            .print-note,
            .no-print {
                display: none !important;
            }
            #monopoly-board {
                box-shadow: none !important;
                margin: 0 !important;
            }
            """
        )

        board = page.locator("#monopoly-board")
        await board.scroll_into_view_if_needed()
        await page.wait_for_timeout(POST_RENDER_WAIT_MS)

        print()
        print("Capturing #monopoly-board...")

        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as tmp:
            tmp_path = Path(tmp.name)

        try:
            await board.screenshot(
                path=str(tmp_path),
                type="png",
                omit_background=False,
                animations="disabled",
                caret="hide",
            )

            with Image.open(tmp_path) as captured:
                captured = captured.convert("RGBA")
                width, height = captured.size
                side = min(width, height)
                if width != height:
                    left = (width - side) // 2
                    top = (height - side) // 2
                    captured = captured.crop((left, top, left + side, top + side))

                if captured.size != (target, target):
                    captured = captured.resize(
                        (target, target),
                        resample=Image.Resampling.LANCZOS,
                    )

                captured.save(output_path, format="PNG", dpi=(dpi, dpi))
        finally:
            tmp_path.unlink(missing_ok=True)
            await browser.close()

    with Image.open(output_path) as verified:
        actual = verified.size

    if actual != (target, target):
        raise RuntimeError(
            f"Final PNG dimensions are {actual[0]} × {actual[1]}, "
            f"expected {target} × {target}."
        )

    print()
    print("Created:")
    print(str(output_path))
    print()
    print(f"Verified PNG size: {actual[0]} × {actual[1]} at {dpi:g} DPI")


def main() -> None:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, ValueError):
            pass

    args = parse_args()

    if args.dpi <= 0:
        print("ERROR: --dpi must be greater than 0", file=sys.stderr)
        raise SystemExit(1)

    html_path = args.input
    if not html_path.is_file():
        print(f"ERROR: Input HTML file not found: {html_path.resolve()}", file=sys.stderr)
        raise SystemExit(1)

    Image = require_pillow()
    async_playwright, PlaywrightError = require_playwright()

    asyncio.run(
        capture_board_png(
            html_path=html_path,
            output_path=args.output,
            dpi=args.dpi,
            Image=Image,
            async_playwright=async_playwright,
            PlaywrightError=PlaywrightError,
        )
    )


if __name__ == "__main__":
    main()
