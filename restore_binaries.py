#!/usr/bin/env python3
"""Restore binary files that are stored in this repo as base64 text chunks.

Why: the GitHub integration used to publish this repo can only carry UTF-8
text, so every binary (native .so libs, flag PNGs, the font, the keystore,
the Gradle wrapper jar) is committed as <name>.b64.000.txt, .001.txt, ...
chunks next to where the real file belongs. Run this once after cloning:

    python3 restore_binaries.py

It is idempotent: files that already exist with the right size are skipped.
CI runs it before ./gradlew; local Gradle builds also restore automatically
via the restoreBinaries task in app/build.gradle.
"""
import base64
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
CHUNK_RE = re.compile(r"^(.*\.b64)\.(\d{3})\.txt$")


def main():
    groups = {}
    for dirpath, _dirs, names in os.walk(ROOT):
        if ".git" in dirpath.split(os.sep):
            continue
        for name in names:
            m = CHUNK_RE.match(name)
            if m:
                groups.setdefault(os.path.join(dirpath, m.group(1)), []).append(
                    (int(m.group(2)), os.path.join(dirpath, name))
                )
    restored = skipped = 0
    for prefix, parts in sorted(groups.items()):
        target = prefix[: -len(".b64")]
        parts.sort()
        encoded = ""
        for _idx, path in parts:
            with open(path, "r") as fh:
                encoded += fh.read()
        raw = base64.b64decode("".join(encoded.split()))
        if os.path.exists(target) and os.path.getsize(target) == len(raw):
            skipped += 1
            continue
        with open(target, "wb") as fh:
            fh.write(raw)
        restored += 1
        print(f"restored {os.path.relpath(target, ROOT)} ({len(raw)} bytes)")
    print(f"done: {restored} restored, {skipped} already present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
