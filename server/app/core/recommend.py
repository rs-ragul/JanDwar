"""
Port of AppRepository.matchRoles -- the NSQF recommendation engine.

This is deliberately NOT the language model's job, on any channel.

PM-AJAY GIA is a government funding scheme. A recommendation has to be
reproducible, auditable and grounded in the real catalogue: the same profile
must produce the same courses today and at an audit next year, every QP code
must actually exist, and every NSQF level must be the real one. A generative
model cannot promise any of that -- it will happily invent a plausible course
code. So the LLM handles conversation and understanding, and the scoring below
decides what is recommended, from the 516 real QPs in job_roles.json.

Every points award emits a matching factor so the user can be told exactly why
a course surfaced, which the problem statement asks for explicitly.
"""

from __future__ import annotations

from . import data

EDU_RANK = {"edu_below8": 0, "edu_8": 1, "edu_10": 2, "edu_12": 3,
            "edu_iti": 4, "edu_grad": 5}

# tolerant mapping from whatever the parser or the LLM produced
AI_EDU = {
    "none": 0, "class5": 0, "below_8th": 0, "below8": 0, "read_write": 0,
    "illiterate": 0, "edu_below8": 0, "primary": 0, "5th": 0,
    "class8": 1, "8th": 1, "edu_8": 1, "eighth": 1, "middle": 1,
    "class10": 2, "10th": 2, "edu_10": 2, "sslc": 2, "tenth": 2, "matric": 2,
    "class12": 3, "12th": 3, "edu_12": 3, "hsc": 3, "twelfth": 3,
    "higher_secondary": 3,
    "iti_diploma": 4, "iti": 4, "diploma": 4, "edu_iti": 4, "polytechnic": 4,
    "graduate": 5, "degree": 5, "edu_grad": 5, "ug": 5, "bachelor": 5,
}

EDU_KEY_BY_RANK = {0: "edu_below8", 1: "edu_8", 2: "edu_10",
                   3: "edu_12", 4: "edu_iti", 5: "edu_grad"}

LIMITING_WORDS = ["cannot", "can't", "cant", "unable", "heavy", "disab",
                  "injur", "weak", "pain", "surgery", "முடியாது", "नहीं", "भारी"]


def edu_rank(value: str | None) -> int | None:
    if not value:
        return None
    return AI_EDU.get(str(value).strip().lower())


# ── role predicates, mirroring data/model/Models.kt ─────────────────────────

def level_int(role) -> int:
    d = "".join(c for c in str(role.get("nsqf_level", "")) if c.isdigit())
    return min(max(int(d), 1), 10) if d else 3


def hours_int(role) -> int:
    d = "".join(c for c in str(role.get("notional_hours", "")) if c.isdigit())
    return int(d) if d else 300


def is_long_term(role) -> bool:
    return hours_int(role) >= 600


def duration_label(role) -> str:
    h = hours_int(role)
    months = max(1, round(h / 130))
    return "%d hrs · ~%d %s" % (h, months, "month" if months == 1 else "months")


def required_edu_rank(role) -> int:
    lv = level_int(role)
    return {1: 0, 2: 0, 3: 1, 4: 2, 5: 3, 6: 4, 7: 4}.get(lv, 5)


def programme_type(role) -> str:
    """
    Which PM-AJAY GIA programme this role's duration fits.

    The published rule sets bands per programme type rather than one blanket
    limit, and this is what a person actually wants to know: a few days of
    recognition-of-prior-learning, a three-month short course, or a year-long
    one. Duration is not used to *exclude* -- the guidelines allow a QP whose
    hours fall outside the standard band where its NOS stipulates that
    duration.
    """
    h = hours_int(role) if "hours_int" in globals() else int(role.get("notional_hours") or 0)
    if 32 <= h <= 80:
        return "rpl"
    if 200 <= h <= 600:
        return "short_term"
    if h > 600:
        return "long_term"
    return "short_term"


def is_fundable(role) -> bool:
    return role.get("sector") in data.FUNDABLE_SECTORS


def matches_interest(role, key) -> bool:
    n = (role.get("job_role") or "").lower()
    s = (role.get("sector") or "").lower()
    if key == "dairy":
        return "dairy" in n or "milk" in n or "cattle" in n
    if key == "cattle":
        return any(w in n for w in ("cattle", "livestock", "dairy", "animal", "bovine"))
    if key == "goat":
        return any(w in n for w in ("goat", "sheep", "small ruminant"))
    if key == "poultry":
        return any(w in n for w in ("poultry", "chicken", "hatchery", "broiler", "quail"))
    if key == "farming":
        return s == "agriculture" or any(w in n for w in
                                         ("farm", "crop", "agri", "horticulture", "nursery"))
    if key == "food":
        return s == "food_processing" or any(w in n for w in
                                             ("food", "baker", "miller", "dairy processing"))
    if key == "machine":
        return s == "electronics_automation" or any(w in n for w in
                                                    ("machine", "technician", "operator",
                                                     "mechanic", "electric"))
    if key == "textile":
        return s == "handloom_textile" or any(w in n for w in
                                              ("textile", "loom", "weav", "dyeing"))
    if key == "construction":
        return s == "construction" or any(w in n for w in
                                          ("construction", "mason", "bar bender",
                                           "plumb", "carpent", "painter"))
    if key == "tailor":
        return s == "apparel" or any(w in n for w in
                                     ("sewing", "tailor", "stitch", "garment"))
    return False


def matches_occupation_text(role, occupation) -> bool:
    if not occupation or not occupation.strip():
        return False
    f = occupation.lower()
    n = (role.get("job_role") or "").lower()
    s = (role.get("sector") or "").lower()
    if any(w in f for w in ("farm", "agri", "cattle", "dairy", "milk",
                            "goat", "sheep", "poultry", "cow")):
        return s == "agriculture" or "farm" in n or "agri" in n or "livestock" in n
    if any(w in f for w in ("tailor", "weav", "loom", "textile", "stitch",
                            "garment", "sew")):
        return s in ("handloom_textile", "apparel")
    if any(w in f for w in ("construct", "mason", "labour", "labor",
                            "coolie", "building")):
        return s == "construction"
    if any(w in f for w in ("food", "cook", "baker", "hotel", "catering", "mess")):
        return s == "food_processing"
    if any(w in f for w in ("electric", "mechanic", "repair", "machine",
                            "welder", "fitter", "driver")):
        return s == "electronics_automation" or "technician" in n or "operator" in n
    if any(w in f for w in ("photo", "video", "media", "design", "print",
                            "computer", "studio")):
        return s == "media_entertainment"
    return False


def self_employment_fit(role) -> bool:
    n = (role.get("job_role") or "").lower()
    return (role.get("sector") in {"agriculture", "food_processing",
                                   "handloom_textile", "apparel"}
            or any(w in n for w in ("entrepreneur", "artisan", "farm", "self")))


def wage_fit(role) -> bool:
    n = (role.get("job_role") or "").lower()
    return (any(w in n for w in ("assistant", "operator", "technician", "worker",
                                 "supervisor", "helper"))
            or role.get("sector") in {"construction", "media_entertainment",
                                      "electronics_automation"})


def is_physically_demanding(role) -> bool:
    n = (role.get("job_role") or "").lower()
    return (role.get("sector") == "construction"
            or any(w in n for w in ("mason", "bar bender", "lifting",
                                    "loader", "helper")))


# ── the scorer ──────────────────────────────────────────────────────────────

def match_roles(profile: dict, lang: str = "en", limit: int = 6) -> list[dict]:
    roles = data.job_roles()
    if not roles:
        return []

    rank = edu_rank(profile.get("education"))
    if rank is None:
        rank = 1  # same default as the app: CLASS_8
    district = (profile.get("district") or "").strip()
    centre = data.centre_for_district(district, profile.get("state") or "")
    interests = profile.get("interests") or []
    skills = profile.get("skills") or []
    family = (profile.get("familyOccupation") or "").strip()
    current = (profile.get("currentLivelihood") or "").strip()
    pref = profile.get("preference")
    mob = profile.get("mobility")
    constraints = (profile.get("physicalConstraints") or "").strip()

    tr = lambda k: data.tr(lang, k)
    out = []

    for role in roles:
        score = 0
        factors = []
        eligible = True

        required = required_edu_rank(role)
        if required <= rank:
            score += 30
        else:
            score -= (required - rank) * 22
            eligible = False
            if required >= 5 and rank < 2:
                continue

        if is_long_term(role) and rank < 2:
            score -= 14

        matched = [i for i in interests if matches_interest(role, i)]
        if matched:
            score += 85 + (len(matched) - 1) * 18
            factors.append({
                "label": "%s %s" % (tr("reason_interest"),
                                    ", ".join(data.interest_label(lang, i) for i in matched)),
                "positive": True})

        family_note = ""
        family_fit = bool(family) and matches_occupation_text(role, family)
        if family_fit:
            score += 55
            family_note = "%s %s" % (tr("reason_family"),
                                     data.occupation_label(lang, family))
            factors.append({"label": family_note, "positive": True})

        if current and matches_occupation_text(role, current):
            score += 32
            if not family_note:
                family_note = "%s %s" % (tr("reason_family"),
                                         data.occupation_label(lang, current))
                factors.append({"label": family_note, "positive": True})

        n_low = (role.get("job_role") or "").lower()
        s_low = (role.get("sector") or "").lower()
        skill_hit = any(len(sk.strip()) >= 3 and
                        (sk.strip().lower() in n_low or sk.strip().lower() in s_low)
                        for sk in skills)
        if skill_hit:
            score += 40

        if not matched and not family_fit and not skill_hit:
            score -= 12

        if is_fundable(role):
            score += 26
            factors.append({"label": tr("reason_fundable"), "positive": True})

        if pref == "pref_self" and self_employment_fit(role):
            score += 24
            factors.append({"label": tr("reason_self"), "positive": True})
        elif pref == "pref_wage" and wage_fit(role):
            score += 24
            factors.append({"label": tr("reason_wage"), "positive": True})

        if centre:
            score += 12
            if role.get("sector") in ("agriculture", "food_processing"):
                score += 10
            factors.append({"label": tr("reason_centre"), "positive": True})

        if mob == "local":
            score += 20 if centre else -14
        elif mob == "district":
            score += 10
        elif mob == "state":
            score += 5

        if constraints and is_physically_demanding(role):
            pc = constraints.lower()
            if any(w in pc for w in LIMITING_WORDS):
                score -= 55

        score += max(0, 8 - level_int(role))

        if not factors:
            factors.append({"label": tr("reason_base"), "positive": True})

        out.append({
            "role": role,
            "score": score,
            "eligible": eligible,
            "factors": factors,
            "reason": factors[0]["label"],
            "familyFitNote": family_note,
            "skillGapNote": _skill_gap(role, rank, lang),
            "centre": centre,
            "regionOpportunity": _region_opportunity(profile, centre, lang, role),
        })

    if not out:
        return []

    ranked = sorted(out, key=lambda m: (not m["eligible"], -m["score"]))

    per_sector: dict[str, int] = {}
    diversified = []
    for m in ranked:
        sec = m["role"].get("sector", "")
        if per_sector.get(sec, 0) >= 2:
            continue
        per_sector[sec] = per_sector.get(sec, 0) + 1
        diversified.append(m)
        if len(diversified) >= limit:
            break
    if len(diversified) < limit:
        seen = {m["role"].get("qp_code") for m in diversified}
        for m in ranked:
            if len(diversified) >= limit:
                break
            if m["role"].get("qp_code") not in seen:
                diversified.append(m)
                seen.add(m["role"].get("qp_code"))

    best = diversified[0]["score"] if diversified else 1
    worst_ref = max(best - 120, 1)
    for m in diversified:
        pct = 95 if best <= worst_ref else round(
            55 + 45.0 * (m["score"] - worst_ref) / (best - worst_ref))
        m["confidence"] = min(99, max(35, int(pct)))
        m["durationLabel"] = duration_label(m["role"])
        m["programmeType"] = programme_type(m["role"])
        m["fundable"] = is_fundable(m["role"])
    return diversified


def _skill_gap(role, rank, lang):
    required = required_edu_rank(role)
    if rank >= required:
        return data.tr(lang, "eligible")
    return "%s · %s" % (data.tr(lang, "needs_edu"),
                        data.tr(lang, EDU_KEY_BY_RANK.get(required, "edu_grad")))


def _region_opportunity(profile, centre, lang, role=None):
    """
    Why this trade makes sense *here*.

    Previously this printed the nearest centre and stopped, which answered
    "where do I train?" but never the PS's actual question -- whether the
    local economy can absorb the trade. The researched per-district notes
    carry that: Alappuzha is the coir capital, Ariyalur is a cement and lime
    belt. When the district's strong sectors include this role's sector, say
    so; that sentence is the difference between a course listing and a
    recommendation.
    """
    district = (profile.get("district") or "").strip()
    econ = data.economy_for_district(district) if district else None
    sector = ((role or {}).get("sector") or "").strip()

    parts = []
    if centre:
        parts.append("%s, %s" % (centre.get("name", ""), centre.get("district", "")))
    if econ:
        strong = econ.get("strong_sectors") or []
        note = (econ.get("note") or "").strip()
        if sector and sector in strong and note:
            parts.append(note)
        elif note and not centre:
            parts.append(note)
    if parts:
        return " — ".join(p for p in parts if p)
    local = (profile.get("localOpportunity") or "").strip()
    if local:
        return local
    district = (profile.get("district") or "").strip()
    if district and district in data.districts().get("without_centre", []):
        return data.tr(lang, "no_centre")
    return data.tr(lang, "reason_base")
