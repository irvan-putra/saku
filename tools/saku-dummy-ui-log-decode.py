#!/usr/bin/env python3
"""Strictly recover six synthetic Saku UI PNGs from a GitHub Actions log.

Usage: python3 saku-dummy-ui-log-decode.py RUN_LOG OUTPUT_DIRECTORY
Requires Pillow for full image decode. OUTPUT_DIRECTORY must not already exist.
"""
from __future__ import annotations

import base64
import hashlib
import io
import re
import sys
from pathlib import Path

EXPECTED = {
    "01-locked.png",
    "02-preview.png",
    "03-cancelled.png",
    "04-approved.png",
    "05-relocked.png",
    "06-launcher-after-session-ended.png",
}
PREFIX = re.compile(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z\s+")
BEGIN = re.compile(r"^SAKU_DUMMY_PNG_BEGIN ([a-z0-9-]+\.png) ([1-9][0-9]{0,6}) ([0-9a-f]{64}) ([1-9][0-9]{0,3}) ([1-9][0-9]{0,3})$")
END = re.compile(r"^SAKU_DUMMY_PNG_END ([a-z0-9-]+\.png)$")
DATA = re.compile(r"^SAKU_DUMMY_PNG_DATA ([A-Za-z0-9+/]+={0,2})$")
MAX_BYTES = 1024 * 1024
MAX_DIMENSION = 2560


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def canonical(line: str) -> str:
    line = re.sub(r"\x1b\[[0-9;]*m", "", line.rstrip("\r\n"))
    return PREFIX.sub("", line, count=1)


def decode_png(data: bytes, width: int, height: int, name: str) -> None:
    try:
        from PIL import Image
    except ImportError:
        fail("Pillow is required for full PNG decode; no files were delivered")
    try:
        with Image.open(io.BytesIO(data)) as image:
            if image.format != "PNG":
                fail(f"{name}: decoded format is {image.format!r}, not PNG")
            if image.size != (width, height):
                fail(f"{name}: decoded dimensions {image.size!r} differ from header")
            if getattr(image, "n_frames", 1) != 1:
                fail(f"{name}: unexpected multi-frame PNG")
            image.verify()
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            if image.size != (width, height) or image.format != "PNG":
                fail(f"{name}: second full decode disagrees")
    except SystemExit:
        raise
    except Exception as error:
        fail(f"{name}: PNG decode failed: {type(error).__name__}: {error}")


def main() -> None:
    if len(sys.argv) != 3:
        fail(__doc__.strip())
    log_path = Path(sys.argv[1])
    out_dir = Path(sys.argv[2])
    if not log_path.is_file():
        fail(f"Missing run log: {log_path}")
    if out_dir.exists():
        fail(f"Refusing to mix with pre-existing output directory: {out_dir}")
    text = log_path.read_text(encoding="utf-8", errors="strict")
    found: dict[str, tuple[bytes, str, int, int]] = {}
    active = None
    encoded_parts: list[str] = []
    for line_number, raw in enumerate(text.splitlines(), 1):
        line = canonical(raw)
        match = BEGIN.fullmatch(line)
        if active is None:
            if match:
                name, length_s, digest, width_s, height_s = match.groups()
                if name not in EXPECTED:
                    fail(f"Unexpected PNG name at line {line_number}: {name}")
                if name in found:
                    fail(f"Duplicate PNG block: {name}")
                length, width, height = int(length_s), int(width_s), int(height_s)
                if length > MAX_BYTES or width > MAX_DIMENSION or height > MAX_DIMENSION:
                    fail(f"Declared PNG size/dimensions exceed bounds: {name}")
                active = (name, length, digest, width, height)
                encoded_parts = []
            elif line.startswith("SAKU_DUMMY_PNG_"):
                fail(f"Malformed or unexpected PNG marker at line {line_number}")
            continue

        name, length, digest, width, height = active
        end = END.fullmatch(line)
        if end:
            if end.group(1) != name:
                fail(f"Mismatched PNG end marker at line {line_number}: {end.group(1)}")
            if not encoded_parts:
                fail(f"Empty PNG data block: {name}")
            encoded = "".join(encoded_parts)
            try:
                data = base64.b64decode(encoded, validate=True)
            except Exception as error:
                fail(f"Invalid base64 in {name}: {error}")
            if len(data) != length:
                fail(f"Length mismatch for {name}: declared {length}, decoded {len(data)}")
            actual_hash = hashlib.sha256(data).hexdigest()
            if actual_hash != digest:
                fail(f"SHA256 mismatch for {name}")
            if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
                fail(f"PNG signature/IHDR missing: {name}")
            if int.from_bytes(data[16:20], "big") != width or int.from_bytes(data[20:24], "big") != height:
                fail(f"IHDR dimensions disagree with marker: {name}")
            decode_png(data, width, height, name)
            found[name] = (data, digest, width, height)
            active = None
            encoded_parts = []
            continue

        data_match = DATA.fullmatch(line)
        if not data_match:
            fail(f"Unexpected/truncated line inside {name} block at line {line_number}")
        chunk = data_match.group(1)
        if len(chunk) > 120:
            fail(f"Oversized base64 line in {name} at line {line_number}")
        encoded_parts.append(chunk)
        if sum(map(len, encoded_parts)) > ((MAX_BYTES + 2) // 3) * 4:
            fail(f"Encoded data exceeds one-MiB bound: {name}")

    if active is not None:
        fail(f"Truncated PNG block without end marker: {active[0]}")
    if set(found) != EXPECTED:
        missing = sorted(EXPECTED - set(found))
        extra = sorted(set(found) - EXPECTED)
        fail(f"PNG block set mismatch; missing={missing}, unexpected={extra}")

    out_dir.mkdir(parents=True, exist_ok=False)
    for name in sorted(EXPECTED):
        data, digest, width, height = found[name]
        path = out_dir / name
        path.write_bytes(data)
        print(f"{path}\t{width}x{height}\t{len(data)} bytes\tsha256={digest}")
    print("All six expected blocks verified and fully decoded. Open and inspect every PNG before making a visual-result claim.")


if __name__ == "__main__":
    main()
