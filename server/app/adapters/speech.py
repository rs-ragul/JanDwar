"""
Speech for the server-side channels, via Bhashini.

The Android app does not need this: Android gives us on-device speech
recognition and text-to-speech for free, offline, which is exactly right for a
low-connectivity handset. A phone call and a WhatsApp voice note arrive at the
server as *audio*, so the server needs its own ASR and TTS -- and it is online
by definition, so the objection that killed Bhashini on the handset does not
apply here.

Bhashini is the Government of India's national language stack (Digital India
Bhashini Mission). It is the right choice for this project specifically:

  * it covers Tamil, Hindi, Telugu, Kannada and Malayalam ASR + TTS properly,
    which Western speech APIs do poorly for rural accents;
  * it is free to use for this scale after registering on the ULCA portal;
  * for a PM-AJAY submission, using the Government's own language
    infrastructure is a point in the design's favour rather than a dependency
    on a foreign vendor.

Everything degrades: no key means IVR still works through DTMF keypad input
and WhatsApp still works through typed text. Nothing here is load-bearing for
the interview logic.
"""

from __future__ import annotations

import base64
import logging

import httpx

log = logging.getLogger("jandwar.speech")

# Bhashini service ids are discovered per-language through the pipeline config
# endpoint; these are the task types we ask for.
ASR = "asr"
TTS = "tts"
TRANSLATION = "translation"

BCP47 = {"en": "en", "ta": "ta", "hi": "hi", "te": "te", "kn": "kn", "ml": "ml"}


class Bhashini:
    """
    Thin client over the ULCA / Bhashini 'compute' API.

    Two steps, as the platform requires:
      1. POST the pipeline config to find the callback URL + service id for the
         (task, language) pair, using the user id + ULCA API key;
      2. POST the actual audio/text to that callback with the returned
         authorization header.

    Config responses are cached per (task, lang) because step 1 is slow and its
    answer is stable.
    """

    CONFIG_URL = ("https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/"
                  "getModelsPipeline")
    PIPELINE_ID = "64392f96daac500b55c543cd"   # MeitY / AI4Bharat public pipeline

    def __init__(self, user_id: str = "", ulca_key: str = "",
                 inference_key: str = "", enabled: bool = True,
                 timeout: float = 30.0):
        self.user_id = user_id
        self.ulca_key = ulca_key
        self.inference_key = inference_key
        self.enabled = bool(enabled and user_id and ulca_key)
        self.timeout = timeout
        self._cache: dict[tuple[str, str], dict] = {}
        self._last_error: str | None = None

    def available(self) -> bool:
        return self.enabled

    def status(self) -> dict:
        return {
            "enabled": self.enabled,
            "configured": bool(self.user_id and self.ulca_key),
            "cached_pipelines": len(self._cache),
            "last_error": self._last_error,
            "note": ("set BHASHINI_USER_ID, BHASHINI_ULCA_KEY and "
                     "BHASHINI_INFERENCE_KEY to enable"
                     if not self.enabled else None),
        }

    # ── pipeline discovery ─────────────────────────────────────────────────
    def _pipeline(self, task: str, lang: str) -> dict | None:
        key = (task, lang)
        if key in self._cache:
            return self._cache[key]
        if not self.enabled:
            return None

        cfg = {"source": BCP47.get(lang, "en")}
        if task == TTS:
            cfg = {"source": BCP47.get(lang, "en")}
        body = {
            "pipelineTasks": [{"taskType": task, "config": {"language": cfg}}],
            "pipelineRequestConfig": {"pipelineId": self.PIPELINE_ID},
        }
        try:
            r = httpx.post(self.CONFIG_URL, json=body, timeout=self.timeout,
                           headers={"userID": self.user_id,
                                    "ulcaApiKey": self.ulca_key})
            r.raise_for_status()
            d = r.json()
            tasks = d.get("pipelineResponseConfig") or []
            if not tasks or not tasks[0].get("config"):
                self._last_error = "no service for %s/%s" % (task, lang)
                return None
            svc = tasks[0]["config"][0]
            inf = d.get("pipelineInferenceAPIEndPoint", {})
            out = {
                "serviceId": svc.get("serviceId"),
                "callbackUrl": inf.get("callbackUrl"),
                "authKey": (inf.get("inferenceApiKey") or {}).get("name"),
                "authValue": (inf.get("inferenceApiKey") or {}).get("value")
                             or self.inference_key,
            }
            if not out["callbackUrl"]:
                self._last_error = "pipeline gave no callback url"
                return None
            self._cache[key] = out
            return out
        except Exception as e:
            self._last_error = "%s: %s" % (e.__class__.__name__, e)
            log.warning("Bhashini config failed for %s/%s: %s", task, lang, e)
            return None

    def _compute(self, pipe: dict, task: str, cfg: dict, inp: dict) -> dict | None:
        body = {
            "pipelineTasks": [{"taskType": task,
                               "config": dict(cfg, serviceId=pipe["serviceId"])}],
            "inputData": inp,
        }
        headers = {}
        if pipe.get("authKey"):
            headers[pipe["authKey"]] = pipe.get("authValue") or ""
        try:
            r = httpx.post(pipe["callbackUrl"], json=body, headers=headers,
                           timeout=self.timeout)
            r.raise_for_status()
            return r.json()
        except Exception as e:
            self._last_error = "%s: %s" % (e.__class__.__name__, e)
            log.warning("Bhashini %s failed: %s", task, e)
            return None

    # ── public API ─────────────────────────────────────────────────────────
    def transcribe(self, audio_bytes: bytes, lang: str) -> str | None:
        """Speech -> text. Returns None if unavailable; caller falls back."""
        pipe = self._pipeline(ASR, lang)
        if not pipe:
            return None
        out = self._compute(
            pipe, ASR,
            {"language": {"sourceLanguage": BCP47.get(lang, "en")},
             "audioFormat": "wav", "samplingRate": 16000},
            {"audio": [{"audioContent": base64.b64encode(audio_bytes).decode()}]})
        if not out:
            return None
        try:
            return out["pipelineResponse"][0]["output"][0]["source"].strip()
        except Exception:
            self._last_error = "unexpected ASR response shape"
            return None

    def synthesize(self, text: str, lang: str, gender: str = "female") -> bytes | None:
        """Text -> WAV bytes. Returns None if unavailable."""
        pipe = self._pipeline(TTS, lang)
        if not pipe:
            return None
        out = self._compute(
            pipe, TTS,
            {"language": {"sourceLanguage": BCP47.get(lang, "en")},
             "gender": gender, "samplingRate": 8000},
            {"input": [{"source": text}]})
        if not out:
            return None
        try:
            b64 = out["pipelineResponse"][0]["audio"][0]["audioContent"]
            return base64.b64decode(b64)
        except Exception:
            self._last_error = "unexpected TTS response shape"
            return None


class NullSpeech:
    def available(self) -> bool:
        return False

    def transcribe(self, *a, **k):
        return None

    def synthesize(self, *a, **k):
        return None

    def status(self) -> dict:
        return {"enabled": False, "available": False,
                "note": "Bhashini not configured; IVR uses DTMF, WhatsApp uses text"}
