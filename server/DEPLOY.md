# JanDwar server — how to run it, and how to demo it

This is the multi-channel half of JanDwar. The Android app covers the
smartphone user; this server covers the two channels the problem statement
also asks for — **a plain phone call (IVR)** and **WhatsApp voice notes** — so
someone with a ₹900 feature phone and no internet gets the same interview and
the same NSQF recommendations.

It is one engine. The app, the phone call and the WhatsApp thread all run the
same 8-question interview, the same lexicon and the same recommender, reading
the same `app/src/main/assets/*.json`. There is no second copy of the logic to
drift out of sync.

---

## 0. The short version

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Open <http://localhost:8000>. You get a console with three tabs — App/Kiosk,
IVR call, WhatsApp — and a **Run a scripted demo** button.

**No API keys. No accounts. No payment. It works immediately.** Everything
below is optional polish on top of a system that already runs.

---

## 1. Does this cost money?

**No.** Not for the demo, not for the evaluation, not for SIH.

| Piece | Cost | Why |
|---|---|---|
| The server itself | ₹0 | Python + FastAPI, runs on your laptop |
| The recommender, NLU, lexicon | ₹0 | Deterministic code, no model calls |
| Ollama (optional LLM) | ₹0 | Open-source, runs locally, no API |
| Bhashini (optional speech) | ₹0 | Govt of India, free on registration |
| Public URL for the demo | ₹0 | Cloudflare Tunnel / ngrok free tier |
| WhatsApp Cloud API | ₹0 | Meta's free test number + 1,000 free conversations/month |
| Twilio trial (phone number) | ₹0 | Free trial credit covers demo calls |
| Permanent hosting | ₹0 | Render / Railway / Fly.io free tier |

The **only** thing that ever costs money is a real inbound Indian phone number
in actual production (an Exotel number is roughly ₹500–1,500/month). You do not
need one to build, demo, or win. Say this in the presentation — "₹0 to run,
one phone number away from production" is a strong line.

---

## 2. Where does the server run?

For SIH, three stages, in this order:

### Stage A — your laptop (development, and honestly good enough for the demo)

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

The judges' laptop and yours are on the same wifi at the venue; the browser
console at `http://<your-laptop-ip>:8000` is a complete demo. If the wifi is
bad, everything still runs on localhost.

### Stage B — laptop + a tunnel (needed only for a *real* phone call / WhatsApp)

Twilio and Meta have to be able to reach your laptop from the internet. A
tunnel gives you a public `https://` URL pointing at your local server, free,
in one command:

```bash
# one-time
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared

# every time you demo
./cloudflared tunnel --url http://localhost:8000
```

It prints something like `https://brave-forest-1234.trycloudflare.com`.
Restart the server with that URL so it can hand out absolute audio links:

```bash
export JANDWAR_PUBLIC_URL=https://brave-forest-1234.trycloudflare.com
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

(`ngrok http 8000` works identically if you prefer it.)

### Stage C — a free host (so the URL survives your laptop closing)

Render.com, free tier, is the least painful:

1. Push this repo to GitHub.
2. Render → New → Web Service → connect the repo.
3. Root directory `server`, runtime Python.
4. Build command: `pip install -r requirements.txt`
5. Start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
6. Add environment variables from §4 as needed.

You get a permanent `https://jandwar.onrender.com`. The free instance sleeps
after 15 minutes idle and takes ~40 s to wake, which is fine for a judged demo
as long as you hit it once beforehand. Railway and Fly.io are equivalent.

> The Android app does **not** need any of this. It is fully offline. The
> server is purely the IVR + WhatsApp + kiosk backend.

---

## 3. Why keep the deterministic engine if we have a server? Why not just Ollama?

This is the right question to ask, and the answer is **both, with a firm line
between them.**

Use Ollama for **language**. Use the deterministic engine for **the
recommendation**. Concretely:

```
caller says something rambling in Tamil
        │
        ├─► rule-based NLU  ── always runs, ~1 ms ────┐
        │                                             ├─► merged profile
        └─► Ollama (if up) ── fills what rules missed ─┘
                                                       │
                                                       ▼
                                    deterministic NSQF recommender
                                    (scores 452 real QP-coded roles)
```

Three reasons the scorer must not be an LLM:

**1. It cannot be allowed to invent a course.** This feeds a government funding
decision under PM-AJAY GIA. Every recommendation carries a real QP code
(`AGR/Q4102`), a real NSQF level and a real notional-hours figure from the
catalogue. An LLM will, eventually, produce a plausible QP code that does not
exist. A candidate who walks into a training centre with a hallucinated course
code is a person we have actively harmed. The recommender only ever *selects
from* the 452 rows; it cannot generate a row.

**2. Latency.** On a phone call, silence over ~1.5 s reads as a dropped line.
A 3B model on a CPU laptop takes 4–15 s for a paragraph. The rule engine
answers in about a millisecond, so the call stays conversational. The LLM is
called only where a slower path is tolerable, and behind a hard timeout.

**3. It has to work when nothing else does.** The whole point of the IVR
channel is reaching places where infrastructure is thin. Ollama down, laptop
swapped, model not pulled — the interview must still complete. Today it does:
with an empty environment the server runs the full 8-slot interview in six
languages and produces real recommendations. You just watched it do that.

So Ollama earns its place at exactly one job: turning *"padichadhu paththaam
vaguppu, ippo vela illa, amma maadu valarkkuraanga"* into
`{education: class10, current: unemployed, family: dairy}` when the rules only
caught two of the three. That is a genuine language problem and the LLM is
genuinely better at it.

Turn it on:

```bash
curl -fsSL https://ollama.com/install.sh | sh
ollama pull qwen2.5:3b-instruct        # ~2 GB, good Indic coverage
export JANDWAR_OLLAMA=1
```

`qwen2.5:3b-instruct` is the recommended default — it handles Tamil, Hindi and
Telugu meaningfully better than Llama 3.2 3B at the same size. If you have
16 GB+ of RAM, `qwen2.5:7b-instruct` is noticeably better again. The server
probes `/api/tags` on startup, logs whether it found the model, and silently
carries on without it if not.

---

## 4. Bhashini — yes, use it here

You mentioned you already have a Bhashini account. **Use it for this server.**

Earlier I argued against Bhashini *inside the Android app*, and that still
holds for the app: Android gives us on-device speech recognition and TTS for
free and offline, and a network round-trip per utterance is exactly wrong for a
rural handset. But this server is online by definition, and a phone call
arrives here as raw audio with no on-device recogniser anywhere in sight. So
the objection evaporates and Bhashini's advantages take over: it is the
Government's own stack, it is free, and its Tamil/Telugu/Kannada/Malayalam
models beat the Western speech APIs on rural accents by a wide margin. For a
PM-AJAY submission, running on Bhashini is also a point in the design's favour.

From your ULCA dashboard (<https://bhashini.gov.in> → ULCA → your profile):

```bash
export BHASHINI_USER_ID=...          # "User ID"
export BHASHINI_ULCA_KEY=...         # "ULCA API Key"
export BHASHINI_INFERENCE_KEY=...    # "Inference API Key", if shown
```

That is all. On the next start the log says `speech: Bhashini configured`, IVR
prompts switch from provider `<Say>` to Bhashini-synthesised `<Play>` audio,
and WhatsApp voice notes start being transcribed and answered with voice notes.

**Do not put these in a file in the repo, and do not paste them to me.** Set
them in your shell, or in Render's environment-variables panel.

### Every environment variable

| Variable | Default | Effect if unset |
|---|---|---|
| `PORT` | `8000` | — |
| `JANDWAR_PUBLIC_URL` | *(empty)* | IVR uses provider TTS instead of Bhashini audio |
| `JANDWAR_DEFAULT_LANG` | `en` | — |
| `JANDWAR_OLLAMA` | `0` | Rule-based NLU only (fully functional) |
| `OLLAMA_MODEL` | `qwen2.5:3b-instruct` | — |
| `OLLAMA_HOST` | `http://127.0.0.1:11434` | — |
| `BHASHINI_USER_ID` / `BHASHINI_ULCA_KEY` / `BHASHINI_INFERENCE_KEY` | *(empty)* | IVR runs on DTMF, WhatsApp on text |
| `WHATSAPP_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID` | *(empty)* | WhatsApp replies go to a dry-run outbox |
| `WHATSAPP_VERIFY_TOKEN` | `jandwar` | — |
| `IVR_PROVIDER` | `twilio` | `twilio` or `exotel` |
| `JANDWAR_ASSETS` | `../app/src/main/assets` | Where the catalogue is read from |

---

## 5. Connecting a real phone call

Free path, ~10 minutes:

1. Sign up at twilio.com — the trial gives you credit and a number.
2. Console → Phone Numbers → your number → **Voice → A call comes in**:
   - Webhook, **POST**, `https://<your-public-url>/ivr/voice`
3. Call the number.

You will hear the language menu, press 2 for Tamil, and be interviewed. Every
question accepts **either** speech **or** a keypad digit, and the DTMF mapping
is spoken aloud, so the call completes even with no speech recognition at all
— which is the whole reason it works on day one for free.

For production in India, Exotel is the realistic provider (Indian numbers,
Indian compliance, better rural call quality). Set `IVR_PROVIDER=exotel`; the
XML dialect differs only slightly and the same endpoints serve it.

## 6. Connecting real WhatsApp

1. developers.facebook.com → create an app → add **WhatsApp**.
2. It gives you a free test number and a temporary token. Copy the token and
   the **Phone number ID**.
3. Configuration → Webhook → Edit:
   - Callback URL `https://<your-public-url>/whatsapp/webhook`
   - Verify token: whatever you set in `WHATSAPP_VERIFY_TOKEN` (default `jandwar`)
   - Subscribe to the **messages** field.
4. Add your own number to the test recipients and message it.

Meta's free tier covers ~1,000 conversations a month, far more than a demo
needs. Without the token, the browser console's WhatsApp tab still drives the
real webhook and shows you the real replies from a dry-run outbox — which is
enough to demonstrate the channel with no Meta account at all.

---

## 7. What to actually show the judges

Open `http://localhost:8000` and, in this order:

1. **App/Kiosk tab → Run a scripted demo.** Eight questions, a profile filling
   up on the right, six NSQF-coded recommendations with GIA-fundable badges.
2. **Switch the language to தமிழ் and do it again.** Same engine, Tamil
   throughout, Tamil skill-gap notes.
3. **IVR tab → Start.** A real phone-call simulation — and point at the TwiML
   panel underneath: *that is the actual XML we send Twilio, this is not a
   mock-up.* Answer a couple of questions with the keypad to show it works
   without speech recognition.
4. **WhatsApp tab → Run a scripted demo.** Point out it is posting a genuine
   Meta webhook payload.
5. Then open the **Android app** on a phone in aeroplane mode and run the same
   interview offline.

The line that lands: *one interview, four ways in — smartphone, feature phone,
WhatsApp, kiosk — and the smartphone one works with the SIM removed.*

---

## 8. Checking it is healthy

```bash
curl localhost:8000/api/health | python3 -m json.tool
```

Reports the catalogue size (452 roles / 303 fundable / 38 districts / 20
centres), whether Ollama and Bhashini are live, and the active session count.
Interactive API docs are at `/docs`.

## 9. Layout

```
server/
  app/
    main.py            FastAPI wiring, startup banner
    config.py          every env var, all optional
    deps.py            singletons
    core/
      data.py          loads the app's own JSON assets — single source of truth
      lexicon.py       1,480 surface forms, script-aware matching
      nlu.py           slot extraction, 8 slots
      recommend.py     the NSQF scorer, ported 1:1 from AppRepository.kt
      engine.py        the interview state machine, channel-agnostic
    adapters/
      llm.py           Ollama, optional, language only — never picks a course
      speech.py        Bhashini ASR/TTS, optional
    channels/
      api.py           JSON API — simulator, kiosk, app
      ivr.py           Twilio/Exotel webhooks, speech + DTMF
      whatsapp.py      Meta Cloud API webhook
    static/index.html  the three-channel console
  data/flow.json       questions extracted from InterviewFlow.kt
  requirements.txt
```
