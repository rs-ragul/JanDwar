# JanDwar / Thozhil Thunai — தொழில்துணை (SIH26097)

**A voice-first, offline-capable, premium native Android app for PM-AJAY livelihood discovery**

> SIH Problem Statement ID: SIH26097 — AI-Driven Voice Assistant for Livelihood Mapping and NSQF-Aligned Skilling Recommendations for SC Communities under GIA component of PM-AJAY
> Organization: Ministry of Social Justice & Empowerment (MoSJE)

JanDwar is an offline-first, voice-enabled Android app helping rural citizens and youth in Tamil Nadu discover **516 real NSQF-aligned qualification packs** and **20 verified TANUVAS/VUTRC training centres** — in their own language (ta, en, hi, te, kn, ml), with honest data-gap disclosure and full functionality even with zero internet.

**Premium rebuild (v2.0): Kotlin + Jetpack Compose + Material3 + MVVM + Hilt + Navigation**

---

## ✨ Key Features (Fixed & Premium)

### 1. Voice-First Empathetic AI Assistant (SIH26097 Compliant)
```
Tier 1 Online:  Bhashini ASR → Bhashini NMT (→ en) → Groq Llama 3.1 8B (empathetic prompt) → ProfileFragment
Tier 2 Offline: Android SpeechRecognizer → DeterministicParser (expanded) → empathetic local questions
Tier 3 Fallback: Typing + skill-gap + centre verification + conversation history bubbles
```
- **Empathetic Conversational Interview**: Not form-filling. JanDwar speaks like trusted village elder per problem statement - "How does your family traditionally work?" not "Enter family occupation"
- **Full PM-AJAY Field Coverage (10 fields)**:
  - education, family/traditional occupation, current livelihood, skills/interests, preference self vs wage, mobility/physical constraints, district, local economic realities, physicalConstraints, localOpportunity
- Interactive pulsing orb (Compose infinite transition, radial gradient indigo→teal) + conversation history bubbles (chat UI)
- Groq LLM `llama-3.1-8b-instant` (free 14.4k/day) with empathetic system prompt - extracts all 10 fields, generates `next_question_native` warm
- **Offline-first**: DeterministicParser keyword tables (Tamil/Hindi/Telugu/Kannada/Malayalam) + empathetic local questions ordered per problem statement (education → familyOccupation → currentLivelihood → interests → preference → mobility → district)
- **Low-connectivity alternatives**: Typed fallback, repeat question, profile summary chips showing what AI understood
- **To enable Groq natural conversation**: Add `groq_api_key` to `app/src/main/assets/config.json` (get free key from https://console.groq.com/keys). Offline still works with warm questions.

### 2. Audio Pipeline (Fixed)
- **STT**: `AndroidSpeechGateway` implements `SpeechGateway` interface, BCP-47 locale (ta-IN etc.), `EXTRA_PREFER_OFFLINE` for patchy network, partial results
- **TTS**:
  - **Sarvam AI bulbul:v1 (meera)**: Premium natural voice, `target_language_code` mapping
  - **Bhashini TTS**: Female voice 22050Hz, Government of India free, base64 WAV handling via MediaPlayer temp file (auto-delete)
  - **Android TTS**: Network/offline voices, locale-aware, voice selection via SharedPreferences `tts_engine` (auto/sarvam/bhashini/android/android_offline)
- **Fixes**: SharedPreferences mismatch (was `jandwar` vs `app_prefs` → now single `jandwar_prefs` via Hilt), Sarvam apiKey empty at init → now lazy read, Groq model invalid → now valid, Bhashini error handling added

### 3. Data-Backed Course Finder (Fixed)
- **516 QPs** across 7 SSCs (ASCI 137, MESC 116, CSDCI 106, TSC 63, FICSI 37, AMHSSC 37, IASC 20)
- **343 fundable** (agriculture 137 + food 37 + construction 106 + handloom 63) per PM-AJAY Annexure-I
- **20 verified centres** (TANUVAS/VUTRC), 38 TN districts (20 with centre, 18 without)
- **Matching rules** (per APP_SPEC.md):
  1. Education gate: role requiredEduRank <= user rank (0=below8,1=8th,2=10th,3=12th,4=ITI,5=grad)
  2. Long-term (≥600h) blocked if edu < Class 10 (PM-AJAY rule)
  3. Interest affinity: sector mapping (dairy→agriculture, textile→handloom/apparel, etc.)
  4. Rank by closeness, tie-break lower NSQF for low-literacy
  5. Centres only if truly in user's district, else CENTRE_DATA_GAP note "confirm with TAHDCO"
  6. Skill-gap line per recommendation: "Entry: Class 10 — you qualify" or "Needs Class 10... see short-term instead"
- **Fixes**: eduRank gaps fixed, requiredEdu mapping corrected, interest matching precise, skill-gap added, centre lookup case-insensitive, level "(inferred)" handling

### 4. Premium Native UI (Kotlin Compose Material3)
- **Design system**: Indigo #1B3A8C → Teal #0FA3A3 gradient, Saffron #FF8A1F accent, Paper #F7FAFC, rounded-2xl (22-28dp) cards, soft elevation 4-8dp, spring animations
- **Typography**: Material3 Typography, Tamil shaping Noto Sans Tamil, overflow-wrap, high-contrast
- **Screens**: Splash (gradient + logo) → Language (grid 2, 124dp cards) → Onboarding (3 slides, dots) → Home (voice orb + personalized + browse + stats 516/343/38/20) → Intake (chips + district bottom sheet) → Results (ranked with skill-gap) → Detail (centre + call + disclaimer) → Courses (search 516) → Voice (pulsing orb + typed fallback) → Settings (language, offline AI 45MB mock download, TTS engine, privacy)
- **Dark mode**: Light/Dark color schemes, statusBar handling
- **Navigation**: Jetpack Navigation Compose with sealed Screen routes, Hilt ViewModel
- **Offline**: All data bundled in assets, Room caching ready, DataStore for prefs

### 5. Multilingual (6 Langs, ONE language at a time)
- `data/i18n.json` holds all strings + interest chips for ta,en,hi,te,kn,ml — no bilingual mixing
- `AppRepository.tr()` + `extraTranslations()` ensures no missing key crashes

---

## 🏗️ Architecture (MVVM + Repository + Hilt)

```
app/src/main/java/in/jandwar/app/
├── ThozhilThunaiApp.kt          @HiltAndroidApp
├── MainActivity.kt              Compose NavHost, permission
├── data/
│   ├── model/Models.kt          JobRole, Centre, DistrictsData, EducationLevel, Preference, Mobility, UserProfile, MatchedRole
│   ├── local/AssetDataSource    Loads JSON from assets with error handling
│   └── repository/AppRepository Caching, tr(), interestLabel(), matchRoles() with skill-gap
├── ai/
│   ├── AiConfig.kt              Loads keys from assets/config.json via Hilt, graceful offline
│   ├── ProfileFragment.kt       Structured output (edu, pref, interests, district, mobility, nextQuestion)
│   ├── NluExtractor.kt          Interface
│   ├── DeterministicParser.kt   Offline keyword parser (ta/hi keywords) + validate()
│   ├── GroqExtractor.kt         Llama 3.1 8B, json_object response_format, validates via DeterministicParser
│   ├── BhashiniGateway.kt       NMT + TTS, base64 audio, mapToBhashiniLang, isAvailable()
│   ├── SarvamGateway.kt         bulbul:v1 meera, lazy apiKey, target_language_code mapping
│   ├── TieredNluExtractor.kt    Tier1 Groq+Bhashini translate → Tier3 Deterministic + local questions
│   ├── SpeechGateway.kt         Interfaces SpeechGateway, Speaker, Translator
│   ├── AndroidSpeechGateway.kt  SpeechRecognizer wrapper, BCP-47, offline pref
│   ├── AndroidTtsSpeaker.kt     TTS wrapper, voice selection network/offline
│   └── ConversationController.kt State machine, MediaPlayer temp file, NetworkCapabilities check
├── di/AppModule.kt              Provides Context, SharedPreferences, AssetDataSource
├── ui/
│   ├── theme/Color, Type, Theme Material3 light/dark, brand colors
│   ├── navigation/NavGraph      Sealed Screen routes
│   ├── components/CommonComponents GradientHeader, PremiumCard, StatCard, PulsingOrb, ChipItem
│   ├── viewmodel/AppViewModel   lang, profile, results, search, ttsEngine, onboarding
│   ├── viewmodel/VoiceViewModel Manages ConversationController
│   └── screens/                 8 Compose screens (Splash, Language, Onboarding, Home, Intake, Results, Detail, Courses, Voice, Settings)
└── util/NetworkUtils.kt         isConnected() via NetworkCapabilities
assets/
├── config.json                  Placeholder (gitignored, empty keys = offline mode)
├── job_roles.json               516 QPs
├── centres.json                 20 centres
├── districts.json               38 districts
└── i18n.json                    6 langs strings + interests
```

---

## 🔧 Build and Run

### Prerequisites
- Android Studio Hedgehog+ (Kotlin 1.9.22, AGP 8.7.3, Compose BOM 2024.02.00)
- JDK 17
- Android SDK 34, minSdk 24
- Device/emulator API 24+ (Android 7.0+)

### Steps
1. Clone:
```bash
git clone https://github.com/rs-ragul/Thozhil-Thunai.git
cd Thozhil-Thunai
```
2. Create `app/src/main/assets/config.json`:
```json
{
  "bhashini_user_id": "YOUR_BHASHINI_USER_ID",
  "bhashini_inference_key": "YOUR_BHASHINI_INFERENCE_KEY",
  "bhashini_app_id": "YOUR_BHASHINI_APP_ID",
  "groq_api_key": "YOUR_GROQ_API_KEY",
  "sarvam_api_key": "YOUR_SARVAM_API_KEY"
}
```
(Leave empty for offline-only mode — app degrades gracefully)

3. Open in Android Studio → Sync Gradle
4. Run `app` on device/emulator

### Command Line
```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Testing

### Manual Test Cases
- **Tamil voice**: Say "நான் பத்தாம் வகுப்பு படித்துள்ளேன், எனக்கு பால் பண்ணையில் ஆர்வம், ஈரோடு" → should extract edu=class10, interest=dairy, district=Erode, ask preference/mobility
- **Offline**: Airplane mode → intake works, matching works, results show, voice shows typed fallback
- **No centre**: Select Chennai (no verified centre) → shows "No verified centre data - confirm with TAHDCO"
- **Long-term block**: Select Below 8th → no long-term (≥600h) courses shown
- **Skill-gap**: 8th pass user viewing level 4 course → "Needs Class 10 for this long-term course; see short-term instead"

### Unit Tests (to add)
- `AppRepository.matchRoles()` parity with Python engine
- `DeterministicParser.parse()` keyword tables
- `ConversationController` state transitions

---

## 📊 Data Asset (Verified)

| Asset | Count | Notes |
|-------|-------|-------|
| NSQF QPs | 516 | 0 duplicate, 7 SSCs |
| Fundable | 343 (66%) | agriculture+food+construction+handloom |
| Official NSQF levels | 430 | +86 "(inferred)" where source blank |
| Verified centres (TN) | 20 | TANUVAS/VUTRC, address+phone+confidence |
| TN districts | 38 | 20 have centre, 18 don't |

---

## 🔐 PM-AJAY Rules (Encoded)

- Education ceiling: role entry ≤ user level
- Long-term needs Class 10 (hard-blocked)
- Fundable only Annexure-I domains (apparel not claimed)
- Asset subsidy: "up to Rs.50,000 or 50% with loan, whichever lower" only
- Funding 100% central, no per-trainee cap quoted

---

## 🌐 Deployment Channels (Architecture)

- **Mobile/kiosk**: Current app (primary)
- **IVR (feature phones)**: Same ConversationController driven by telephony IVR tree
- **WhatsApp voice-notes**: Same gateway behind WhatsApp Business webhook

---

## 📝 Fixes Applied (See FIXES_APPLIED.md)

Full list of 50+ bugs fixed: build system, architecture, UI, matching, voice pipeline, data, security. Highlights:
- Fixed SharedPreferences mismatch (`jandwar` vs `app_prefs` → single `jandwar_prefs` via Hilt)
- Fixed Sarvam apiKey empty at init (now lazy)
- Fixed Groq model invalid (openai/gpt-oss-120b → llama-3.1-8b-instant)
- Fixed offline fallback (was error "AI requires internet" → now deterministic + local questions)
- Fixed eduRank gaps, added skill-gap, centre case-insensitive
- Rewrote Java 1847-line Activity to Kotlin Compose MVVM Hilt Navigation premium

---

## 📄 License & Disclaimer

Course/centre details indicative; confirm eligibility, fees, empanelment with TAHDCO / training centre / SSC before enrolling.

---

## 👥 Team

SIH Team for SIH26097 — Thozhil Thunai (தொழில்துணை)

See `ThozhilThunai_Complete_Proposal.md` for full proposal.

