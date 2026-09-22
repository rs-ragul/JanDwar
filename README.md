# JanDwar

JanDwar means "Gateway for Citizens". It is an offline-first Android app for helping people in Tamil Nadu discover NSQF-aligned livelihood courses and verified training centres.

The app is designed for village users and low-connectivity situations: large touch targets, a voice-first entry point, plain language, and honest centre-data gaps.

## Run in Android Studio

1. Open `G:\Project\ThozhilThunai` in Android Studio.
2. Let Gradle sync with the bundled Gradle wrapper.
3. Select an Android device or emulator.
4. Run the `app` configuration.

Command-line debug build:

```text
./gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Current user flow

1. Splash screen with the JanDwar brand mark.
2. Language selection before entering the app. Supported languages: English, Tamil, Hindi, Telugu, Kannada, and Malayalam.
3. Three short onboarding screens.
4. Home screen with:
   - a prominent voice assistant card;
   - a personalized course finder;
   - a browse-all-courses catalogue;
   - offline and verified-data statistics.
5. A custom in-app voice screen backed by Android SpeechRecognizer, with live transcript and Stop control.
6. Tap-based personalized matching using education, work preference, travel range, district, and interests.
7. Deterministic matching of bundled qualification packs.
8. Course details with local verified-centre information or an explicit TAHDCO data-gap message.
9. Settings with language switching and privacy/offline-data notes.

## Project structure

- `app/src/main/java/in/jandwar/app/MainActivity.java`: current native Android UI, navigation, local parser, matching logic, and voice fallback.
- `app/src/main/assets/job_roles.json`: bundled qualification-pack catalogue.
- `app/src/main/assets/centres.json`: verified training-centre records.
- `app/src/main/assets/districts.json`: Tamil Nadu district lists.
- `app/src/main/assets/i18n.json`: translated labels and interest names.
- `brand/logo_premium.png`: source brand artwork.
- `app/src/main/res/drawable/logo_premium.png`: packaged app artwork.
- `APP_SPEC.md`: audited product facts and matching rules.
- `PROJECT_STATUS.md`: current implementation status and handoff notes.

## Adding a language

Add the language to `data/i18n.json` and the packaged copy under `app/src/main/assets/i18n.json`. Add its language code to the language list, translate every UI string and interest label, and add its speech locale in `MainActivity.speechLocale()`. New flow labels currently have an explicit translation block in `MainActivity.addFlowTranslations()`.

## Wiring Bhashini later

The current voice path deliberately uses Android `SpeechRecognizer` inside a custom in-app screen so the prototype works without an API key. The session continues until the user taps Stop, closes it, or presses mobile Back. To add Bhashini, introduce a small `SpeechGateway` implementation behind the existing `startVoice()` entry point. Keep credentials outside the APK, use a backend or local configuration mechanism, and preserve the Android recognizer as an offline/graceful fallback. Voice should only produce text/fields; course selection must remain deterministic and data-backed.

## Product safeguards

- The app ships its core dataset and does not require network access for matching.
- It never fabricates a training centre for a district without verified data.
- Centre, eligibility, fee, batch date, and empanelment details must be confirmed before enrolment.
- The asset-support wording is limited to the audited rule: up to Rs.50,000 or 50% of asset cost with a loan, whichever is lower.

## Known prototype limitations

- The project is currently a compact native Java/View implementation, not the Compose/Hilt architecture described in the original build prompt.
- Android SpeechRecognizer availability depends on the device and installed speech service.
- Continuous listening is implemented as repeated Android recognizer sessions; a production Bhashini gateway should provide a true streaming session where available.
- The browse-all catalogue is intentionally local and currently loads the full bundled list into one scrollable screen.
- The repository has no automated unit or UI test suite yet.
- Gradle reports the existing Android SDK XML compatibility warning and the existing deprecated-API note for the legacy activity result/permission APIs; the debug build succeeds.
