#!/usr/bin/env python3
"""Validate the exported Figma design tokens.

The token files are generated, which means a silent regression in the exporter
would ship a structurally valid but semantically empty palette. These checks
assert the things a designer would actually notice: that both themes are present,
that every colour is a real hex value, and that no role went missing.

Usage:
    verify_tokens.py <design-dir>
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

HEX = re.compile(r"^#[0-9A-F]{6}$")
MIN_ROLES = 28
REQUIRED_ROLES = {
    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
    "tertiary", "onTertiary", "error", "onError",
    "surface", "onSurface", "onSurfaceVariant",
    "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh",
    "outline", "outlineVariant",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def check_tokens(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))

    for section in ("palette", "scheme", "shape", "spacing", "state", "motion"):
        if section not in data:
            fail(f"tokens.json missing '{section}'")

    # Tonal palettes: every named palette must cover the full tone ladder.
    for name, tones in data["palette"].items():
        if not isinstance(tones, dict):
            continue
        for stop in ("0", "40", "80", "100"):
            if stop not in tones:
                fail(f"palette '{name}' missing tone {stop}")
        value = tones["40"]["$value"]
        if not HEX.match(value):
            fail(f"palette '{name}' tone 40 is not a hex colour: {value!r}")
    print(f"  palettes: {len(data['palette'])}")

    scheme = data["scheme"]
    for mode in ("light", "dark"):
        if mode not in scheme:
            fail(f"scheme missing '{mode}'")
        roles = scheme[mode]
        if len(roles) < MIN_ROLES:
            fail(f"{mode} scheme has only {len(roles)} roles, "
                 f"expected >= {MIN_ROLES}")
        missing = REQUIRED_ROLES - roles.keys()
        if missing:
            fail(f"{mode} scheme missing roles: {sorted(missing)}")
        for role, entry in roles.items():
            if entry.get("$type") != "color":
                fail(f"{mode}.{role} is not typed as a color")
            if not HEX.match(entry.get("$value", "")):
                fail(f"{mode}.{role} has a bad value: {entry.get('$value')!r}")
        print(f"  {mode} roles: {len(roles)}")

    # Light and dark must actually differ, or theme switching does nothing.
    if scheme["light"]["surface"]["$value"] == scheme["dark"]["surface"]["$value"]:
        fail("light and dark share the same surface colour")

    for section in ("shape", "spacing"):
        for key, entry in data[section].items():
            if key.startswith("$"):
                continue
            if entry.get("$type") != "dimension":
                fail(f"{section}.{key} should be a dimension")


def check_variables(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    collections = data.get("variableCollections")
    if not collections:
        fail("variables.json has no variableCollections")

    collection = collections[0]
    modes = collection.get("modes", [])
    if sorted(modes) != ["Dark", "Light"]:
        fail(f"expected Light and Dark modes, got {modes}")

    variables = collection.get("variables", [])
    if len(variables) < MIN_ROLES:
        fail(f"only {len(variables)} Figma variables, expected >= {MIN_ROLES}")

    for var in variables:
        if var.get("type") != "COLOR":
            fail(f"variable {var.get('name')} is not a COLOR")
        for mode in ("Light", "Dark"):
            value = var.get("valuesByMode", {}).get(mode)
            if value is None:
                fail(f"variable {var.get('name')} missing {mode} value")
            for channel in ("r", "g", "b", "a"):
                v = value.get(channel)
                if not isinstance(v, (int, float)) or not 0.0 <= v <= 1.0:
                    fail(f"variable {var.get('name')}.{mode}.{channel} "
                         f"out of range: {v!r} (Figma wants 0..1)")
    print(f"  figma variables: {len(variables)}")


def main() -> None:
    design_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "design")

    tokens = design_dir / "tokens.json"
    variables = design_dir / "variables.json"
    for path in (tokens, variables):
        if not path.is_file():
            fail(f"{path} was not generated")

    check_tokens(tokens)
    check_variables(variables)
    print("  OK  design tokens verified")


if __name__ == "__main__":
    main()
