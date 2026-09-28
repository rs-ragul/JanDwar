#!/usr/bin/env python3
"""
Regression check for OnDeviceNlu's self-employment vs wage-employment slot.

Shipped v2.1 classified "I want to work for myself" as wage employment,
because the wage list contained the fragment "work for". This script parses
the real word lists straight out of OnDeviceNlu.kt and replays the phrases
that matter, so the bug cannot come back unnoticed.

    python3 tools/check_nlu_preference.py
"""
import re
import sys
import pathlib

KT = pathlib.Path(__file__).resolve().parents[1] / \
    "app/src/main/java/in/jandwar/app/ai/OnDeviceNlu.kt"


def grab(src, name):
    m = re.search(r'private val %s = listOf\((.*?)\n    \)' % name, src, re.S)
    if not m:
        sys.exit("could not find %s in OnDeviceNlu.kt" % name)
    return re.findall(r'"([^"]*)"', m.group(1))


def normalise(t):
    t = t.lower()
    t = re.sub(r'[!-/:-@\[-`{-~]', ' ', t)
    return re.sub(r'\s+', ' ', t).strip()


def main():
    src = KT.read_text(encoding="utf-8")
    SS = grab(src, "SELF_STRONG")
    WS = grab(src, "WAGE_STRONG")
    SW = grab(src, "SELF_WEAK")
    WW = grab(src, "WAGE_WEAK")

    def pref(text):
        s = normalise(text)
        if any(w in s for w in SS):
            return "pref_self"
        if any(w in s for w in WS):
            return "pref_wage"
        if any(w in s for w in SW):
            return "pref_self"
        if any(w in s for w in WW):
            return "pref_wage"
        return None

    cases = [
        ("I want to work for myself", "pref_self"),
        ("I would like to work for myself.", "pref_self"),
        ("I want my own business", "pref_self"),
        ("start a shop of my own", "pref_self"),
        ("self employment", "pref_self"),
        ("I want to be my own boss", "pref_self"),
        ("I don't want a job, I want to work for myself", "pref_self"),
        ("I want a job with a company", "pref_wage"),
        ("I want a salary every month", "pref_wage"),
        ("I want to work for someone", "pref_wage"),
        ("looking for placement", "pref_wage"),
        ("I want a job", "pref_wage"),
        ("factory job is fine", "pref_wage"),
        ("\u0b9a\u0bca\u0ba8\u0bcd\u0ba4\u0bae\u0bbe \u0ba4\u0bca\u0bb4\u0bbf\u0bb2\u0bcd \u0b9a\u0bc6\u0baf\u0bcd\u0baf\u0ba3\u0bc1\u0bae\u0bcd", "pref_self"),
        ("\u0b8e\u0ba9\u0b95\u0bcd\u0b95\u0bc1 \u0bb5\u0bc7\u0bb2\u0bc8\u0b95\u0bcd\u0b95\u0bc1 \u0baa\u0bcb\u0b95\u0ba3\u0bc1\u0bae\u0bcd", "pref_wage"),
        ("\u092e\u0941\u091d\u0947 \u0905\u092a\u0928\u093e \u0915\u093e\u092e \u0915\u0930\u0928\u093e \u0939\u0948", "pref_self"),
        ("\u092e\u0941\u091d\u0947 \u0928\u094c\u0915\u0930\u0940 \u091a\u093e\u0939\u093f\u090f", "pref_wage"),
        ("nothing in particular", None),
    ]

    failed = 0
    for text, want in cases:
        got = pref(text)
        if got != want:
            failed += 1
            print("FAIL  %-50s -> %-10s expected %s" % (text, got, want))

    if failed:
        print("\n%d/%d failed" % (failed, len(cases)))
        return 1
    print("all %d preference cases pass" % len(cases))
    return 0


if __name__ == "__main__":
    sys.exit(main())
