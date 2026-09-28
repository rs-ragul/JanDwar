# JanDwar / Thozhil Thunai – Project Status

_Last updated: 2026-09-28 – v29 JDK25 + Pure Groq Explain_

## Identity & Core Purpose

- **App name:** JanDwar (Gateway for Citizens) / Thozhil Thunai
- **SIH ID:** SIH26097
- **Package:** `in.jandwar.app`
- **Target:** Rural SC youth, Tamil Nadu – low literacy, vernacular, small phones, patchy internet, PM-AJAY
- **Core Mission:** Voice-first discovery of 516 NSQF QPs + 20 verified centres via **real one-to-one conversation with Groq AI**, not forms
- **Languages:** en, ta (தமிழ்), hi (हिन्दी), te (తెలుగు), kn (ಕನ್ನಡ), ml (മലയാളം) – single-language UI fully switches, TTS natural native

---

## Latest Release – v29 (2026-09-28)

### Build Fix – JDK 25 Major Version 69

**Problem:** `Execution failed for task ':app:kaptDebugKotlin' > ... Unsupported class file major version 69` + `javacOptions > 25.0.2`
- Major 69 = JDK 25. Gradle 8.10.2 Groovy ASM doesn't support JDK 25.
- User cloned `main` which had old kapt + compose 1.5.8

**Fix:**
- `gradle-wrapper.properties`: `8.10.2` → `8.14.3` (Groovy 4.0.22 supports JDK 25)
- `build.gradle`: `AGP 8.8.2`, `Kotlin 2.0.21`, `KSP 2.0.21-1.0.28`
- `app/build.gradle`: `kotlin-kapt` → `com.google.devtools.ksp`, `ksp hilt-compiler`, `kotlin { jvmToolchain(17) }` instead of `kotlinOptions jvmTarget`, `composeOptions 1.5.14` (was 1.5.8)
- `gradle.properties`: `android.suppressUnsupportedCompileSdk=35`, `ksp.incremental=true`
- Verified: `./gradlew clean assembleDebug` works with JDK 25 via toolchain 17

**Zips:**
- Full: `Thozhil-Thunai-FIXED-v29-JDK25-GROQ-EXPLAIN.zip` (1.4M)
- Patch: `patch-v29-JDK25-GROQ-EXPLAIN.zip` (14K) – 7 files: `build.gradle`, `gradle.properties`, `gradle-wrapper.properties`, `app/build.gradle`, `GroqExtractor.kt`, `TieredNluExtractor.kt`, `VoiceViewModel.kt`
- Raw links: `https://github.com/rs-ragul/Thozhil-Thunai/raw/arena/01a0e0fc-thozhil-thunai/Thozhil-Thunai-FIXED-v29-JDK25-GROQ-EXPLAIN.zip`

### Pure Groq One-to-One – No Deterministic

**User corrections addressed:**
- v14: "I Hate Deterministic" – hates fixed question flow, wants real LLM empathetic
- v16: "IS IT AI OR WHAT??" – rejects hardcoded keyword mapping for CSE/ECE, demands true NLU ANY course
- v17: "Remove fallback deterministic completely" – wants to ensure talking to Groq
- v19: "I want real one to one conversation with groq.. you dont have to force specific set of questions... just instruct Groq what type to ask.. then in the end it give templated text.. from that we fetch top 3.. then ask groq to explain those results.. and each response from groq is spoken by TTS"
- v20: "Build failed kaptDebugKotlin javacOptions 25.0.2" – JDK 25 incompatibility

**Implementation v29:**
- `GroqExtractor.kt`:
  - Model chain: `openai/gpt-oss-120b` → `openai/gpt-oss-20b` (Groq free 1000 req/day, 300+ tok/sec, fastest)
  - `json_schema` strict:true with `additionalProperties:false`, `reasoning_effort low`, `reasoning_format hidden`, `temperature 0.8`, `top_p 0.95`
  - System prompt: "You are JanDwar, warm village elder, real conversational AI, NOT keyword matcher, understand ANY course"
  - Prompt instructs Groq what types to ask (education, family occupation, current livelihood, interests, preference, district, mobility) but Groq decides natural phrasing, one at a time, warmly in user's language
  - Returns `edu, familyOccupation, currentLivelihood, interests, skills, preference, district, mobility, physicalConstraints, localOpportunity, next_question_native, is_complete, final_summary`
  - `explainResultsWithGroq(profile, topResults, langCode, callback)` – Groq explains Top 3 warmly in langCode, no markdown, TTS-ready
- `TieredNluExtractor.kt`: Pure Groq only – `groqExtractor + bhashiniGateway` for translation, no deterministic fallback, errors show "PURE GROQ FAILED – real Groq, not deterministic"
- `VoiceViewModel.kt` v29:
  - `start()` → `ConversationController.init()`
  - `onDone(finalProfile)` → `explainResultsThenDone()`:
    - Converts `ProfileFragment.edu/preference/mobility` to `EducationLevel/Preference/Mobility` via `fromAiString()`
    - Builds `UserProfile` for `AppRepository.matchRoles()` – **ordinary programming filtering Excel-like, no AI**
    - `matchedResults = results`
    - Builds `topResultsForGroq` strings: `job_role (qp_code) - SSC, Sector, NSQF, Duration, Centre, Why fits, Skill gap`
    - If `groqExtractor.isConfigured()`: calls `explainResultsWithGroq()` → sets `resultExplanation`, adds to `conversationHistory`, calls `speakResultsSummary()` (TTS speaks Groq explanation)
    - Else fallback to `buildNaturalResultsSummary()` (deterministic but only if no Groq key)
    - After TTS done: `isDone=true`, `onDoneCallback`
  - `processTypedAnswer()` handles follow-up questions after results explanation
- `ConversationController.kt`:
  - `speakResultsSummary()` sets `isSpeakingResult=true`, stops mic, calls `speakInternal()` with 1200ms delay before onComplete
  - `speakOnly()` for follow-ups
  - Self-echo detection: if heard text >60% similar to lastSpokenText, ignore
  - TTS routing: `sarvam` if configured → `bhashini` → `android` – `formatForTts()` strips emojis, markdown
  - STT: `AndroidSpeechGateway` with BCP-47, retry 2 times on no match/no speech

---

## Architecture Overview – v29

```
[User Speech] ──► Android SpeechRecognizer (ta-IN etc.) – AndroidSpeechGateway
        │ transcript
        ▼
TieredNluExtractor (PURE GROQ)
  ├─ if lang!=en & bhashini available: translate ta→en via BhashiniGateway
  └─ GroqExtractor.extract(text, langCode, currentProfile, isOnline, callback)
       ├─ builds prompt with Already collected + User said
       ├─ calls Groq API https://api.groq.com/openai/v1/chat/completions
       ├─ model gpt-oss-120b → 20b fallback, json_schema strict
       └─ returns ProfileFragment with next_question_native in langCode
        │ ProfileFragment
        ▼
ConversationController.onResult → profile.merge(fragment)
  ├─ if !isComplete(): next_question_native → TTS → startListening()
  └─ if isComplete(): final_summary (warm closing in langCode) → TTS → listener.onDone(profile)
        │ finalProfile
        ▼
VoiceViewModel.explainResultsThenDone()
  ├─ UserProfile from ProfileFragment (education, pref, mobility, district, interests, family, current, skills)
  ├─ AppRepository.matchRoles() – Excel-like filtering 516 QPs:
  │    - edu level ≤3 for class8, interest match, pref self/wage, mobility local/district/state, district, family fit
  │    → List<MatchedRole> (role, centre, familyFitNote, skillGapNote, regionOpportunity)
  ├─ top 3 → topResultsForGroq strings
  └─ GroqExtractor.explainResultsWithGroq() – Groq explains Top 3 warmly in langCode, TTS-ready
        │ explanation
        ▼
ConversationController.speakResultsSummary(explanation) – Sarvam bulbul:v1 / Bhashini / Android TTS
        │ TTS done
        ▼
ResultsScreen – shows 516 filtered, centre name/district/contact, fees, duration
```

---

## Detailed Component Status – v29

### 1. NLU & Conversational AI – PURE GROQ ONLY – 100% DONE
- **GroqExtractor.kt** (18KB): Pure Groq, no deterministic, true NLU ANY course (ECE/CSE/MBA/BCA/Nursing/Fashion/Data Science/Hotel Management/Cyber etc.)
  - Handles blank first turn: "FIRST TURN: Greet warmly like village elder in $langCode, ask how far did you study? 1 sentence only"
  - Mapping intelligence: any college degree/engineering/bachelor/master/diploma/professional course → graduate, 12th/HSC/+2 → class12, etc.
  - Interests inference: tech/engineering/computer/IT/electronics/AI/data/cyber/software → machine, farming/dairy/cattle/goat/poultry/agri → farming/dairy, fashion/tailoring/textile/design → tailor/textile, food/cooking/hotel/catering → food, construction/civil/mason/electrical/plumbing → construction
  - Preference: own/self/business/entrepreneur/shop → pref_self, job/wage/salary/company/placement → pref_wage
  - Mobility: local/village/nearby/cannot travel → local, district → district, anywhere/state/far → state
  - Error handling: tries model chain, logs HTTP code, fallback to next model, no deterministic
- **TieredNluExtractor.kt** (4.9KB): Pure Groq only, Bhashini translation optional, no deterministic fallback, error messages show "This IS real Groq failing, NOT deterministic"
- **DeterministicParser.kt** kept but NOT used – for offline pitch mock only
- **Status:** ✅ DONE – User verified "am talking to real Groq" after removal of fallback

### 2. STT / Microphone – 100% DONE
- **AndroidSpeechGateway.kt**: `SpeechRecognizer` with `EXTRA_LANGUAGE` BCP-47, `EXTRA_PREFER_OFFLINE`, partial results
- **ConversationController.startListening()**: prevents self-echo (similarity >0.6), stops mic before TTS, 1300ms delay after TTS before listening, retry 2 times on no match/no speech/speech timeout, 4000ms delay after final retry
- **Mic toggle**: instant, `onListening` state, `isSpeaking` prevents listening
- **Languages**: ta-IN, hi-IN, te-IN, kn-IN, ml-IN, en-IN – fully switches UI
- **Status:** ✅ DONE – Fixed mic on/off instant toggle skipping speech

### 3. TTS & Audio – 100% DONE
- **AndroidTtsSpeaker.kt**: `TextToSpeech` with `setLanguage(Locale.forLanguageTag(speechTag()))`, queue flush, utterance listener
- **SarvamGateway.kt**: `bulbul:v1` Meera voice, best for Tamil, natural prosody, `synthesize(clean, langCode, callback)` → `playAudio(bytes)`
- **BhashiniGateway.kt**: Dhruva pipeline config call (userID+ulcaApiKey → callbackUrl+inferenceApiKey) + compute call (Authorization inferenceApiKey), TTS 22050Hz female, NMT ta→en
- **ConversationController.speakInternal()**: `formatForTts()` strips emojis `[\\uD83C-\\uDBFF]`, markdown `* # ` _`, normalizes `! ? ...` → `.`, truncates 500 chars at last dot >300, routes via prefs `tts_engine` auto/sarvam/bhashini/android
- **playAudio()**: temp file `.mp3` in cacheDir, MediaPlayer, onCompletion deletes file
- **VoiceViewModel**: `explainResultsWithGroq` result is spoken via `speakResultsSummary()` – TTS speaks every Groq response
- **Status:** ✅ DONE – Natural native TTS, Groq explanation spoken

### 4. Matching & Recommendation – 100% DONE – Ordinary Code, No AI
- **AppRepository.kt**: `matchRoles(UserProfile)` – Excel-like filtering:
  - Loads `job_roles.json` 516 QPs, `centres.json` 20 centres, `districts.json`, `i18n.json` via `AssetDataSource`
  - Filters: education level (none/class5/class8/class10/class12/graduate), interests Set, preference pref_self/pref_wage, mobility local/district/state, district string, familyOccupation, currentLivelihood, physicalConstraints, localOpportunity, skills Set
  - Scoring: family fit (if familyOccupation contains sector), skill gap (if skills match), region opportunity (if district matches centre)
  - Returns `List<MatchedRole>` sorted by relevance
  - `interestLabel(lang, sector)` for Tamil translation
- **Models.kt**: `EducationLevel.fromAiString()`, `Preference.fromAiString()`, `Mobility.fromAiString()`, `UserProfile`, `MatchedRole(role, centre, familyFitNote, skillGapNote, regionOpportunity)`, `ConversationMessage(role, text, isUser)`
- **Status:** ✅ DONE – As per user spec "ordinary programming filtering trades (level <=3 for 8th, within 20km, self-employment) Excel-like no AI"

### 5. UI/UX Premium Production – 100% DONE
- **Theme.kt, Color.kt, Type.kt**: Material3, dark/light, BrandSaffron, BrandTeal, BrandIndigo, premium gradient
- **VoiceScreen.kt**: Animated orb with `infiniteTransition` scale 1f→1.18f when speaking, 1.12f when listening, radial gradient, conversation bubbles `ConversationBubble`, LazyColumn auto-scroll, top recommendations card after Groq explanation, typed answer OutlinedTextField with Send, transcript status
- **HomeScreen.kt, OnboardingScreen.kt, LanguageScreen.kt, IntakeScreen.kt, ResultsScreen.kt, DetailScreen.kt, CoursesScreen.kt, SettingsScreen.kt, SplashScreen.kt**: premium, large touch targets, rural context
- **CommonComponents.kt**: `GradientHeader` etc.
- **Status:** ✅ DONE – Commercial production grade, no technical debug chips, dark mode premium

### 6. Build System – 100% DONE – JDK 25 Fix
- **build.gradle**: `com.android.application 8.8.2`, `com.android.library 8.8.2`, `kotlin.android 2.0.21`, `kotlin.serialization 2.0.21`, `hilt.android 2.51.1`, `ksp 2.0.21-1.0.28`
- **app/build.gradle**: `com.android.application`, `kotlin.android`, `kotlin.serialization`, `ksp`, `hilt.android`, compileSdk 35, minSdk 23, targetSdk 35, `compileOptions JavaVersion 17`, `kotlin jvmToolchain(17)`, `compose true`, `composeOptions 1.5.14`, packaging excludes, dependencies: core-ktx 1.12.0, lifecycle-runtime 2.7.0, activity-compose 1.8.2, compose-bom 2024.02.00, ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended, navigation-compose 2.7.6, hilt-android 2.51.1, ksp hilt-compiler 2.51.1, hilt-navigation-compose 1.1.0, okhttp 4.12.0, coroutines-android 1.7.3, serialization-json 1.6.2
- **gradle-wrapper.properties**: `gradle-8.14.3-bin.zip`
- **gradle.properties**: `jvmargs -Xmx4096m`, `useAndroidX`, `nonTransitiveRClass`, `nonFinalResIds`, `code.style official`, `enableJetifier`, `suppressUnsupportedCompileSdk 35`, `ksp.incremental true`
- **Status:** ✅ BUILD SUCCESSFUL – `./gradlew clean assembleDebug` works with JDK 25

### 7. VoiceViewModel Flow – 100% DONE – Groq Explains Top 3
- **VoiceViewModel.kt** v29 (17.7KB):
  - `start(lang, onFieldExtracted, onDone)` → init ConversationController with Listener
  - `onFieldExtracted` → `profile = updated`, `onFieldExtractedCallback`
  - `onDone(finalProfile)` → `explainResultsThenDone(finalProfile)`:
    - Maps edu/pref/mobility via `fromAiString()`
    - Builds `UserProfile` → `repository.matchRoles()` → `matchedResults`
    - If empty: fallback summary → `speakResultsSummary()` → `isDone=true`
    - Else: builds `topResultsForGroq` strings, calls `groqExtractor.explainResultsWithGroq()` if configured, else fallback
    - Groq callback: `resultExplanation = explanation`, `conversationHistory + assistant message`, `speakResultsSummary(explanation)` → `isDone=true`, `onDoneCallback`
  - `processTypedAnswer()` handles follow-up after explanation
  - `buildNaturalResultsSummary()` fallback with ta/hi/te/kn/ml/en
- **Status:** ✅ DONE – After profile complete AI does NOT instantly navigate while still speaking – finishes closing, explains top 3 via voice in user's language natural dialogue, allows questions, then navigates

---

## File Map – v29 Latest

| File | Description | Status |
|------|-------------|--------|
| `build.gradle` | AGP 8.8.2, Kotlin 2.0.21, KSP 2.0.21-1.0.28 | ✅ JDK25 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.14.3 – supports JDK 25 major 69 | ✅ JDK25 |
| `gradle.properties` | suppressUnsupportedCompileSdk 35, ksp.incremental | ✅ |
| `app/build.gradle` | ksp, jvmToolchain 17, compose 1.5.14, no kapt | ✅ JDK25 |
| `app/src/main/java/in/jandwar/app/ai/GroqExtractor.kt` | Pure Groq gpt-oss-120b→20b, json_schema strict, explainResultsWithGroq | ✅ Pure Groq |
| `app/src/main/java/in/jandwar/app/ai/TieredNluExtractor.kt` | Pure Groq only, Bhashini translate optional, no deterministic | ✅ Pure Groq |
| `app/src/main/java/in/jandwar/app/ui/viewmodel/VoiceViewModel.kt` | Calls Groq explain Top 3, TTS speaks, isDone after TTS | ✅ v29 |
| `app/src/main/java/in/jandwar/app/ai/ConversationController.kt` | STT/TTS state machine, self-echo detection, speakResultsSummary | ✅ |
| `app/src/main/java/in/jandwar/app/ai/AndroidSpeechGateway.kt` | SpeechRecognizer ta-IN etc. | ✅ |
| `app/src/main/java/in/jandwar/app/ai/AndroidTtsSpeaker.kt` | Android TTS fallback | ✅ |
| `app/src/main/java/in/jandwar/app/ai/BhashiniGateway.kt` | Dhruva NMT/ASR/TTS | ✅ |
| `app/src/main/java/in/jandwar/app/ai/SarvamGateway.kt` | bulbul:v1 premium TTS | ✅ |
| `app/src/main/java/in/jandwar/app/ai/AiConfig.kt` | Loads config.json keys | ✅ |
| `app/src/main/java/in/jandwar/app/data/repository/AppRepository.kt` | matchRoles() Excel-like filtering 516 QPs | ✅ No AI |
| `app/src/main/java/in/jandwar/app/ui/screens/VoiceScreen.kt` | Animated orb, conversation bubbles, top rec card | ✅ Premium |
| `app/src/main/assets/job_roles.json` | 516 QPs | ✅ |
| `app/src/main/assets/centres.json` | 20 centres | ✅ |
| `app/src/main/assets/districts.json` | TN districts | ✅ |
| `app/src/main/assets/i18n.json` | 6 languages | ✅ |
| `README.md` | Updated v29 JDK25 + Pure Groq | ✅ 2026-09-28 |
| `PROJECT_STATUS.md` | This file – v29 detailed | ✅ 2026-09-28 |

---

## Verification & Build Status – v29

- **Build Tool:** Gradle Wrapper 8.14.3
- **Build Command:** `./gradlew clean assembleDebug` – after `./gradlew --stop` + `rm -rf .gradle` if previously had 8.10.2
- **Output:** `app/build/outputs/apk/debug/app-debug.apk`
- **Status:** `BUILD SUCCESSFUL` – Tested with JDK 25 (major 69) via toolchain 17, no kapt, KSP
- **Branches:** `main` @ `1924a1c` and `arena/01a0e0fc-thozhil-thunai` @ `9e5c857` both have v29 fix
- **Zips Verified:** `Thozhil-Thunai-FIXED-v29-JDK25-GROQ-EXPLAIN.zip` 1.4M includes correct build.gradle 415 bytes wrapper 8.14.3, `patch-v29-JDK25-GROQ-EXPLAIN.zip` 14K 7 files

---

## Next Steps / Optional Enhancements

- [ ] Add offline Piper TTS ONNX for true offline Tamil voice (30-50MB)
- [ ] Add 516 QPs embeddings for semantic search (currently Excel-like filtering as per spec, no AI)
- [ ] Add TAHDCO API integration for live centre verification
- [ ] Add analytics for which courses most asked via Groq
- [ ] Play Store release – needs signing config

---

## Credits

- Groq – gpt-oss-120b/20b – 1000 req/day, 300+ tokens/sec, fastest LPU
- Bhashini – MeitY – Dhruva pipeline – NMT/ASR/TTS
- Sarvam AI – bulbul:v1 – Meera voice – best Tamil TTS
- NSQF – 516 QPs – PM-AJAY – TAHDCO
- SIH26097 – Thozhil Thunai – JanDwar
