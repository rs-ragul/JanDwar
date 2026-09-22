# JanDwar Project Status

_Last updated: 2026-09-22_

## Identity

- App name: JanDwar
- Meaning: Gateway for Citizens
- Package/application id: `in.jandwar.app`
- Purpose: help rural users discover verified livelihood courses and nearby training centres in Tamil Nadu.
- Primary design goal: user-friendly for village users, low literacy, small phones, and patchy connectivity.

## What was already present

The repository already contained a working native Android prototype with:

- language-first launch flow;
- three-screen onboarding;
- home, settings, intake, results, and detail screens;
- bundled role, centre, district, and translation JSON data;
- deterministic matching logic;
- Android SpeechRecognizer fallback;
- brand logo and gradient resources;
- JanDwar package identity.

The previous UI placed the voice wording inside onboarding and placed the microphone action at the bottom of a long manual-details screen. This made the primary product promise unclear and made the intake page feel like a form.

## Changes made in this continuation

### Voice-first home

- Added a prominent home-screen voice assistant card with a visible microphone action.
- The card uses the existing Android SpeechRecognizer flow and requests microphone permission when needed.
- Voice remains optional; users can always use manual offline search.

### Clear manual path

- Manual intake is now labelled `Offline search`.
- The intake copy explains that no internet or account is needed.
- The voice action was removed from the bottom of the long manual form so it is no longer hidden or confusing.

### Course discovery paths

- Added `Personalized course finder` as the guided three-question flow.
- Added `Browse all courses` as a separate catalogue route showing the bundled valid qualification packs.
- Existing course cards still lead to the current detail screen and verified-centre logic.

### Alignment and language copy

- Added status-bar spacing so page content does not begin underneath the system status area.
- Replaced the misleading onboarding title `Or just speak, What interests you?` with a clear voice-assistant introduction.
- Added localized copy for the new high-visibility flow labels in English, Tamil, Hindi, Telugu, Kannada, and Malayalam.

### Documentation

- Added `README.md` with run instructions, structure, data locations, language guidance, Bhashini integration guidance, and known limitations.
- Added this file as the handoff source of truth for future AI sessions.

## Latest continuation changes

### Custom voice experience

- Replaced the Android system speech-recognition activity with an in-app voice screen.
- The screen has a JanDwar-branded animated listening orb, live partial transcript, listening status, and an explicit Stop listening button.
- Recognition restarts after each completed phrase while the user remains on the voice screen, so the session continues until Stop, Close, or mobile Back is used.
- Android `SpeechRecognizer` is still the local engine. Bhashini is not connected yet.

### Mobile back behavior

- Added explicit screen state and `onBackPressed()` handling.
- Home, language selection, and splash retain normal exit behavior.
- Settings, personalized finder, and course catalogue return to Home.
- Results return to the finder; course details return to Results.
- Voice Back/Close stops recognition and returns Home.
- Onboarding moves to the previous page before returning to language selection.

### Personalized finder cleanup

- Removed the typed sentence field, `Use this sentence` action, and the redundant `Offline search` section from the personalized finder.
- The finder is now tap-based only. The voice experience is a separate first-class path from Home.

### Multilingual layout hardening

- Long language labels can wrap to two lines in language cards.
- App-header titles can wrap to two lines while the action remains compact.
- Added high-quality Android line breaking and normal hyphenation frequency to shared text creation.
- Increased language-card height to reduce Tamil/Malayalam clipping and alignment shifts.
- Added translations for the custom voice status, prompt, close, stop, and unavailable states.

## Verification

Command executed successfully:

```text
./gradlew.bat :app:assembleDebug
```

Result: `BUILD SUCCESSFUL`.

Generated debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The build still emits the pre-existing Android SDK XML compatibility warning and a deprecated-API note. These do not block the debug build.

## Current architecture notes

- UI and navigation are currently implemented in `MainActivity.java` with programmatic Android Views.
- Data is loaded from packaged assets at startup.
- Matching and parsing are local and deterministic.
- Bhashini and external AI APIs are not wired yet.
- No API keys are stored in the project.
- No automated unit or Compose UI tests are currently present.

## Next recommended work

1. Install the debug APK on a small Android phone and check voice permission, status-bar spacing, long Tamil text wrapping, and the 516-course scroll performance.
2. Add focused unit tests around `match()` and `parseSentence()` before changing matching rules.
3. Extract `SpeechGateway` and `TextParser` interfaces so Bhashini and a future AI backend can be added without changing the UI.
4. Consider moving the catalogue to a lazy list before production if browse-all performance is poor on low-end devices.
5. Replace deprecated activity-result and permission APIs when modernizing the project architecture.
6. Only then consider a larger Compose migration; it is not required to test the current prototype.

## Handoff instruction for the next AI

Read this file and `README.md` first. The latest implementation is in `MainActivity.java`. Test on a small phone in Tamil and Malayalam, verify the custom voice session really continues until Stop, and verify mobile Back from every screen before making broader UI changes.

Suggested commit message after review:

```text
feat: add custom continuous voice flow and app navigation
```
