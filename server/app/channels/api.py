"""
Plain JSON API.

Used by the browser simulator, and equally usable by the Android app, a kiosk
front-end, or anything else. Same engine, same catalogue, same answers.
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from ..config import settings
from ..core import data
from ..core.recommend import match_roles
from ..deps import get_engine, get_llm, get_speech, get_store

router = APIRouter(prefix="/api", tags=["api"])


class StartReq(BaseModel):
    lang: str = "en"
    channel: str = "sim"


class SayReq(BaseModel):
    session: str
    text: str


def _view(session, reply: str) -> dict:
    filled, total = session.progress()
    return {
        "session": session.id,
        "lang": session.lang,
        "reply": reply,
        "slot": session.current,
        "done": session.done,
        "usedLlm": session.used_llm,
        "progress": {"filled": filled, "total": total},
        "profile": session.frag.to_dict(),
        "results": [_role_view(m) for m in session.results],
        "turns": [{"who": t.who, "text": t.text, "slot": t.slot}
                  for t in session.turns],
    }


def _role_view(m: dict) -> dict:
    r = m["role"]
    return {
        "qpCode": r.get("qp_code"),
        "jobRole": r.get("job_role"),
        "nsqfLevel": r.get("nsqf_level"),
        "sector": r.get("sector"),
        "ssc": r.get("ssc"),
        "duration": m.get("durationLabel"),
        "confidence": m.get("confidence"),
        "eligible": m.get("eligible"),
        "fundable": m.get("fundable"),
        "reason": m.get("reason"),
        "skillGap": m.get("skillGapNote"),
        "regionOpportunity": m.get("regionOpportunity"),
        "centre": (m.get("centre") or {}).get("name") if m.get("centre") else None,
        "factors": m.get("factors"),
    }


@router.get("/health")
async def health():
    return {
        "status": "ok",
        "data": data.summary(),
        "config": settings.summary(),
        "llm": get_llm().status(),
        "speech": get_speech().status(),
        "sessions": get_store().count(),
    }


@router.get("/languages")
async def languages():
    return [{"code": c, "name": n} for c, n in data.languages()]


@router.post("/session")
async def start(req: StartReq):
    engine, store = get_engine(), get_store()
    session, reply = engine.start(lang=req.lang, channel=req.channel)
    store.put(session)
    return _view(session, reply)


@router.post("/say")
async def say(req: SayReq):
    engine, store = get_engine(), get_store()
    session = store.get(req.session)
    if session is None:
        raise HTTPException(404, "session expired or unknown")
    reply = engine.answer(session, req.text)
    return _view(session, reply)


@router.get("/session/{sid}")
async def get_session(sid: str):
    session = get_store().get(sid)
    if session is None:
        raise HTTPException(404, "session expired or unknown")
    return _view(session, "")


class MatchReq(BaseModel):
    """Score a profile directly, with no interview -- useful for testing."""
    education: str | None = None
    district: str = ""
    familyOccupation: str = ""
    currentLivelihood: str = ""
    interests: list[str] = []
    skills: list[str] = []
    preference: str | None = None
    mobility: str | None = None
    physicalConstraints: str = ""
    localOpportunity: str = ""
    lang: str = "en"
    limit: int = 6


@router.post("/match")
async def match(req: MatchReq):
    p = req.model_dump()
    lang = p.pop("lang")
    limit = p.pop("limit")
    return {"results": [_role_view(m) for m in match_roles(p, lang, limit)]}


@router.get("/roles")
async def roles(q: str = "", sector: str = "", limit: int = 50):
    out = []
    ql = q.strip().lower()
    for r in data.job_roles():
        if sector and r.get("sector") != sector:
            continue
        if ql and ql not in (r.get("job_role") or "").lower():
            continue
        out.append(r)
        if len(out) >= limit:
            break
    return {"count": len(out), "roles": out}


@router.get("/centres")
async def centres():
    return {"count": len(data.centres()), "centres": data.centres()}
