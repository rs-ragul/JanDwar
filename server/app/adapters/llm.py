"""
Optional local LLM via Ollama -- fully open source, no API key, no per-call cost.

WHAT IT IS AND IS NOT ALLOWED TO DO
-----------------------------------
The model helps with *language*: reading a rambling sentence and pulling out
which slot values are in it. It never chooses a course. Recommendation stays in
recommend.py, scored against the real NSQF catalogue, because:

  * PM-AJAY GIA is public money -- a recommendation must be reproducible and
    auditable, and must cite a QP code that actually exists;
  * a generative model will invent a plausible-looking QP code and NSQF level
    without hesitation, and nobody downstream can tell;
  * an IVR caller is holding a phone to their ear: the deterministic path
    answers in milliseconds, a 7B model on CPU takes seconds.

So the LLM is an *overlay*. The rules run first and always; if the model is
running and returns clean JSON, its non-empty fields are merged on top. If it
is absent, slow, or returns rubbish, the interview is unaffected. That is why
the service still works with Ollama switched off.
"""

from __future__ import annotations

import json
import logging
import re

import httpx

from ..core.nlu import Fragment

log = logging.getLogger("jandwar.llm")

LANG_NAME = {"en": "English", "ta": "Tamil", "hi": "Hindi",
             "te": "Telugu", "kn": "Kannada", "ml": "Malayalam"}

SCHEMA = """{
 "education": "class5|class8|class10|class12|iti_diploma|graduate|null",
 "familyOccupation": "short English phrase or null",
 "currentLivelihood": "short English phrase or null",
 "interests": ["dairy|cattle|goat|poultry|farming|food|machine|textile|construction|tailor"],
 "preference": "pref_self|pref_wage|null",
 "mobility": "local|district|state|null",
 "physicalConstraints": "short phrase, or None if they said they have none, or null",
 "district": "Tamil Nadu district name in English or null"
}"""

PROMPT = """You extract structured fields from one sentence spoken by a rural \
job seeker in {lang}. They were just asked about: {slot}.

Return ONLY a JSON object with exactly these keys, no prose, no code fence:
{schema}

Rules:
- Use null for anything not clearly stated. Never guess.
- Do not invent a district that was not said.
- "physicalConstraints": if they say they have no problem, return "None".
- interests must come only from the listed keys.

Sentence: {text}
JSON:"""


class OllamaLlm:
    def __init__(self, host: str, model: str, timeout: float = 12.0,
                 enabled: bool = True):
        self.host = host.rstrip("/")
        self.model = model
        self.timeout = timeout
        self.enabled = enabled
        self._ok: bool | None = None
        self._models: list[str] = []

    # ── availability ───────────────────────────────────────────────────────
    def probe(self) -> bool:
        """Ask Ollama what it has. Cached; call refresh() to re-check."""
        if not self.enabled:
            self._ok = False
            return False
        try:
            r = httpx.get(self.host + "/api/tags", timeout=3.0)
            r.raise_for_status()
            self._models = [m.get("name", "") for m in r.json().get("models", [])]
            base = self.model.split(":")[0]
            self._ok = any(m == self.model or m.split(":")[0] == base
                           for m in self._models)
            if not self._ok:
                log.warning("Ollama is up but %r is not pulled. Have: %s",
                            self.model, ", ".join(self._models) or "(none)")
        except Exception as e:
            log.info("Ollama not reachable at %s (%s) -- using rules only",
                     self.host, e.__class__.__name__)
            self._ok = False
        return bool(self._ok)

    def refresh(self):
        self._ok = None
        return self.probe()

    def available(self) -> bool:
        if self._ok is None:
            return self.probe()
        return bool(self._ok)

    def status(self) -> dict:
        return {"enabled": self.enabled, "host": self.host, "model": self.model,
                "available": bool(self._ok), "pulled": self._models}

    # ── extraction ─────────────────────────────────────────────────────────
    def extract(self, text: str, lang: str, slot: str,
                current: Fragment) -> Fragment | None:
        if not self.available():
            return None
        prompt = PROMPT.format(lang=LANG_NAME.get(lang, "English"),
                               slot=slot.replace("_", " ").lower(),
                               schema=SCHEMA, text=text)
        try:
            r = httpx.post(
                self.host + "/api/generate",
                json={"model": self.model, "prompt": prompt, "stream": False,
                      "format": "json",
                      "options": {"temperature": 0, "num_predict": 220}},
                timeout=self.timeout)
            r.raise_for_status()
            raw = r.json().get("response", "")
        except Exception as e:
            log.warning("LLM call failed (%s) -- falling back to rules",
                        e.__class__.__name__)
            self._ok = None          # re-probe next time
            return None
        return self._parse(raw)

    def reply(self, text: str, lang: str, system: str) -> str | None:
        """Free-form phrasing help. Never used to choose a recommendation."""
        if not self.available():
            return None
        try:
            r = httpx.post(
                self.host + "/api/generate",
                json={"model": self.model,
                      "prompt": system + "\n\n" + text, "stream": False,
                      "options": {"temperature": 0.4, "num_predict": 120}},
                timeout=self.timeout)
            r.raise_for_status()
            return (r.json().get("response") or "").strip() or None
        except Exception:
            return None

    # ── parsing ────────────────────────────────────────────────────────────
    @staticmethod
    def _parse(raw: str) -> Fragment | None:
        if not raw:
            return None
        s = raw.strip()
        if "{" in s:
            s = s[s.index("{"): s.rindex("}") + 1] if "}" in s else s
        s = re.sub(r"^```(?:json)?|```$", "", s.strip()).strip()
        try:
            d = json.loads(s)
        except Exception:
            return None
        if not isinstance(d, dict):
            return None

        def val(k):
            v = d.get(k)
            if v is None:
                return None
            v = str(v).strip()
            return None if v.lower() in ("", "null", "none", "n/a") else v

        f = Fragment()
        edu = val("education")
        if edu in ("class5", "class8", "class10", "class12",
                   "iti_diploma", "graduate"):
            f.edu = edu
        f.district = val("district")
        f.family_occupation = val("familyOccupation")
        f.current_livelihood = val("currentLivelihood")
        pref = val("preference")
        if pref in ("pref_self", "pref_wage"):
            f.preference = pref
        mob = val("mobility")
        if mob in ("local", "district", "state"):
            f.mobility = mob

        # "None" is meaningful for constraints -- it means "asked and answered".
        pc = d.get("physicalConstraints")
        if pc is not None and str(pc).strip().lower() not in ("", "null", "n/a"):
            f.physical_constraints = str(pc).strip()

        allowed = {"dairy", "cattle", "goat", "poultry", "farming",
                   "food", "machine", "textile", "construction", "tailor"}
        items = d.get("interests")
        if isinstance(items, list):
            for i in items:
                i = str(i).strip().lower()
                if i in allowed and i not in f.interests:
                    f.interests.append(i)
        return f


class NullLlm:
    """Used when Ollama is disabled, so callers never branch on None."""

    def available(self) -> bool:
        return False

    def extract(self, *a, **k):
        return None

    def reply(self, *a, **k):
        return None

    def status(self) -> dict:
        return {"enabled": False, "available": False,
                "note": "set JANDWAR_OLLAMA=1 to enable"}
