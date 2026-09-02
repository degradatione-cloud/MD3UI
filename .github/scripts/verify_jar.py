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

import io
import json
import sys
import zipfile
from pathlib import Path

# The mod module itself is intentionally thin: a canvas adapter, a screen
# router, an adopted-screen painter, config, and renderer detection. The widget
# library lives in `core`, which Loom nests as its own jar, so counting only
# top-level classes here would undercount by design.
MIN_MOD_CLASSES = 6
MIN_TOTAL_CLASSES = 15
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
        print(f"  mod classes: {len(classes)}")
        if len(classes) < MIN_MOD_CLASSES:
            fail(f"only {len(classes)} classes in the mod jar, "
                 f"expected at least {MIN_MOD_CLASSES}")

        if ENTRYPOINT not in names:
            fail(f"missing client entrypoint {ENTRYPOINT}")

        # The core module is bundled via Loom's `include`, which nests it as its
        # own jar under META-INF/jars rather than merging the classes. Count
        # through the nested jar so the total reflects what actually ships.
        core_direct = [n for n in names if n.startswith("dev/md3ui/core/")]
        nested = [n for n in names
                  if n.startswith("META-INF/jars/") and n.endswith(".jar")]

        total = len(classes)
        if core_direct:
            print("  core: inline")
        elif nested:
            core_jars = [n for n in nested if "core" in n.lower()]
            if not core_jars:
                fail(f"no core jar among nested jars: {nested}")
            for name in core_jars:
                with zipfile.ZipFile(io.BytesIO(zf.read(name))) as inner:
                    inner_classes = [n for n in inner.namelist()
                                     if n.endswith(".class")]
                    total += len(inner_classes)
                    print(f"  core: nested {Path(name).name} "
                          f"({len(inner_classes)} classes)")
        else:
            fail("core classes are neither bundled nor nested; the mod would "
                 "crash with NoClassDefFoundError at runtime")

        print(f"  total classes: {total}")
        if total < MIN_TOTAL_CLASSES:
            fail(f"only {total} classes across mod + core, "
                 f"expected at least {MIN_TOTAL_CLASSES}")

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
