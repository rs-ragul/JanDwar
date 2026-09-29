"""
Shared data access.

Every channel -- Android app, IVR phone call, WhatsApp -- is answering from the
*same* files. The app reads them out of its APK assets; the server reads the
very same paths off disk. There is deliberately no second copy of the
catalogue, the lexicon or the translations, because two copies is how the
phone and the phone-call start recommending different courses.
"""

from __future__ import annotations

import functools
import json
import os
import pathlib

# Repo root = .../server/app/core/data.py -> up 4
ROOT = pathlib.Path(__file__).resolve().parents[3]

# The app's assets are the source of truth. Override for a standalone deploy
# where only the server directory was shipped.
def _find_assets() -> pathlib.Path:
    """
    Locate the JSON catalogue.

    Order: explicit env var, then the Android app's assets folder (the normal
    case -- the server and the app share one source of truth), then a copy
    bundled next to the server. The last fallback is what lets this server be
    handed to someone as a standalone package with no Android project.
    """
    env = os.environ.get("JANDWAR_ASSETS")
    if env:
        return pathlib.Path(env)
    for cand in (ROOT / "app/src/main/assets",
                 ROOT / "assets",
                 pathlib.Path(__file__).resolve().parents[2] / "assets"):
        if (cand / "job_roles.json").is_file():
            return cand
    return ROOT / "app/src/main/assets"


ASSETS = _find_assets()

# flow.json is generated from InterviewFlow.kt by tools/extract_flow.py
SERVER_DATA = pathlib.Path(os.environ.get("JANDWAR_SERVER_DATA", ROOT / "server/data"))


def _read(path: pathlib.Path):
    with path.open(encoding="utf-8") as f:
        return json.load(f)


@functools.lru_cache(maxsize=1)
def job_roles() -> list[dict]:
    """The NSQF catalogue. Malformed rows are dropped exactly as the app does."""
    raw = _read(ASSETS / "job_roles.json")
    rows = raw["roles"] if isinstance(raw, dict) and "roles" in raw else raw
    out = []
    for r in rows:
        name = (r.get("job_role") or "").strip()
        low = name.lower()
        if (len(name) > 3 and low != "english hindi"
                and not name.startswith("QG-") and not low.startswith("qp code")):
            out.append(r)
    return out


@functools.lru_cache(maxsize=1)
def centres() -> list[dict]:
    raw = _read(ASSETS / "centres.json")
    return raw["centres"] if isinstance(raw, dict) and "centres" in raw else raw


@functools.lru_cache(maxsize=1)
def districts() -> dict:
    return _read(ASSETS / "districts.json")


@functools.lru_cache(maxsize=1)
def i18n() -> dict:
    return _read(ASSETS / "i18n.json")


@functools.lru_cache(maxsize=1)
def lexicon_forms() -> dict:
    return _read(ASSETS / "lexicon.json")["forms"]


@functools.lru_cache(maxsize=1)
def flow() -> dict:
    p = SERVER_DATA / "flow.json"
    if not p.exists():
        raise SystemExit(
            "server/data/flow.json is missing -- run: python3 tools/extract_flow.py")
    return _read(p)


# ── translation helpers, mirroring AppRepository ────────────────────────────

def tr(lang: str, key: str) -> str:
    s = i18n().get("strings", {})
    for L in (lang, "en"):
        v = s.get(L, {}).get(key)
        if v and v.strip():
            return v
    return key


def interest_label(lang: str, key: str) -> str:
    m = i18n().get("interest", {})
    for L in (lang, "en"):
        v = m.get(L, {}).get(key)
        if v and v.strip():
            return v
    return key.capitalize()


def occupation_label(lang: str, raw: str) -> str:
    """Canonical English occupation -> display label. Free text passes through."""
    if not raw or not raw.strip():
        return raw
    m = i18n().get("occupation", {})
    k = raw.strip().lower()
    for L in (lang, "en"):
        v = m.get(L, {}).get(k)
        if v and v.strip():
            return v
    return raw


@functools.lru_cache(maxsize=1)
def district_economy() -> dict:
    """Per-district livelihood notes, keyed by district name."""
    try:
        return _read(ASSETS / "district_economy.json")
    except Exception:
        return {}


def economy_for_district(district: str) -> dict | None:
    if not district:
        return None
    return district_economy().get(district.strip())


def centre_for_district(district: str, state: str = "") -> dict | None:
    """
    Nearest centre, preferring a CONFIRMED record.

    `state` scopes the search. District names are not unique across India and
    the catalogue now spans five states, so an unscoped match could hand back
    a centre in another state.
    """
    if not district:
        return None
    d = district.strip().lower()
    st = (state or "").strip().lower()
    hits = [
        c for c in centres()
        if (c.get("district") or "").strip().lower() == d
        and (not st or (c.get("state") or "").strip().lower() == st)
    ]
    if not hits:
        return None
    for c in hits:
        if str(c.get("confidence", "")).upper().startswith("CONFIRMED"):
            return c
    return hits[0]


def states() -> list[str]:
    by = districts().get("by_state") or {}
    if by:
        return sorted(by.keys())
    return sorted({c.get("state", "") for c in centres() if c.get("state")})


def districts_of_state(state: str) -> list[str]:
    by = districts().get("by_state") or {}
    return (by.get(state) or {}).get("all", [])


def state_of_district(district: str) -> str | None:
    if not district:
        return None
    d = district.strip().lower()
    for st, v in (districts().get("by_state") or {}).items():
        if any(x.strip().lower() == d for x in v.get("all", [])):
            return st
    return None


def languages() -> list[tuple[str, str]]:
    return [tuple(x) for x in i18n().get("langs", [])]


def summary() -> dict:
    roles = job_roles()
    sectors: dict[str, int] = {}
    for r in roles:
        sectors[r.get("sector", "")] = sectors.get(r.get("sector", ""), 0) + 1
    dd = districts()
    return {
        "roles": len(roles),
        "sectors": dict(sorted(sectors.items(), key=lambda kv: -kv[1])),
        "fundable": sum(1 for r in roles if r.get("sector") in FUNDABLE_SECTORS),
        "centres": len(centres()),
        "districts": len(dd.get("all", [])),
        "districts_with_centre": len(dd.get("with_centre", [])),
        "languages": [c for c, _ in languages()],
        "lexicon_categories": len(lexicon_forms()),
        "lexicon_forms": sum(len(v) for v in lexicon_forms().values()),
        "assets_dir": str(ASSETS),
    }


# This catalogue's sector names mapped onto the PM-AJAY GIA Annexure-1
# eligible-activity list (assets/gia_funding_rules.json, researched per state).
#
# Replaces a GUESSED four-sector whitelist that was wrong in an expensive
# direction: it marked 116 media_entertainment and 20 electronics roles as
# unfundable when Annexure-1 covers 31 sectors including both. Every sector in
# this catalogue maps onto that list, so the sector test excludes nothing --
# which is the correct answer, not a bug.
ANNEXURE1_SECTOR = {
    "agriculture": "agriculture",
    "food_processing": "food_processing",
    "construction": "plumbing_construction",
    "handloom_textile": "handloom_textile",
    "apparel": "readymade_garments",
    "electronics_automation": "electronics",
    "media_entertainment": "media_entertainment",
    # Added with the five-state drop. gia_funding_rules.json records
    # "qp_whitelist": "none stated - central GIA guidelines do not restrict
    # sectors/QPs" for AP, Karnataka and UP, so no sector in this catalogue
    # is excluded. Leaving these out marked 20 roles "not funded".
    "automotive": "automobile_repair",
    "healthcare": "healthcare",
    "leather": "leather_footwear",
    "tourism_hospitality": "hospitality",
    "fisheries": "fisheries",
    "aerospace_aviation": "aerospace_aviation",
}

FUNDABLE_SECTORS = set(ANNEXURE1_SECTOR)
