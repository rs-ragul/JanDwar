#!/usr/bin/env python3
"""
Replays realistic utterances through the same lexicon + fuzzy matcher the app
uses, so slang coverage can be checked without a device.

    python3 tools/check_nlu.py
"""
import json
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
LEX = json.loads((ROOT / "app/src/main/assets/lexicon.json").read_text(encoding="utf-8"))["forms"]


def normalise(t):
    t = t.lower()
    t = re.sub(r"[!-/:-@\[-`{-~]", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def tolerance(n):
    return 0 if n <= 3 else (1 if n <= 6 else 2)


def within(a, b, mx):
    if a == b:
        return True
    n, m = len(a), len(b)
    if abs(n - m) > mx:
        return False
    prev = list(range(m + 1))
    for i in range(1, n + 1):
        cur = [i] + [0] * m
        for j in range(1, m + 1):
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1,
                         prev[j - 1] + (0 if a[i - 1] == b[j - 1] else 1))
        if min(cur) > mx:
            return False
        prev = cur
    return prev[m] <= mx


def has(text, key):
    forms = LEX.get(key, [])
    for f in forms:
        if f in text:
            return True
    toks = text.split()
    for f in forms:
        if " " in f:
            continue
        tol = tolerance(len(f))
        if tol == 0 or any(c.isdigit() for c in f):
            continue
        for t in toks:
            if any(c.isdigit() for c in t):
                continue
            if abs(len(t) - len(f)) <= tol and within(t, f, tol):
                return True
    return False


EDU_ORDER = [("edu.none", "class5"), ("edu.iti_diploma", "iti_diploma"),
             ("edu.graduate", "graduate"), ("edu.class12", "class12"),
             ("edu.class10", "class10"), ("edu.class8", "class8"),
             ("edu.class5", "class5")]


def edu(t):
    t = normalise(t)
    for k, v in EDU_ORDER:
        if has(t, k):
            return v
    return None


def pref(t):
    t = normalise(t)
    for k, v in [("pref.self_strong", "pref_self"), ("pref.wage_strong", "pref_wage"),
                 ("pref.self_weak", "pref_self"), ("pref.wage_weak", "pref_wage")]:
        if has(t, k):
            return v
    return None


def mob(t):
    t = normalise(t)
    for k, v in [("mob.state", "state"), ("mob.district", "district"),
                 ("mob.local", "local"), ("mob.anywhere", "state")]:
        if has(t, k):
            return v
    return None


CASES = [
    # (function, utterance, expected)
    (edu, "I studied up to 10th", "class10"),
    (edu, "sslc only", "class10"),
    (edu, "I have a diploma", "iti_diploma"),
    (edu, "ITI fitter", "iti_diploma"),
    (edu, "plus two finished", "class12"),
    (edu, "I am a graduate", "graduate"),
    (edu, "never went to school", "class5"),
    # Tamil, including the spelling Google's recogniser actually returned
    (edu, "\u0ba8\u0bbe\u0ba9\u0bcd \u0b9f\u0bbf\u0baa\u0bcd\u0bb3\u0bae\u0bcb \u0baa\u0b9f\u0bbf\u0b9a\u0bcd\u0b9a\u0bbf\u0bb0\u0bc1\u0b95\u0bcd\u0b95\u0bc7\u0ba9\u0bcd", "iti_diploma"),
    (edu, "\u0b9f\u0bbf\u0baa\u0bcd\u0bb3\u0bae\u0bcb", "iti_diploma"),
    (edu, "\u0baa\u0ba4\u0bcd\u0ba4\u0bbe\u0bae\u0bcd \u0bb5\u0b95\u0bc1\u0baa\u0bcd\u0baa\u0bc1", "class10"),
    (edu, "\u0baa\u0b9f\u0bbf\u0b95\u0bcd\u0b95\u0bb2", "class5"),
    (edu, "\u092e\u0948\u0902\u0928\u0947 \u0926\u0938\u0935\u0940\u0902 \u0915\u0940 \u0939\u0948", "class10"),
    (edu, "\u0921\u093f\u092a\u094d\u0932\u094b\u092e\u093e \u0915\u093f\u092f\u093e", "iti_diploma"),

    (pref, "I want to work for myself", "pref_self"),
    (pref, "I don't want a job, I want to work for myself", "pref_self"),
    (pref, "I want a salary every month", "pref_wage"),
    (pref, "I want to work for a company", "pref_wage"),
    (pref, "\u0b9a\u0bca\u0ba8\u0bcd\u0ba4\u0bae\u0bbe \u0ba4\u0bca\u0bb4\u0bbf\u0bb2\u0bcd", "pref_self"),
    (pref, "\u0b95\u0bae\u0bcd\u0baa\u0bc6\u0ba9\u0bbf \u0bb5\u0bc7\u0bb2\u0bc8", "pref_wage"),
    (pref, "\u0905\u092a\u0928\u093e \u0915\u093e\u092e \u0915\u0930\u0928\u093e \u0939\u0948", "pref_self"),

    # The two answers that both contain "anywhere"
    (mob, "anywhere in Tamilnadu", "state"),
    (mob, "anywhere in the state", "state"),
    (mob, "anywhere in my district", "district"),
    (mob, "only my village", "local"),
    (mob, "I cannot travel", "local"),
    (mob, "anywhere", "state"),
    # Tamil: "whole state" previously matched nothing and forced a re-prompt
    (mob, "\u0bae\u0bbe\u0ba8\u0bbf\u0bb2\u0bae\u0bcd \u0bae\u0bc1\u0bb4\u0bc1\u0bb5\u0ba4\u0bc1\u0bae\u0bcd", "state"),
    (mob, "\u0bae\u0bbe\u0bb5\u0b9f\u0bcd\u0b9f\u0bae\u0bcd \u0bae\u0bc1\u0bb4\u0bc1\u0bb5\u0ba4\u0bc1\u0bae\u0bcd", "district"),
    (mob, "\u0b8e\u0b99\u0bcd\u0b95 \u0b8a\u0bb0\u0bcd", "local"),
    (mob, "\u092a\u0942\u0930\u0947 \u0930\u093e\u091c\u094d\u092f", "state"),
    (mob, "\u0905\u092a\u0928\u0947 \u091c\u093f\u0932\u0947 \u092e\u0947\u0902", "district"),
]


def check_occupation_labels():
    """
    Every occupation label the on-device NLU can write onto the profile must have
    a display translation in every language, otherwise an English chip appears
    next to Tamil ones. The profile itself keeps the canonical English string --
    role matching and the cloud prompt both key off it -- so this guards only the
    display layer.
    """
    src = (ROOT / "app/src/main/java/in/jandwar/app/ai/OnDeviceNlu.kt").read_text(encoding="utf-8")

    block = src[src.index("OCCUPATION_ORDER"):]
    block = block[:block.index(")\n")]
    labels = re.findall(r'to\s+"([^"]+)"', block)

    fn = src[src.index("private fun detectStudentStatus"):]
    fn = fn[:fn.index("\n    }")]
    labels += re.findall(r'->\s*"([^"]+)"', fn)

    i18n = json.loads((ROOT / "app/src/main/assets/i18n.json").read_text(encoding="utf-8"))
    occ = i18n.get("occupation", {})
    if not occ:
        print("FAIL  i18n.json has no \"occupation\" section")
        return 1

    bad = 0
    for lab in labels:
        for lang in occ:
            if not occ[lang].get(lab.lower(), "").strip():
                print("FAIL  occupation label %-24s has no %s translation" % (lab, lang))
                bad += 1
    if not bad:
        print("all %d occupation labels translated into %d languages"
              % (len(labels), len(occ)))
    return 1 if bad else 0


def check_every_form_resolves():
    """
    Every surface form resolves to its own detector.

    The point of this is regression pressure on *adding vocabulary*. Slang is
    the one part of this system that will keep growing, and each new word is a
    chance to collide with a category that already exists. The failures this
    caught when the lexicon went from 1,480 to 3,158 forms were not cosmetic:

      "+2"                     -> None    (Class 12 in Tamil Nadu, unmatched)
      "veliyoor poga mudiyathu"-> state   (means *cannot* leave town)
      "baahar nahi ja sakta"   -> state   (means *cannot* go outside)

    The mobility ones inverted the answer: a person who cannot leave their
    village was recorded as willing to travel anywhere in the state, and the
    recommender then sent them to a centre in another district. A category
    that cannot recognise its own vocabulary is worse than an empty one,
    because it fails silently.
    """
    import os
    import sys as _sys
    server = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "server")
    server = os.path.normpath(server)
    if server not in _sys.path:
        _sys.path.insert(0, server)
    from app.core import data, nlu as N
    e = N.Nlu()
    raw = data.lexicon_forms()

    EDU = dict(N.EDU_ORDER)
    OCC = dict(N.OCCUPATION_ORDER)
    PREF = {"pref.self_strong": "pref_self", "pref.self_weak": "pref_self",
            "pref.wage_strong": "pref_wage", "pref.wage_weak": "pref_wage"}
    MOB = {"mob.state": "state", "mob.anywhere": "state",
           "mob.district": "district", "mob.local": "local"}

    fails, checked = [], 0
    for cat, flist in raw.items():
        for f in flist:
            s = N.normalise(f)
            checked += 1
            if cat in EDU:
                got, want = e.detect_education(s), EDU[cat]
            elif cat in PREF:
                got, want = e.detect_preference(s), PREF[cat]
            elif cat in MOB:
                got, want = e.detect_mobility(s), MOB[cat]
            elif cat.startswith("interest."):
                want = cat.split(".", 1)[1]
                got = want if want in e.detect_interests(s) else e.detect_interests(s)
            elif cat.startswith("occ."):
                want = OCC.get(cat)
                if not want:
                    checked -= 1
                    continue
                got = e.detect_occupation(s)
            else:
                checked -= 1          # markers are context-dependent
                continue
            if got != want:
                fails.append((cat, f, want, got))

    if fails:
        for cat, f, want, got in fails[:25]:
            print("  FAIL %-20s %-28s want=%-14s got=%s" % (cat, f, want, got))
        raise SystemExit("%d of %d surface forms do not resolve to their own "
                         "detector" % (len(fails), checked))
    print("all %d surface forms resolve to their own detector" % checked)


def main():
    bad = 0
    for fn, text, want in CASES:
        got = fn(text)
        if got != want:
            bad += 1
            print("FAIL  %-14s %-44s -> %-12s expected %s" % (fn.__name__, text, got, want))
    total = len(CASES)
    if bad:
        print("\n%d/%d failed" % (bad, total))
        return 1
    print("all %d NLU cases pass (%d surface forms in lexicon)"
          % (total, sum(len(v) for v in LEX.values())))
    rc = check_occupation_labels()
    if rc:
        return rc
    check_every_form_resolves()
    return 0


if __name__ == "__main__":
    sys.exit(main())
