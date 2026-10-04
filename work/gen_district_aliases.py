# -*- coding: utf-8 -*-
"""
Write DISTRICT_ALIASES into both engines from work/district_aliases.py.

Python : server/app/core/nlu.py
Kotlin : app/src/main/java/in/jandwar/app/ai/OnDeviceNlu.kt

Refuses to write unless the key set exactly equals districts.json, so the
"Nilgiris" / "The Nilgiris" class of silent miss cannot come back.
"""
from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, "/home/user/work")
from district_aliases import ALIASES  # noqa: E402

ROOT = pathlib.Path("/home/user/JanDwar")
PY = ROOT / "server/app/core/nlu.py"
KT = ROOT / "app/src/main/java/in/jandwar/app/ai/OnDeviceNlu.kt"
DJ = ROOT / "app/src/main/assets/districts.json"


def validate() -> dict[str, list[str]]:
    dj = json.load(open(DJ, encoding="utf-8"))
    want = set(dj["all"])
    have = set(ALIASES)
    missing = sorted(want - have)
    extra = sorted(have - want)
    if missing:
        sys.exit(f"ABORT: {len(missing)} districts have no aliases: {missing[:8]}")
    if extra:
        sys.exit(f"ABORT: alias keys not present in districts.json: {extra}")

    # every district must carry at least one non-ASCII (native script) form
    noscript = [d for d, fs in ALIASES.items()
                if not any(any(ord(c) > 127 for c in f) for f in fs)]
    if noscript:
        sys.exit(f"ABORT: no native-script form for: {noscript}")

    # short forms are substring-matched -- flag them for a human to eyeball
    short = {d: [f for f in fs if len(f) < 4] for d, fs in ALIASES.items()}
    short = {d: f for d, f in short.items() if f}
    if short:
        print(f"  note: {len(short)} district(s) carry a form under 4 chars: {short}")

    # duplicate forms across different districts would make the match arbitrary
    seen: dict[str, str] = {}
    dupes = []
    for d, fs in ALIASES.items():
        for f in fs:
            k = f.lower()
            if k in seen and seen[k] != d:
                dupes.append((k, seen[k], d))
            seen[k] = d
    if dupes:
        sys.exit(f"ABORT: the same form is claimed by two districts: {dupes}")

    by_state = {s: len(v["all"]) for s, v in dj["by_state"].items()}
    print(f"  validated {len(ALIASES)} districts, "
          f"{sum(len(v) for v in ALIASES.values())} forms, states {by_state}")
    return ALIASES


def render_python(al: dict[str, list[str]]) -> str:
    out = ["DISTRICT_ALIASES = {"]
    for d, fs in al.items():
        forms = ", ".join(json.dumps(f, ensure_ascii=False) for f in fs)
        out.append(f"    {json.dumps(d, ensure_ascii=False)}: [{forms}],")
    out.append("}")
    return "\n".join(out)


def render_kotlin(al: dict[str, list[str]]) -> str:
    out = ["    private val DISTRICT_ALIASES: Map<String, List<String>> = mapOf("]
    for d, fs in al.items():
        forms = ", ".join(json.dumps(f, ensure_ascii=False) for f in fs)
        out.append(f"        {json.dumps(d, ensure_ascii=False)} to listOf({forms}),")
    out.append("    )")
    return "\n".join(out)


def splice(path: pathlib.Path, start_marker: str, close: str, block: str) -> None:
    """Replace from the line starting with start_marker to the first line
    equal to `close`, inclusive."""
    lines = path.read_text(encoding="utf-8").split("\n")
    s = next((i for i, l in enumerate(lines) if l.startswith(start_marker)), None)
    if s is None:
        sys.exit(f"ABORT: marker {start_marker!r} not found in {path.name}")
    e = next((i for i in range(s + 1, len(lines)) if lines[i] == close), None)
    if e is None:
        sys.exit(f"ABORT: closing {close!r} not found after line {s} in {path.name}")
    old_n = e - s + 1
    lines[s:e + 1] = block.split("\n")
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"  {path.name}: replaced {old_n} lines with {len(block.splitlines())}")


if __name__ == "__main__":
    al = validate()
    splice(PY, "DISTRICT_ALIASES = {", "}", render_python(al))
    splice(KT, "    private val DISTRICT_ALIASES", "    )", render_kotlin(al))
    print("  OK")
