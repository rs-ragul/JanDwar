"""Singletons wired once at import. Kept tiny and free of framework types."""

from __future__ import annotations

from .adapters.llm import NullLlm, OllamaLlm
from .adapters.speech import Bhashini, NullSpeech
from .config import settings
from .core.engine import Engine, SessionStore

_store = SessionStore(ttl_seconds=settings.session_ttl)

_llm = (OllamaLlm(settings.ollama_host, settings.ollama_model,
                  settings.ollama_timeout, enabled=True)
        if settings.ollama_enabled else NullLlm())

_speech = (Bhashini(settings.bhashini_user_id, settings.bhashini_ulca_key,
                    settings.bhashini_inference_key,
                    enabled=settings.bhashini_enabled)
           if settings.bhashini_enabled else NullSpeech())

_engine = Engine(llm=_llm)


def get_store() -> SessionStore:
    return _store


def get_engine() -> Engine:
    return _engine


def get_llm():
    return _llm


def get_speech():
    return _speech
