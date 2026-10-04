# JanDwar — Complete Project & Application Documentation

**Smart India Hackathon 2026 · Problem Statement 26097**
*AI-Driven Voice Assistant for Livelihood Mapping and NSQF-Aligned Skilling Recommendations for SC Communities under the GIA component of PM-AJAY*

| | |
|---|---|
| Ministry | Social Justice & Empowerment (MoSJE) |
| PS Category | Software |
| Android package | `in.jandwar.app` |
| App version | v2.9 (versionCode 11) |
| Server version | FastAPI app `title="JanDwar", version="2.4"` |
| Repository | `Thozhil-Thunai` (directory name is historical — the product is **JanDwar**) |
| Document date | 2026-10-01 |

> **Note on naming.** The git repository is still called `Thozhil-Thunai`, which was the
> original working title. The product, the Android package, the Gradle root project and
> all user-facing text are **JanDwar**. `docs_JanDwar_Proposal.md` in the repo root is an
> early proposal written under the old name and against an older, smaller data set
> (516 roles / 20 centres / 1 state); it is kept for history and is **superseded by this
> document**.

---

## How to read this

This document is written so that someone who has never seen the repository can rebuild,
run, demo, extend and defend the whole system. It is organised in five parts:

| Part | Covers |
|---|---|
| **I — The project** | What it is, the three channels, the repository, the whole-system architecture, the single source of truth |
| **II — The Android app** | Build, layers, the nine slots, the NLU engine, the conversation engine, voice, UI, configuration |
| **III — The server** | FastAPI service, shared core, the JSON API, IVR, WhatsApp, adapters, deployment |
| **IV — The data** | All eight assets, schemas, counts, provenance, GIA funding rules, generated files |
| **V — Engineering** | Tooling, the four test suites, the build pipeline, known gaps, glossary |

Every number in this document was read out of the source or produced by running the
code on 2026-10-01, not recalled from memory.

### Table of contents

**Part I — The project**
1. [What the project is](#1-what-the-project-is)
2. [Three channels, one engine](#2-three-channels-one-engine)
3. [Repository map](#3-repository-map)
4. [Whole-system architecture](#4-whole-system-architecture)
5. [One source of truth](#5-one-source-of-truth)

**Part II — The Android app**
6. [Build and run the app](#6-build-and-run-the-app)
7. [App architecture](#7-app-architecture)
8. [The interview model — nine slots](#8-the-interview-model--nine-slots)
9. [The on-device NLU engine](#9-the-on-device-nlu-engine)
10. [The conversation engine](#10-the-conversation-engine)
11. [Voice pipeline](#11-voice-pipeline)
12. [UI layer](#12-ui-layer)
13. [App configuration and the optional cloud adapter](#13-app-configuration-and-the-optional-cloud-adapter)

**Part III — The server**
14. [Server overview](#14-server-overview)
15. [Server core](#15-server-core)
16. [The recommendation engine](#16-the-recommendation-engine)
17. [Channel: JSON API](#17-channel-json-api)
18. [Channel: IVR](#18-channel-ivr)
19. [Channel: WhatsApp](#19-channel-whatsapp)
20. [Adapters](#20-adapters)
21. [Deployment](#21-deployment)

**Part IV — The data**
22. [The data pack](#22-the-data-pack)
23. [PM-AJAY GIA funding rules](#23-pm-ajay-gia-funding-rules)

**Part V — Engineering**
24. [Tooling and generated files](#24-tooling-and-generated-files)
25. [Test suites](#25-test-suites)
26. [Known limitations](#26-known-limitations)
27. [Glossary](#27-glossary)

---

# Part I — The project

## 1. What the project is

A rural beneficiary of PM-AJAY's Grant-in-Aid (GIA) component must be **profiled** before
any skilling money can be spent on them. Today that profiling is a paper form, in English,
filled in by a facilitator, taking 25–40 minutes per person. JanDwar replaces it with a
**two-minute spoken interview** that the beneficiary can complete themselves — in the app,
on a phone call, or by WhatsApp voice note — and that ends with a **real NSQF job role, its
QP code, the GIA programme type that funds it, and a named training centre with a phone
number**.

### Problem statement → implementation

| PS requirement | Implemented as | Where |
|---|---|---|
| Multilingual / dialect voice conversation, **not** form-filling | 9-slot spoken interview, 6 languages, rotating phrasings, acknowledgement before each question | `ai/ConversationEngine.kt`, `ai/InterviewFlow.kt`, `server/app/core/engine.py` |
| Dialect and code-mixed speech | 3,596 surface forms over 38 concept categories — native script, romanised, and ASR misspellings | `assets/lexicon.json`, `ai/Lexicon.kt`, `server/app/core/lexicon.py` |
| Empathetic, natural conversation | acknowledgement + question per turn, re-ask only the failed slot, never trap the caller | `ConversationEngine`, `Engine._retry` |
| Education | completed / in-progress / dropout distinguished | `OnDeviceNlu.detectEducation*`, `nlu.detect_education_*` |
| Family / traditional occupation | a **separate slot** from current livelihood, scored as an asset (+55) | slot `FAMILY_OCCUPATION` |
| Current livelihood | slot `CURRENT_LIVELIHOOD`, explicitly protected from cross-contamination | `Engine.answer` clears the sibling field |
| Skills and interests | 10 interest keys + free-text skills (+40) | slot `INTERESTS` |
| Mobility / physical constraints | 3 mobility bands + a `None` sentinel so "no problem" is an answer | slots `MOBILITY`, `CONSTRAINTS` |
| Self-employment vs wage preference | strength ladder, not a binary keyword | slot `PREFERENCE` |
| Local economic reality | 187 per-district economy notes | `assets/district_economy.json` |
| AI/ML profiling → NSQF recommendations | 476 usable NSQF roles over 13 sectors, 21 named weights | `data/repository/AppRepository.kt`, `server/app/core/recommend.py` |
| Skill-gap identification | soft education gate + bridge-course wording per role | `_skill_gap()` |
| Region-specific opportunity | district economy note + nearest verified centre | `_region_opportunity()` |
| Works in low-connectivity / low-tech | **zero network calls needed end to end** in the app; IVR completes on the keypad alone | §14, §18 |
| IVR / WhatsApp for non-smartphone users | `server/app/channels/ivr.py`, `whatsapp.py` | §18, §19 |

### At a glance

| | |
|---|---|
| Kotlin source | **36 files · 9,901 lines** |
| Python server | **18 files · 3,393 lines** |
| Python tooling | **8 files · 2,435 lines** |
| JSON data assets | **8 files · 21,422 lines · 707 KB** |
| Android resources | 10 XML files |
| Project docs in repo | 7 Markdown files · 1,964 lines |
| Languages | 6 — English, Tamil, Hindi, Telugu, Kannada, Malayalam |
| Channels | 3 — Android app, IVR phone call, WhatsApp voice note |
| Interview slots | 9 |
| NSQF job roles | **476 usable** (540 raw rows, 64 malformed filtered) over **13 sectors** |
| Training centres | **660** — 294 CONFIRMED, 350 LIKELY, 16 UNVERIFIED; 392 with a phone number |
| Districts | **187** across **5 states** (TN 38 · UP 75 · KA 31 · AP 29 · KL 14); 185 have a centre |
| District economy notes | 187 |
| Sources logged | 190, all `retrieved = 2026-09-29` |
| NLU lexicon | 3,596 surface forms / 38 categories |
| District aliases | 363 forms over 187 keys |
| UI strings | 156 keys × 6 languages |
| APK | 20.3 MB · minSdk 24 (Android 7.0) · targetSdk 35 |
| Automated checks | 37 + 353 + 31 + 16, all passing |

---

## 2. Three channels, one engine

The problem statement explicitly asks for operation in *"low-connectivity, low-tech
environments (IVR, WhatsApp voice notes, lightweight mobile/kiosk)"*. That is three
different front doors. The design decision that makes the project coherent is that
**there is exactly one interview and one recommender behind all three**.

```
   Android app  ─────┐          (Kotlin, runs entirely on the handset)
                     │
   IVR phone call ───┼────►  same 9 slots
                     │       same lexicon
   WhatsApp voice ───┘       same 476-role catalogue
                             same 21-weight recommender
```

* The **Android app** carries its own complete copy of the engine, in Kotlin, and never
  needs the server at all. This is the offline path and the primary tested path.
* The **IVR** and **WhatsApp** channels run against the Python port of the same engine on
  the FastAPI server, because a phone call and a voice note arrive as *audio at a server*,
  not as a handset event.
* The two implementations are held identical by `tools/audit.py`, which compares the slot
  order, the district alias table and the sector map **across Kotlin and Python** and fails
  the build if they drift (§25).

The interview prompts are not retyped between the two. `tools/extract_flow.py` parses them
straight out of `ai/InterviewFlow.kt` into `server/data/flow.json`, so the Kotlin file is
the single point of truth for all 54 multilingual prompts.

---

## 3. Repository map

```
Thozhil-Thunai/
├── README.md                         291 lines — build + run instructions
├── PROJECT_STATUS.md                 200 lines — honest status, gaps, priorities
├── CHANGES.md                        696 lines — numbered defect log, symptom→cause→fix
├── TEST_SCRIPTS.md                    95 lines — manual device test script
├── docs_JanDwar_Proposal.md          180 lines — superseded early proposal
├── build.gradle · settings.gradle · gradle.properties · gradlew
├── render.yaml                        42 lines — Render blueprint for the server
├── brand/logo_premium.png                     — master brand asset, all icons derive from it
├── screenshots/                               — 6 device screenshots
├── data/                                      — LEGACY first-cut research (see note below)
│
├── app/                              ANDROID APP — 36 Kotlin files, 9,901 lines
│   ├── build.gradle                  107
│   └── src/main/
│       ├── AndroidManifest.xml        65
│       ├── assets/                            — the 8 JSON data assets, 21,422 lines
│       ├── res/                               — themes, colours, launcher icons
│       └── java/in/jandwar/app/
│           ├── JanDwarApp.kt          12      — @HiltAndroidApp
│           ├── MainActivity.kt       223      — single activity, Compose host
│           ├── ai/                            — the engine, 3,313 lines
│           │   ├── OnDeviceNlu.kt    711      — slot extraction, 6 languages
│           │   ├── ConversationEngine.kt 537  — turn loop, phases, state
│           │   ├── InterviewFlow.kt  472      — the 54 multilingual prompts
│           │   ├── GroqClient.kt     342      — OPTIONAL cloud adapter, inert by default
│           │   ├── VoiceListener.kt  344      — SpeechRecognizer wrapper
│           │   ├── Lexicon.kt        301      — surface-form matcher
│           │   ├── TtsSpeaker.kt     264      — TextToSpeech wrapper
│           │   ├── MicEarcon.kt      262      — audio cues around the mic
│           │   ├── HybridNlu.kt      166      — picks on-device vs cloud
│           │   ├── ProfileFragment.kt 117     — Slot enum + partial profile
│           │   ├── AiConfig.kt        84      — reads assets/config.json
│           │   └── NluEngine.kt       33      — the interface both NLUs implement
│           ├── data/
│           │   ├── local/AssetDataSource.kt 308  — loads + validates the 8 assets
│           │   ├── model/Models.kt          451  — all domain types
│           │   └── repository/AppRepository.kt 441 — recommender + lookups
│           ├── di/AppModule.kt        32      — Hilt bindings
│           ├── ui/
│           │   ├── navigation/NavGraph.kt 27  — 10 routes
│           │   ├── screens/                   — 10 Compose screens, 3,147 lines
│           │   ├── components/CommonComponents.kt 762
│           │   ├── theme/                     — Color, Theme, Type
│           │   └── viewmodel/                 — AppViewModel 371, VoiceViewModel 161
│           └── util/NetworkMonitor.kt  25
│
├── server/                           FASTAPI SERVICE — 18 files, 3,393 lines
│   ├── requirements.txt                 5     — fastapi, uvicorn, httpx, pydantic, multipart
│   ├── DEPLOY.md                      315
│   ├── IVR.md                         186     — how a real phone call works, end to end
│   ├── data/flow.json                 380     — GENERATED from InterviewFlow.kt
│   └── app/
│       ├── main.py                     90     — FastAPI app, routers, startup log
│       ├── config.py                   73     — every setting from env, all optional
│       ├── deps.py                     37     — singletons: store, engine, llm, speech
│       ├── core/
│       │   ├── nlu.py                 705     — Python port of OnDeviceNlu.kt
│       │   ├── recommend.py           387     — the 21-weight scorer
│       │   ├── engine.py              338     — session + interview state machine
│       │   ├── data.py                246     — shared catalogue access
│       │   └── lexicon.py             221     — surface-form matcher
│       ├── channels/
│       │   ├── ivr.py                 277     — TwiML, DTMF, per-language voices
│       │   ├── whatsapp.py            224     — Meta Cloud API webhook
│       │   └── api.py                 152     — plain JSON API
│       └── adapters/
│           ├── telephony.py           224     — G.711 / PCM16 conversion, stdlib only
│           ├── llm.py                 218     — OPTIONAL Ollama overlay, off by default
│           └── speech.py              201     — OPTIONAL Bhashini ASR/TTS
│
└── tools/                            VERIFICATION + GENERATORS — 8 files, 2,435 lines
    ├── audit.py                       292     — 37 whole-system checks
    ├── check_answers.py               334     — 353 realistic spoken answers
    ├── check_nlu.py                   265     — lexicon self-consistency
    ├── check_telephony.py             142     — 16 audio-format checks
    ├── extract_flow.py                146     — InterviewFlow.kt → flow.json
    ├── gen_lexicon.py                 486     — builds lexicon.json
    ├── gen_i18n.py                    503     — builds i18n.json
    └── gen_icons.py                   267     — builds every launcher icon from brand/
```

> **`data/` is legacy.** It holds the first research cut — 516 raw roles, **20** centres,
> **38** districts, one state. It is not read by the app or the server. The live catalogue
> is `app/src/main/assets/`. The directory is kept only so the growth from 20 → 660 centres
> is auditable. Do not edit it and do not quote its numbers.

---

## 4. Whole-system architecture

```
┌──────────────── CHANNELS ────────────────┐   ┌──── SERVICE (FastAPI) ────┐   ┌─ DATA PACK ─┐
│                                          │   │                           │   │             │
│  Android app ────────────────────────────┼──►│  (not needed — the app    │   │  660 centres│
│  Kotlin · Compose · fully offline        │   │   carries its own engine) │   │  476 roles  │
│                                          │   │                           │   │  187 distr. │
│  IVR phone call ─────────────────────────┼──►│  /ivr/voice               │   │  190 sources│
│  Twilio TwiML · DTMF + ASR               │   │  /ivr/lang                │   │  6 languages│
│                                          │   │  /ivr/turn                │   │  8 JSON     │
│  WhatsApp voice note ────────────────────┼──►│  /ivr/audio               │   │    assets   │
│  Meta Cloud API webhook                  │   │  /whatsapp/webhook        │   │             │
│                                          │   │  /api/*                   │   │             │
└──────────────────────────────────────────┘   └───────────┬───────────────┘   └──────▲──────┘
                                                           │                          │
                                               ┌───────────▼───────────────┐          │
                                               │       CORE ENGINE         │──────────┘
                                               │  1  slot state machine    │
                                               │  2  deterministic NLU     │
                                               │  3  profile builder       │
                                               │  4  weighted recommender  │
                                               └───────────────────────────┘

OPTIONAL ADAPTERS — each has a working fallback, none is load-bearing:
    Bhashini ASR/TTS  → Android on-device speech, and Twilio's own Google voices
    Ollama (server)   → off by default; the deterministic NLU always runs first
    Groq (app)        → inert by default; see §13
    ffmpeg            → text-only WhatsApp
```

### What runs where

| Concern | Android app | Server |
|---|---|---|
| Interview script | `InterviewFlow.kt` (source of truth) | `flow.json` (generated from it) |
| Slot extraction | `OnDeviceNlu.kt` | `core/nlu.py` (faithful port) |
| Lexicon | `assets/lexicon.json` via `Lexicon.kt` | the **same file** via `core/lexicon.py` |
| Catalogue | `assets/*.json` from the APK | the **same files** off disk |
| Recommender | `AppRepository.kt` | `core/recommend.py` |
| Speech in | Android `SpeechRecognizer` (offline-capable) | Bhashini, or the provider's ASR, or DTMF |
| Speech out | Android `TextToSpeech` | Bhashini `<Play>`, or Twilio `<Say>` with a named Google voice |
| Session state | in-memory in the ViewModel | `SessionStore`, TTL 3600 s |
| Network required | **no** | yes — it is a server |

### A turn, end to end (app)

1. User taps the mic on `VoiceScreen`.
2. `MicEarcon` plays the start cue; `VoiceListener` starts `SpeechRecognizer`.
3. Partial results stream to `VoiceViewModel` and render live.
4. On final result the transcript goes to `ConversationEngine.onUserUtterance()`.
5. `HybridNlu` is asked to extract. `cloudAvailable()` is false by default, so it calls
   `OnDeviceNlu.extractForSlot(raw, lang, slot, stateHint)`.
6. `OnDeviceNlu` normalises the text, runs the five-stage match order (§9) and returns a
   `ProfileFragment`.
7. The engine clears the sibling occupation field so one answer cannot fill two slots,
   then merges with `answering = currentSlot`.
8. If nothing was gained **and** the current slot is still empty → re-ask (max 2 retries,
   then skip rather than trap the user).
9. Otherwise advance to the next unfilled slot in order.
10. When all nine are filled or skipped, `AppRepository.recommend()` scores all 476 roles.
11. Results render on `ResultsScreen`; `TtsSpeaker` narrates the top three.
12. Tapping one opens `DetailScreen` with the QP code, NSQF level, notional hours, GIA
    programme type, the skill-gap line, the named centre with its phone number, and the
    per-factor score breakdown.

---

## 5. One source of truth

The single most important structural decision in this project: **there is no second copy
of anything**.

| Artefact | Source of truth | Consumers | Generator |
|---|---|---|---|
| Interview prompts (54) | `ai/InterviewFlow.kt` | app directly; server via `flow.json` | `tools/extract_flow.py` |
| Lexicon (3,596 forms) | `tools/gen_lexicon.py` | `assets/lexicon.json` → app **and** server | `tools/gen_lexicon.py` |
| UI strings (156 × 6) | `tools/gen_i18n.py` | `assets/i18n.json` → app **and** server | `tools/gen_i18n.py` |
| District aliases (363) | `work/gen_district_aliases.py` | `OnDeviceNlu.DISTRICT_ALIASES` **and** `nlu.DISTRICT_ALIASES` | generator + audit parity check |
| Catalogue (660/476/187) | `app/src/main/assets/*.json` | app from APK assets; server reads the **same paths** off disk | hand-curated, sourced |
| Launcher icons | `brand/logo_premium.png` | every mipmap density | `tools/gen_icons.py` |

`server/app/core/data.py` makes the sharing explicit:

```python
# Repo root = .../server/app/core/data.py -> up 4
ROOT = pathlib.Path(__file__).resolve().parents[3]

def _find_assets() -> pathlib.Path:
    env = os.environ.get("JANDWAR_ASSETS")
    if env:
        return pathlib.Path(env)
    for cand in (ROOT / "app/src/main/assets",     # normal case: share with the app
                 ROOT / "assets",
                 pathlib.Path(__file__).resolve().parents[2] / "assets"):
        if (cand / "job_roles.json").is_file():
            return cand
    return ROOT / "app/src/main/assets"
```

The comment in that file states the reason plainly: *"two copies is how the phone and the
phone-call start recommending different courses."*

**Never hand-edit a generated file.** `lexicon.json`, `i18n.json`, `flow.json` and
`DISTRICT_ALIASES` are all outputs. Edit the generator, re-run it, re-run the test suites.

---

# Part II — The Android app

## 6. Build and run the app

### Prerequisites

| | |
|---|---|
| JDK | 17 |
| Android SDK | Platform 35 + Build-Tools 35.0.0 |
| Gradle | 8.14.3 (the wrapper downloads it) |
| AGP | 8.7.3 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Hilt | 2.51.1 |
| Compose BOM | 2024.10.01 |

### Android Studio

`File → Open…` → select the repository folder → wait for the Gradle sync → `Run ▶`.
There is nothing to configure. No API key is required for any feature.

### Command line

```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

### Build configuration

`app/build.gradle`:

| Setting | Value |
|---|---|
| `namespace` / `applicationId` | `in.jandwar.app` |
| `compileSdk` / `targetSdk` | 35 |
| `minSdk` | 24 (Android 7.0) — a four-year-old ₹6,000 handset is the target |
| `versionCode` / `versionName` | 11 / `"2.9"` |
| `sourceCompatibility` / `targetCompatibility` | Java 17 |
| Compose | enabled, BOM 2024.10.01 |

`gradle.properties` ships with `-Xmx2048m` so it builds on a modest laptop. On a machine
with 16 GB+, raise it to `-Xmx4096m` for faster builds.

> **Low-memory build note.** On a constrained machine, raise `org.gradle.jvmargs`
> **before** starting the build — `GRADLE_OPTS` is truncated at the first space by some
> shells, so passing it on the command line silently loses every flag after the first.

### Runtime permissions

From `AndroidManifest.xml`:

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | the spoken interview |
| `INTERNET` | only for the optional cloud adapters; nothing core needs it |
| `ACCESS_NETWORK_STATE` | `NetworkMonitor` shows an offline badge |
| `CALL_PHONE` *(dial intent)* | tapping a centre's phone number |

---

## 7. App architecture

Four layers, strictly one-directional.

```
  ui/          Compose screens · ViewModels · theme · navigation
      │ observes StateFlow
  ai/          ConversationEngine · HybridNlu · OnDeviceNlu · Lexicon
      │        VoiceListener · TtsSpeaker · InterviewFlow · ProfileFragment
      │ calls
  data/        AppRepository (recommender + lookups)
      │        AssetDataSource (load + validate the 8 JSON assets)
      │
  assets/      job_roles · centres · districts · district_economy
               gia_funding_rules · i18n · lexicon · config
```

* **Dependency injection** — Hilt. `di/AppModule.kt` is 32 lines and binds exactly the
  singletons that need to be shared: `AssetDataSource`, `AppRepository`, `AiConfig`,
  `OnDeviceNlu`, `HybridNlu`.
* **State** — `StateFlow` only. No `LiveData`, no mutable globals. `AppViewModel` owns the
  app-wide state (language, profile, results); `VoiceViewModel` owns only the microphone
  session, because its lifetime is shorter and its failure modes are different.
* **Threading** — coroutines. Asset loading is `Dispatchers.IO` and happens once at
  startup; NLU and recommendation are pure CPU and run on `Dispatchers.Default`.
* **No database.** Eight JSON assets are read from the APK at startup into immutable
  in-memory lists. There is no Room, no SQLite, no migration, and nothing to corrupt.

### Why no runtime database

The catalogue is 707 KB, read-only, and ships with the binary. A database would add a
schema, a migration path and a failure mode, and would buy nothing — there is no user
data to persist. Profiles are deliberately **not** stored (see §13 on DPDP).

---

## 8. The interview model — nine slots

Defined once in `ai/ProfileFragment.kt` as `enum class Slot`, and mirrored exactly in
`server/app/core/nlu.py::SLOTS`:

| # | Slot | Type | What it captures |
|---|---|---|---|
| 1 | `EDUCATION` | enum | highest level **completed**, with in-progress and dropout detected separately |
| 2 | `FAMILY_OCCUPATION` | free text → canonical | the traditional/family trade |
| 3 | `CURRENT_LIVELIHOOD` | free text → canonical | what they do today |
| 4 | `INTERESTS` | list of 10 keys + free text | what they want to learn |
| 5 | `PREFERENCE` | enum | self-employment vs wage work |
| 6 | `MOBILITY` | enum | `LOCAL` / `DISTRICT` / `STATE` |
| 7 | `CONSTRAINTS` | free text + sentinel | physical or health limits |
| 8 | **`STATE`** | gazetteer | one of five states |
| 9 | `DISTRICT` | gazetteer, scoped by state | one of 187 |

### Why STATE comes immediately before DISTRICT

The catalogue spans five states and **district names collide**: Hassan, Bijapur,
Pratapgarh, Balrampur and Aurangabad each exist in more than one state in India. Asking
for a district against one flat list of 187 names would route a caller to a centre
2,000 km away. So:

* the state is **always asked explicitly and never assumed**;
* the district question is then phrased *inside* that state — `DISTRICT_IN_STATE` in both
  engines renders *"Which district of Tamil Nadu do you live in?"*, with the state name
  itself translated into the interview language by `STATE_LABELS`;
* `detect_district(s, original, state=...)` takes a state hint and scopes the match;
* if a district is given first, `state_of_district()` back-fills the state — the audit
  checks that all 187 back-fill correctly.

### Usability gate

```
isUsable() = education && state && district && (interests || familyOccupation)
```

Below that, the engine declines to produce a recommendation rather than guessing.
`MAX_TURNS = 14` in Kotlin and `MAX_RETRIES = 2` on the server bound the interview so it
can never loop.

### The `None` sentinel

"No, I have no health problem" is a **real answer**, not a blank. `CONSTRAINTS` stores the
sentinel `NO_CONSTRAINT = "None"`. Without it the slot stays empty, the engine re-asks, and
the user hears the same question three times.

### Inferable vs must-ask slots

`server/app/core/engine.py` encodes a subtle and important rule:

```python
INFERABLE = {"EDUCATION", "STATE", "DISTRICT", "PREFERENCE", "MOBILITY", "CONSTRAINTS"}
```

These take enum or gazetteer values, so a mention anywhere in the conversation is
unambiguous and re-asking would feel robotic. The three free-text occupation slots are
**deliberately excluded**: `FAMILY_OCCUPATION`, `CURRENT_LIVELIHOOD` and `INTERESTS` all
draw on the same occupation vocabulary, so *"my family farms"* would otherwise set all
three at once and silently skip two of the dimensions the PS requires. Those are always
asked out loud.

The same protection exists at merge time:

```python
if slot == "FAMILY_OCCUPATION":
    frag.current_livelihood = ""
elif slot == "CURRENT_LIVELIHOOD":
    frag.family_occupation = ""
```

---

## 9. The on-device NLU engine

`ai/OnDeviceNlu.kt` (711 lines) and its faithful Python port `server/app/core/nlu.py`
(705 lines). This is the heart of the product and the thing that makes it defensible:
**it is deterministic, offline, instant, and every decision can be explained.**

### No model, no key, no network

There is no neural network here. The engine is a normaliser, a lexicon, a set of
detectors and a precedence order. On a mid-range handset a turn resolves in about a
millisecond; a 3B parameter model on CPU takes 4–15 seconds, which is unusable when a
caller is holding a phone to their ear and the TwiML timeout is five seconds.

More importantly: **nothing is generated, so nothing can be invented.** A generative model
will produce a plausible-looking QP code and NSQF level without hesitation, and nobody
downstream can tell it is fictional — while a real person is sent to a course that does
not exist and a government funding decision is made on it.

### The lexicon

`assets/lexicon.json` — 3,596 surface forms over 38 categories. A *surface form* is
something a speech recogniser plausibly returns, which includes:

* native script in all six languages,
* romanised and code-mixed speech (Indian recognisers emit this constantly),
* **misspellings the ASR actually produces** — e.g. Tamil `டிப்ளமோ` for *diploma*.

`Lexicon.kt` / `lexicon.py` expose `words`, `phrases`, `owner`, `match_len`, `best_match`
and `forms_of`. Storing it as a JSON asset rather than Kotlin constants means field slang
can be added without recompiling, and `tools/check_nlu.py` validates against the exact
same bytes the app loads.

### Normalisation

`normalise()` lowercases, strips a defined punctuation class, collapses whitespace and
applies Unicode NFC. Indic scripts are normalised but **not** transliterated — the lexicon
carries both scripts rather than converting between them, because transliteration is lossy
in exactly the cases that matter.

### Five-stage match order

For each slot the detectors run in a fixed precedence, strictest first:

1. **Exact phrase** — the whole normalised utterance is a known form.
2. **Longest phrase match** — the longest known multi-word form wins. This is the reason
   *"anywhere in my district"* and *"anywhere in the state"* are different answers; a
   simple precedence list collapses them.
3. **Strict token match** — whole-token only. Short state forms (`tn`, `ap`, `up`, `u p`,
   `tamil`) are in `AMBIGUOUS_STATE_FORMS` and are only honoured as whole tokens **while
   the state question is the one being asked**. Without this, *"I studied up to 10th"*
   sets the state to Uttar Pradesh and *"I speak Tamil"* sets it to Tamil Nadu.
4. **Loose / substring match** — only for slots where it is safe.
5. **Free text** — the raw utterance is kept as a skill or constraint string.

### Short-Indic-stem rule

Short Indic stems are matched only on a token boundary, never as a substring. Indic
scripts form long agglutinated words, so a three-character stem appears inside dozens of
unrelated words; substring matching on them produced 35 cross-category collisions in
testing, all of which this rule removes.

### Education: completed vs in-progress vs dropout

Three separate detectors — `detect_education_completed`, `detect_education_loose`,
`is_in_progress`, `is_dropout`. *"College 2nd year"* is **Class 12 completed plus current
student**, not a graduate. Getting this wrong offered mid-course students roles they
cannot enrol in and told them they had no skill gap.

`EDU_ORDER` ranks: `BELOW_8=0 · CLASS_8=1 · CLASS_10=2 · CLASS_12=3 · ITI_DIPLOMA=4 ·
GRADUATE=5`.

### Preference and mobility as ladders

`detect_preference` runs `strict` then `loose`; `detect_mobility` likewise. A binary
keyword test ("own"/"job") misreads *"I'd like my own work eventually but a job for now"*.
The ladder scores strength of expression instead.

### District and state resolution

* `DISTRICT_ALIASES` — 363 forms over 187 keys, generated by
  `work/gen_district_aliases.py`, held byte-identical between Kotlin and Python by the
  audit.
* `_build_district_index()` and `_build_state_of_district()` build reverse indexes at
  construction.
* `detect_district(s, original, state=None)` scopes by state when one is known.
* `detect_state(s, original, allow_ambiguous=False)` gates the short forms.
* The audit resolves **550 district forms state-scoped and unscoped, and 66 state forms**,
  with zero failures.

### Cross-slot protection

`Fragment.merge(other, answering=slot)` allows **only the slot actually being answered** to
overwrite an existing value. Every other field may only fill a gap. Without this, a later
rambling answer silently rewrites an earlier correct one.

### Non-answers

```python
NON_ANSWERS = {"yes", "no", "ok", "okay", "hmm", "nothing", "dont know", "don't know", ...}
```
Filtered before free-text capture so an acknowledgement is never stored as a skill.

---

## 10. The conversation engine

`ai/ConversationEngine.kt` (537 lines) on the handset; `server/app/core/engine.py`
(338 lines) on the server. Same phases, same guarantees.

### Phases

```
GREETING ──► ASKING ◄──┐ (re-ask, max 2)
                │      │
                └──────┘
                │
                ▼
          RECOMMENDING ──► DONE
```

The greeting **already contains the first question** so the user is never left waiting for
a prompt after a pleasantry.

### Session state (server)

```python
@dataclass
class Session:
    id: str                     # uuid4 hex, 12 chars — no name, no phone, no Aadhaar
    lang: str = "en"
    channel: str = "sim"        # "sim" | "ivr" | "whatsapp"
    frag: Fragment              # the partial profile
    asked: list[str]            # slots already put to the user
    current: str | None         # the slot awaiting an answer
    turns: list[Turn]           # full transcript, for the UI and for debugging
    retries: int = 0
    seed: int = 0               # rotates the phrasing so it never repeats verbatim
    done: bool = False
    results: list[dict]
    created / updated: float
    used_llm: bool = False      # only for a UI badge
```

### The `seed` field

Every prompt family is a **list** of phrasings. `_pick(lst, seed)` indexes by
`seed % len(lst)` and the seed increments each successful turn, so the assistant does not
repeat itself word for word. This is the cheapest possible way to sound human and it costs
nothing at runtime.

### Failure → recovery

| Failure | Behaviour |
|---|---|
| Empty utterance | re-prompt, `retries += 1` |
| Nothing extracted and slot still empty | re-prompt |
| `retries > MAX_RETRIES` (2) | **skip the slot and move on** — never trap the user on one question |
| Recogniser error | the mic cue plays, the question is repeated, the keypad path stays open on IVR |
| Session expired (TTL 3600 s) | IVR says *"your session expired, please call again"* and hangs up cleanly |
| All nine slots filled or skipped | `_finish()` → recommend → narrate |

### Finishing

```python
def _finish(self, s):
    s.done = True
    s.current = None
    s.results = match_roles(self.profile(s), s.lang, limit=6)
    return (closing(s.lang) + " " + self._results_narration(s)).strip()
```

The narration speaks the **top three** results — job role, NSQF level, duration label and
the reason — because a spoken list longer than three is not retainable. The full six are
available on screen or by message.

---

## 11. Voice pipeline

### Recognition — `ai/VoiceListener.kt` (344 lines)

Wraps Android `SpeechRecognizer`. Partial results stream to the UI so the user can see
they are being heard. The language tag is set from the chosen interview language
(`ta-IN`, `hi-IN`, `te-IN`, `kn-IN`, `ml-IN`, `en-IN`).

`EXTRA_PREFER_OFFLINE` is requested, so the recogniser uses the device's offline language
pack when one is installed. **This is the one genuine offline caveat**: if the handset has
no Tamil offline pack, Android falls back to a network recogniser. It is documented
honestly rather than hidden, and the mitigation is to pre-install the packs on the demo
device (Settings → System → Languages → Voice input → Offline speech recognition).

### Speech — `ai/TtsSpeaker.kt` (264 lines)

Wraps Android `TextToSpeech`, selecting the voice for the interview language and queueing
utterances so a question is never spoken over a previous one.

### Mic cues — `ai/MicEarcon.kt` (262 lines)

Short tones on mic-open and mic-close. On a voice-only product the user otherwise has no
idea whether the device is listening.

> **Android 10+ constraint.** Two ordinary apps cannot hold the microphone at the same
> time. If a screen recorder with audio capture is running, the app's recogniser will not
> get the mic. This is an OS policy, not a defect — record the screen without audio, or
> use `scrcpy` (≥ 2.0, Android 11+) for audio forwarding.

### Server-side speech

A phone call and a voice note arrive at the server as **audio**, so the server needs its
own ASR and TTS. That is Bhashini (§20) — and it is optional, because IVR completes on the
keypad and WhatsApp completes on typed text.

---

## 12. UI layer

10 Compose screens, 3,147 lines, plus a 762-line shared component library. Routes are
declared in `ui/navigation/NavGraph.kt`.

| Screen | Lines | Purpose |
|---|---|---|
| `SplashScreen` | 164 | brand frame while assets load |
| `OnboardingScreen` | 204 | what the app does, in three cards |
| `LanguageScreen` | 201 | six languages, each written in its own script |
| `HomeScreen` | 391 | entry point — start the interview, or browse |
| `VoiceScreen` | 662 | the interview: mic, live transcript, chips, progress |
| `IntakeScreen` | 397 | tap-only equivalent of the interview, for noisy places |
| `ResultsScreen` | 350 | the ranked recommendations with match % |
| `DetailScreen` | 338 | one role in full — QP, level, hours, GIA track, centre, factors |
| `CoursesScreen` | 197 | browse the whole 476-role catalogue |
| `SettingsScreen` | 343 | language, voice, data summary, about |

### Design notes that matter for the user

* **Large touch targets.** The intake path is five large taps, designed to be usable by
  someone who cannot read the labels.
* **Every string is translated.** 156 keys × 6 languages, with key parity enforced by the
  audit — there is no screen that silently falls back to English.
* **Nothing is invented in the UI.** The 95 roles with no published NSQF level and the 100
  with no notional hours render an em dash `—`. The scoring defaults (level 3, 300 hours)
  are internal and must never leak to the screen.
* **QP codes contain slashes.** All but three of the 540 `qp_code` values contain `/`, so
  they are URL-encoded before being used in a navigation route.
* **Offline badge.** `util/NetworkMonitor.kt` drives a small indicator, so the user can see
  that the app is working with no connection rather than wondering.

---

## 13. App configuration and the optional cloud adapter

### `assets/config.json`

```json
{
  "_readme": "...",
  "groq_api_key": "PASTE_YOUR_GROQ_API_KEY_HERE",
  "groq_model": "llama-3.3-70b-versatile"
}
```

### What actually happens at runtime

`ai/AiConfig.kt` treats placeholder text as **absent**. `isPlaceholder()` matches
`PASTE_…`, `your_…`, `<…>` and empty strings. Therefore, **as shipped**:

```
AiConfig.isPlaceholder("PASTE_YOUR_GROQ_API_KEY_HERE")  →  true
AiConfig.groqEnabled()                                  →  false
HybridNlu.cloudAvailable()                              →  false
⇒ every single turn is served by the deterministic on-device NLU
```

This is not a degraded mode. It is the tested, primary, and **only** path that the shipped
APK takes. The on-device engine answers the whole interview in six languages, scores all
476 roles, applies the GIA rules and names a centre, with **no key, no network and no
model**. `tools/audit.py` asserts that no live key exists in the source and that
`config.json` is still a placeholder.

### Removing the cloud adapter entirely

If you want the Groq code gone from the binary rather than merely inert, it is four edits:

1. delete `app/src/main/java/in/jandwar/app/ai/GroqClient.kt`;
2. drop the `groq` constructor parameter and the `cloudAvailable()` branch from
   `ai/HybridNlu.kt` (it then simply delegates to `OnDeviceNlu`);
3. drop the two Groq fields from `ai/AiConfig.kt`;
4. delete `app/src/main/assets/config.json` and its `AssetDataSource` loader line.

Nothing else references it. The interview, the recommender and all four test suites are
unaffected.

### Privacy — DPDP Act 2023

| | |
|---|---|
| Name collected | none |
| Aadhaar or any government ID | none |
| Phone number | only the caller ID the telephony provider supplies, used as a session alias and never stored |
| Session identifier | random `uuid4` hex, 12 characters |
| Retention | in-memory, TTL 3600 s, swept on every access |
| Data region | wherever the server is deployed — the Render blueprint is region-pinned by the operator |
| On-device | nothing is written to disk; the profile lives in a ViewModel and dies with the process |
| Consent | spoken at the start of the IVR call and shown on the app's onboarding screen |

The app therefore has no user data at rest to lose, which is the strongest possible
position under the DPDP Act.

---

# Part III — The server

## 14. Server overview

`server/` is a FastAPI service whose entire job is to give the **non-smartphone** channels
the same interview the app gives. It is five dependencies:

```
fastapi==0.115.6
uvicorn[standard]==0.34.0
httpx==0.28.1
pydantic==2.10.4
python-multipart==0.0.20
```

The core — NLU, engine, recommender, data access — is **standard library only**. There is
no numpy, no ML runtime, no database and no model file. That is why it starts instantly on
a free-tier instance and why it can be handed to someone as a standalone package.

### Run it

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Boot log

`main.py` logs exactly what it found, so a misconfiguration is visible in the first four
lines rather than at the first phone call:

```
catalogue: 476 job roles (476 PM-AJAY GIA fundable) across 13 sectors
coverage : 187 districts, 660 training centres, 6 languages
lexicon  : 3596 surface forms in 38 categories
interview: 9 slots, prompts extracted from InterviewFlow.kt
LLM      : off — deterministic NLU only (this is fully functional)
speech   : Bhashini not configured — IVR uses DTMF, WhatsApp uses text
note     : JANDWAR_PUBLIC_URL is unset. Set it to your public https URL before
           connecting telephony or WhatsApp.
```

### Configuration — `app/config.py`

Every setting comes from an environment variable and **nothing is required**. With a
completely empty environment the service boots and runs a complete nine-slot interview in
six languages through the browser simulator and through DTMF on a real phone call.

| Variable | Default | Effect |
|---|---|---|
| `JANDWAR_HOST` | `0.0.0.0` | bind address |
| `PORT` / `JANDWAR_PORT` | `8000` | port (Render sets `PORT`) |
| `JANDWAR_PUBLIC_URL` | — | public https base; falls back to `RENDER_EXTERNAL_URL` |
| `JANDWAR_DEFAULT_LANG` | `en` | language if the caller presses nothing |
| `JANDWAR_SESSION_TTL` | `3600` | session lifetime in seconds |
| `IVR_PROVIDER` | `twilio` | XML dialect — `twilio` or `exotel` |
| `IVR_VOICE_TA` *(and `_HI`, `_TE`, …)* | — | override the named provider voice per language |
| `JANDWAR_OLLAMA` | `false` | enable the optional local LLM overlay |
| `OLLAMA_HOST` | `http://127.0.0.1:11434` | |
| `OLLAMA_MODEL` | `qwen2.5:3b-instruct` | |
| `OLLAMA_TIMEOUT` | `12` | seconds |
| `JANDWAR_BHASHINI` | `true` | enable Bhashini **if** keys are present |
| `BHASHINI_USER_ID` / `_ULCA_KEY` / `_INFERENCE_KEY` | — | ULCA credentials |
| `WHATSAPP_TOKEN` / `_PHONE_NUMBER_ID` | — | Meta Cloud API |
| `WHATSAPP_VERIFY_TOKEN` | `jandwar` | webhook handshake |
| `JANDWAR_ASSETS` | — | override the catalogue path for a standalone deploy |
| `JANDWAR_SERVER_DATA` | `server/data` | where `flow.json` lives |

### Singletons — `app/deps.py`

37 lines, deliberately free of framework types:

```python
_store  = SessionStore(ttl_seconds=settings.session_ttl)
_llm    = OllamaLlm(...) if settings.ollama_enabled else NullLlm()
_speech = Bhashini(...)  if settings.bhashini_enabled else NullSpeech()
_engine = Engine(llm=_llm)
```

`NullLlm` and `NullSpeech` are real objects with the same interface that return `None` and
report `available() == False`. There is no `if adapter is not None` scattered through the
channels — the absent case is an object, not a branch.

---

## 15. Server core

### `core/data.py` — shared catalogue access (246 lines)

Locates the app's assets (§5), loads each file behind `functools.lru_cache(maxsize=1)`, and
applies **the same validity filter the app applies** so both sides see 476 roles, not 540:

```python
if (len(name) > 3 and low != "english hindi"
        and not name.startswith("QG-") and not low.startswith("qp code")):
```

It also provides the lookups every channel needs: `tr()`, `interest_label()`,
`occupation_label()`, `economy_for_district()`, `centre_for_district()`, `states()`,
`districts_of_state()`, `state_of_district()`, `languages()` and `summary()`.

`centre_for_district(district, state="")` is state-scoped for the collision reason in §8,
and prefers a `CONFIRMED` record over a `LIKELY` one:

```python
for c in hits:
    if str(c.get("confidence", "")).upper().startswith("CONFIRMED"):
        return c
return hits[0]
```

### `core/lexicon.py` — surface-form matcher (221 lines)

Python twin of `Lexicon.kt`. Constructor `Lexicon(forms_dict)`; loader
`data.lexicon_forms()`. API: `.words`, `.phrases`, `.owner`, `.match_len`, `.best_match`,
`.forms_of`. *(Note: there is no `.forms` attribute — the raw dict comes from
`data.lexicon_forms()`.)*

### `core/nlu.py` — the engine (705 lines)

A faithful port of `OnDeviceNlu.kt`, including detector precedence, so the same sentence
fills the same slots on every channel. Covered in full in §9. Public surface:

```python
Fragment(...)                      # the partial profile
  .is_filled(slot) · .missing_slots() · .filled_count()
  .merge(other, answering=None) · .to_dict()

Nlu()
  .extract(raw, lang="en") -> Fragment
  .extract_for_slot(raw, lang, slot, state_hint=None) -> Fragment
  .detect_district / detect_state / detect_education / detect_preference
  .detect_mobility / detect_interests / detect_occupation / detect_constraints
  .is_in_progress / is_dropout / mentions_family / detect_student_status
```

### `core/engine.py` — the interview (338 lines)

Covered in §10. Public surface:

```python
Engine(llm=None)
  .start(lang="en", channel="sim") -> (Session, greeting_text)
  .answer(session, raw_text)       -> next_assistant_line
  .profile(session)                -> dict        # staticmethod

SessionStore(ttl_seconds=3600)
  .put(session, alias=None) · .get(sid) · .by_alias(alias)
  .drop_alias(alias) · .count()
```

The `alias` mechanism is what lets an IVR call find its session again by `CallSid` and a
WhatsApp thread by phone number, without either channel knowing about the other.

---

## 16. The recommendation engine

`core/recommend.py` (387 lines) and `data/repository/AppRepository.kt` (441 lines).
Identical scoring, deliberately.

### Why rules and not a model

PM-AJAY GIA is public money. A recommendation has to be **reproducible, auditable, and
must cite a QP code that actually exists**. Every point in the score below can be
re-derived by hand from the profile and the catalogue. There are no embeddings and no
learned weights, so there is nothing that can drift, nothing that needs a GPU, and nothing
a judge can ask about that cannot be answered precisely.

### The 21 weights

| Signal | Score |
|---|---|
| Education requirement met | **+30** |
| Education short of requirement | **−22 × gap**; dropped entirely if the role needs ≥ GRADUATE and the user is below CLASS_10 |
| Long-term role (≥ 600 h) and below Class 10 | **−14** |
| First matched interest | **+85** |
| Each further matched interest | **+18** |
| Family occupation matches the role | **+55** |
| Current livelihood matches | **+32** |
| Free-text skill matches | **+40** |
| No interest/occupation/skill signal at all | **−12** |
| GIA-fundable sector | **+26** |
| Self-employment preference fits the role | **+24** |
| Wage preference fits the role | **+24** |
| A training centre exists in the district | **+12** |
| …and the role is agriculture or food processing | **+10** |
| Mobility `LOCAL` with a centre in the district | **+20** |
| Mobility `LOCAL` with no local centre | **−14** |
| Mobility `DISTRICT` | **+10** |
| Mobility `STATE` | **+5** |
| A stated physical constraint blocks a demanding role | **−55** |
| Low-NSQF-level tie-break | **+ max(0, 8 − level)** |

Every point awarded emits a `MatchFactor`, which is what `DetailScreen` renders. There is
no unexplained component in the total.

### Shaping

After scoring, results are diversified: **at most two roles per sector**, then topped up
from the remainder until `limit = 6`. Without this, a dairy-interested profile returns six
near-identical dairy roles and the beneficiary has no real choice.

### Confidence

```
worstRef = max(best − 120, 1)
pct      = 95                                            if best ≤ worstRef
         = round(55 + 45 × (score − worstRef) / (best − worstRef))  otherwise
pct      = clamp(pct, 35, 99)
```

Never 100 %, never 0 %. The audit asserts the clamp holds across **748 district × profile
runs**.

### Programme type and duration

`programme_type(role)` maps notional hours onto the GIA tracks — RPL 32–80 h, STT
200–600 h, EDP 80 h, LTT 6–12 months — so the recommendation names the funding instrument,
not just the course. `duration_label(role)` renders it for speech and screen.

### Honesty about missing data

95 of 540 raw roles carry no NSQF level and 100 carry no notional hours. Scoring uses a
neutral internal default (level 3, 300 h) so those roles are neither favoured nor hidden,
but the UI prints **`—`**. The defaults must never reach the screen.

### Verified outcomes

| Language / state / district | Expected top result |
|---|---|
| `ta` · Tamil Nadu · சேலம் | Goat and Sheep Farmer · NSQF 3 · 99 % · Govt ITI Salem |
| `te` · Andhra Pradesh · అనంతపురం | Water Resource Supervisor · NSQF 2 · 99 % · ICAR KVK Reddipalli |
| `kn` · Karnataka · ಬೆಳಗಾವಿ | Processing Supervisor (Dyeing & Printing) · NSQF 4 · 99 % |
| `ml` · Kerala · ആലപ്പുഴ | Purchase Assistant – Food & Agri Commodities · NSQF 3 · 99 % |
| `hi` · Uttar Pradesh · वाराणसी | Jute Selector cum Assorter · NSQF 1 · 99 % · Govt ITI Varanasi |

---

## 17. Channel: JSON API

`app/channels/api.py` (152 lines), prefix `/api`. Used by the browser simulator and
equally usable by a kiosk front-end or the Android app.

| Route | Method | Purpose |
|---|---|---|
| `/api/health` | GET | data summary, config summary, LLM status, speech status, live session count |
| `/api/languages` | GET | the six supported languages |
| `/api/session` | POST | start an interview → `{lang, channel}` |
| `/api/say` | POST | one turn → `{session, text}` |
| `/api/session/{sid}` | GET | full session view |
| `/api/match` | POST | score a profile directly, without an interview |
| `/api/roles` | GET | browse the catalogue — `?q=&sector=&limit=` |
| `/api/centres` | GET | the 660 centres |

The session view returned by every conversational route:

```json
{
  "session": "a1b2c3d4e5f6",
  "lang": "ta",
  "reply": "...",
  "slot": "DISTRICT",
  "done": false,
  "usedLlm": false,
  "progress": {"filled": 8, "total": 9},
  "profile": { ... },
  "results": [ ... ],
  "turns": [{"who": "bot", "text": "...", "slot": "EDUCATION"}, ...]
}
```

and each result:

```json
{
  "qpCode": "AGR/Q4302", "jobRole": "Goat and Sheep Farmer",
  "nsqfLevel": "3", "sector": "agriculture", "ssc": "ASCI",
  "duration": "...", "confidence": 99, "eligible": true, "fundable": true,
  "reason": "...", "skillGap": "...", "regionOpportunity": "...",
  "centre": "Govt ITI Salem", "factors": [ ... ]
}
```

`/api/health` is also the **wake-up endpoint** for a sleeping free-tier host — hit it
before a demo.

---

## 18. Channel: IVR

`app/channels/ivr.py` (277 lines), prefix `/ivr`. This is the PS's "feature phone"
deployment: a caller on a ten-year-old handset dials a number and gets the same interview.
No app, no smartphone, no data connection, no literacy requirement.

### How a call flows

```
caller dials  ──►  Twilio answers  ──►  POST /ivr/voice
                                          │  returns TwiML: language menu, numDigits=1
                   caller presses 2  ──►  POST /ivr/lang
                                          │  starts a session, returns greeting + Q1
                   caller speaks/presses ──► POST /ivr/turn?sid=…
                                          │  engine.answer() → next question
                                          └─ loop until done, then <Hangup/>
```

### Two input modes on every question

* **Speech** — the provider transcribes (`SpeechResult`) or posts a recording
  (`RecordingUrl`), which Bhashini then transcribes.
* **Keypad (DTMF)** — mapped to the same slot values.

Keypad **beats** ASR when both arrive, because a digit is unambiguous:

```python
digits = (Digits or "").strip()
if digits and slot in DTMF:
    said = DTMF[slot].get(digits, said)
elif digits and not said:
    said = digits
```

### The DTMF map

| Slot | Keys |
|---|---|
| `EDUCATION` | 1 did not study · 2 5th · 3 8th · 4 10th · 5 12th · 6 ITI · 7 degree |
| `PREFERENCE` | 1 own business · 2 a job |
| `MOBILITY` | 1 only my village · 2 anywhere in my district · 3 anywhere in the state |
| `CONSTRAINTS` | 1 no · 2 yes, I have a health problem |

`DTMF_HINT` appends the key list to the spoken prompt in English, Tamil and Hindi.
**Eight of the nine slots are answerable by keypad**; `DISTRICT` is speech-only because it
is a 187-value gazetteer, and `0` skips it. This is why the IVR works on day one with zero
paid services configured.

### Every language names a real voice

This is the single most expensive lesson in the IVR work and it is encoded as a constant:

```python
VOICE = {
    "en": ("en-IN", "Google.en-IN-Standard-A"),
    "ta": ("ta-IN", "Google.ta-IN-Standard-C"),
    "hi": ("hi-IN", "Google.hi-IN-Standard-A"),
    "te": ("te-IN", "Google.te-IN-Standard-C"),
    "kn": ("kn-IN", "Google.kn-IN-Standard-C"),
    "ml": ("ml-IN", "Google.ml-IN-Standard-C"),
}
```

Twilio's default voice is the Basic voice `man`, which speaks a handful of European
languages. `<Say language="ta-IN">` **with no `voice` attribute** does not produce Tamil —
the verb errors, the caller hears silence, and the call drops. Each Indic language must
name a Google voice that actually supports it. Any of them can be overridden with
`IVR_VOICE_TA=Google.ta-IN-Wavenet-C`.

### Bhashini audio vs provider TTS

```python
if speech.available() and settings.public_url:
    return "<Play>%s/ivr/audio?lang=%s&text=%s</Play>" % (...)
return '<Say language="%s" voice="%s">%s</Say>' % (loc, voice, escape(text))
```

When Bhashini is configured the provider is handed a URL to **our own** synthesised audio,
because provider TTS for Tamil, Telugu and Kannada ranges from poor to nonexistent.
Otherwise it falls back to `<Say>` with the named voice. Both paths work.

### The hop budget

A Twilio trial account allows **10 TwiML fetches per call** and a **5-second timeout** on
each. A complete nine-slot interview is engineered to finish in **9 hops**, verified in
all six languages. The techniques:

* `actionOnEmptyResult="true"` on `<Gather>` instead of a trailing `<Redirect>` — a
  redirect costs a hop;
* `max_retries = 0` at the TwiML level; retries are handled inside the engine;
* `_reserve_district()` — with one hop left the engine reserves it for `DISTRICT`, because
  without a district there is no centre and the whole answer is weaker.

Other trial limits worth knowing before a demo: ~$15–20 credit, verified caller IDs only,
10 minutes per call, 75 minutes of quota ≈ 12 Tamil calls, and **no Indian DID** — Indian
numbers have been barred as caller IDs since August 2024, so a demo must use a US trial
number dialled from a verified Indian mobile. A Tamil call runs about six minutes.

### `/ivr/audio` and the float32 bug

Bhashini returns **32-bit IEEE-float WAV**. Every desktop player opens it. Twilio's
`<Play>` accepts MP3, 16-bit PCM WAV and µ-law only — handed float32 it either rejects the
media or plays static, and the caller hears nothing *while curl still reports a cheerful
200, `audio/wav`, and a plausible byte count*. The route therefore rewrites everything to
8 kHz mono 16-bit PCM (or µ-law with `?fmt=ulaw`) before it leaves the process (§20).

---

## 19. Channel: WhatsApp

`app/channels/whatsapp.py` (224 lines), prefix `/whatsapp`. Meta's WhatsApp Cloud API.

```
user sends a Tamil voice note
   → GET/POST /whatsapp/webhook
   → _download_media(media_id)        (Graph API v21.0)
   → Bhashini transcribes
   → engine.answer()
   → Bhashini synthesises the reply
   → _send_audio() as a voice note  +  _send_text() alongside
```

The text is sent alongside the audio so a literate user, or a poor-audio situation, still
works.

| Route | Purpose |
|---|---|
| `GET /whatsapp/webhook` | Meta's one-time `hub.challenge` verification handshake |
| `POST /whatsapp/webhook` | inbound messages |
| `GET /whatsapp/outbox/{phone}` | read (and clear) what *would* have been sent |

### The OUTBOX

```python
OUTBOX: dict[str, list[dict]] = {}
```

When no Meta token is configured, outbound messages are recorded here instead of being
delivered. This lets the browser simulator drive the **real** webhook and the **real**
engine with no Meta account at all — the only thing being faked is the final hop to Meta's
servers. It is also what makes the channel demonstrable before business verification
completes.

### Degradation

| Missing | Behaviour |
|---|---|
| Bhashini keys | thread runs on typed text |
| Meta token | webhook still parses, logs and records to `OUTBOX` |
| ffmpeg | text-only |

### Status

**Honest status: the webhook, media download and audio pipeline work; the full
conversation loop is still being wired.** The app and the IVR are the two channels tested
end to end on real devices and real calls.

---

## 20. Adapters

Three optional adapters. Each has a null implementation and a working fallback. Remove all
three and the interview still completes and still returns a real course from real data.

### `adapters/telephony.py` (224 lines) — audio conversion

Pure standard library, on purpose: the server's requirements are five packages, `numpy` is
not among them, and `audioop` was **removed in Python 3.13**, so neither can be leaned on.

```python
to_telephony_wav(data: bytes, fmt: str = "pcm16" | "ulaw") -> bytes
describe(data: bytes) -> str
class NotWav(ValueError)
```

Handles `WAVE_FORMAT_PCM`, `IEEE_FLOAT`, `ALAW`, `MULAW` and `EXTENSIBLE`; decodes,
resamples to `TELEPHONY_RATE = 8000`, normalises to `TARGET_PEAK = 0.95` (the upstream
audio peaks slightly above 1.0 and would clip audibly the moment it is quantised), and
re-encodes.

> **G.711 µ-law is a 14-bit codec, not 16-bit.** The encoder uses `sample >> 2`, clips at
> **8159**, and biases by `0x84 >> 2`. Getting this wrong produces audio that sounds
> plausible and is subtly wrong. `tools/check_telephony.py` cross-checks the
> implementation against the reference across inputs and asserts the output re-decodes.

Measured on a real Bhashini payload: float32 **37,002 bytes** → pcm16 **18,516 bytes** →
µ-law **9,282 bytes**.

### `adapters/speech.py` (201 lines) — Bhashini

Bhashini is the Government of India's national language stack (Digital India Bhashini
Mission). It is the right choice here specifically because it covers Tamil, Hindi, Telugu,
Kannada and Malayalam ASR **and** TTS properly for rural accents, it is free at this scale
after ULCA registration, and for a PM-AJAY submission using the Government's own language
infrastructure is a point in the design's favour rather than a dependency on a foreign
vendor.

```python
Bhashini(user_id, ulca_key, inference_key, enabled=True)
  .available() · .status()
  .transcribe(audio_bytes, lang) -> str | None
  .synthesize(text, lang, gender="female") -> bytes | None
NullSpeech()   # same interface, returns None
```

The Android app does **not** use this — Android gives on-device recognition and TTS for
free and offline, which is exactly right for a low-connectivity handset. A phone call and
a voice note arrive at the server as audio, and the server is online by definition, so the
objection that rules Bhashini out on the handset does not apply here.

### `adapters/llm.py` (218 lines) — optional local LLM

**Off by default** (`JANDWAR_OLLAMA=false`). When enabled it runs **Ollama** locally —
fully open source, no API key, no per-call cost, default model `qwen2.5:3b-instruct`.

What it is allowed to do is tightly bounded, and the module's own docstring states it:

> The model helps with *language*: reading a rambling sentence and pulling out which slot
> values are in it. **It never chooses a course.** Recommendation stays in `recommend.py`,
> scored against the real NSQF catalogue, because PM-AJAY GIA is public money — a
> recommendation must be reproducible and auditable, and must cite a QP code that actually
> exists; a generative model will invent a plausible-looking QP code and NSQF level
> without hesitation, and nobody downstream can tell; and an IVR caller is holding a phone
> to their ear.

Mechanically it is an **overlay**: the rules run first and always, and only non-empty
fields from clean JSON are merged on top.

```python
before = s.frag.filled_count()
frag = nlu().extract_for_slot(raw, s.lang, slot, state_hint=s.frag.state)   # always
if self.llm is not None and self.llm.available():                           # maybe
    extra = self.llm.extract(raw, s.lang, slot, s.frag)
    if extra is not None:
        frag.merge(extra)
```

If it is absent, slow, or returns rubbish, the interview is unaffected. `NullLlm` is the
default and reports `available() == False`.

---

## 21. Deployment

### Render (the blueprint path)

`render.yaml` at the repo root is read automatically when a Blueprint is created from the
repository, so there is nothing to type into the dashboard:

```yaml
services:
  - type: web
    name: jandwar
    runtime: python
    plan: free
    rootDir: server
    buildCommand: pip install -r requirements.txt
    startCommand: uvicorn app.main:app --host 0.0.0.0 --port $PORT
    healthCheckPath: /api/health
```

`RENDER_EXTERNAL_URL` is injected by Render and picked up by `config.py`, so the public
URL that the telephony provider needs is configured with **zero effort**. Secrets
(`BHASHINI_*`, `WHATSAPP_*`) are declared `sync: false` — they are filled in the Render
dashboard and never committed.

Live instance: `https://jandwar.onrender.com`.

> **Outstanding deployment action.** The `/ivr/audio` float32 fix is present in
> `adapters/telephony.py` in `main`, but the deployed instance still runs an older build.
> **Redeploy from `main` before any IVR demo**, or calls will be silent.

### Local tunnel (for a laptop demo)

```bash
cloudflared tunnel --url http://localhost:8000
# then: export JANDWAR_PUBLIC_URL=https://<the-tunnel-host>
```

Point the Twilio number's Voice webhook at `https://<host>/ivr/voice` (HTTP POST).

### Free-tier caveat

A free host sleeps. Hit `/api/health` to wake it before a demo. The **app path needs no
server at all**, which is the real mitigation.

---

# Part IV — The data

## 22. The data pack

Eight JSON files in `app/src/main/assets/`, 707 KB, 21,422 lines. Read by the app from the
APK and by the server from the same paths on disk.

| File | Size | Shape |
|---|---|---|
| `job_roles.json` | 98 KB | `list[540]` |
| `centres.json` | 307 KB | `list[660]` |
| `districts.json` | 12 KB | `{all, with_centre, without_centre, by_state, note}` |
| `district_economy.json` | 85 KB | `{district → note}`, 187 keys |
| `gia_funding_rules.json` | 33 KB | `{state → rules}`, 5 keys |
| `i18n.json` | 82 KB | `{langs, strings, occupation, interest}` |
| `lexicon.json` | 90 KB | `{_readme, forms}` — 38 categories, 3,596 forms |
| `config.json` | <1 KB | `{_readme, groq_api_key, groq_model}` — placeholder |

### `job_roles.json`

```json
{
  "qp_code": "AGR/Q4302",
  "job_role": "Goat and Sheep Farmer",
  "nsqf_level": "3",
  "notional_hours": "300",
  "ssc": "Agriculture Skill Council of India",
  "sector": "agriculture"
}
```

540 raw rows; 64 malformed source rows are filtered identically on both sides, leaving
**476 usable** roles across **13 sectors**. **95 rows have no `nsqf_level`** and **100 have
no `notional_hours`** — these render `—` and are never invented.

> All but three of the 540 `qp_code` values contain a `/`. **URL-encode them** before using
> one in a navigation route or an API path.

### `centres.json`

```json
{
  "name": "Government ITI, Salem",
  "district": "Salem", "state": "Tamil Nadu",
  "address": "...", "phone": "...",
  "trades": ["..."], "dairy_course": false,
  "confidence": "CONFIRMED",
  "confidence_note": "listed on the DET regional roster with a phone number",
  "source": "https://skilltraining.tn.gov.in/...",
  "retrieved": "2026-09-29"
}
```

| | |
|---|---|
| Total | **660** |
| CONFIRMED | **294** |
| LIKELY | **350** |
| UNVERIFIED | **16** |
| With a phone number | **392** (268 without — labelled, not invented) |
| Every record carries a `source` URL | enforced by `audit.py` |
| Every record carries `retrieved` | `2026-09-29` |

The confidence grade is the honest core of the data asset. A LIKELY centre is **declared
as likely**, never stated as fact.

### `districts.json`

An **object**, not a list:

```json
{ "all": [187 names], "with_centre": [185], "without_centre": [2],
  "by_state": { "Tamil Nadu": {"all": [38], ...}, ... },
  "note": "..." }
```

Coverage: Tamil Nadu 38 · Uttar Pradesh 75 · Karnataka 31 · Andhra Pradesh 29 · Kerala 14.

### `district_economy.json`

187 per-district notes describing the local livelihood reality. This is what powers the
*"why this trade, here"* line — the single most persuasive sentence on the results screen.

### `i18n.json`

`{langs, strings, occupation, interest}` — **156 string keys × 6 languages**, plus 17
occupation labels and 10 interest labels in all six. Key parity across languages and the
absence of empty strings are both enforced by `audit.py`.

### Provenance

**190 sources**, all retrieved `2026-09-29`, documented in `Data_Documentation_JanDwar.pdf`
(19 pages): methodology, source hierarchy, confidence taxonomy, per-state coverage, the QP
register and a full register of sources with access dates.

Principal sources: Tamil Nadu DET and TNSDC ITI rosters · ICAR Krishi Vigyan Kendra roster
(ATARI Zones X and XI) · PNB and Canara Bank RSETI/RUDSETI directories · NIRDPR RSETI
directory · Jan Shikshan Sansthan network · CSR&TI Mysuru (Central Silk Board) · Coir
Board, Handloom and MSME-DI training units · National Qualifications Register ·
Directorate General of Training.

> **Known arithmetic error in the shipped PDF**: it states *"193 district datasets
> (38+14+31+29+75)"*; that sum is **187**. The code and the assets are correct at 187; only
> the PDF's headline number is wrong.

---

## 23. PM-AJAY GIA funding rules

`gia_funding_rules.json` — five state keys (Andhra Pradesh, Karnataka, Kerala, Tamil Nadu,
Uttar Pradesh), each with:

```
scheme · implementing_structure · eligibility · cost_norms · state_trade_list
state_training_institution_layer · machine_usable_rule · sources
```

### Central GIA predicate

| Track | Duration |
|---|---|
| **RPL** — Recognition of Prior Learning | 32–80 hours |
| **STT** — Short Term Training | 200–600 hours |
| **EDP** — Entrepreneurship Development Programme | 80 hours |
| **LTT** — Long Term Training | 6–12 months |

Floors: **≥ 70 % placement**, **≥ 30 % women**, **≤ 30 % infrastructure spend**.
**No sector whitelist.**

Named machine-usable rules: `KA-GIA-ELIG-001`, `AP-GIA-ELIG-001`, `UP-GIA-ELIG-001`.
Tamil Nadu and Kerala run on the central guidelines only.

State specifics encoded:

* **Karnataka** — Dr B. R. Ambedkar Development Corporation "Prosperity Scheme", GSC
  21–50 years, income ≤ ₹5 lakh, CMKKY delivered via KSDC.
* **Uttar Pradesh** — GoUP order of 14-Jul-2022 binding all UPSCFDC schemes under PM-AJAY;
  a four-month scheme plus a toolkit.
* **Andhra Pradesh** — district SC Societies under APSCCF; ₹33k–40k per trainee at 100 %
  subsidy.
* **Kerala** — SC Development Department; six SCDD-run ITIs mapped as a direct PM-AJAY fit.

### The sector whitelist that was wrong

`core/data.py::ANNEXURE1_SECTOR` maps this catalogue's 13 sector names onto the PM-AJAY GIA
Annexure-1 eligible-activity list. It replaced a **guessed four-sector whitelist** that was
wrong in an expensive direction: it marked 116 `media_entertainment` and 20 `electronics`
roles as *unfundable* when Annexure-1 covers 31 sectors including both.

`gia_funding_rules.json` records `"qp_whitelist": "none stated — central GIA guidelines do
not restrict sectors/QPs"` for AP, Karnataka and UP. Every sector in this catalogue
therefore maps onto Annexure-1, and the sector test excludes nothing — **which is the
correct answer, not a bug**. The audit asserts the map covers every sector present in the
data, so it cannot silently rot when a sector is added.

> **General rule learned here: a whitelist keyed on data values will rot.** Derive it from
> the data, or assert key-set equality in a test.

---

# Part V — Engineering

## 24. Tooling and generated files

### `tools/` — shipped with the repo

| Script | Lines | What it does |
|---|---|---|
| `audit.py` | 292 | 37 whole-system checks against the **real** engine (§25) |
| `check_answers.py` | 334 | 353 realistic spoken answers, per slot |
| `check_nlu.py` | 265 | lexicon self-consistency, 31 cases |
| `check_telephony.py` | 142 | 16 audio-format checks |
| `extract_flow.py` | 146 | `InterviewFlow.kt` → `server/data/flow.json` |
| `gen_lexicon.py` | 486 | builds `assets/lexicon.json` |
| `gen_i18n.py` | 503 | builds `assets/i18n.json` |
| `gen_icons.py` | 267 | every launcher icon from `brand/logo_premium.png` |

### Generated files — never hand-edit

| Output | Generator |
|---|---|
| `assets/lexicon.json` | `tools/gen_lexicon.py` |
| `assets/i18n.json` | `tools/gen_i18n.py` |
| `server/data/flow.json` | `tools/extract_flow.py` |
| `OnDeviceNlu.DISTRICT_ALIASES` | `work/gen_district_aliases.py` |
| every launcher icon | `tools/gen_icons.py` |

### Change recipes

```bash
# vocabulary
edit tools/gen_lexicon.py   →  python3 tools/gen_lexicon.py
                            →  python3 tools/check_nlu.py && python3 tools/check_answers.py

# UI copy
edit tools/gen_i18n.py      →  python3 tools/gen_i18n.py  →  python3 tools/audit.py

# interview script
edit ai/InterviewFlow.kt    →  python3 tools/extract_flow.py     # syncs the server

# anything at all
python3 tools/audit.py
```

### `CHANGES.md`

696 lines, a numbered defect log running to **entry 73**. Each entry records the
**symptom**, the **real cause** and the **fix** — not just what changed. Read entry 65
before touching asset deserialisation and entry 66 before touching the state/district
flow.

---

## 25. Test suites

Four suites, all runnable offline from the repository root, all green as of
2026-10-01.

```bash
python3 tools/audit.py            # 37/37
python3 tools/check_answers.py    # 353/353
python3 tools/check_nlu.py        # 31 cases · 3,596 forms · 2,906 resolve to own detector
python3 tools/check_telephony.py  # 16/16
```

### `audit.py` — 37 checks against the real engine

Not a mock. It boots the actual engine and the actual catalogue.

**1. Assets** — job roles load and filter to 476 · 13 sectors · every role has a `qp_code`
· 660 centres load · **every centre has a source URL** · confidence values valid · 187
districts / 5 states · every centre district is a known district · district_economy keys
known (187, 0 unknown) · GIA rules present for all 5 states · i18n key parity (156 × 6) ·
no empty i18n strings · lexicon 3,596 forms / 38 categories.

**2. Geography resolution** — all 187 districts have aliases · every district has a native
script form · **550 district forms resolve, state-scoped and unscoped, 0 failures** · 66
state forms resolve · state back-fills from district for all 187.

**3. Interview flow, all six languages** — the slot order matches · 54 prompts present
across 6 languages, 0 missing · a complete nine-slot interview plus results in **en, ta,
hi, te, kn, ml** (Salem, Salem, Varanasi, Anantapur, Belagavi, Alappuzha).

**4. Recommender across all districts** — **748 district × profile runs, 0 empty** · every
result is explainable (0 results with no factors) · confidence clamped 35–99 · a centre is
attached wherever the district has one (185 districts, 0 misses).

**5. Kotlin ↔ Python parity** — `DISTRICT_ALIASES` keys match (kt 187 vs py 187) · alias
form count matches (kt 363 vs py 363) · slot order matches (9 vs 9) ·
`ANNEXURE1_SECTOR` covers every sector in the data (13 mapped, none missing).

**6. Safety** — no API key anywhere in source · `config.json` is still a placeholder · no
silent `catch → emptyList` in the data layer.

### `check_answers.py` — 353 realistic utterances

The complement to `check_nlu.py`. Where that one proves the lexicon is internally
consistent, this one asks the question that matters in the field: **for each question the
interview asks, is a realistic spoken answer understood?** The utterances are written as a
person would actually say them — full sentences, code-mixed, contracted, hedged, negated,
with filler. Result: **353/353, 100 %**, including `CONSTRAINTS` 38/38 and `STATE` 27/27.

### `check_nlu.py` — lexicon self-consistency

31 scenario cases, **2,906 surface forms each resolving to its own detector**, and 17
occupation labels confirmed translated into all six languages.

### `check_telephony.py` — 16 audio checks

Guards the float32 bug specifically: format detection, resampling to 8 kHz, peak
normalisation, µ-law round-trip against the reference, non-WAV input raising `NotWav`, and
the output re-decoding to 8,000 samples at 8,000 Hz.

### Manual device testing

`TEST_SCRIPTS.md` (95 lines) is the on-device script. **Every build is installed and
tested on a real Android phone**; that loop is the only source of truth for microphone,
TTS voice availability and ASR language-pack behaviour, none of which can be reproduced in
a sandbox. The pass that matters: aeroplane mode on → each of the six languages → a full
interview by voice → results → detail screen → the centre's phone link.

---

## 26. Known limitations

Stated plainly, because a limitation you declare is a strength and one a judge finds is
not.

| # | Limitation | Impact | Fix |
|---|---|---|---|
| 1 | **WhatsApp is not finished.** Webhook, media download and audio pipeline work; the conversation loop is still being wired. | One of three channels is a demo, not a deployment | Finish the loop |
| 2 | **350 of 660 centres are LIKELY, not CONFIRMED**, and 268 have no published phone number. | Both are labelled, never asserted — but a judge may probe | Spot-verify 20–30 by phone |
| 3 | **95 of 476 roles carry no NSQF level and 100 no notional hours.** | Rendered `—`; ranking uses a neutral internal default | Source the missing QP metadata |
| 4 | **Offline speech recognition needs the device language pack.** | Tamil/Telugu ASR may fall back to network on a bare handset | Pre-install packs; say it out loud before a judge finds it |
| 5 | **No officer dashboard.** Profiles export as CSV; the MIS integration is designed, not built. | No admin view yet | Build against one state's MIS |
| 6 | **Five states, not twenty-eight.** | Coverage is deep, not national | Each state is a crawl and a review, not a rewrite |
| 7 | **The deployed Render instance runs an older build** than `main`, so `/ivr/audio` is still silent there. | Live IVR demo fails until redeployed | Redeploy from `main` |
| 8 | **64 malformed rows in the source catalogue are filtered, not fixed.** | 476 shown instead of 540 | Clean the source data |
| 9 | **Parametric TTS voices are clear, not warm.** | Tone is functional rather than friendly | Bhashini neural voices when quota allows |
| 10 | **No analytics.** | No adoption evidence | Deliberately out of scope under DPDP |

### Explicitly *not* worth doing

* **More states.** Five is already unusual for a hackathon entry, and depth beats breadth
  when a judge picks one district and probes.
* **Swapping the on-device NLU for a bundled small LLM.** The lexicon engine is faster,
  testable, and every decision it makes can be explained. That explainability is worth more
  than fluency, and it is the whole reason a funding officer can defend a recommendation.
* **More UI screens.** Ten is enough; polish the three that get demoed.

---

## 27. Glossary

| Term | Meaning |
|---|---|
| **PM-AJAY** | Pradhan Mantri Anusuchit Jaati Abhyuday Yojana — the umbrella scheme |
| **GIA** | Grant-in-Aid — the PM-AJAY component that funds skilling |
| **MoSJE** | Ministry of Social Justice and Empowerment — the owning ministry |
| **NSQF** | National Skills Qualifications Framework — levels 1–10 |
| **QP code** | Qualification Pack code, e.g. `AGR/Q4302` — identifies a job role |
| **NQR** | National Qualifications Register — where QP codes are published |
| **SSC** | Sector Skill Council — the body that owns a QP |
| **NCVET** | National Council for Vocational Education and Training |
| **RPL** | Recognition of Prior Learning — 32–80 h, the track for existing skill |
| **STT** | Short Term Training — 200–600 h |
| **EDP** | Entrepreneurship Development Programme — 80 h |
| **LTT** | Long Term Training — 6–12 months |
| **ITI** | Industrial Training Institute |
| **KVK** | Krishi Vigyan Kendra — ICAR farm science centre |
| **RSETI** | Rural Self Employment Training Institute — bank-run |
| **JSS** | Jan Shikshan Sansthan |
| **DET** | Directorate of Employment and Training (state) |
| **Slot** | One of the nine things the interview collects |
| **Surface form** | A spelling a speech recogniser plausibly returns for a concept |
| **Fragment** | A partial profile produced by one turn of NLU |
| **DTMF** | Dual-Tone Multi-Frequency — telephone keypad tones |
| **TwiML** | Twilio Markup Language — the XML a voice webhook returns |
| **DID** | Direct Inward Dialling — a rentable phone number |
| **Hop** | One TwiML fetch; a Twilio trial call allows 10 |
| **G.711 µ-law** | 8 kHz telephony codec, 14-bit magnitude |
| **Bhashini** | Government of India's national language stack (ASR/TTS) |
| **DPDP Act 2023** | Digital Personal Data Protection Act |
| **ULCA** | Universal Language Contribution API — Bhashini's registration portal |

---

*JanDwar — Complete Project & Application Documentation · SIH 2026 PS 26097 ·
app v2.9 (versionCode 11) · server v2.4 · documented 2026-10-01.
Every figure here was read from the source or produced by running the code.*
