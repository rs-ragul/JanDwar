# -*- coding: utf-8 -*-
"""
Apply the four fixes the audit turned up. Idempotent-ish: every edit asserts
its anchor first, so a second run fails loudly rather than corrupting a file.
"""
from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path("/home/user/Thozhil-Thunai")
KT = ROOT / "app/src/main/java/in/jandwar/app"
done = []


def patch(path: pathlib.Path, old: str, new: str, label: str) -> None:
    s = path.read_text(encoding="utf-8")
    assert old in s, f"ANCHOR MISSING [{label}] in {path.name}"
    assert s.count(old) == 1, f"ANCHOR AMBIGUOUS x{s.count(old)} [{label}] in {path.name}"
    path.write_text(s.replace(old, new), encoding="utf-8")
    done.append(label)


# ── 1. district_economy key does not match districts.json ───────────────────
p = ROOT / "app/src/main/assets/district_economy.json"
econ = json.loads(p.read_text(encoding="utf-8"))
old_key = "Bhadohi (Sant Ravidas Nagar)"
assert old_key in econ, "economy key already renamed?"
assert "Bhadohi" not in econ, "Bhadohi already present - would overwrite"
econ["Bhadohi"] = econ.pop(old_key)
if isinstance(econ["Bhadohi"], dict):
    econ["Bhadohi"]["district"] = "Bhadohi"
# keep the file key-sorted the way it was written
p.write_text(json.dumps(dict(sorted(econ.items())), ensure_ascii=False, indent=2) + "\n",
             encoding="utf-8")
done.append("economy key Bhadohi")


# ── 2. multi-token ambiguous state form ("u p") could never match ───────────
patch(ROOT / "server/app/core/nlu.py",
      '''        lo = original.lower()
        tokens = set(re.split(r"[^\\w]+", s + " " + lo)) - {""}''',
      '''        lo = original.lower()
        seq = [t for t in re.split(r"[^\\w]+", s + " " + lo) if t]
        tokens = set(seq)
        # "u p" is spoken as two tokens but stored de-spaced, so make the
        # concatenation of each adjacent pair reachable too.
        tokens |= {seq[i] + seq[i + 1] for i in range(len(seq) - 1)}''',
      "py detect_state adjacent-token join")

patch(KT / "ai/OnDeviceNlu.kt",
      '''        val tokens = (s + " " + lowerOriginal)
            .split(Regex("[^\\\\p{L}\\\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .toSet()''',
      '''        val seq = (s + " " + lowerOriginal)
            .split(Regex("[^\\\\p{L}\\\\p{N}]+"))
            .filter { it.isNotEmpty() }
        // "u p" is spoken as two tokens but stored de-spaced, so make the
        // concatenation of each adjacent pair reachable too.
        val tokens = seq.toMutableSet().apply {
            for (i in 0 until seq.size - 1) add(seq[i] + seq[i + 1])
        }''',
      "kt detectState adjacent-token join")


# ── 3. duration label invented "300 hrs" for rows with no hours ─────────────
patch(ROOT / "server/app/core/recommend.py",
      '''def duration_label(role) -> str:
    h = hours_int(role)
    months = max(1, round(h / 130))
    return "%d hrs · ~%d %s" % (h, months, "month" if months == 1 else "months")''',
      '''def duration_label(role) -> str:
    """Empty when the catalogue does not state the hours -- hours_int()
    falls back to 300 so ranking stays stable, but showing that number to a
    beneficiary would be presenting a default as a fact."""
    if not any(c.isdigit() for c in str(role.get("notional_hours", ""))):
        return ""
    h = hours_int(role)
    months = max(1, round(h / 130))
    return "%d hrs · ~%d %s" % (h, months, "month" if months == 1 else "months")''',
      "py duration_label honest")

patch(KT / "data/model/Models.kt",
      '''    fun durationLabel(): String {
        val h = hoursInt()
        val months = Math.round(h / 130f).coerceAtLeast(1)
        return "$h hrs · ~$months ${if (months == 1) "month" else "months"}"
    }''',
      '''    /**
     * Empty when the catalogue does not state the hours. hoursInt() falls
     * back to 300 so ranking stays stable, but printing that number would be
     * showing a beneficiary a default dressed up as a fact.
     */
    fun durationLabel(): String {
        if (!hasHours()) return ""
        val h = hoursInt()
        val months = Math.round(h / 130f).coerceAtLeast(1)
        return "$h hrs · ~$months ${if (months == 1) "month" else "months"}"
    }''',
      "kt durationLabel honest")

# call sites must tolerate the empty label
patch(KT / "ui/screens/CoursesScreen.kt",
      'Badge(text = role.durationLabel(), color = accent)',
      'Badge(text = role.durationLabel().ifBlank { "—" }, color = accent)',
      "kt CoursesScreen duration guard")
patch(KT / "ui/screens/ResultsScreen.kt",
      'Badge(text = role.durationLabel(), color = BrandTeal)',
      'Badge(text = role.durationLabel().ifBlank { "—" }, color = BrandTeal)',
      "kt ResultsScreen duration guard")
patch(KT / "ui/screens/DetailScreen.kt",
      'KeyValueRow(viewModel.tr("duration"), role.durationLabel())',
      'KeyValueRow(viewModel.tr("duration"), role.durationLabel().ifBlank { "—" })',
      "kt DetailScreen duration guard")
patch(KT / "ui/viewmodel/VoiceViewModel.kt",
      'append(m.role.durationLabel()).append(")")',
      'append(m.role.durationLabel().ifBlank { "-" }).append(")")',
      "kt VoiceViewModel duration guard")

print("APPLIED:")
for d in done:
    print("  -", d)
print(f"{len(done)} edits OK")
