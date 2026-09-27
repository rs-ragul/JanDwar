# JanDwar (Gateway for Citizens)

JanDwar is an offline-first, voice-enabled Android app helping rural citizens and youth in Tamil Nadu discover NSQF-aligned livelihood training programs and verified nearby training centres.

The app is designed specifically for rural context: large touch targets, conversational voice assistant, plain regional languages, honest data-gap disclosure, and full functionality even with patchy or zero internet connectivity.

---

## Key Features

1. **Voice-First Pure AI Assistant:**
   - Interactive, animated voice orb for conversational intake.
   - Powered purely by Groq LLM (`llama-3.1-70b-versatile`) for warm, natural dialogue, empathetic follow-ups, and parameter extraction.
   - No hardcoded rules or deterministic fallback. The conversation is entirely steered by AI.
2. **Audio & Speech Pipeline:**
   - **Speech-to-Text (STT):** Android `SpeechRecognizer` using BCP-47 locale tags (e.g., `ta-IN`), gracefully degrading to offline STT without internet.
   - **Text-to-Speech (TTS):** 
     - **Sarvam AI (bulbul:v1):** Premium natural voice for online mode.
     - **Bhashini (AI4Bharat):** High-quality female voice for regional languages.
     - **Android TTS:** Network/Offline fallback.
3. **Data-Backed Course Finder:**
   - 516 bundled NSQF qualification packs with eligibility and sector categorizations.
   - Matches against AI-extracted JSON profile (education, interests, mobility).
   - Honest centre verification: shows actual verified centres or alerts the user to contact local TAHDCO/Skill offices if no verified data exists.
4. **Multilingual Support:**
   - Full localization in English, Tamil (தமிழ்), Hindi (हिन्दी), Telugu (తెలుగు), Kannada (ಕನ್ನಡ), and Malayalam (മലയാളം).

---

## Build and Run

### Android Studio
1. Open `G:\Project\ThozhilThunai` in Android Studio.
2. Allow Gradle to sync dependencies.
3. Select an Android device or emulator (Android 8.0+ / API 26+ recommended).
4. Run the `app` configuration.

### Command Line
```powershell
.\gradlew.bat assembleDebug
```
The compiled APK will be at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```text
app/
 ├── src/main/java/in/jandwar/app/
 │    ├── MainActivity.java             # Core UI, navigation, screen management, matching logic
 │    └── ai/
 │         ├── AiConfig.java            # Reads API keys from assets/config.json
 │         ├── BhashiniGateway.java     # Bhashini Dhruva API (NMT, ASR, TTS) client
 │         ├── SarvamGateway.java       # Sarvam AI TTS (bulbul:v1) client
 │         ├── ConversationController.java # Multi-turn voice state machine, orb animation, STT/TTS
 │         ├── GroqExtractor.java       # Groq LLM (llama-3.1-70b-versatile) online conversational NLU
 │         ├── NluExtractor.java        # Shared NLU interface & ProfileFragment
 │         └── TieredNluExtractor.java  # Online/offline automatic NLU router
 └── src/main/assets/
      ├── config.json                   # Bhashini, Sarvam, & Groq API credentials
      ├── job_roles.json                # 516 NSQF qualification packs
      ├── centres.json                  # Verified training centres in Tamil Nadu
      ├── districts.json                # Tamil Nadu district master list
      └── i18n.json                     # Multilingual strings and sector tags
```

---

## Configuration & Credentials

API credentials are kept in `app/src/main/assets/config.json`:
```json
{
  "bhashini_user_id": "YOUR_BHASHINI_USER_ID",
  "bhashini_inference_key": "YOUR_BHASHINI_INFERENCE_KEY",
  "bhashini_app_id": "YOUR_BHASHINI_APP_ID",
  "groq_api_key": "YOUR_GROQ_API_KEY",
  "sarvam_api_key": "YOUR_SARVAM_API_KEY"
}
```

---

## Current Status & Next Steps

See [PROJECT_STATUS.md](file:///g:/Project/ThozhilThunai/PROJECT_STATUS.md) for detailed notes on component progress.
