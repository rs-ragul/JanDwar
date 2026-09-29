"""
IVR channel -- the PS's "feature phone" deployment.

A caller on a ten-year-old Nokia dials a number and has the same interview.
No app, no smartphone, no data connection, no literacy requirement.

Two input modes, both supported at once on every question:
  * speech  -- the provider records the caller and posts the audio here;
                Bhashini transcribes it into the shared engine.
  * keypad  -- DTMF digits mapped to the same slot values, so the call still
                completes with no ASR at all. This is the reason the IVR works
                on day one with zero paid services configured.

We emit provider XML. Twilio speaks TwiML; Exotel's Voice Streaming/Applet XML
is close enough that one renderer with a dialect flag covers both, which
matters because Exotel is the realistic Indian deployment and Twilio is the
realistic free trial.
"""

from __future__ import annotations

import logging
from xml.sax.saxutils import escape

from fastapi import APIRouter, Form, Request, Response

from ..config import settings
from ..core import data
from ..core.engine import Engine, SessionStore, question
from ..deps import get_engine, get_speech, get_store
from ..adapters import telephony

log = logging.getLogger("jandwar.ivr")
router = APIRouter(prefix="/ivr", tags=["ivr"])

# Language menu offered at pickup. Kept to the six the catalogue supports.
LANG_MENU = [("1", "en"), ("2", "ta"), ("3", "hi"),
             ("4", "te"), ("5", "kn"), ("6", "ml")]

# DTMF fallbacks per slot: pressing a key is always a valid answer.
DTMF = {
    "EDUCATION": {"1": "I did not study", "2": "5th standard", "3": "8th standard",
                  "4": "10th standard", "5": "12th standard", "6": "ITI diploma",
                  "7": "degree"},
    "PREFERENCE": {"1": "I want my own business", "2": "I want a job"},
    "MOBILITY": {"1": "only my village", "2": "anywhere in my district",
                 "3": "anywhere in the state"},
    "CONSTRAINTS": {"1": "no", "2": "yes I have a health problem"},
}

DTMF_HINT = {
    "EDUCATION": {
        "en": " Or press 1 if you did not study, 2 for 5th, 3 for 8th, 4 for 10th, "
              "5 for 12th, 6 for I T I, 7 for degree.",
        "ta": " அல்லது படிக்கவில்லை என்றால் 1, ஐந்தாம் வகுப்புக்கு 2, எட்டாம் வகுப்புக்கு 3, "
              "பத்தாம் வகுப்புக்கு 4, பன்னிரண்டாம் வகுப்புக்கு 5, ஐ டி ஐ க்கு 6, பட்டப்படிப்புக்கு 7 அழுத்தவும்.",
        "hi": " या नहीं पढ़े तो 1, पाँचवीं के लिए 2, आठवीं के लिए 3, दसवीं के लिए 4, "
              "बारहवीं के लिए 5, आई टी आई के लिए 6, डिग्री के लिए 7 दबाएँ.",
    },
    "PREFERENCE": {
        "en": " Press 1 for your own work, 2 for a job.",
        "ta": " சொந்தத் தொழிலுக்கு 1, வேலைக்கு 2 அழுத்தவும்.",
        "hi": " अपना काम के लिए 1, नौकरी के लिए 2 दबाएँ.",
    },
    "MOBILITY": {
        "en": " Press 1 for your village only, 2 for your district, 3 for anywhere in the state.",
        "ta": " உங்கள் ஊர் மட்டும் என்றால் 1, மாவட்டம் என்றால் 2, மாநிலம் முழுவதும் என்றால் 3.",
        "hi": " सिर्फ गाँव के लिए 1, जिले के लिए 2, पूरे राज्य के लिए 3 दबाएँ.",
    },
    "CONSTRAINTS": {
        "en": " Press 1 for no, 2 for yes.",
        "ta": " இல்லை என்றால் 1, இருந்தால் 2 அழுத்தவும்.",
        "hi": " नहीं के लिए 1, हाँ के लिए 2 दबाएँ.",
    },
}


def _hint(slot: str, lang: str) -> str:
    m = DTMF_HINT.get(slot)
    if not m:
        return ""
    return m.get(lang) or m.get("en") or ""


# ── XML rendering ───────────────────────────────────────────────────────────

# Locale + an EXPLICIT voice per language.
#
# This pairing is not optional. Twilio's default voice is the Basic voice
# "man", which only speaks a handful of European languages. Sending
# <Say language="ta-IN"> with the default voice does not produce Tamil -- the
# verb errors and the caller hears silence before the call drops. Every Indic
# language has to name a Google voice that actually supports it.
#
# Overridable per language with e.g. IVR_VOICE_TA=Google.ta-IN-Wavenet-C.
VOICE = {
    "en": ("en-IN", "Google.en-IN-Standard-A"),
    "ta": ("ta-IN", "Google.ta-IN-Standard-C"),
    "hi": ("hi-IN", "Google.hi-IN-Standard-A"),
    "te": ("te-IN", "Google.te-IN-Standard-C"),
    "kn": ("kn-IN", "Google.kn-IN-Standard-C"),
    "ml": ("ml-IN", "Google.ml-IN-Standard-C"),
}
for _l in list(VOICE):
    _v = __import__("os").environ.get("IVR_VOICE_" + _l.upper())
    if _v:
        VOICE[_l] = (VOICE[_l][0], _v)


def _xml(body: str) -> Response:
    return Response(content='<?xml version="1.0" encoding="UTF-8"?>\n' + body,
                    media_type="application/xml")


def _say(text: str, lang: str) -> str:
    """
    Speak a line. If Bhashini is configured we hand the provider a URL to our
    own synthesised audio, because provider TTS for Tamil/Telugu/Kannada is
    poor to nonexistent. Otherwise fall back to provider <Say>.
    """
    speech = get_speech()
    if speech.available() and settings.public_url:
        from urllib.parse import quote
        url = "%s/ivr/audio?lang=%s&text=%s" % (
            settings.public_url, lang, quote(text[:900]))
        return "<Play>%s</Play>" % escape(url)
    loc, voice = VOICE[lang] if lang in VOICE else VOICE["en"]
    return '<Say language="%s" voice="%s">%s</Say>' % (loc, voice, escape(text))


NOINPUT = {
    "en": "Sorry, I did not catch that.",
    "ta": "மன்னிக்கவும், எனக்கு கேட்கவில்லை.",
    "hi": "माफ़ कीजिए, मुझे सुनाई नहीं दिया.",
    "te": "క్షమించండి, నాకు వినిపించలేదు.",
    "kn": "ಕ್ಷಮಿಸಿ, ನನಗೆ ಕೇಳಿಸಲಿಲ್ಲ.",
    "ml": "ക്ഷമിക്കണം, എനിക്ക് കേൾക്കാൻ കഴിഞ്ഞില്ല.",
}


def _gather(prompt: str, lang: str, action: str, num_digits: int | None = None,
            speech: bool = True) -> str:
    """One turn: speak, then accept speech and/or keypad."""
    inputs = "speech dtmf" if speech else "dtmf"
    attrs = ['input="%s"' % inputs, 'action="%s"' % escape(action),
             'method="POST"', 'timeout="6"', 'speechTimeout="auto"',
             'language="%s"' % (VOICE.get(lang) or VOICE["en"])[0]]
    if num_digits:
        attrs.append('numDigits="%d"' % num_digits)
    # Spoken only when the caller gives no input at all; Gather falls through.
    retry = _say(NOINPUT.get(lang, NOINPUT["en"]), lang)
    return ("<Response><Gather %s>%s</Gather>%s<Redirect method=\"POST\">%s</Redirect>"
            "</Response>"
            % (" ".join(attrs), _say(prompt, lang), retry, escape(action)))


# ── routes ──────────────────────────────────────────────────────────────────

@router.post("/voice")
@router.get("/voice")
async def voice(request: Request):
    """Entry point the telephony provider hits when the call connects."""
    menu = ("Welcome to Jan Dwar, the livelihood helper. "
            "For English press 1. தமிழுக்கு 2. हिंदी के लिए 3. "
            "తెలుగు కోసం 4. ಕನ್ನಡಕ್ಕೆ 5. മലയാളത്തിന് 6.")
    return _xml(_gather(menu, "en", "/ivr/lang", num_digits=1, speech=False))


@router.post("/lang")
async def choose_lang(Digits: str = Form(default=""),
                      CallSid: str = Form(default=""),
                      From: str = Form(default="")):
    lang = dict(LANG_MENU).get((Digits or "").strip(), settings.default_lang)
    engine = get_engine()
    store = get_store()
    alias = CallSid or From or "anon"
    store.drop_alias(alias)
    session, text = engine.start(lang=lang, channel="ivr")
    store.put(session, alias=alias)
    log.info("IVR call %s -> session %s lang=%s", alias, session.id, lang)
    return _xml(_gather(text + _hint(session.current, lang), lang,
                        "/ivr/turn?sid=" + session.id))


@router.post("/turn")
async def turn(request: Request,
               sid: str = "",
               SpeechResult: str = Form(default=""),
               Digits: str = Form(default=""),
               RecordingUrl: str = Form(default=""),
               CallSid: str = Form(default="")):
    store = get_store()
    engine = get_engine()
    session = store.get(sid) or store.by_alias(CallSid or "")
    if session is None:
        return _xml("<Response>%s<Hangup/></Response>"
                    % _say("Sorry, your session expired. Please call again.", "en"))

    if session.done:
        return _xml("<Response>%s<Hangup/></Response>"
                    % _say(_sms_note(session, session.lang), session.lang))

    lang = session.lang
    slot = session.current
    said = (SpeechResult or "").strip()

    # Keypad beats ASR: it is unambiguous.
    digits = (Digits or "").strip()
    if digits and slot in DTMF:
        said = DTMF[slot].get(digits, said)
    elif digits and not said:
        said = digits

    # Provider recorded audio instead of transcribing -> use Bhashini.
    if not said and RecordingUrl:
        said = _transcribe_url(RecordingUrl, lang) or ""

    reply = engine.answer(session, said)

    if session.done:
        return _xml("<Response>%s%s<Hangup/></Response>"
                    % (_say(reply, lang),
                       _say(_sms_note(session, lang), lang)))

    return _xml(_gather(reply + _hint(session.current, lang), lang,
                        "/ivr/turn?sid=" + session.id))


def _sms_note(session, lang: str) -> str:
    return {"en": "We will also send you a message with the details. Thank you.",
            "ta": "விவரங்களை செய்தியாகவும் அனுப்புவோம். நன்றி.",
            "hi": "हम विवरण संदेश से भी भेजेंगे। धन्यवाद।"}.get(lang,
           "We will also send you a message with the details. Thank you.")


def _transcribe_url(url: str, lang: str) -> str | None:
    import httpx
    speech = get_speech()
    if not speech.available():
        return None
    try:
        audio = httpx.get(url, timeout=25.0).content
    except Exception as e:
        log.warning("could not fetch recording: %s", e)
        return None
    return speech.transcribe(audio, lang)


@router.get("/audio")
async def audio(text: str = "", lang: str = "en", fmt: str = "pcm16"):
    """
    Serves Bhashini-synthesised speech so <Play> can fetch it.

    Bhashini hands back 32-bit IEEE-float WAV. Twilio's <Play> accepts MP3,
    16-bit PCM WAV and mu-law only -- given float32 it plays static or
    rejects the media outright, and the caller hears nothing while curl still
    reports a cheerful 200. So everything is rewritten to 8 kHz mono 16-bit
    PCM (or mu-law with ?fmt=ulaw) before it leaves this process.
    """
    speech = get_speech()
    wav = speech.synthesize(text, lang) if speech.available() else None
    if not wav:
        return Response(status_code=404)

    media = "audio/wav"
    try:
        before = telephony.describe(wav)
        wav = telephony.to_telephony_wav(wav, "ulaw" if fmt == "ulaw" else "pcm16")
        log.debug("audio %s -> %s", before, telephony.describe(wav))
    except telephony.NotWav as e:
        # Not a WAV we understand (an MP3, say). Passing it through is the
        # right call -- Twilio plays MP3 natively -- but say so in the log,
        # because silently shipping a format the provider may reject is the
        # exact failure this function exists to prevent.
        log.warning("audio left unconverted (%s); provider may reject it", e)
    return Response(content=wav, media_type=media,
                    headers={"Cache-Control": "public, max-age=86400"})
