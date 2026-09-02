#!/usr/bin/env python3
"""Validate a built MD3UI jar before it is allowed near a release.

A Gradle build can succeed and still produce a jar that is useless: resource
filtering may have left ``${version}`` unsubstituted, the class output may have
been silently empty, or the mod entrypoint may not have made it in. CI checks all
of that here rather than discovering it from a crash report.

Usage:
    verify_jar.py <path-to-jar> <expected-mc-version>
"""

from __future__ import annotations

import json
import sys
import zipfile
from pathlib import Path

MIN_CLASSES = 15
ENTRYPOINT = "dev/md3ui/mod/Md3UI.class"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def main() -> None:
    if len(sys.argv) != 3:
        fail(f"usage: {Path(sys.argv[0]).name} <jar> <mc-version>")

    jar_path = Path(sys.argv[1])
    expected_mc = sys.argv[2]

    if not jar_path.is_file():
        fail(f"{jar_path} does not exist")

    size = jar_path.stat().st_size
    print(f"  jar: {jar_path.name} ({size} bytes)")
    if size < 4096:
        fail(f"jar is implausibly small ({size} bytes)")

    with zipfile.ZipFile(jar_path) as zf:
        names = zf.namelist()

        classes = [n for n in names if n.endswith(".class")]
        print(f"  classes: {len(classes)}")
        if len(classes) < MIN_CLASSES:
            fail(f"only {len(classes)} classes, expected at least {MIN_CLASSES}")

        if ENTRYPOINT not in names:
            fail(f"missing client entrypoint {ENTRYPOINT}")

        # The core module is bundled via Loom's `include`, so its classes must be
        # reachable at runtime either directly or as a nested jar.
        has_core_direct = any(n.startswith("dev/md3ui/core/") for n in names)
        has_core_nested = any(
            n.startswith("META-INF/jars/") and "core" in n for n in names
        )
        if not (has_core_direct or has_core_nested):
            fail("core classes are neither bundled nor nested; the mod would "
                 "crash with NoClassDefFoundError at runtime")
        print(f"  core: {'inline' if has_core_direct else 'nested jar'}")

        if "fabric.mod.json" not in names:
            fail("fabric.mod.json is missing")

        raw = zf.read("fabric.mod.json").decode("utf-8")
        if "${" in raw:
            fail("fabric.mod.json contains an unsubstituted placeholder; "
                 "processResources filtering did not run")

        try:
            meta = json.loads(raw)
        except json.JSONDecodeError as exc:
            fail(f"fabric.mod.json is not valid JSON: {exc}")

        if meta.get("id") != "md3ui":
            fail(f"unexpected mod id {meta.get('id')!r}")

        version = meta.get("version", "")
        print(f"  version: {version}")
        if expected_mc not in version:
            fail(f"version {version!r} does not mention {expected_mc}")

        depends = meta.get("depends", {})
        mc_dep = depends.get("minecraft")
        if not mc_dep:
            fail("no minecraft dependency declared")
        print(f"  minecraft dep: {mc_dep}")

        if "fabric-screen-api-v1" not in depends:
            fail("fabric-screen-api-v1 must be a hard dependency; the mod "
                 "cannot hook screens without it")

        entrypoints = meta.get("entrypoints", {}).get("client", [])
        if "dev.md3ui.mod.Md3UI" not in entrypoints:
            fail(f"client entrypoint not declared, got {entrypoints}")

        # Mixins are deliberately absent; assert it so a future change is a
        # conscious decision rather than an accident.
        if any(n.endswith("mixins.json") for n in names):
            fail("a mixin config appeared in the jar; MD3UI's compatibility "
                 "guarantee depends on shipping none")
        if "mixins" in meta:
            fail("fabric.mod.json declares mixins")

    print(f"  OK  {jar_path.name} verified for {expected_mc}")


if __name__ == "__main__":
    main()
