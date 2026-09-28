# JanDwar — Gateway for Citizens

**Smart India Hackathon 2026 · Problem Statement 26097**
*AI-Driven Voice Assistant for Livelihood Mapping and NSQF-Aligned Skilling
Recommendations for SC Communities under the GIA component of PM-AJAY.*

Package: `in.jandwar.app` · minSdk 24 · targetSdk 35 · Kotlin 2.0.21 · Jetpack Compose

---

## 1. Build it

```bash
# From the project root
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

In **Android Studio**: `File → Open…` → select this folder → wait for the Gradle
sync → `Run ▶`. No other setup is needed.

**Requirements**

| | |
|---|---|
| JDK | 17 (Android Studio ships one; `File → Settings → Build → Gradle → Gradle JDK`) |
| Android SDK | Platform 35 + Build-Tools 35.0.0 |
| Gradle | 8.14.3 (the wrapper downloads it automatically) |
| AGP | 8.7.3 |

If your machine has 16 GB+ of RAM, you can speed builds up by raising the heap
in `gradle.properties` (`org.gradle.jvmargs=-Xmx4096m`).

---

## 2. The one optional thing you may want to fill in

Open **`app/src/main/assets/config.json`**:

```json
{
  "groq_api_key": "PASTE_YOUR_GROQ_API_KEY_HERE",
  "groq_model": "llama-3.3-70b-versatile"
}
```

* **Leave it as-is** → the app runs the built-in on-device conversation engine.
  Everything works: the interview, matching, results, centres. No internet needed.
* **Paste a free key** from <https://console.groq.com/keys> → the interview is
  driven by a real LLM, so it flows naturally, handles digressions, and phrases
  every question in the user's own language.

The app treats placeholder text (`PASTE_…`, `your_…`, `<…>`, empty) as *no key*,
so an unedited file never causes an error. Nothing else needs changing.

> ⚠️ **Security note:** the previous revision of this repository had a live Groq
> API key hard-coded in `get_groq_models.js`, `list_models.js` and `test_groq.js`.
> Those files have been deleted, but the key is still in the git history —
> **revoke it** at <https://console.groq.com/keys> and issue a new one.

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
        ┌──────────────┐    online + key    ┌────────────┐
        │  HybridNlu   │───────────────────▶│ GroqClient │
        └──────┬───────┘                    └────────────┘
               │ always                            │ on failure
               ▼                                   │
        ┌──────────────┐                           │
        │ OnDeviceNlu  │◀──────────────────────────┘
        └──────┬───────┘
               ▼
        ┌──────────────┐
        │ AppRepository│  → NSQF matching, skill gaps, centres, GIA funding
        └──────────────┘
```

**The rule that drives the design: the assistant can never dead-end.**

| What breaks | What the user sees |
|---|---|
| No API key | Scripted-but-warm on-device interview in all 6 languages |
| No internet | Same — plus offline speech recognition where available |
| Groq call fails mid-interview | Silently falls back for that turn; conversation continues |
| No microphone / permission denied | Switches to typing, keeps the same conversation |
| No TTS voice for the language | Questions stay readable on screen |
| Speech not understood twice | Re-prompts, then offers the keyboard |

`OnDeviceNlu` also runs on *every* cloud turn, so a district name or interest the
LLM overlooked is still captured, and the cloud can never regress the profile.

### What the interview collects

Education · family / traditional occupation · current livelihood · skills &
interests · self-employment vs wage preference · mobility & physical constraints
· district — the seven inputs the problem statement asks for, gathered
conversationally rather than as a form.

---

## 4. Matching engine (`AppRepository.matchRoles`)

516 NSQF qualification packs scored against the profile. Every point awarded
produces a matching `MatchFactor`, so the detail screen can explain *exactly*
why a course surfaced.

| Signal | Weight |
|---|---|
| First interest hit / each additional | +85 / +18 |
| Family occupation continuity | +55 |
| Free-text skill match | +40 |
| Current livelihood match | +32 |
| Education requirement met / unmet | +30 / −22 per rank gap |
| PM-AJAY GIA fundable sector | +26 |
| Self-employment vs wage preference fit | +24 |
| Mobility LOCAL with a centre / without | +20 / −14 |
| Verified centre in district | +12 (+10 agri & food) |
| Long-term pack below Class 10 | −14 |
| Physically demanding role vs stated constraint | −55 |

* Education is a **soft gate**: instead of hiding a course, the app flags
  *"needs more schooling — ask the centre about a bridge course."*
* Results are capped at 6 with a **max 2 per sector** diversity rule, so a
  beneficiary sees genuine alternatives rather than six variants of one trade.
* Confidence is normalised to 35–99 % against the best available match.

---

## 5. Data (all bundled, all offline)

| File | Contents |
|---|---|
| `assets/job_roles.json` | 516 NSQF qualification packs across 7 sectors |
| `assets/centres.json` | 20 verified training centres |
| `assets/districts.json` | 38 TN districts (20 with a centre, 18 without) |
| `assets/i18n.json` | 136 UI strings × 6 languages + interest vocabulary |
| `assets/config.json` | AI key placeholder (see §2) |

Languages: English, தமிழ், हिन्दी, తెలుగు, ಕನ್ನಡ, മലയാളം.

PM-AJAY GIA priority domains (agriculture, food processing, construction,
handloom & textile) account for **343 of the 516** packs; those are the ones
badged as fundable.

To change UI copy, edit `/home/user/work/gen_i18n.py`-style generators or the
JSON directly — the repository resolves `lang → en → built-in fallback → key`,
so a missing translation degrades instead of crashing.

---

## 6. Project layout

```
app/src/main/java/in/jandwar/app/
├── JanDwarApp.kt                  @HiltAndroidApp
├── MainActivity.kt                NavHost + transitions
├── ai/
│   ├── AiConfig.kt                reads assets/config.json, placeholder-aware
│   ├── ConversationEngine.kt      speak → listen → understand → speak loop
│   ├── GroqClient.kt              cloud LLM: interview + result narration
│   ├── HybridNlu.kt               cloud → on-device router, never errors
│   ├── OnDeviceNlu.kt             offline multilingual slot extraction
│   ├── InterviewFlow.kt           conversational script, 6 languages
│   ├── NluEngine.kt               contract
│   ├── ProfileFragment.kt         accumulating profile + slot model
│   ├── TtsSpeaker.kt              text-to-speech with voice selection
│   └── VoiceListener.kt           speech recognition with typed failures
├── data/
│   ├── local/AssetDataSource.kt
│   ├── model/Models.kt
│   └── repository/AppRepository.kt matching engine + i18n
├── di/AppModule.kt
├── ui/
│   ├── components/CommonComponents.kt   design system
│   ├── navigation/NavGraph.kt
│   ├── screens/                   10 screens
│   ├── theme/                     colour, type, Material 3 theme
│   └── viewmodel/
└── util/NetworkMonitor.kt
```

---

## 7. Honesty guarantees

The app never invents a course, centre, fee, subsidy or batch date. When a
district has no verified centre it says so and points at TAHDCO. Every
recommendation screen carries the confirm-before-enrolling disclaimer, and the
funding figure shown is the published GIA rule (up to ₹50,000 or 50 % of asset
cost with loan, whichever is lower).

Answers never leave the phone unless a Groq key is configured; with a key, only
the conversation text is sent — no identifiers.

---

## 8. Historical documents

`APP_SPEC.md` and `docs_JanDwar_Proposal.md` are earlier planning documents kept
for reference. Where they disagree with this README or the code, **the code is
authoritative** — they predate the current architecture.
