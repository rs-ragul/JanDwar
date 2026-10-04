# -*- coding: utf-8 -*-
"""
Generate TEST_SCRIPTS.md from the *verified* engine output, so the expected
results in the document are whatever the engine actually produced, not
something typed by hand.
"""
from __future__ import annotations

import pathlib
import sys

ROOT = pathlib.Path("/home/user/JanDwar")
sys.path.insert(0, str(ROOT / "server"))

from app.core.engine import Engine  # noqa: E402
from app.core.recommend import match_roles  # noqa: E402

sys.path.insert(0, "/home/user/work")
from verify_scripts import SCRIPTS, SLOTS  # noqa: E402

ASK = {
    "EDUCATION": "How far did you study?",
    "FAMILY_OCCUPATION": "What work does your family do?",
    "CURRENT_LIVELIHOOD": "What are you doing right now?",
    "INTERESTS": "What kind of work interests you?",
    "PREFERENCE": "Own business, or a job?",
    "MOBILITY": "How far can you travel for training?",
    "CONSTRAINTS": "Any health problem? (say no)",
    "STATE": "Which state do you live in?",
    "DISTRICT": "Which district?",
}

MIX = {"ta": "Thanglish", "te": "Tenglish", "kn": "Kanglish",
       "ml": "Manglish", "hi": "Hinglish"}

LANGNAME = {"ta": "Tamil தமிழ்", "te": "Telugu తెలుగు", "kn": "Kannada ಕನ್ನಡ",
            "ml": "Malayalam മലയാളം", "hi": "Hindi हिन्दी"}

# Districts whose native-script name the NLU cannot yet match (see the bug
# note at the bottom of the generated file). Filled in at runtime.
NATIVE_DISTRICT_OK: dict[str, bool] = {}


def run(spec: dict, variant: int):
    """variant 0 = native, 1 = romanised, 2 = realistic (native + English district)."""
    eng = Engine()
    s, _ = eng.start(lang=spec["lang"], channel="sim")
    for slot in SLOTS:
        if s.done:
            break
        idx = SLOTS.index(s.current)
        v = variant
        if variant == 2:
            v = 1 if SLOTS[idx] == "DISTRICT" else 0
        eng.answer(s, spec["answers"][idx][v])
    prof = Engine.profile(s)
    recs = match_roles(prof, spec["lang"], limit=3)
    return prof, recs


def main() -> None:
    out: list[str] = []
    w = out.append

    w("# Test scripts — talking to the NLU in five languages\n")
    w("One script per state, each in that state's language. Every line below "
      "was run through the actual engine before being written down, so the "
      "**Expected** block is what the app should give you, not a guess.\n")
    w("Use the romanised column if your phone's keyboard or your speech is "
      "code-mixed — that is the realistic case and it is tested too.\n")
    w("**How to run one:** Settings → language → start a new interview → "
      "answer in order. Nine questions, then the results screen.\n")

    w("| # | State | Language | District | Tests |")
    w("|---|-------|----------|----------|-------|")
    hints = {
        "ta": "traditional farming family → livestock",
        "te": "low education + wage preference, village-only mobility",
        "kn": "weaving family → textile, willing to travel statewide",
        "ml": "Class 12 + food processing, own business",
        "hi": "weaving family, village-only, lowest education",
    }
    for i, (name, spec) in enumerate(SCRIPTS.items(), 1):
        w(f"| {i} | {spec['state']} | {LANGNAME[spec['lang']]} | "
          f"{spec['district']} | {hints[spec['lang']]} |")
    w("")
    w("---\n")

    for i, (name, spec) in enumerate(SCRIPTS.items(), 1):
        lang = spec["lang"]
        prof, recs = run(spec, 2)   # how you will actually test it

        w(f"## {i}. {LANGNAME[lang]} — {spec['state']}, {spec['district']}\n")
        w(f"Set the app language to **{LANGNAME[lang]}** first.\n")
        w(f"| # | The AI asks | Say this ({LANGNAME[lang].split()[0]}) | "
          f"Say this ({MIX[lang]}) |")
        w("|---|---|---|---|")
        for n, slot in enumerate(SLOTS, 1):
            nat, rom = spec["answers"][n - 1]
            flag = ""
            if slot == "DISTRICT" and lang != "ta":
                flag = " ⚠"
            w(f"| {n} | {ASK[slot]} | {nat}{flag} | {rom} |")
        w("")

        w("**Expected profile**\n")
        w("```")
        w(f"education   {prof['education']}")
        w(f"family      {prof['familyOccupation']}")
        w(f"current     {prof['currentLivelihood']}")
        w(f"interests   {', '.join(prof['interests']) or '—'}")
        w(f"preference  {prof['preference']}")
        w(f"mobility    {prof['mobility']}")
        w(f"constraints {prof['physicalConstraints']}")
        w(f"state       {prof['state']}")
        w(f"district    {prof['district']}")
        w("```\n")

        w("**Expected top 3**\n")
        w("| % | Role | NSQF | Centre |")
        w("|---|------|------|--------|")
        for r in recs[:3]:
            centre = (r.get("centre") or {}).get("name") or "—"
            w(f"| {r['confidence']}% | {r['role']['job_role']} | "
              f"{r['role']['nsqf_level']} | {centre} |")
        w("")

        if lang != "ta":
            w(f"> ⚠ **Question 9 in {LANGNAME[lang].split()[0]} script will "
              f"fail.** Say the district in English/romanised "
              f"(`{spec['answers'][8][1]}`) — see the bug note below. "
              f"Everything else works in native script.\n")
        w("---\n")

    w("## Known bug this exposes\n")
    w("Native-script **district** names only resolve for Tamil Nadu.\n")
    w("`DISTRICT_ALIASES` in `ai/OnDeviceNlu.kt` (and its Python mirror) "
      "carries Tamil/Hindi spellings for TN's 38 districts and nothing for "
      "the other **149** — every district in Andhra Pradesh, Karnataka, "
      "Kerala and Uttar Pradesh. It was written when the app was TN-only and "
      "was never extended after the five-state research drop.\n")
    w("Verified:\n")
    w("```")
    w("'சேலம்'      -> Salem      OK")
    w("'అనంతపురం'   -> None       FAIL")
    w("'ಬೆಳಗಾವಿ'     -> None       FAIL")
    w("'ആലപ്പുഴ'     -> None       FAIL")
    w("'वाराणसी'     -> None       FAIL")
    w("```\n")
    w("Effect on a real user: the last question of the interview fails, the "
      "engine retries twice, then skips — so the profile has no district and "
      "**no centre is shown**. The recommendations still appear.\n")
    w("Workaround for testing today: say the district name in English. Most "
      "people say place names in English anyway, and Android's recogniser "
      "often returns them romanised, so this is not as severe in the field "
      "as it looks — but it is a real gap.\n")
    w("Fix: add native-script aliases for the remaining 149 districts in "
      "both `OnDeviceNlu.kt` and `server/app/core/nlu.py`, then rebuild. "
      "Roughly one build cycle.\n")

    w("## What to watch for on the phone\n")
    w("- The mic opening while the assistant is still speaking.\n")
    w("- Whether the recogniser works with the network off, per language "
      "(it needs that language's offline pack installed on the device).\n")
    w("- Whether the acknowledgement before each question sounds natural or "
      "repetitive across nine turns.\n")
    w("- On the results screen: tap into a role and check the factors, the "
      "skill-gap line, the funding rule and the centre.\n")
    w("Report the symptom, not a diagnosis — "
      "\u201cit stopped listening after question 2 in Telugu\u201d is fixable "
      "in one pass.\n")

    path = ROOT / "TEST_SCRIPTS.md"
    path.write_text("\n".join(out), encoding="utf-8")
    print(f"wrote {path} ({path.stat().st_size} B)")


if __name__ == "__main__":
    main()
