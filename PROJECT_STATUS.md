# JanDwar Project Status

_Last updated: 2026-09-26_

## Identity & Core Purpose

- **App name:** JanDwar (meaning "Gateway for Citizens")
- **Package / Application ID:** `in.jandwar.app`
- **Target Audience:** Rural youth and village job seekers in Tamil Nadu (low literacy, vernacular languages, small Android phones, intermittent connectivity).
- **Core Mission:** Voice-first discovery of NSQF-aligned livelihood training programs, qualification packs, and verified district training centres (TAHDCO, PMKVY, etc.).
- **Supported Languages:** English (`en`), Tamil (`ta`), Hindi (`hi`), Telugu (`te`), Kannada (`kn`), and Malayalam (`ml`).

---

## Architecture Overview

The app utilizes a pure voice-first conversational AI interface without hardcoded rules:

```
[User Speech] ───► Android SpeechRecognizer (Locale: en-IN, ta-IN, hi-IN, etc.)
                          │
                   (User Transcript)
                          ▼
            TieredNluExtractor (in.jandwar.app.ai)
             └── Online: Groq LLM (llama-3.1-70b-versatile) ──► Conversational follow-up + ProfileFragment
                          │
                   (ProfileFragment)
                          ▼
            MainActivity.applyProfileFragment()
                          ▼
            MainActivity.match() ──► NSQF Qualification Packs (job_roles.json) + Verified Centres (centres.json)
                          │
             (AI Response / Next Question)
                          ▼
            Speech Output (TTS Pipeline)
             ├── Premium: Sarvam AI (bulbul:v1)
             ├── Regional Default: Bhashini Dhruva Pipeline (BhashiniGateway.java)
             └── Fallback: Android TextToSpeech (Network / Offline Voices)
```

---

## Detailed Component Status

### 1. Natural Language Understanding (NLU) & Conversational AI
- **Online LLM:** Powered by Groq API (`llama-3.1-70b-versatile`) via `GroqExtractor.java`.
  - Prompts are tuned to conduct a warm, empathetic, multi-turn intake dialogue.
  - Dynamically extracts 5 core parameters:
    1. `education` (`below_8th`, `pass_8th`, `pass_10th`, `pass_12th`, `iti_diploma`, `graduate`)
    2. `interests` (Agriculture, Dairy, Tailoring, Driving, Electrical, Beauty, Healthcare, Retail, etc.)
    3. `preference` (`pref_self` for self-employment / business, `pref_wage` for salaried jobs)
    4. `mobility` (`within_village`, `within_block`, `within_district`, `anywhere`)
    5. `district` (Tamil Nadu districts)
  - Returns structured JSON while generating natural, conversational follow-up questions in the user's native language.
  - **No Deterministic Fallback:** Completely removed `DeterministicParser`. The interview flow relies 100% on AI to ensure natural and dynamic conversation without rigid arrays of questions.

### 2. Speech-to-Text (STT) / Microphone
- **Engine:** Android `SpeechRecognizer` managed by `ConversationController.java`.
- **Locale configuration:** Maps app language code to precise BCP-47 speech locale (`en-IN`, `ta-IN`, `hi-IN`, `te-IN`, `kn-IN`, `ml-IN`).
- **Continuous listening:** Auto-restarts speech recognition after questions until the flow completes or the user stops it.
- **Offline handling:** Includes gracefully handled `EXTRA_PREFER_OFFLINE` intent rules for when the network drops, keeping the microphone functional even on patchy 3G/4G networks.

### 3. Text-to-Speech (TTS) & Audio Playback
- **TTS Engine Routing:** Handled by `ConversationController.java`. Text is normalized (stripped of markdown/special characters) before synthesis to prevent robotic reading of punctuation.
- **Sarvam AI (Premium Voice):** Integrated via `SarvamGateway.java` using the `bulbul:v1` voice model. This is the top-tier preference for natural conversational pacing.
- **Bhashini TTS (AI4Bharat):** Used as a secondary robust TTS for regional languages (Tamil, Telugu, Kannada, Malayalam). Text is shaped and normalized without relying on SSML. Configured to use a female voice at `22050` Hz sampling rate.
- **Android Network Voices:** Used as the fallback default for English and Hindi as they provide excellent natural prosody.
- **Developer Options:** A Developer Settings section in the UI allows testing between Auto, Sarvam, Bhashini, Android Network, and Android Offline engines.

### 4. Matching & Course Recommendation Engine
- Implemented in `MainActivity.java` (`match()` and `applyProfileFragment()`).
- Matches against 516 NSQF qualification packs (`job_roles.json`).
- Checks user education ceiling, interest tags, self/wage preference, mobility, and verified district centre availability (`centres.json`).
- Displays verified centre locations or honest data-gap alerts (advising users to consult local TAHDCO / district skill development offices).
- **Final Target Flow:** After profile completion, the AI fills the English template (`ProfileFragment` JSON) natively. The app then parses this and fetches the matching `job_roles.json` records to display immediately (Completed).

### 5. Offline Pitch Mockup
- Added an in-app "Download Offline AI (45MB)" progress bar and toggle in Settings.
- Serves as a visual demonstration for investor/stakeholder pitches to showcase the planned on-device edge AI experience without requiring a 2GB model download during live demos.

---

## TTS Analysis & Questions for Future AI Review

The primary focus for improving the audio experience is **making TTS output empathetic, natural, and expressive** across Indian languages. 

### A. Bhashini Dhruva TTS Optimization
1. **Model Selection:** In `BhashiniGateway.java`, service IDs are discovered dynamically. We currently rely on text shaping instead of SSML.
2. **Text Formatting:** LLM prompts explicitly constrain output to short 1-2 sentence conversational responses with warm tone to prevent flat TTS readings.

### B. Cloud TTS Alternatives for Indian Languages
1. **Sarvam AI:** Integrated via `SarvamGateway.java`. Provides high-fidelity, natural voices (e.g., `bulbul:v1`) for Indian languages.
2. **Google Cloud TTS (Neural2 / Journey / Studio Voices):** Does direct REST integration with Google Cloud TTS offer superior prosody over Android's on-device `TextToSpeech` binding?
3. **ElevenLabs Multilingual v2:** Viability for Tamil/Hindi conversational pacing vs cost constraints for rural public-service deployments.

### C. On-Device / Offline Neural TTS Alternatives
For true offline conversational speech:
1. **Piper TTS:** Can Piper TTS with Tamil and Indian English ONNX models be integrated via Android JNI/C++ without exceeding ~30-50MB total footprint?
2. **Kokoro 82M / MeloTTS:** Can Kokoro 82M run via ONNX Runtime Mobile or Sherpa-ONNX on low-end Android devices (2GB RAM, Android 8-11) within reasonable inference latency (<300ms)?

---

## File Map

| File Path | Description |
|---|---|
| `app/src/main/java/in/jandwar/app/MainActivity.java` | Core UI, navigation, screen management, matching logic, profile application |
| `app/src/main/java/in/jandwar/app/ai/ConversationController.java` | Voice orb UI state, STT listener, TTS playback, conversational state machine |
| `app/src/main/java/in/jandwar/app/ai/GroqExtractor.java` | Online Groq LLM integration (`llama-3.1-70b-versatile`) for pure AI NLU |
| `app/src/main/java/in/jandwar/app/ai/TieredNluExtractor.java` | Routes the pure AI inference |
| `app/src/main/java/in/jandwar/app/ai/BhashiniGateway.java` | Government of India Bhashini Dhruva API client (NMT, ASR, TTS) |
| `app/src/main/java/in/jandwar/app/ai/SarvamGateway.java` | Sarvam AI API client (TTS `bulbul:v1`) |
| `app/src/main/java/in/jandwar/app/ai/AiConfig.java` | Secure credentials loader from `assets/config.json` |
| `app/src/main/assets/config.json` | API keys for Bhashini, Sarvam, and Groq |
| `app/src/main/assets/job_roles.json` | 516 NSQF qualification packs with eligibility and sector metadata |
| `app/src/main/assets/centres.json` | Verified training centres across Tamil Nadu districts |
| `app/src/main/assets/i18n.json` | Multi-language UI string translations |

---

## Verification & Build Status

- **Build Tool:** Gradle Wrapper (`gradlew.bat`)
- **Build Target:** `assembleDebug`
- **Output APK:** `app/build/outputs/apk/debug/app-debug.apk`
- **Status:** `BUILD SUCCESSFUL` (0 compilation errors, clean execution).
