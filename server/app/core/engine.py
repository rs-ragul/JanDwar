"""
The conversation engine shared by every server-side channel.

Same script, same slots, same recommender as the Android app -- the prompts are
literally extracted from InterviewFlow.kt by tools/extract_flow.py. A caller on
a feature phone and a user on a smartphone are talking to one product.

Channel-agnostic on purpose: it takes text in and returns text out plus a bit
of control information. The IVR adapter turns that into voice, the WhatsApp
adapter into a voice note, the simulator into HTML. None of them own any
interview logic.
"""

from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field

from . import data
from .nlu import Nlu, Fragment, normalise
from .recommend import match_roles

# One shared NLU: loading the lexicon is not free and it is stateless.
_NLU: Nlu | None = None


def nlu() -> Nlu:
    global _NLU
    if _NLU is None:
        _NLU = Nlu()
    return _NLU


MAX_RETRIES = 2

#: Slots we are willing to consider answered *without* having asked them.
#:
#: These take enum or gazetteer values -- a school level, a district name, a
#: yes/no, one of three travel ranges -- so a mention anywhere in the
#: conversation is unambiguous and re-asking would feel robotic.
#:
#: The free-text occupation slots are deliberately NOT here. FAMILY_OCCUPATION,
#: CURRENT_LIVELIHOOD and INTERESTS all draw on the same occupation vocabulary,
#: so "my family farms" sets all three and the interview silently skips two of
#: the PS's required dimensions. Those must always be asked out loud.
INFERABLE = {"EDUCATION", "STATE", "DISTRICT", "PREFERENCE", "MOBILITY", "CONSTRAINTS"}


@dataclass
class Turn:
    who: str          # "bot" | "user"
    text: str
    slot: str | None = None


@dataclass
class Session:
    id: str
    lang: str = "en"
    channel: str = "sim"
    frag: Fragment = field(default_factory=Fragment)
    asked: list[str] = field(default_factory=list)
    current: str | None = None
    turns: list[Turn] = field(default_factory=list)
    retries: int = 0
    seed: int = 0
    done: bool = False
    results: list[dict] = field(default_factory=list)
    created: float = field(default_factory=time.time)
    updated: float = field(default_factory=time.time)
    # Set when a cloud/local LLM produced the last reply, for the UI badge.
    used_llm: bool = False

    def progress(self) -> tuple[int, int]:
        return self.frag.filled_count(), len(data.flow()["slot_order"])


def _pick(lst: list[str], seed: int) -> str:
    return lst[seed % len(lst)] if lst else ""


def _f(section: str, lang: str):
    fl = data.flow()[section]
    return fl.get(lang) or fl.get("en")


def greeting(lang: str, seed: int = 0) -> str:
    return _pick(_f("greeting", lang), seed)


#: The district prompt, phrased so it names the state the person already gave.
#: Mirrors ai/InterviewFlow.kt::DISTRICT_IN_STATE.
DISTRICT_IN_STATE = {
    "en": ["Which district of %s do you live in?",
           "And which district in %s is your home?"],
    "ta": ["%s மாநிலத்தில் எந்த மாவட்டத்தில் வசிக்கிறீர்கள்?",
           "%s-ல் உங்கள் ஊர் எந்த மாவட்டத்தில் உள்ளது?"],
    "hi": ["%s के किस जिले में आप रहते हैं?",
           "%s में आपका घर किस जिले में है?"],
    "te": ["%s లోని ఏ జిల్లాలో మీరు నివసిస్తున్నారు?",
           "%s లో మీ ఇల్లు ఏ జిల్లాలో ఉంది?"],
    "kn": ["%s ರಾಜ್ಯದ ಯಾವ ಜಿಲ್ಲೆಯಲ್ಲಿ ನೀವು ವಾಸಿಸುತ್ತೀರಿ?",
           "%s ನಲ್ಲಿ ನಿಮ್ಮ ಮನೆ ಯಾವ ಜಿಲ್ಲೆಯಲ್ಲಿದೆ?"],
    "ml": ["%s ലെ ഏത് ജില്ലയിലാണ് നിങ്ങൾ താമസിക്കുന്നത്?",
           "%s ൽ നിങ്ങളുടെ വീട് ഏത് ജില്ലയിലാണ്?"],
}

#: English state name rendered in each interview language.
STATE_LABELS = {
    "Tamil Nadu": {"en": "Tamil Nadu", "ta": "தமிழ்நாடு", "hi": "तमिलनाडु",
                   "te": "తమిళనాడు", "kn": "ತಮಿಳುನಾಡು", "ml": "തമിഴ്‌നാട്"},
    "Kerala": {"en": "Kerala", "ta": "கேரளா", "hi": "केरल",
               "te": "కేరళ", "kn": "ಕೇರಳ", "ml": "കേരളം"},
    "Karnataka": {"en": "Karnataka", "ta": "கர்நாடகா", "hi": "कर्नाटक",
                  "te": "కర్ణాటక", "kn": "ಕರ್ನಾಟಕ", "ml": "കർണാടക"},
    "Andhra Pradesh": {"en": "Andhra Pradesh", "ta": "ஆந்திரப் பிரதேசம்",
                       "hi": "आंध्र प्रदेश", "te": "ఆంధ్రప్రదేశ్",
                       "kn": "ಆಂಧ್ರಪ್ರದೇಶ", "ml": "ആന്ധ്രാപ്രദേശ്"},
    "Uttar Pradesh": {"en": "Uttar Pradesh", "ta": "உத்தரப் பிரதேசம்",
                      "hi": "उत्तर प्रदेश", "te": "ఉత్తరప్రదేశ్",
                      "kn": "ಉತ್ತರ ಪ್ರದೇಶ", "ml": "ഉത്തർപ്രദേശ്"},
}


def state_label(state: str, lang: str) -> str:
    return (STATE_LABELS.get(state) or {}).get(lang) or state


def question(slot: str, lang: str, seed: int = 0, state: str | None = None) -> str:
    if slot == "DISTRICT" and state:
        forms = DISTRICT_IN_STATE.get(lang) or DISTRICT_IN_STATE["en"]
        return _pick(forms, seed).replace("%s", state_label(state, lang))
    q = data.flow()["questions"][slot]
    return _pick(q.get(lang) or q.get("en"), seed)


def ack(lang: str, seed: int = 0) -> str:
    return _pick(_f("ack", lang), seed)


def reprompt(lang: str, seed: int = 0) -> str:
    return _pick(_f("reprompt", lang), seed)


def closing(lang: str) -> str:
    c = data.flow()["closing"]
    return c.get(lang) or c.get("en")


def result_intro(lang: str, count: int) -> str:
    s = data.flow()["result_intro"]
    return (s.get(lang) or s.get("en")).replace("%d", str(count)).replace("{n}", str(count))


def result_outro(lang: str) -> str:
    s = data.flow()["result_outro"]
    return s.get(lang) or s.get("en")


def no_result(lang: str) -> str:
    s = data.flow()["no_result"]
    return s.get(lang) or s.get("en")


class Engine:
    """Drives one interview. Stateless itself; all state lives on the Session."""

    def __init__(self, llm=None):
        self.llm = llm

    # ── lifecycle ──────────────────────────────────────────────────────────
    def start(self, lang: str = "en", channel: str = "sim") -> tuple[Session, str]:
        s = Session(id=uuid.uuid4().hex[:12], lang=lang, channel=channel)
        # The greeting already contains the first question (education).
        s.current = data.flow()["slot_order"][0]
        s.asked.append(s.current)
        text = greeting(lang, s.seed)
        s.turns.append(Turn("bot", text, s.current))
        return s, text

    def answer(self, s: Session, raw: str) -> str:
        """Feed a user utterance, get the assistant's next line."""
        s.updated = time.time()
        raw = (raw or "").strip()
        s.turns.append(Turn("user", raw, s.current))
        s.used_llm = False

        if s.done:
            return self._results_narration(s)

        if not raw:
            return self._retry(s)

        # 1. deterministic extraction, always
        slot = s.current or data.flow()["slot_order"][0]
        frag = nlu().extract_for_slot(raw, s.lang, slot, state_hint=s.frag.state)

        # 2. optional LLM overlay for anything the rules could not place
        if self.llm is not None and self.llm.available():
            extra = self.llm.extract(raw, s.lang, slot, s.frag)
            if extra is not None:
                frag.merge(extra)
                s.used_llm = True

        before = s.frag.filled_count()
        # "My family farms" is about the family, not about what the person
        # does today; letting one answer populate the other loses a real
        # answer and skips a question.
        if slot == "FAMILY_OCCUPATION":
            frag.current_livelihood = ""
        elif slot == "CURRENT_LIVELIHOOD":
            frag.family_occupation = ""
        s.frag.merge(frag, answering=slot)
        gained = s.frag.filled_count() - before

        if gained == 0 and not s.frag.is_filled(slot):
            return self._retry(s)

        s.retries = 0
        s.seed += 1
        return self._advance(s)

    # ── internals ──────────────────────────────────────────────────────────
    def _retry(self, s: Session) -> str:
        s.retries += 1
        if s.retries > MAX_RETRIES:
            # Do not trap the caller on one question.
            s.retries = 0
            return self._advance(s, skip=True)
        text = reprompt(s.lang, s.retries)
        q = question(s.current, s.lang, s.seed + s.retries, s.frag.state) if s.current else ""
        out = (text + " " + q).strip()
        s.turns.append(Turn("bot", out, s.current))
        return out

    def _advance(self, s: Session, skip: bool = False) -> str:
        order = data.flow()["slot_order"]
        nxt = None
        for slot in order:
            if slot in s.asked and (skip or s.frag.is_filled(slot)):
                continue
            if s.frag.is_filled(slot) and slot in INFERABLE:
                continue
            nxt = slot
            break

        if nxt is None:
            return self._finish(s)

        s.current = nxt
        s.asked.append(nxt)
        line = (ack(s.lang, s.seed) + " " + question(nxt, s.lang, s.seed, s.frag.state)).strip()
        s.turns.append(Turn("bot", line, nxt))
        return line

    def _finish(self, s: Session) -> str:
        s.done = True
        s.current = None
        profile = self.profile(s)
        s.results = match_roles(profile, s.lang, limit=6)
        text = (closing(s.lang) + " " + self._results_narration(s)).strip()
        s.turns.append(Turn("bot", text, None))
        return text

    def _results_narration(self, s: Session) -> str:
        if not s.results:
            return no_result(s.lang)
        parts = [result_intro(s.lang, len(s.results))]
        for i, m in enumerate(s.results[:3], 1):
            r = m["role"]
            parts.append("%d. %s. NSQF %s. %s. %s." % (
                i, r.get("job_role"), r.get("nsqf_level"),
                m["durationLabel"], m["reason"]))
        parts.append(result_outro(s.lang))
        return " ".join(parts)

    @staticmethod
    def profile(s: Session) -> dict:
        f = s.frag
        return {
            "education": f.edu,
            "state": f.state or "",
            "district": f.district or "",
            "familyOccupation": f.family_occupation or "",
            "currentLivelihood": f.current_livelihood or "",
            "interests": f.interests,
            "skills": f.skills,
            "preference": f.preference,
            "mobility": f.mobility,
            "physicalConstraints": f.physical_constraints or "",
            "localOpportunity": f.local_opportunity or "",
        }


# ── session store ───────────────────────────────────────────────────────────

class SessionStore:
    """
    In-memory with a TTL. An IVR call and a WhatsApp thread both need to find
    their session again by a channel-specific key (CallSid / phone number).
    """

    def __init__(self, ttl_seconds: int = 3600):
        self._by_id: dict[str, Session] = {}
        self._alias: dict[str, str] = {}
        self.ttl = ttl_seconds

    def put(self, s: Session, alias: str | None = None) -> Session:
        self._by_id[s.id] = s
        if alias:
            self._alias[alias] = s.id
        return s

    def get(self, sid: str) -> Session | None:
        self._sweep()
        return self._by_id.get(sid)

    def by_alias(self, alias: str) -> Session | None:
        self._sweep()
        sid = self._alias.get(alias)
        return self._by_id.get(sid) if sid else None

    def drop_alias(self, alias: str) -> None:
        self._alias.pop(alias, None)

    def _sweep(self) -> None:
        now = time.time()
        dead = [k for k, v in self._by_id.items() if now - v.updated > self.ttl]
        for k in dead:
            self._by_id.pop(k, None)
        for a, sid in list(self._alias.items()):
            if sid not in self._by_id:
                self._alias.pop(a, None)

    def count(self) -> int:
        self._sweep()
        return len(self._by_id)
