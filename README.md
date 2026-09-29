# JanDwar — Gateway for Citizens

**Smart India Hackathon 2026 · Problem Statement 26097**
*AI-Driven Voice Assistant for Livelihood Mapping and NSQF-Aligned Skilling
Recommendations for SC Communities under the GIA component of PM-AJAY.*

Package `in.jandwar.app` · **v2.8 (versionCode 10)** · minSdk 24 · targetSdk 35
· Kotlin 2.0.21 · Jetpack Compose

> **The app is offline-first and offline-complete.** Every feature — the spoken
> interview, slot extraction in six languages, NSQF matching, skill-gap
> reasoning, centre lookup, funding rules — runs on the device with no network
> and no API key. A Groq key is an *optional* upgrade that makes the
> conversation freer; it is not required for anything.

---

## 1. Build it

```bash
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
```

In **Android Studio**: `File → Open…` → select this folder → wait for the
Gradle sync → `Run ▶`. Nothing else to configure.

| | |
|---|---|
| JDK | 17 |
| Android SDK | Platform 35 + Build-Tools 35.0.0 |
| Gradle | 8.14.3 (wrapper downloads it) |
| AGP | 8.7.3 |

On a machine with 16 GB+ RAM, raise `org.gradle.jvmargs` in `gradle.properties`
to `-Xmx4096m` for faster builds.

---

## 2. The one optional thing

`app/src/main/assets/config.json`:

```json
{
  "groq_api_key": "PASTE_YOUR_GROQ_API_KEY_HERE",
  "groq_model": "llama-3.3-70b-versatile"
}
```

* **Leave it alone** → the on-device engine runs the whole interview. This is
  the tested, primary path.
* **Paste a free key** from <https://console.groq.com/keys> → the LLM phrases
  the questions and handles digressions more loosely.

Placeholder text (`PASTE_…`, `your_…`, `<…>`, empty) is treated as *no key*, so
an unedited file never errors.

---

## 3. How the assistant works

```
        ┌──────────────┐   speech    ┌───────────────┐
        │ TtsSpeaker   │────────────▶│  Beneficiary  │
        └──────▲───────┘             └───────┬───────┘
               │                             │ voice
       next question                         ▼
        ┌──────┴────────────┐        ┌───────────────┐
        │ ConversationEngine│◀───────│ VoiceListener │
        └──────┬────────────┘  text  └───────────────┘
               │ utterance
               ▼
        ┌──────────────┐   key + online   ┌────────────┐
        │  HybridNlu   │─────────────────▶│ GroqClient │   (optional)
        └──────┬───────┘                  └─────┬──────┘
               │ ALWAYS                         │ on any failure
               ▼                                │
        ┌──────────────┐                        │
        │ OnDeviceNlu  │◀───────────────────────┘
        │ 3 596 forms  │
        └──────┬───────┘
               ▼
        ┌──────────────┐
        │ AppRepository│ → NSQF matching · skill gaps · centres · GIA funding
        └──────────────┘
```

**The rule that drives the design: the assistant can never dead-end.**

| What breaks | What the user sees |
|---|---|
| No API key | Full on-device interview in all 6 languages |
| No internet | Same, plus offline speech recognition where the device has the pack |
| Groq fails mid-interview | Silent fallback for that turn; conversation continues |
| Mic denied / absent | Switches to typing, same conversation |
| No TTS voice for the language | Questions stay readable on screen |
| Not understood twice | Re-prompts, then offers the keyboard |

`OnDeviceNlu` runs on **every** turn, cloud or not, so a district the LLM missed
is still captured and the cloud can never regress the profile.

### What the interview collects — 9 slots

Education · family / traditional occupation · current livelihood · skills &
interests · self-employment vs wage preference · mobility · physical
constraints · **state** · **district**.

State is asked, never inferred, and the district question is then scoped to
that state: *"And which district in Kerala is your home?"*

---

## 4. The offline NLU

`OnDeviceNlu.kt` + `Lexicon.kt`, driven by `assets/lexicon.json` —
**3 596 surface forms across 38 categories**, covering English, Tamil, Hindi,
Telugu, Kannada, Malayalam in native script *and* romanised, including
code-mixed and misspelled forms people actually speak.

Matcher rules (Python in `server/app/core/lexicon.py` is the reference
implementation; Kotlin mirrors it exactly):

* one `fold()` applied to both forms and input; tokenise on whitespace
* phrases match as substrings; Indic single words only when ≥ 4 chars;
  Latin single words must match a whole token
* exact matches win via a global owner index; fuzzy matching only for unseen
  tokens, first character must agree
* edit tolerance scales on `max(len(a), len(b))`
* **longest matched form wins** — this replaced a precedence list that could
  not express specificity and inverted under negation

Things it gets right that a naive keyword matcher does not:

| Said | Understood as |
|---|---|
| "college 2nd year" | Class 12 **completed**, currently a Student |
| "12th dropout" | Class 10 completed, not a student |
| "I studied up to 10th" | Class 10 — *not* Uttar Pradesh |
| "I speak Tamil" | a language remark — *not* Tamil Nadu |
| "anywhere in my district" vs "anywhere in the state" | two different mobility answers |
| "no problem at all" | constraints answered "None", not left blank |

**Never hand-edit `assets/lexicon.json`.** Edit `work/lexicon_extra.py`, run
`python3 work/gen_lexicon.py`, then both test suites.

---

## 5. Matching engine (`AppRepository.matchRoles`)

**476 NSQF qualification packs** scored against the profile. Every point
produces a `MatchFactor`, so the detail screen explains exactly why a course
surfaced.

| Signal | Weight |
|---|---|
| First interest hit / each additional | +85 / +18 |
| Family occupation continuity | +55 |
| Free-text skill match | +40 |
| Current livelihood match | +32 |
| Education requirement met / unmet | +30 / −22 per rank gap |
| PM-AJAY GIA eligible sector | +26 |
| Self-employment vs wage preference fit | +24 |
| Mobility LOCAL with a centre / without | +20 / −14 |
| Centre in district | +12 (+10 agri & food) |
| Long-term pack below Class 10 | −14 |
| Physically demanding role vs stated constraint | −55 |

* Education is a **soft gate**: a course above the person's level is flagged
  *"needs more schooling — ask about a bridge course"*, not hidden.
* Top 6, **max 2 per sector**, so the user sees real alternatives.
* Confidence normalised to 35–99 % against the best available match.

---

## 6. Data — all bundled, all offline

| File | Contents |
|---|---|
| `assets/job_roles.json` | 540 rows → **476 usable NSQF packs**, 13 sectors |
| `assets/centres.json` | **660 training centres** across 5 states — 294 CONFIRMED, 350 LIKELY, 16 UNVERIFIED, each with its source URL and retrieval date |
| `assets/districts.json` | **187 districts** (185 with a centre) + `by_state` breakdown |
| `assets/district_economy.json` | 187 rows; 52 carry a researched "what this district runs on" note |
| `assets/gia_funding_rules.json` | PM-AJAY GIA rules researched per state, all 5 |
| `assets/i18n.json` | **156 UI strings × 6 languages** + 17 occupation labels × 6 |
| `assets/lexicon.json` | **3 596 NLU surface forms / 38 categories** (generated) |
| `assets/config.json` | optional AI key placeholder |

**Coverage by state**

| State | Districts | Centres |
|---|---|---|
| Tamil Nadu | 38 | 180 |
| Uttar Pradesh | 75 | 109 |
| Karnataka | 31 | 142 |
| Andhra Pradesh | 29 | 83 |
| Kerala | 14 | 146 |

Languages: English, தமிழ், हिन्दी, తెలుగు, ಕನ್ನಡ, മലയാളം.

**Funding.** `gia_funding_rules.json` records, for three of the five states,
that the central GIA guidelines state *no* QP/sector whitelist. All 13 sectors
in the catalogue are therefore eligible; duration decides the programme type
(RPL 32–80 h · short-term 200–600 h · long-term > 600 h), not eligibility.

---

## 7. Project layout

```
app/src/main/java/in/jandwar/app/
├── JanDwarApp.kt                  @HiltAndroidApp
├── MainActivity.kt                NavHost + transitions
├── ai/                            12 files
│   ├── AiConfig.kt                reads config.json, placeholder-aware
│   ├── ConversationEngine.kt      speak → listen → understand → speak
│   ├── GroqClient.kt              optional cloud LLM
│   ├── HybridNlu.kt               router; never returns an error
│   ├── OnDeviceNlu.kt             offline slot extraction, 6 languages
│   ├── Lexicon.kt                 longest-match fuzzy matcher
│   ├── InterviewFlow.kt           conversational script, 6 languages
│   ├── NluEngine.kt               contract
│   ├── ProfileFragment.kt         accumulating profile, 9 slots
│   ├── TtsSpeaker.kt / VoiceListener.kt
│   └── MicEarcon
├── data/{local,model,repository}/
├── di/AppModule.kt
├── ui/{components,navigation,screens,theme,viewmodel}/   10 screens
└── util/NetworkMonitor.kt

server/          FastAPI mirror: REST + Twilio IVR + WhatsApp webhooks
tools/           check_nlu.py · check_answers.py · extract_flow.py
work/            generators: gen_lexicon.py · gen_i18n.py · gen_icons.py
```

---

## 8. Tests

```bash
python3 tools/check_nlu.py        # lexicon self-audit
python3 tools/check_answers.py    # realistic spoken answers, per question
```

```
check_nlu.py      31 NLU cases · 17 occupation labels × 6 languages
                  2 906 / 2 906 surface forms resolve to their own detector

check_answers.py  EDUCATION 56 · EDUCATION_IN_PROGRESS 20 · FAMILY_OCCUPATION 46
                  CURRENT_LIVELIHOOD 47 · INTERESTS 48 · PREFERENCE 35
                  MOBILITY 36 · CONSTRAINTS 38 · STATE 27
                  TOTAL 353 / 353  (100 %)
```

`check_answers.py` is the one that matters: none of its utterances were copied
from the lexicon, and enum slots assert the *value*, because filling a slot
with the wrong value is worse than leaving it empty. Both exit non-zero on
failure. Run them after **any** vocabulary change.

---

## 9. Low-connectivity channels (`server/`)

A FastAPI service mirrors the same engine for people with no smartphone:

* `GET /api/{health,languages,roles,centres}`, `POST /api/{session,say,match}`
* `GET|POST /ivr/voice`, `POST /ivr/{lang,turn}` — Twilio IVR, keypad wins over
  speech, per-language Google voices (`ta-IN-Standard-C` etc.; a bare
  `language=` attribute without an explicit `voice` is broken for every Indian
  language)
* `GET|POST /whatsapp/webhook` — voice notes and text

See `server/IVR.md` and `render.yaml`.

---

## 10. Honesty guarantees

Never invents a course, centre, fee, subsidy or batch date. Every centre
carries its evidence level and source URL; where a district has no centre the
app says so. The funding figure shown is the published GIA rule. Answers never
leave the phone unless a Groq key is configured, and then only conversation
text — no identifiers.

---

## 11. History

`CHANGES.md` is the engineering log — 69 numbered entries, each with the
symptom, the actual cause and the fix. `docs_JanDwar_Proposal.md` is an early
planning document; where it disagrees with the code, **the code is
authoritative**.
