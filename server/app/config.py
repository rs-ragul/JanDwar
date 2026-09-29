"""
Configuration, entirely from environment variables.

Nothing here is required. With an empty environment the service still boots and
runs a complete interview in six languages through the browser simulator and
through DTMF on a phone call -- that is the point of the deterministic core.
Each variable you add lights up one more capability.
"""

from __future__ import annotations

import os


def _b(name: str, default: bool = False) -> bool:
    v = os.environ.get(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


class Settings:
    def __init__(self):
        # ── server ──────────────────────────────────────────────────────
        self.host = os.environ.get("JANDWAR_HOST", "0.0.0.0")
        self.port = int(os.environ.get("PORT", os.environ.get("JANDWAR_PORT", "8000")))
        # Public https base, e.g. the ngrok/cloudflare URL. Telephony and
        # WhatsApp need absolute URLs to fetch audio from.
        # Render sets RENDER_EXTERNAL_URL automatically, so a Render deploy
        # needs no manual configuration at all.
        self.public_url = (os.environ.get("JANDWAR_PUBLIC_URL")
                           or os.environ.get("RENDER_EXTERNAL_URL")
                           or "").rstrip("/")
        self.default_lang = os.environ.get("JANDWAR_DEFAULT_LANG", "en")

        # ── Ollama (open-source LLM, free, local) ───────────────────────
        self.ollama_enabled = _b("JANDWAR_OLLAMA", False)
        self.ollama_host = os.environ.get("OLLAMA_HOST", "http://127.0.0.1:11434")
        self.ollama_model = os.environ.get("OLLAMA_MODEL", "qwen2.5:3b-instruct")
        self.ollama_timeout = float(os.environ.get("OLLAMA_TIMEOUT", "12"))

        # ── Bhashini (Govt of India speech stack, free tier) ────────────
        self.bhashini_enabled = _b("JANDWAR_BHASHINI", True)
        self.bhashini_user_id = os.environ.get("BHASHINI_USER_ID", "")
        self.bhashini_ulca_key = os.environ.get("BHASHINI_ULCA_KEY", "")
        self.bhashini_inference_key = os.environ.get("BHASHINI_INFERENCE_KEY", "")

        # ── WhatsApp Cloud API (Meta) ───────────────────────────────────
        self.wa_token = os.environ.get("WHATSAPP_TOKEN", "")
        self.wa_phone_id = os.environ.get("WHATSAPP_PHONE_NUMBER_ID", "")
        self.wa_verify_token = os.environ.get("WHATSAPP_VERIFY_TOKEN", "jandwar")

        # ── Telephony ───────────────────────────────────────────────────
        # "twilio" or "exotel" -- only changes the XML dialect we speak.
        self.ivr_provider = os.environ.get("IVR_PROVIDER", "twilio").lower()

        self.session_ttl = int(os.environ.get("JANDWAR_SESSION_TTL", "3600"))

    def summary(self) -> dict:
        return {
            "public_url": self.public_url or "(not set -- IVR audio will use relative URLs)",
            "default_lang": self.default_lang,
            "ollama": {"enabled": self.ollama_enabled, "model": self.ollama_model,
                       "host": self.ollama_host},
            "bhashini": {"enabled": self.bhashini_enabled,
                         "configured": bool(self.bhashini_user_id and
                                            self.bhashini_ulca_key)},
            "whatsapp": {"configured": bool(self.wa_token and self.wa_phone_id)},
            "ivr_provider": self.ivr_provider,
        }


settings = Settings()
