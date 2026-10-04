# -*- coding: utf-8 -*-
"""
Whole-app functional audit.

Runs the real engine, not a mock. Every check either prints PASS with the
number it verified, or FAIL with the specific thing that is broken. Exit code
is non-zero if anything failed, so it can gate a release.

    python3 tools/audit.py
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "server"))

from app.core import data                     # noqa: E402
from app.core.engine import Engine, question  # noqa: E402
from app.core.nlu import (Nlu, normalise, SLOTS, STATE_FORMS,  # noqa: E402
                          DISTRICT_ALIASES, NO_CONSTRAINT)
from app.core.recommend import match_roles    # noqa: E402

A = ROOT / "app/src/main/assets"
KT = ROOT / "app/src/main/java/in/jandwar/app"
LANGS = ["en", "ta", "hi", "te", "kn", "ml"]

FAILS: list[str] = []
N = 0


def check(name: str, ok: bool, detail: str = "") -> None:
    global N
    N += 1
    if ok:
        print(f"  PASS  {name:<44} {detail}")
    else:
        print(f"  FAIL  {name:<44} {detail}")
        FAILS.append(f"{name}: {detail}")


def section(t: str) -> None:
    print(f"\n{t}\n{'-' * 74}")


# ── 1. assets ───────────────────────────────────────────────────────────────
section("1. Assets")

roles = data.job_roles()
check("job_roles load + filter", len(roles) == 476, f"{len(roles)} valid roles")
sectors = {r["sector"] for r in roles}
check("sectors", len(sectors) == 13, f"{len(sectors)} sectors")
noqp = [r for r in roles if not r.get("qp_code")]
check("every role has a qp_code", not noqp, f"{len(noqp)} without")
blank_lv = [r for r in roles if not str(r.get("nsqf_level") or "").strip()]
blank_hr = [r for r in roles if not any(c.isdigit() for c in str(r.get("notional_hours") or ""))]
print(f"  note  {len(blank_lv)} roles have no NSQF level, "
      f"{len(blank_hr)} no notional hours -- must render as a dash, not a default")

centres = data.centres()
check("centres load", len(centres) == 660, f"{len(centres)} centres")
nosrc = [c for c in centres if not (c.get("source") or "").strip()]
check("every centre has a source URL", not nosrc, f"{len(nosrc)} missing")
badconf = {c.get("confidence") for c in centres} - {"CONFIRMED", "LIKELY", "UNVERIFIED"}
check("confidence values valid", not badconf, f"unexpected {badconf or 'none'}")

dj = json.load(open(A / "districts.json", encoding="utf-8"))
check("districts", len(dj["all"]) >= 186, f"{len(dj['all'])} districts / "
      f"{len(dj['by_state'])} states")

# centre districts must exist in the district list
known = set(dj["all"])
orphan = sorted({c["district"] for c in centres if c["district"] not in known})
check("centre districts all known", not orphan, f"{len(orphan)} orphans {orphan[:4]}")

econ = json.load(open(A / "district_economy.json", encoding="utf-8"))
check("district_economy keys known", set(econ) <= known,
      f"{len(econ)} entries, {len(set(econ) - known)} unknown")

gia = json.load(open(A / "gia_funding_rules.json", encoding="utf-8"))
check("gia rules per state", set(gia) == set(dj["by_state"]), f"{len(gia)} states")

i18n = json.load(open(A / "i18n.json", encoding="utf-8"))
sizes = {k: len(v) for k, v in i18n["strings"].items()}
check("i18n key parity", len(set(sizes.values())) == 1,
      f"{list(sizes.values())[0]} keys x {len(sizes)} langs")
empty = [(l, k) for l, v in i18n["strings"].items() for k, s in v.items()
         if not str(s).strip()]
check("no empty i18n strings", not empty, f"{len(empty)} empty")

lex = json.load(open(A / "lexicon.json", encoding="utf-8"))["forms"]
check("lexicon", sum(len(v) for v in lex.values()) == 3596,
      f"{sum(len(v) for v in lex.values())} forms / {len(lex)} categories")

# ── 2. district + state resolution ──────────────────────────────────────────
section("2. Geography resolution")

nlu = Nlu()
check(f"all {len(known)} districts have aliases", set(DISTRICT_ALIASES) == known,
      f"{len(DISTRICT_ALIASES)} keys")
noscript = [d for d, fs in DISTRICT_ALIASES.items()
            if not any(any(ord(c) > 127 for c in f) for f in fs)]
check("every district has a native form", not noscript, f"{len(noscript)} without")

fails = []
tested = 0
for st, v in dj["by_state"].items():
    for d in v["all"]:
        for form in [d] + DISTRICT_ALIASES[d]:
            tested += 1
            if nlu.detect_district(normalise(form), form, st) != d:
                fails.append((st, d, form))
check("district forms resolve (state-scoped)", not fails,
      f"{tested} forms, {len(fails)} failed {fails[:3]}")

sfails = []
for st, forms in STATE_FORMS.items():
    for f in forms:
        if nlu.detect_state(normalise(f), f, allow_ambiguous=True) != st:
            sfails.append((st, f))
check("state forms resolve", not sfails,
      f"{sum(len(v) for v in STATE_FORMS.values())} forms, {len(sfails)} failed {sfails[:3]}")

backfill = [d for d in known if nlu.state_of_district(d) is None]
check("state back-fills from district", not backfill, f"{len(backfill)} unmapped")

# ── 3. interview, every language ────────────────────────────────────────────
section("3. Interview flow, all 6 languages")

flow = data.flow()
check("flow slot order", flow["slot_order"] == SLOTS, " -> ".join(SLOTS[:3]) + " ...")
missing_q = [(s, l) for s in SLOTS for l in LANGS if not flow["questions"].get(s, {}).get(l)]
check("every slot has a question in 6 langs", not missing_q,
      f"{len(SLOTS) * len(LANGS)} prompts, {len(missing_q)} missing")

ANSWERS = {
    "en": ["10th pass", "farming family", "daily wage work", "goat rearing",
           "own business", "within my district", "no", "Tamil Nadu", "Salem"],
    "ta": ["பத்தாம் வகுப்பு முடித்தேன்", "எங்க குடும்பம் விவசாயம் பண்றாங்க",
           "இப்போ கூலி வேலை பாக்குறேன்", "ஆடு வளர்ப்பு பிடிக்கும்",
           "சொந்தமா தொழில் தொடங்கணும்", "மாவட்டத்துக்குள்ள போகலாம்",
           "இல்லை", "தமிழ்நாடு", "சேலம்"],
    "te": ["ఎనిమిదో తరగతి వరకు చదివాను", "మా కుటుంబం వ్యవసాయం చేస్తుంది",
           "పని వెతుకుతున్నాను", "పాడి పరిశ్రమ", "ఉద్యోగం కావాలి",
           "మా ఊరు మాత్రమే", "లేదు", "ఆంధ్రప్రదేశ్", "అనంతపురం"],
    "kn": ["ಹತ್ತನೇ ತರಗತಿ ಮುಗಿಸಿದೆ", "ನಮ್ಮ ಮನೆಯಲ್ಲಿ ನೇಯ್ಗೆ ಮಾಡುತ್ತಾರೆ",
           "ಕೆಲಸ ಹುಡುಕುತ್ತಿದ್ದೇನೆ", "ಹೊಲಿಗೆ ಕಲಿಯಬೇಕು", "ಸ್ವಂತ ಉದ್ಯಮ",
           "ರಾಜ್ಯದಲ್ಲಿ ಎಲ್ಲಿಯಾದರೂ", "ಇಲ್ಲ", "ಕರ್ನಾಟಕ", "ಬೆಳಗಾವಿ"],
    "ml": ["പന്ത്രണ്ടാം ക്ലാസ് കഴിഞ്ഞു", "വീട്ടിൽ കൃഷി ചെയ്യുന്നു",
           "കൂലിപ്പണി ചെയ്യുന്നു", "ഭക്ഷ്യ സംസ്കരണം പഠിക്കണം",
           "സ്വന്തം ബിസിനസ്സ്", "എന്റെ ജില്ലയിൽ മാത്രം", "ഇല്ല",
           "കേരളം", "ആലപ്പുഴ"],
    "hi": ["आठवीं तक पढ़ा हूँ", "हमारे घर में बुनाई का काम होता है",
           "दिहाड़ी मजदूरी करता हूँ", "सिलाई सीखना है", "अपना काम करना है",
           "सिर्फ गाँव में", "नहीं", "उत्तर प्रदेश", "वाराणसी"],
}

for lang in LANGS:
    eng = Engine()
    s, greet = eng.start(lang=lang, channel="sim")
    unfilled = []
    while not s.done and s.current:
        idx = SLOTS.index(s.current)
        eng.answer(s, ANSWERS[lang][idx])
        if not s.frag.is_filled(SLOTS[idx]):
            unfilled.append(SLOTS[idx])
    prof = Engine.profile(s)
    recs = match_roles(prof, lang, limit=6)
    ok = not unfilled and bool(greet) and len(recs) > 0 and prof["district"]
    check(f"{lang}: 9 slots + results", ok,
          f"{9 - len(unfilled)}/9 slots, {len(recs)} results, "
          f"{prof['district'] or 'NO DISTRICT'}"
          + (f", unfilled {unfilled}" if unfilled else ""))

# ── 4. recommender breadth ──────────────────────────────────────────────────
section("4. Recommender across all districts")

PROFILES = [
    dict(education="class8", interests=["farming"], preference="pref_self",
         mobility="local"),
    dict(education="class10", interests=["tailor"], preference="pref_wage",
         mobility="district"),
    dict(education="class12", interests=["machine"], preference="pref_wage",
         mobility="state"),
    dict(education="graduate", interests=["food"], preference="pref_self",
         mobility="state"),
]
empties, nofactor, badpct = [], [], []
for st, v in dj["by_state"].items():
    for d in v["all"]:
        for base in PROFILES:
            p = dict(base, state=st, district=d, familyOccupation="",
                     currentLivelihood="", skills=[], physicalConstraints="",
                     localOpportunity="")
            rs = match_roles(p, "en", limit=6)
            if not rs:
                empties.append((st, d))
            for r in rs:
                if not r.get("factors"):
                    nofactor.append(r["role"]["job_role"])
                if not (35 <= r["confidence"] <= 99):
                    badpct.append((r["role"]["job_role"], r["confidence"]))
check("every district returns results", not empties,
      f"{len(dj['all']) * len(PROFILES)} runs, {len(empties)} empty")
check("every result is explainable", not nofactor,
      f"{len(nofactor)} results with no factors")
check("confidence clamped 35-99", not badpct, f"{len(badpct)} out of range")

# centre attachment where the district actually has one
with_centre = set(dj["with_centre"])
nocentre = []
for st, v in dj["by_state"].items():
    for d in v["all"]:
        if d not in with_centre:
            continue
        p = dict(PROFILES[0], state=st, district=d, familyOccupation="",
                 currentLivelihood="", skills=[], physicalConstraints="",
                 localOpportunity="")
        rs = match_roles(p, "en", limit=3)
        if rs and not any(r.get("centre") for r in rs):
            nocentre.append(d)
check("centre attached where one exists", not nocentre,
      f"{len(with_centre)} districts, {len(nocentre)} without a centre on results")

# ── 5. Kotlin / Python parity ───────────────────────────────────────────────
section("5. Kotlin <-> Python parity")

kt_nlu = (KT / "ai/OnDeviceNlu.kt").read_text(encoding="utf-8")
kt_models = (KT / "data/model/Models.kt").read_text(encoding="utf-8")

def kt_block(text: str, name: str) -> str:
    i = text.index(name)
    depth, j = 0, text.index("mapOf(", i) + len("mapOf(")
    for k in range(j, len(text)):
        if text[k] == "(":
            depth += 1
        elif text[k] == ")":
            if depth == 0:
                return text[j:k]
            depth -= 1
    raise AssertionError(f"unterminated {name}")

blk = kt_block(kt_nlu, "DISTRICT_ALIASES")
kt_keys = set(re.findall(r'"([^"]+)" to listOf\(', blk))
check("DISTRICT_ALIASES keys match", kt_keys == set(DISTRICT_ALIASES),
      f"kt {len(kt_keys)} vs py {len(DISTRICT_ALIASES)}")

kt_forms = sum(len(re.findall(r'"[^"]*"', m)) - 1
               for m in re.findall(r'"[^"]+" to listOf\([^)]*\)', blk))
py_forms = sum(len(v) for v in DISTRICT_ALIASES.values())
check("DISTRICT_ALIASES form count matches", kt_forms == py_forms,
      f"kt {kt_forms} vs py {py_forms}")

kt_pf = (KT / "ai/ProfileFragment.kt").read_text(encoding="utf-8")
m = re.search(r'enum class Slot\s*\{(.*?);', kt_pf, re.S)
body = re.sub(r'//[^\n]*', '', m.group(1)) if m else ''
kt_slot_names = re.findall(r'\b([A-Z][A-Z_]{2,})\b', body)
check("slot order matches Python", kt_slot_names == SLOTS,
      f"kt {kt_slot_names[:3]}... ({len(kt_slot_names)}) vs py ({len(SLOTS)})")

kt_sec_blk = kt_block(kt_models, "ANNEXURE1_SECTOR") if "ANNEXURE1_SECTOR" in kt_models else ""
kt_sectors = set(re.findall(r'"([^"]+)"\s+to\s+', kt_sec_blk))
check("ANNEXURE1_SECTOR covers every sector in the data",
      sectors <= kt_sectors, f"{len(kt_sectors)} mapped, "
      f"missing {sorted(sectors - kt_sectors) or 'none'}")

# ── 6. safety / regressions ─────────────────────────────────────────────────
section("6. Safety")

src = list(KT.rglob("*.kt")) + list((ROOT / "server/app").rglob("*.py"))
leaked = [p.name for p in src if "gsk_" in p.read_text(encoding="utf-8", errors="ignore")]
check("no API key in source", not leaked, f"{leaked or 'clean'}")

check("config.json removed (Groq eliminated)", not (A / "config.json").exists(), "file should not exist")

swallow = [p.name for p in (KT / "data").rglob("*.kt")
           if re.search(r"catch\s*\([^)]*\)\s*\{\s*empty(List|Map)\(\)\s*\}", 
                        p.read_text(encoding="utf-8"))]
check("no silent catch -> emptyList in data layer", not swallow, f"{swallow or 'clean'}")

print(f"\n{'=' * 74}")
print(f"{N - len(FAILS)}/{N} checks passed")
if FAILS:
    print("\nFAILURES:")
    for f in FAILS:
        print(f"  - {f}")
sys.exit(1 if FAILS else 0)
