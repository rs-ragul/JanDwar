"""
WhatsApp channel -- the PS's "WhatsApp voice-note interface".

Meta's WhatsApp Cloud API. The user sends a voice note in Tamil; Bhashini
transcribes it; the shared engine answers; Bhashini synthesises the reply and
we upload it back as a voice note, with the text alongside so a literate user
or a poor-audio situation still works.

Falls back cleanly: with no Bhashini key the thread runs on typed text, and
with no WhatsApp token at all the webhook still parses and logs, so the
integration can be demonstrated without a Meta account.
"""

from __future__ import annotations

import io
import logging

import httpx
from fastapi import APIRouter, Request, Response

from ..config import settings
from ..deps import get_engine, get_speech, get_store

log = logging.getLogger("jandwar.whatsapp")
router = APIRouter(prefix="/whatsapp", tags=["whatsapp"])

#: When no Meta token is configured we cannot actually deliver anything, so
#: outbound messages are recorded here instead. This is what lets the browser
#: simulator drive the *real* webhook and engine with no Meta account -- the
#: only thing being faked is the final hop to Meta's servers.
OUTBOX: dict[str, list[dict]] = {}


def _record(to: str, kind: str, body: str):
    OUTBOX.setdefault(to, []).append({"type": kind, "body": body})

GRAPH = "https://graph.facebook.com/v21.0"

GREET_HINT = {
    "en": "Send a voice note or type your answer.",
    "ta": "குரல் செய்தி அனுப்பவும் அல்லது பதிலை தட்டச்சு செய்யவும்.",
    "hi": "वॉइस नोट भेजें या अपना उत्तर टाइप करें.",
}


@router.get("/webhook")
async def verify(request: Request):
    """Meta's one-time webhook verification handshake."""
    q = request.query_params
    if (q.get("hub.mode") == "subscribe"
            and q.get("hub.verify_token") == settings.wa_verify_token):
        return Response(content=q.get("hub.challenge", ""), media_type="text/plain")
    return Response(status_code=403)


@router.post("/webhook")
async def incoming(request: Request):
    body = await request.json()
    try:
        for entry in body.get("entry", []):
            for change in entry.get("changes", []):
                value = change.get("value", {})
                for msg in value.get("messages", []):
                    await _handle(msg)
    except Exception as e:                       # never 500 at Meta
        log.exception("whatsapp handler failed: %s", e)
    return {"status": "ok"}


async def _handle(msg: dict):
    frm = msg.get("from")
    if not frm:
        return
    store, engine, speech = get_store(), get_engine(), get_speech()

    session = store.by_alias("wa:" + frm)
    text = ""
    mtype = msg.get("type")

    if mtype == "text":
        text = (msg.get("text") or {}).get("body", "").strip()
    elif mtype in ("audio", "voice"):
        media_id = (msg.get(mtype) or {}).get("id")
        audio = _download_media(media_id)
        if audio and speech.available():
            lang = session.lang if session else settings.default_lang
            text = speech.transcribe(audio, lang) or ""
        if not text:
            _send_text(frm, "I could not hear that clearly. Please type your "
                            "answer or send the voice note again.")
            return
    elif mtype == "interactive":
        inter = msg.get("interactive") or {}
        text = ((inter.get("button_reply") or {}).get("id")
                or (inter.get("list_reply") or {}).get("id") or "")
    else:
        _send_text(frm, "Please send a voice note or text.")
        return

    # New thread, or an explicit restart.
    if session is None or text.strip().lower() in ("hi", "hello", "start", "restart"):
        lang = _guess_lang(text)
        session, reply = engine.start(lang=lang, channel="whatsapp")
        store.put(session, alias="wa:" + frm)
        _reply(frm, reply, session.lang)
        return

    reply = engine.answer(session, text)
    _reply(frm, reply, session.lang)

    if session.done:
        _send_text(frm, _summary(session))
        store.drop_alias("wa:" + frm)


def _guess_lang(text: str) -> str:
    """Script detection is enough to pick the interview language."""
    for ch in text or "":
        o = ord(ch)
        if 0x0B80 <= o <= 0x0BFF:
            return "ta"
        if 0x0900 <= o <= 0x097F:
            return "hi"
        if 0x0C00 <= o <= 0x0C7F:
            return "te"
        if 0x0C80 <= o <= 0x0CFF:
            return "kn"
        if 0x0D00 <= o <= 0x0D7F:
            return "ml"
    return settings.default_lang


def _summary(session) -> str:
    lines = []
    for i, m in enumerate(session.results[:5], 1):
        r = m["role"]
        lines.append("%d. %s\n   NSQF %s · %s · %d%% match\n   %s"
                     % (i, r.get("job_role"), r.get("nsqf_level"),
                        m["durationLabel"], m["confidence"], m["skillGapNote"]))
        if m.get("centre"):
            lines.append("   Centre: %s" % m["centre"].get("name"))
    return "\n".join(lines) if lines else "No matching courses found."


# ── Cloud API plumbing ──────────────────────────────────────────────────────

def _configured() -> bool:
    return bool(settings.wa_token and settings.wa_phone_id)


def _download_media(media_id: str) -> bytes | None:
    if not (_configured() and media_id):
        return None
    h = {"Authorization": "Bearer " + settings.wa_token}
    try:
        meta = httpx.get("%s/%s" % (GRAPH, media_id), headers=h, timeout=20).json()
        url = meta.get("url")
        if not url:
            return None
        return httpx.get(url, headers=h, timeout=30).content
    except Exception as e:
        log.warning("media download failed: %s", e)
        return None


def _reply(to: str, text: str, lang: str):
    """Voice note first when we can make one, text always."""
    speech = get_speech()
    if speech.available():
        wav = speech.synthesize(text, lang)
        if wav:
            _send_audio(to, wav)
    _send_text(to, text)


def _send_text(to: str, body: str):
    if not _configured():
        log.info("[whatsapp dry-run] -> %s: %s", to, body[:160])
        _record(to, "text", body)
        return
    try:
        httpx.post("%s/%s/messages" % (GRAPH, settings.wa_phone_id),
                   headers={"Authorization": "Bearer " + settings.wa_token},
                   json={"messaging_product": "whatsapp", "to": to,
                         "type": "text", "text": {"body": body[:4096]}},
                   timeout=20)
    except Exception as e:
        log.warning("send text failed: %s", e)


def _send_audio(to: str, wav: bytes):
    if not _configured():
        log.info("[whatsapp dry-run] -> %s: <%d bytes of audio>", to, len(wav))
        _record(to, "audio", "%d bytes of synthesised speech" % len(wav))
        return
    try:
        files = {"file": ("reply.wav", io.BytesIO(wav), "audio/wav")}
        up = httpx.post("%s/%s/media" % (GRAPH, settings.wa_phone_id),
                        headers={"Authorization": "Bearer " + settings.wa_token},
                        data={"messaging_product": "whatsapp", "type": "audio/wav"},
                        files=files, timeout=40).json()
        mid = up.get("id")
        if not mid:
            return
        httpx.post("%s/%s/messages" % (GRAPH, settings.wa_phone_id),
                   headers={"Authorization": "Bearer " + settings.wa_token},
                   json={"messaging_product": "whatsapp", "to": to,
                         "type": "audio", "audio": {"id": mid}}, timeout=20)
    except Exception as e:
        log.warning("send audio failed: %s", e)


@router.get("/outbox/{phone}")
async def outbox(phone: str, clear: bool = True):
    """
    Dev aid: what we *would* have sent to `phone`. Only ever populated while
    the Meta credentials are absent; with a real token the messages go to
    WhatsApp and this stays empty.
    """
    msgs = OUTBOX.get(phone, [])
    if clear:
        OUTBOX[phone] = []
    return {"configured": _configured(), "messages": msgs}
