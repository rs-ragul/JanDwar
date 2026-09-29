# -*- coding: utf-8 -*-
"""
Drive the real engine with the five demo scripts and report, slot by slot,
whether each scripted answer actually filled the slot it was meant to fill.

Run from the repo root:  python3 /home/user/work/verify_scripts.py
"""
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path("/home/user/Thozhil-Thunai")
sys.path.insert(0, str(ROOT / "server"))

from app.core.engine import Engine, question  # noqa: E402
from app.core.recommend import match_roles    # noqa: E402

SLOTS = ["EDUCATION", "FAMILY_OCCUPATION", "CURRENT_LIVELIHOOD", "INTERESTS",
         "PREFERENCE", "MOBILITY", "CONSTRAINTS", "STATE", "DISTRICT"]

# lang, state, district, then 9 (native, romanised) answer pairs in slot order
SCRIPTS = {
    "TA / Tamil Nadu / Salem": dict(
        lang="ta", state="Tamil Nadu", district="Salem",
        answers=[
            ("பத்தாம் வகுப்பு முடித்தேன்", "10th mudichen"),
            ("எங்க குடும்பம் விவசாயம் பண்றாங்க", "enga kudumbam vivasayam pannuranga"),
            ("இப்போ கூலி வேலை பாக்குறேன்", "ippo coolie velai paakuren"),
            ("ஆடு வளர்ப்பு பிடிக்கும்", "aadu valarpu pidikkum"),
            ("சொந்தமா தொழில் தொடங்கணும்", "sonthama thozhil thodanganum"),
            ("மாவட்டத்துக்குள்ள போகலாம்", "district kulla polam"),
            ("இல்லை", "illa"),
            ("தமிழ்நாடு", "Tamil Nadu"),
            ("சேலம்", "Salem"),
        ]),
    "TE / Andhra Pradesh / Anantapur": dict(
        lang="te", state="Andhra Pradesh", district="Anantapur",
        answers=[
            ("ఎనిమిదో తరగతి వరకు చదివాను", "8th varaku chadivanu"),
            ("మా కుటుంబం వ్యవసాయం చేస్తుంది", "maa kutumbam vyavasayam chestundi"),
            ("పని వెతుకుతున్నాను", "pani vethukutunnanu"),
            ("పాడి పరిశ్రమ", "dairy nerchukovali"),
            ("ఉద్యోగం కావాలి", "udyogam kavali"),
            ("మా ఊరు మాత్రమే", "maa ooru matrame"),
            ("లేదు", "ledu"),
            ("ఆంధ్రప్రదేశ్", "Andhra Pradesh"),
            ("అనంతపురం", "Anantapur"),
        ]),
    "KN / Karnataka / Belagavi": dict(
        lang="kn", state="Karnataka", district="Belagavi",
        answers=[
            ("ಹತ್ತನೇ ತರಗತಿ ಮುಗಿಸಿದೆ", "10th mugiside"),
            ("ನಮ್ಮ ಮನೆಯಲ್ಲಿ ನೇಯ್ಗೆ ಮಾಡುತ್ತಾರೆ", "namma maneyalli neyge maadtare"),
            ("ಕೆಲಸ ಹುಡುಕುತ್ತಿದ್ದೇನೆ", "kelasa hudukuttiddene"),
            ("ಹೊಲಿಗೆ ಕಲಿಯಬೇಕು", "holige kaliyabeku"),
            ("ಸ್ವಂತ ಉದ್ಯಮ", "swanta udyama"),
            ("ರಾಜ್ಯದಲ್ಲಿ ಎಲ್ಲಿಯಾದರೂ", "rajyadalli elliyadaru"),
            ("ಇಲ್ಲ", "illa"),
            ("ಕರ್ನಾಟಕ", "Karnataka"),
            ("ಬೆಳಗಾವಿ", "Belagavi"),
        ]),
    "ML / Kerala / Alappuzha": dict(
        lang="ml", state="Kerala", district="Alappuzha",
        answers=[
            ("പന്ത്രണ്ടാം ക്ലാസ് കഴിഞ്ഞു", "12th kazhinju"),
            ("വീട്ടിൽ കൃഷി ചെയ്യുന്നു", "veettil krishi cheyyunnu"),
            ("കൂലിപ്പണി ചെയ്യുന്നു", "koolippani cheyyunnu"),
            ("ഭക്ഷ്യ സംസ്കരണം പഠിക്കണം", "food processing padikkanam"),
            ("സ്വന്തം ബിസിനസ്സ്", "swantham business"),
            ("എന്റെ ജില്ലയിൽ മാത്രം", "ente jillayil mathram"),
            ("ഇല്ല", "illa"),
            ("കേരളം", "Kerala"),
            ("ആലപ്പുഴ", "Alappuzha"),
        ]),
    "HI / Uttar Pradesh / Varanasi": dict(
        lang="hi", state="Uttar Pradesh", district="Varanasi",
        answers=[
            ("आठवीं तक पढ़ा हूँ", "8th tak padha hoon"),
            ("हमारे घर में बुनाई का काम होता है", "hamare ghar me bunai ka kaam hota hai"),
            ("दिहाड़ी मजदूरी करता हूँ", "dihadi mazdoori karta hoon"),
            ("सिलाई सीखना है", "silai seekhna hai"),
            ("अपना काम करना है", "apna kaam karna hai"),
            ("सिर्फ गाँव में", "sirf gaon me"),
            ("नहीं", "nahi"),
            ("उत्तर प्रदेश", "Uttar Pradesh"),
            ("वाराणसी", "Varanasi"),
        ]),
}


def run(name: str, spec: dict, variant: int) -> bool:
    """variant 0 = native script, 1 = romanised / English-mixed."""
    eng = Engine()
    s, _greet = eng.start(lang=spec["lang"], channel="sim")
    label = ["native", "romanised"][variant]
    problems: list[str] = []

    for slot in SLOTS:
        if s.done:
            break
        asked = s.current
        idx = SLOTS.index(asked)
        eng.answer(s, spec["answers"][idx][variant])
        frag = s.frag
        if not frag.is_filled(asked):
            problems.append(f"{asked} NOT FILLED by "
                            f"{spec['answers'][idx][variant]!r}")

    d = s.frag.to_dict()
    if d.get("state") != spec["state"]:
        problems.append(f"state={d.get('state')!r} expected {spec['state']!r}")
    if d.get("district") != spec["district"]:
        problems.append(f"district={d.get('district')!r} expected {spec['district']!r}")

    recs = match_roles(Engine.profile(s), spec["lang"], limit=3)
    print(f"\n{'─' * 78}\n{name}  [{label}]")
    print(f"  profile : {json.dumps(d, ensure_ascii=False)}")
    for r in recs[:3]:
        c = (r.get("centre") or {}).get("name", "—")
        print(f"  -> {r['confidence']:>3}%  {r['role']['job_role'][:48]:<48} "
              f"NSQF {r['role']['nsqf_level']}  | {c[:40]}")
    if problems:
        for p in problems:
            print(f"  !! {p}")
        return False
    print("  OK  all 9 slots filled, state+district correct")
    return True


if __name__ == "__main__":
    allok = True
    for n, sp in SCRIPTS.items():
        for v in (0, 1):
            allok &= run(n, sp, v)
    print("\n" + "=" * 78)
    print("ALL SCRIPTS PASS" if allok else "SOME SCRIPTS FAILED — fix before shipping")
    sys.exit(0 if allok else 1)
