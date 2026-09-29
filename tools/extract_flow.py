#!/usr/bin/env python3
"""
Derive server/data/flow.json from the app's InterviewFlow.kt.

The IVR and WhatsApp channels must ask the *same* questions, in the same
languages, as the Android app -- otherwise the three channels are three
different products. Rather than retyping 90+ multilingual prompts (which would
drift the first time anyone edits one), this parses them straight out of the
Kotlin so the app source stays the single point of truth.

Run from the repo root:  python3 tools/extract_flow.py
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
KT = ROOT / "app/src/main/java/in/jandwar/app/ai/InterviewFlow.kt"
OUT = ROOT / "server/data/flow.json"

LANGS = ["en", "ta", "hi", "te", "kn", "ml"]

STR = re.compile(r'"((?:[^"\\]|\\.)*)"')


def unescape(s):
    return s.encode().decode("unicode_escape") if "\\" in s else s


def _balanced(text, start):
    """Text inside the (...) that opens at index `start`."""
    depth = 0
    j = start
    while j < len(text):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:j]
        j += 1
    raise SystemExit("unbalanced parentheses at %d" % start)


def block(src, start_pat):
    m = re.search(start_pat, src)
    if not m:
        raise SystemExit("pattern not found: %s" % start_pat)
    return _balanced(src, src.index("(", m.end() - 1))


def parse_lang_lists(body):
    """Parse  "en" to listOf("a", "b"), "ta" to listOf(...)  -> {lang: [...]}"""
    out = {}
    for lang in LANGS:
        m = re.search(r'"%s"\s+to\s+listOf\s*\(' % lang, body)
        if not m:
            continue
        out[lang] = [unescape(x) for x in
                     STR.findall(_balanced(body, body.index("(", m.end() - 1)))]
    return out


def parse_lang_strings(body):
    """Parse  "en" to "text", "ta" to "text"  -> {lang: text}"""
    out = {}
    for lang in LANGS:
        m = re.search(r'"%s"\s+to\s+"((?:[^"\\]|\\.)*)"' % lang, body)
        if m:
            out[lang] = unescape(m.group(1))
    return out


def main():
    src = KT.read_text(encoding="utf-8")

    out = {
        "_generated_by": "tools/extract_flow.py -- do not edit by hand",
        "_source": "app/src/main/java/in/jandwar/app/ai/InterviewFlow.kt",
        "langs": LANGS,
    }

    out["greeting"] = parse_lang_lists(block(src, r"private val GREETING\s*=\s*mapOf"))

    qblock = block(src, r"private val QUESTIONS[^=]*=\s*mapOf")
    slots = list(re.finditer(r"Slot\.([A-Z_]+)\s+to\s+mapOf\s*\(", qblock))
    questions, order = {}, []
    for k, m in enumerate(slots):
        end = slots[k + 1].start() if k + 1 < len(slots) else len(qblock)
        name = m.group(1)
        order.append(name)
        questions[name] = parse_lang_lists(qblock[m.start():end])
    out["slot_order"] = order
    out["questions"] = questions

    for name, pat in [("ack", r"private val ACK\s*=\s*mapOf"),
                      ("reprompt", r"private val REPROMPT\s*=\s*mapOf")]:
        out[name] = parse_lang_lists(block(src, pat))

    for name, pat in [("closing", r"private val CLOSING\s*=\s*mapOf"),
                      ("result_intro", r"private val RESULT_INTRO\s*=\s*mapOf"),
                      ("result_outro", r"private val RESULT_OUTRO\s*=\s*mapOf"),
                      ("no_result", r"private val NO_RESULT\s*=\s*mapOf")]:
        out[name] = parse_lang_strings(block(src, pat))

    # ---- validate before writing: a silent gap here is a silent IVR ----
    slot_enum = (ROOT / "app/src/main/java/in/jandwar/app/ai/ProfileFragment.kt").read_text(
        encoding="utf-8")
    enum_body = slot_enum[slot_enum.index("enum class Slot {"):]
    enum_body = enum_body[:enum_body.index(";")]
    declared = re.findall(r"\b([A-Z][A-Z_]+)\b", enum_body)

    problems = []
    for s in declared:
        if s not in questions:
            problems.append("slot %s is in the enum but has no question" % s)
    for lang in LANGS:
        if not out["greeting"].get(lang):
            problems.append("greeting/%s" % lang)
        for slot in order:
            if not out["questions"][slot].get(lang):
                problems.append("question/%s/%s" % (slot, lang))
        for k in ("ack", "reprompt", "closing", "result_intro",
                  "result_outro", "no_result"):
            if not out[k].get(lang):
                problems.append("%s/%s" % (k, lang))
    if problems:
        print("INCOMPLETE:")
        for p in problems[:12]:
            print("   -", p)
        return 1

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    n = sum(len(v) for s in questions.values() for v in s.values())
    print("wrote %s" % OUT.relative_to(ROOT))
    print("  slots (%d): %s" % (len(order), ", ".join(order)))
    print("  %d slot prompts across %d languages, plus greeting/ack/reprompt/closing"
          % (n, len(LANGS)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
