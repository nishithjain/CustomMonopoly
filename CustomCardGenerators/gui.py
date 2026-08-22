#!/usr/bin/env python3
"""Desktop GUI for the Monopoly edition generator."""

from __future__ import annotations

import queue
import shutil
import subprocess
import sys
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from generator.board_spec import INNER_BOARD_SIZE_CM, OUTER_BOARD_SIZE_CM
from generator.pipeline import GenerationOptions, run_generation
from generator.utils import (
    configure_stdio,
    edition_output_dir,
    inner_box_display_path,
    inner_box_path,
    inner_box_status,
    list_editions,
)
from generator.validator import ValidationResult

DPI_CHOICES = ["150", "200", "300", "600"]

ARTIFACT_LABELS = (
    ("propertyHtml", "property card HTML"),
    ("propertyPng", "property card PNG"),
    ("eventHtml", "event card HTML"),
    ("eventPng", "event card PNG"),
    ("boardHtml", "board HTML"),
    ("boardPng", "board PNG"),
)


def relative_to_root(path: Path) -> str:
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return str(path)


def _configure_tcl_env() -> None:
    """Help relocated Python installs find Tcl/Tk libraries."""
    import os

    if os.environ.get("TCL_LIBRARY"):
        return
    prefixes = [Path(getattr(sys, "base_prefix", sys.prefix)), Path(sys.prefix), Path(sys.executable).resolve().parent]
    for prefix in prefixes:
        tcl = prefix / "tcl" / "tcl8.6"
        if (tcl / "init.tcl").is_file():
            os.environ["TCL_LIBRARY"] = str(tcl)
            tk = prefix / "tcl" / "tk8.6"
            if (tk / "tk.tcl").is_file():
                os.environ["TK_LIBRARY"] = str(tk)
            return


def _try_import_ctk():
    try:
        import customtkinter as ctk

        return ctk
    except ImportError:
        return None


def open_folder(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    if sys.platform == "win32":
        subprocess.run(["explorer", str(path.resolve())], check=False)
    elif sys.platform == "darwin":
        subprocess.run(["open", str(path)], check=False)
    else:
        subprocess.run(["xdg-open", str(path)], check=False)


def validation_log_lines(result: ValidationResult) -> list[str]:
    lines: list[str] = []
    if not any(i.source == "edition.json" and i.level == "error" for i in result.issues):
        lines.append("✓ edition.json")
    if not any(i.source == "properties.json" and i.level == "error" for i in result.issues):
        lines.append("✓ properties.json")
        if result.property_count:
            lines.append(f"     {result.property_count} properties")
    if not any(i.source == "events.json" and i.level == "error" for i in result.issues):
        lines.append("✓ events.json")
        if result.event_count:
            lines.append(f"     {result.event_count} events")
    if not any(i.source == "banking_values.json" and i.level == "error" for i in result.issues):
        lines.append("✓ banking_values.json")
    if not any(i.source == "board_relationships.json" and i.level == "error" for i in result.issues):
        lines.append("✓ board_relationships.json")
    if not any(i.source == "templates" and i.level == "error" for i in result.issues):
        lines.append("✓ templates")

    inner = result.inner_box or {}
    if inner.get("found"):
        lines.append("✓ InnerBox.png found")
        lines.append(f"     {inner.get('expectedPath') or inner.get('path')}")
    else:
        lines.append("⚠ InnerBox.png not found")
        lines.append("     Center board area will remain empty.")

    for issue in result.errors:
        lines.append("")
        lines.append(issue.format_block())
    if result.ok:
        if result.warnings:
            lines.append("")
            lines.append(f"Validation successful with {len(result.warnings)} warning(s).")
        else:
            lines.append("")
            lines.append("Validation successful.")
    else:
        lines.append("")
        lines.append("Validation failed.")
    return lines


class GeneratorApp:
    def __init__(self, ctk) -> None:
        self.ctk = ctk
        _configure_tcl_env()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("green")

        self.root = ctk.CTk()
        self.root.title("Monopoly Edition Generator")
        self.root.geometry("720x900")
        self.root.minsize(640, 780)

        self.queue: queue.Queue = queue.Queue()
        self.busy = False
        self.validation_logged = False
        self.preview_image = None
        self.editions = list_editions()
        self.edition_by_label = {}
        used: dict[str, int] = {}
        for item in self.editions:
            label = item["name"]
            if label in used:
                label = f"{item['name']} ({item['folderName']})"
            used[label] = 1
            self.edition_by_label[label] = item

        self._build()
        self.root.after(80, self._poll_queue)
        if self.editions:
            self._on_edition_change(self.edition_combo.get())

    def _build(self) -> None:
        ctk = self.ctk
        self.root.grid_columnconfigure(0, weight=1)
        self.root.grid_rowconfigure(2, weight=1)

        header = ctk.CTkLabel(
            self.root,
            text="MONOPOLY EDITION GENERATOR",
            font=ctk.CTkFont(size=22, weight="bold"),
        )
        header.grid(row=0, column=0, padx=24, pady=(18, 8), sticky="w")

        form = ctk.CTkFrame(self.root, fg_color="transparent")
        form.grid(row=1, column=0, padx=24, pady=4, sticky="nsew")
        form.grid_columnconfigure(0, weight=1)
        form.grid_columnconfigure(1, weight=1)

        ctk.CTkLabel(form, text="Edition", font=ctk.CTkFont(weight="bold")).grid(
            row=0, column=0, columnspan=2, sticky="w"
        )
        labels = list(self.edition_by_label.keys()) or ["(no editions found)"]
        default = "India" if "India" in self.edition_by_label else labels[0]
        self.edition_combo = ctk.CTkComboBox(
            form,
            values=labels,
            command=self._on_edition_change,
            width=320,
        )
        self.edition_combo.set(default)
        self.edition_combo.grid(row=1, column=0, columnspan=2, sticky="ew", pady=(0, 12))

        ctk.CTkLabel(form, text="Generate", font=ctk.CTkFont(weight="bold")).grid(
            row=2, column=0, columnspan=2, sticky="w"
        )
        checks = ctk.CTkFrame(form, fg_color="transparent")
        checks.grid(row=3, column=0, columnspan=2, sticky="w", pady=(0, 12))
        self.var_properties = ctk.BooleanVar(value=True)
        self.var_events = ctk.BooleanVar(value=True)
        self.var_board = ctk.BooleanVar(value=True)
        self.var_png = ctk.BooleanVar(value=True)
        ctk.CTkCheckBox(checks, text="Property Cards", variable=self.var_properties).grid(row=0, column=0, padx=(0, 16))
        ctk.CTkCheckBox(checks, text="Event Cards", variable=self.var_events).grid(row=0, column=1, padx=(0, 16))
        ctk.CTkCheckBox(checks, text="Board", variable=self.var_board).grid(row=0, column=2, padx=(0, 16))
        ctk.CTkCheckBox(checks, text="Generate PNGs", variable=self.var_png).grid(row=0, column=3)

        ctk.CTkLabel(form, text="Board", font=ctk.CTkFont(weight="bold")).grid(
            row=4, column=0, sticky="w"
        )
        dims = ctk.CTkFrame(form)
        dims.grid(row=5, column=0, sticky="ew", pady=(0, 10), padx=(0, 12))
        ctk.CTkLabel(
            dims,
            text=f"Outer Board        {OUTER_BOARD_SIZE_CM:g} × {OUTER_BOARD_SIZE_CM:g} cm",
        ).pack(anchor="w", padx=12, pady=(8, 2))
        ctk.CTkLabel(
            dims,
            text=f"Inner Artwork      {INNER_BOARD_SIZE_CM:g} × {INNER_BOARD_SIZE_CM:g} cm",
        ).pack(anchor="w", padx=12, pady=(2, 8))

        ctk.CTkLabel(form, text="DPI", font=ctk.CTkFont(weight="bold")).grid(
            row=4, column=1, sticky="w"
        )
        dpi_frame = ctk.CTkFrame(form)
        dpi_frame.grid(row=5, column=1, sticky="ew", pady=(0, 10))
        self.dpi_combo = ctk.CTkComboBox(dpi_frame, values=DPI_CHOICES, width=120)
        self.dpi_combo.set("300")
        self.dpi_combo.pack(anchor="w", padx=12, pady=12)

        ctk.CTkLabel(form, text="Inner Artwork", font=ctk.CTkFont(weight="bold")).grid(
            row=6, column=0, columnspan=2, sticky="w"
        )
        art = ctk.CTkFrame(form)
        art.grid(row=7, column=0, columnspan=2, sticky="nsew", pady=(0, 10))
        art.grid_columnconfigure(1, weight=1)
        self.inner_status = ctk.CTkLabel(art, text="", justify="left", anchor="w")
        self.inner_status.grid(row=0, column=1, sticky="w", padx=12, pady=(8, 4))
        self.inner_path_label = ctk.CTkLabel(art, text="", justify="left", anchor="w", wraplength=420)
        self.inner_path_label.grid(row=1, column=1, sticky="w", padx=12, pady=(0, 4))
        self.preview_label = ctk.CTkLabel(art, text="", width=140, height=140)
        self.preview_label.grid(row=0, column=0, rowspan=3, padx=12, pady=12)

        buttons = ctk.CTkFrame(art, fg_color="transparent")
        buttons.grid(row=2, column=1, sticky="w", padx=12, pady=(0, 10))
        self.replace_btn = ctk.CTkButton(
            buttons, text="Select / Replace Inner Artwork", command=self._replace_inner_box, width=220
        )
        self.replace_btn.pack(side="left", padx=(0, 8))
        self.remove_btn = ctk.CTkButton(
            buttons, text="Remove Inner Artwork", command=self._remove_inner_box, width=180, fg_color="#8a3b3b"
        )
        self.remove_btn.pack(side="left")

        actions = ctk.CTkFrame(form, fg_color="transparent")
        actions.grid(row=8, column=0, columnspan=2, sticky="ew", pady=(4, 8))
        self.validate_btn = ctk.CTkButton(actions, text="Validate", command=self._validate, width=160)
        self.validate_btn.pack(side="left", padx=(0, 12))
        self.generate_btn = ctk.CTkButton(
            actions, text="Generate Selected", command=self._generate, width=200
        )
        self.generate_btn.pack(side="left")
        self.open_btn = ctk.CTkButton(
            actions, text="Open Output Folder", command=self._open_output, width=180, state="disabled"
        )
        self.open_btn.pack(side="right")

        self.progress = ctk.CTkProgressBar(form)
        self.progress.grid(row=9, column=0, columnspan=2, sticky="ew", pady=(0, 4))
        self.progress.set(0)
        self.progress_label = ctk.CTkLabel(form, text="", anchor="w")
        self.progress_label.grid(row=10, column=0, columnspan=2, sticky="w")

        status_frame = ctk.CTkFrame(self.root)
        status_frame.grid(row=2, column=0, padx=24, pady=(4, 18), sticky="nsew")
        status_frame.grid_rowconfigure(1, weight=1)
        status_frame.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(status_frame, text="STATUS", font=ctk.CTkFont(weight="bold")).grid(
            row=0, column=0, sticky="w", padx=10, pady=(8, 4)
        )
        self.log = ctk.CTkTextbox(status_frame, wrap="word")
        self.log.grid(row=1, column=0, sticky="nsew", padx=10, pady=(0, 10))

    def selected_edition_id(self) -> str | None:
        item = self.edition_by_label.get(self.edition_combo.get())
        if not item:
            return None
        return item["folderName"]

    def selected_display_name(self) -> str:
        item = self.edition_by_label.get(self.edition_combo.get())
        if not item:
            return self.edition_combo.get()
        return item["name"]

    def _append_log(self, text: str) -> None:
        self.log.insert("end", text + "\n")
        self.log.see("end")

    def _clear_log(self) -> None:
        self.log.delete("1.0", "end")

    def _on_edition_change(self, _value=None) -> None:
        edition_id = self.selected_edition_id()
        if not edition_id:
            return
        status = inner_box_status(edition_id)
        expected = inner_box_display_path(edition_id)
        if status["found"]:
            self.inner_status.configure(text="✓ InnerBox.png found")
            self.inner_path_label.configure(text=expected)
            self.remove_btn.configure(state="normal")
        else:
            self.inner_status.configure(
                text="⚠ InnerBox.png not found\nCenter area will remain empty."
            )
            self.inner_path_label.configure(text=expected)
            self.remove_btn.configure(state="disabled")
        self._update_preview(edition_id if status["found"] else None)
        out = edition_output_dir(edition_id)
        self.open_btn.configure(state="normal" if out.is_dir() else "disabled")

    def _update_preview(self, edition_id: str | None) -> None:
        if not edition_id:
            self.preview_image = None
            self.preview_label.configure(image=None, text="No preview")
            return
        path = inner_box_path(edition_id)
        try:
            from PIL import Image

            # Copy into memory so the PNG is not left open and can be replaced.
            with Image.open(path) as source:
                image = source.copy()
            image.thumbnail((140, 140))
            self.preview_image = self.ctk.CTkImage(
                light_image=image, dark_image=image, size=image.size
            )
            self.preview_label.configure(image=self.preview_image, text="")
        except Exception:
            self.preview_image = None
            self.preview_label.configure(image=None, text="No preview")

    def _set_busy(self, busy: bool) -> None:
        self.busy = busy
        state = "disabled" if busy else "normal"
        for widget in (
            self.edition_combo,
            self.validate_btn,
            self.generate_btn,
            self.replace_btn,
            self.dpi_combo,
        ):
            widget.configure(state=state)
        if not busy:
            self._on_edition_change()
        else:
            self.remove_btn.configure(state="disabled")

    def _replace_inner_box(self) -> None:
        edition_id = self.selected_edition_id()
        if not edition_id or self.busy:
            return
        from tkinter import filedialog, messagebox

        chosen = filedialog.askopenfilename(
            title="Select InnerBox.png",
            filetypes=[("PNG images", "*.png"), ("All files", "*.*")],
        )
        if not chosen:
            return
        dest = inner_box_path(edition_id)
        if dest.exists():
            if not messagebox.askyesno(
                "Replace Inner Artwork",
                f"InnerBox.png already exists for {self.selected_display_name()}.\n\nReplace it?",
            ):
                return
        if Path(chosen).resolve() == dest.resolve():
            self._append_log("That file is already the inner artwork for this edition.")
            return
        dest.parent.mkdir(parents=True, exist_ok=True)
        self._update_preview(None)
        try:
            shutil.copy2(chosen, dest)
        except OSError as exc:
            messagebox.showerror(
                "Replace Inner Artwork",
                f"Could not save InnerBox.png:\n\n{exc}\n\n"
                "Close any program that has the file open and try again.",
            )
            self._on_edition_change()
            return
        self._append_log(f"Saved InnerBox.png to {inner_box_display_path(edition_id)}")
        self._on_edition_change()

    def _remove_inner_box(self) -> None:
        edition_id = self.selected_edition_id()
        if not edition_id or self.busy:
            return
        from tkinter import messagebox

        dest = inner_box_path(edition_id)
        if not dest.exists():
            return
        if not messagebox.askyesno(
            "Remove Inner Artwork",
            f"Remove InnerBox.png for {self.selected_display_name()}?\n\n"
            "The center of the generated board will become empty.",
        ):
            return
        self._update_preview(None)
        try:
            dest.unlink()
        except OSError as exc:
            messagebox.showerror(
                "Remove Inner Artwork",
                f"Could not remove InnerBox.png:\n\n{exc}\n\n"
                "Close any program that has the file open and try again.",
            )
            self._on_edition_change()
            return
        self._append_log("Removed InnerBox.png. Center will remain empty on the next generate.")
        self._on_edition_change()

    def _options(self, validate_only: bool) -> GenerationOptions | None:
        edition_id = self.selected_edition_id()
        if not edition_id:
            self._append_log("No edition selected.")
            return None
        if not validate_only and not any(
            (
                self.var_properties.get(),
                self.var_events.get(),
                self.var_board.get(),
            )
        ):
            self._append_log("Select at least Property Cards, Event Cards, or Board.")
            return None
        return GenerationOptions(
            edition_id=edition_id,
            generate_properties=self.var_properties.get() and not validate_only,
            generate_events=self.var_events.get() and not validate_only,
            generate_board=self.var_board.get() and not validate_only,
            generate_pngs=self.var_png.get() and not validate_only,
            dpi=float(self.dpi_combo.get()),
            validate_only=validate_only,
        )

    def _validate(self) -> None:
        self._run_job(validate_only=True)

    def _generate(self) -> None:
        self._run_job(validate_only=False)

    def _run_job(self, validate_only: bool) -> None:
        options = self._options(validate_only)
        if not options or self.busy:
            return
        self._clear_log()
        self._append_log(f"Edition: {self.selected_display_name()}")
        if not validate_only:
            self._append_log(f"Selected: {self._selection_summary()}")
        self._append_log("")
        self.progress.set(0)
        self.progress_label.configure(text="Starting...")
        self.validation_logged = False
        self._set_busy(True)

        def worker() -> None:
            def progress(message: str, percent: int) -> None:
                self.queue.put(("progress", message, percent))

            def validated(result: ValidationResult) -> None:
                self.queue.put(("validation", result))

            try:
                outcome = run_generation(options, progress=progress, after_validate=validated)
                self.queue.put(("done", outcome, validate_only))
            except Exception as exc:
                self.queue.put(("failed", str(exc)))

        threading.Thread(target=worker, daemon=True).start()

    def _poll_queue(self) -> None:
        try:
            while True:
                item = self.queue.get_nowait()
                kind = item[0]
                if kind == "progress":
                    _, message, percent = item
                    self.progress.set(percent / 100.0)
                    self.progress_label.configure(text=message)
                    self._append_log(message)
                elif kind == "validation":
                    for line in validation_log_lines(item[1]):
                        self._append_log(line)
                    self._append_log("")
                    self.validation_logged = True
                elif kind == "done":
                    _, outcome, validate_only = item
                    self._finish(outcome, validate_only)
                elif kind == "failed":
                    self._append_log(f"[ERROR] {item[1]}")
                    self.progress_label.configure(text="Failed")
                    self._set_busy(False)
        except queue.Empty:
            pass
        self.root.after(80, self._poll_queue)

    def _finish(self, outcome, validate_only: bool) -> None:
        if not self.validation_logged:
            for line in validation_log_lines(outcome.validation):
                self._append_log(line)
            self._append_log("")
        if outcome.error and not outcome.ok:
            self._append_log(f"[ERROR] {outcome.error}")
        if outcome.ok and not validate_only and outcome.status == "success":
            self._log_written_files(outcome)
            self.open_btn.configure(state="normal")
        self.progress.set(1 if outcome.ok else 0)
        self.progress_label.configure(text="Done" if outcome.ok else "Stopped")
        self._set_busy(False)

    def _log_written_files(self, outcome) -> None:
        self._append_log("")
        self._append_log("Files written:")
        wrote_any = False
        for key, label in ARTIFACT_LABELS:
            paths = outcome.artifacts.get(key) or []
            if not paths:
                continue
            wrote_any = True
            if len(paths) == 1:
                self._append_log(f"     {label}: {relative_to_root(paths[0])}")
            else:
                self._append_log(
                    f"     {len(paths)} {label} files in {relative_to_root(paths[0].parent)}/"
                )
        if not wrote_any:
            self._append_log("     Nothing was written.")
        for note in outcome.png_notes:
            self._append_log(f"     Note: {note}")
        if outcome.report_path:
            self._append_log(f"     report: {relative_to_root(outcome.report_path)}")
        self._append_log("")
        self._append_log(f"Output folder: {outcome.output_dir}")

    def _selection_summary(self) -> str:
        parts = []
        if self.var_properties.get():
            parts.append("Property Cards")
        if self.var_events.get():
            parts.append("Event Cards")
        if self.var_board.get():
            parts.append("Board")
        picked = ", ".join(parts) if parts else "nothing"
        png = "PNGs on" if self.var_png.get() else "PNGs off (HTML only)"
        return f"{picked}  |  {png}"

    def _open_output(self) -> None:
        edition_id = self.selected_edition_id()
        if edition_id:
            open_folder(edition_output_dir(edition_id))

    def run(self) -> int:
        self.root.mainloop()
        return 0


def main() -> int:
    configure_stdio()
    _configure_tcl_env()
    ctk = _try_import_ctk()
    if ctk is None:
        print(
            "CustomTkinter is required for the GUI.\n\n"
            "Install with:\n    pip install customtkinter pillow"
        )
        return 1
    return GeneratorApp(ctk).run()


if __name__ == "__main__":
    raise SystemExit(main())
