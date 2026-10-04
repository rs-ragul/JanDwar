#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Fold the 5-state research drop into the app assets.

Design decisions, and why:

**`districts.json` keeps its flat shape.** The app decodes it straight into
`DistrictsData(all, with_centre, without_centre)`, and the district picker,
the Home stats and the Settings stats all read those three lists. Changing
the top-level shape would mean touching the model, the repository, the
view-model and three screens. So the three lists stay, now holding the union
across all five states, and a `by_state` map is added beside them for the
state-aware UI that can come later. Old code keeps working; new code has what
it needs.

**`confidence` is split in two.** The research returns a prose sentence --
"CONFIRMED - address and phones from the KVK's own official website" -- which
is far more useful to a human than a bare enum but useless to a filter. It
becomes `confidence` (CONFIRMED / LIKELY / UNVERIFIED) plus `confidence_note`
(the full sentence). Nothing is discarded.

**Nulls are preserved as nulls.** 268 of the 660 centres have no phone and
199 no address, because the source did not publish one. That is the correct
outcome and must not be back-filled with a plausible-looking guess: a wrong
phone number on a screen a person is about to dial is worse than a blank.
"""

import json
import pathlib
import collections
import re

RESEARCH = pathlib.Path("/home/user/research/jandwar")
ASSETS = pathlib.Path("/home/user/JanDwar/app/src/main/assets")

STATE_LABEL = {
    "andhra_pradesh": "Andhra Pradesh",
    "karnataka": "Karnataka",
    "kerala": "Kerala",
    "tamil_nadu": "Tamil Nadu",
    "uttar_pradesh": "Uttar Pradesh",
}

CONF_RE = re.compile(r"^\s*(CONFIRMED|LIKELY|UNVERIFIED)", re.I)


def split_confidence(raw):
    """'CONFIRMED - from the DET list' -> ('CONFIRMED', 'from the DET list')."""
    if not raw:
        return "UNVERIFIED", None
    m = CONF_RE.match(str(raw))
    if not m:
        return "UNVERIFIED", str(raw)
    level = m.group(1).upper()
    note = str(raw)[m.end():].lstrip(" -–—:;,").strip() or None
    return level, note


def load(state, name):
    p = RESEARCH / state / name
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else None


def main():
    states = sorted(d.name for d in RESEARCH.iterdir() if d.is_dir())

    # ── centres ──────────────────────────────────────────────────────────
    centres = []
    conf_count = collections.Counter()
    for st in states:
        for c in load(st, "centres.json") or []:
            level, note = split_confidence(c.get("confidence"))
            conf_count[level] += 1
            centres.append({
                "state": STATE_LABEL[st],
                "district": c.get("district"),
                "name": c.get("name"),
                "address": c.get("address"),
                "phone": c.get("phone"),
                "trades": c.get("trades"),
                "dairy_course": c.get("dairy_course"),
                "confidence": level,
                "confidence_note": note,
                "source": c.get("source"),
                "retrieved": c.get("retrieved"),
            })

    # ── districts ────────────────────────────────────────────────────────
    by_state = {}
    all_d, with_c, without_c = [], [], []
    have_centre = {c["district"] for c in centres if c.get("district")}
    for st in states:
        d = load(st, "districts.json") or {}
        ds = d.get("districts") or d.get("all") or []
        ds = [x if isinstance(x, str) else x.get("name") for x in ds]
        ds = [x for x in ds if x]
        w = sorted([x for x in ds if x in have_centre])
        wo = sorted([x for x in ds if x not in have_centre])
        by_state[STATE_LABEL[st]] = {
            "all": sorted(ds), "with_centre": w, "without_centre": wo,
        }
        all_d += ds
        with_c += w
        without_c += wo

    # A district name is not unique across India. Within these five states the
    # flat union is only safe if there are no repeats -- check, do not assume.
    dupes = [n for n, k in collections.Counter(all_d).items() if k > 1]

    districts = {
        "all": sorted(set(all_d)),
        "with_centre": sorted(set(with_c)),
        "without_centre": sorted(set(without_c) - set(with_c)),
        "by_state": by_state,
        "note": ("Flat lists are the union across all states and preserve the "
                 "original schema the app decodes. Use by_state for "
                 "state-aware lookups."),
    }

    # ── job roles ────────────────────────────────────────────────────────
    roles = json.loads((ASSETS / "job_roles.json").read_text(encoding="utf-8"))
    wrapper = None
    if isinstance(roles, dict):
        for k in ("roles", "job_roles"):
            if k in roles:
                wrapper, roles = k, roles[k]
                break
    have_qp = {r.get("qp_code") for r in roles}
    added = 0
    for st in states:
        for r in load(st, "job_roles_additional.json") or []:
            qp = r.get("qp_code")
            if not qp or qp in have_qp:
                continue
            have_qp.add(qp)
            roles.append(r)
            added += 1

    # ── district economy ─────────────────────────────────────────────────
    # Two shapes came back: Kerala and Tamil Nadu are a bare list of district
    # records, the other three wrap the list in {state, note, districts,
    # retrieved}. Accept either rather than demanding the producer re-cut it.
    econ = {}
    for st in states:
        e = load(st, "district_economy.json")
        if isinstance(e, dict):
            rows = e.get("districts") or []
        elif isinstance(e, list):
            rows = e
        else:
            rows = []
        if isinstance(rows, dict):          # {district_name: {...}}
            rows = [dict(v, district=k) for k, v in rows.items()]
        for d in rows:
            if not isinstance(d, dict):
                continue
            name = d.get("district") or d.get("name")
            if name:
                econ[name] = dict(d, state=STATE_LABEL[st])

    # ── funding rules ────────────────────────────────────────────────────
    rules = {}
    for st in states:
        r = load(st, "gia_funding_rules.json")
        if r:
            rules[STATE_LABEL[st]] = r

    # ── write ────────────────────────────────────────────────────────────
    def w(name, obj):
        (ASSETS / name).write_text(
            json.dumps(obj, ensure_ascii=False, indent=1), encoding="utf-8")
        print("  wrote %-26s %s" % (name, f"{len(json.dumps(obj))//1024} KB"))

    w("centres.json", centres)
    w("districts.json", districts)
    w("job_roles.json", {wrapper: roles} if wrapper else roles)
    w("district_economy.json", econ)
    w("gia_funding_rules.json", rules)

    print("\ncentres        :", len(centres), dict(conf_count))
    print("districts      :", len(districts["all"]),
          "| with centre:", len(districts["with_centre"]),
          "| without:", len(districts["without_centre"]))
    print("duplicate district names across states:", dupes or "none")
    print("job roles      :", len(roles), "(+%d new)" % added)
    print("economy notes  :", len(econ))
    print("funding rules  :", list(rules))


if __name__ == "__main__":
    main()
