"""
JanDwar multi-channel server.

One engine, one catalogue, one lexicon -- reached three ways:

    Android app  ──┐
    IVR phone call ├──> shared interview + NSQF recommender
    WhatsApp       ──┘

Boots with an entirely empty environment. Ollama and Bhashini are optional
upgrades, not requirements.

    uvicorn app.main:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import logging
import pathlib

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from .channels import api, ivr, whatsapp
from .config import settings
from .core import data
from .deps import get_llm

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s %(name)s  %(message)s",
    datefmt="%H:%M:%S")
log = logging.getLogger("jandwar")

STATIC = pathlib.Path(__file__).parent / "static"

app = FastAPI(title="JanDwar", version="2.4",
              description="Voice livelihood mapping and NSQF skilling "
                          "recommendations — app, IVR and WhatsApp on one engine.")

# The simulator is served from this origin; a kiosk front-end may not be.
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"],
                   allow_headers=["*"])

app.include_router(api.router)
app.include_router(ivr.router)
app.include_router(whatsapp.router)

if STATIC.is_dir():
    app.mount("/static", StaticFiles(directory=str(STATIC)), name="static")


@app.get("/", include_in_schema=False)
async def index():
    f = STATIC / "index.html"
    if f.exists():
        return FileResponse(str(f))
    return {"service": "JanDwar", "docs": "/docs", "health": "/api/health"}


@app.on_event("startup")
async def startup():
    d = data.summary()
    log.info("catalogue: %d job roles (%d PM-AJAY GIA fundable) across %d sectors",
             d["roles"], d["fundable"], len(d["sectors"]))
    log.info("coverage : %d districts, %d training centres, %d languages",
             d["districts"], d["centres"], len(d["languages"]))
    log.info("lexicon  : %d surface forms in %d categories",
             d["lexicon_forms"], d["lexicon_categories"])
    n = len(data.flow()["slot_order"])
    log.info("interview: %d slots, prompts extracted from InterviewFlow.kt", n)

    llm = get_llm()
    if llm.available():
        log.info("LLM      : Ollama ready (%s)", settings.ollama_model)
    else:
        log.info("LLM      : off — deterministic NLU only (this is fully functional)")

    from .deps import get_speech
    if get_speech().available():
        log.info("speech   : Bhashini configured")
    else:
        log.info("speech   : Bhashini not configured — IVR uses DTMF, "
                 "WhatsApp uses text")

    if not settings.public_url:
        log.info("note     : JANDWAR_PUBLIC_URL is unset. Set it to your public "
                 "https URL before connecting telephony or WhatsApp.")
