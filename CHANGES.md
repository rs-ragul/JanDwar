# JanDwar — what changed and why

Every file below was touched while fixing the project for SIH 2026 PS 26097.
**40 added · 37 modified · 44 deleted · 1 renamed.**

APK in this bundle: **versionCode 5 / versionName 2.3** — includes the post-install UI fixes 26 to 29, the field-test fixes 30 to 40, and the language-accuracy pass 41 to 47 below.

---

## A. Bugs found and fixed

### Build-breaking

| # | Problem | Fix |
|---|---|---|
| 1 | Kotlin 2.0 requires the Compose Compiler Gradle plugin; it was missing, so every build failed at configuration. | Added `org.jetbrains.kotlin.plugin.compose` 2.0.21 to the root and app build scripts. |
| 2 | **9 duplicate `.java` files** shadowed their `.kt` twins (`MainActivity`, `AiConfig`, `BhashiniGateway`, `ConversationController`, `GroqExtractor`, `NluExtractor`, `ProfileFragment`, `SarvamGateway`, `TieredNluExtractor`) causing duplicate-class errors. | Deleted all 9 Java duplicates. |
| 3 | `gradlew` was a 430-byte hand-written stub that emitted `DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'` unquoted, so Java tried to load `"-Xmx64m"` as the main class. | Regenerated the genuine Gradle 8.14.3 wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, `gradle-wrapper.properties`). |
| 4 | A stray `gradle-wrapper.properties` sat in the repo root and confused the wrapper. | Deleted. |
| 5 | `local.properties` (machine-specific SDK path) was committed, breaking every other machine. | Deleted and git-ignored. |
| 6 | `VoiceViewModel` read `matched.role.duration_hours`; the field is `notional_hours`. | No longer referenced; the view model was rewritten and `JobRole.durationLabel()` is used instead. |
| 7 | Deprecated `packagingOptions` block. | Renamed to `packaging`. |
| 8 | `AppRepository` exposed `getCentres()` / `getDistricts()` / `getI18n()` **and** private fields named `centres` / `districts` / `i18n`, producing a JVM signature clash. | Backing fields renamed `centreList` / `districtData` / `i18nData`. |
| 9 | `AppViewModel` declared both a `resultNarration` property and a `setResultNarration()` function, producing a JVM signature clash. | Function renamed `updateResultNarration()`. |
| 10 | Committed build artefacts (`app/build/`, `.gradle/`, `.idea/`) plus four `.zip` files bloated the repo and poisoned incremental builds. | Removed and git-ignored. |

### Logic and flow

| # | Problem | Fix |
|---|---|---|
| 11 | **The language screen was unreachable.** `AppViewModel.init` forced `currentLang = "en"` while the splash and nav host both branched on `lang.isBlank()`, so a first-time user never got to choose a language. | Added a separate `hasChosenLanguage` flag persisted in prefs; `currentLang` always holds a usable code. |
| 12 | **Voice to Results was broken.** `runMatching()` was only called from `IntakeScreen`; `ResultsScreen` read `appViewModel.results` while `VoiceViewModel` kept its own private `matchedResults`, so finishing the interview showed an empty screen. | One pipeline: `AppViewModel.completeInterview(fragment)` applies the profile **and** runs matching, so both entry points write the same `results` flow. |
| 13 | **Every QP code contains a slash** (for example `AGR/Q0101`), so the route `detail/{qp_code}` was parsed as two path segments and the detail screen could never be reached by navigation. | `Screen.Detail.createRoute()` URL-encodes the code and `decodeArg()` reverses it. |
| 14 | Operator-precedence bug in `matchRoles()` mixed `&&` and `||`, so education gating applied to the wrong branch. | Rewritten with explicit grouping. |
| 15 | Skill matching used a bare `contains`, so the skill `it` matched `hospitality`, `furniture` and similar. | Requires length 3 or more and matches only against `job_role` / `sector`. |
| 16 | Match reasons were hardcoded English regardless of the chosen language. | All reasons localised through `reason_*` keys in `i18n.json`. |
| 17 | `enMap` had a duplicate `name` key, a missing `edu_iti`, and a **Telugu string inside the Malayalam map**. | All inline translation maps deleted; i18n now loads purely from `assets/i18n.json` with a `lang` to `en` to built-in-fallback to key chain. |
| 18 | Education was a **hard filter**, so a Class-8 user saw almost nothing. | Now a soft gate: a score penalty plus an `eligible=false` flag rendered as "needs more schooling, ask about a bridge course". Only graduate-level packs are hard-dropped for below-Class-10 users. |
| 19 | `matchRoles()` returned only 3 results, frequently all from one sector. | Limit raised to 6 with a **max two per sector** diversity cap and a top-up pass. |
| 20 | **The app was dead without a Groq API key.** `DeterministicParser` existed but was never wired in, so no key meant no conversation at all. | New `HybridNlu` routes cloud to on-device and never surfaces an error; `OnDeviceNlu` plus `InterviewFlow` give a complete offline interview in 6 languages. |
| 21 | Debug strings such as "Groq AI explaining your best matches..." were shown to beneficiaries. | Removed; all user-facing copy now comes from `i18n.json`. |
| 22 | `AndroidManifest` lacked `<queries>`, so on Android 11+ the app could not see the speech recogniser, the TTS engine or the dialer. | Added `<queries>` for `RecognitionService`, `TTS_SERVICE`, `DIAL` and `VIEW`. |
| 23 | Missing mipmaps and night theme; `AppTheme` cast the context straight to `Activity` and crashed in previews. | Full adaptive-icon set at 5 densities, `values-night/themes.xml`, and a safe context lookup. |
| 24 | `CALL_PHONE` permission was requested for what is only a dial-out. | Uses `ACTION_DIAL`; the permission was dropped. |
| 25 | A **live Groq API key** was hard-coded in `get_groq_models.js`, `list_models.js` and `test_groq.js`. | Files deleted. **The key remains in git history, so revoke it.** |

### Post-install UI fixes (v2.1) — found on a real device

| # | Problem | Fix |
|---|---|---|
| 26 | **`PrimaryButton` swallowed the whole screen.** Its inner `Box` used `Modifier.fillMaxSize()`. Inside a `Column`, an unweighted child is measured against *all* remaining height, so the button grew to the full viewport and starved its `weight(1f)` sibling. Language, Onboarding, Intake and Voice all rendered as one giant button with their real content collapsed to 0 dp. | Inner `Box` is now `.fillMaxWidth().heightIn(min = 58.dp)`. One line, four screens fixed. |
| 27 | **The app drew under the status bar.** `enableEdgeToEdge()` is mandatory on targetSdk 35 / Android 15, but no window insets were consumed, so headers sat on top of the clock and battery. | `statusBarsPadding()` applied *inside* `BrandHeader`'s gradient `Box` (the gradient still runs edge to edge behind the bar, only the content moves down), plus `statusBarsPadding()` on `OnboardingScreen`, which has no header, and `navigationBarsPadding()` on the Splash footer and the Language / Intake / Onboarding bottom bars. |
| 28 | `HeroActionCard`'s decorative circle was a `Box(Modifier.size(180.dp))`, which forced every home card to at least 180 dp tall regardless of its content. | Replaced with `Modifier.matchParentSize()` and a radial gradient, so the decoration follows the card instead of dictating it. |
| 29 | **The voice screen was audio-only.** Nothing on screen showed what the assistant had just said or what the microphone was hearing, which is unusable in a noisy room, on a phone with no TTS voice, or for a hard-of-hearing user. | New `LiveCaption` block under the orb: one card always shows the assistant's current line with a live "speaking" marker, and a second teal card streams the user's partial speech as it is recognised. The scrollable transcript is kept below both. |


### Field-test fixes (v2.2) — reported from the installed app

| # | Problem | Fix |
|---|---|---|
| 30 | **Every line appeared twice** — once in the caption card above the orb and again in the transcript below it. | The caption card is gone. The transcript is the single source of truth; the line currently being spoken is framed in saffron, tagged "Speaking" and set in a larger face, so it still reads as a caption without being duplicated. |
| 31 | **"I want to work for myself" was recorded as "A job with an employer".** `WAGE_WORDS` contained the fragment `"work for"`, which matches inside *"work for myself"*, and it was tested before any self-employment phrase. | Preference detection rebuilt as strong/weak tiers. `"work for"` is gone; `"for myself"`, `"by myself"`, `"on my own"`, `"be my own boss"` and Indic equivalents are strong self signals, `"work for someone"` / `"work for a company"` are strong wage signals, and self is resolved first so *"I don't want a job, I want to work for myself"* lands correctly. Locked down by `tools/check_nlu_preference.py` (18 cases). |
| 32 | The mobility chip rendered the raw slot name **`travel_state`** because the NLU emits `state` while the string table only had `travel_any`. | Added a real `travel_state` string in all six languages and an explicit slot-to-key mapping, so a raw slot value can never reach the screen. |
| 33 | The mic button said **"Listening" while the microphone was off**. The label was driven by `Phase.LISTENING`, which is set *before* the recogniser opens — and a de-bounce in `VoiceListener.start()` silently discarded starts issued within 600 ms, so the phase often never corresponded to a live mic. | New `micLive` state fed by `onReadyForSpeech` / `onMicClosed`; the button and orb read that instead of the phase. The de-bounce now re-schedules the start rather than dropping it. |
| 34 | **The microphone transcribed the assistant's own voice.** The mic was opened the instant the TTS engine reported "done", which is a beat before the audio has drained from the speaker. | Any recogniser is cancelled before speaking, and the mic opens only after a 450 ms guard once playback finishes. |
| 35 | Button copy said "Tap the circle and speak" although the control is a button, not the orb. | Now "Tap and speak", retranslated in all six languages. |
| 36 | **Switching away from English broke the mic** — it lit up and died immediately. `EXTRA_PREFER_OFFLINE` was forced on for every request, and most handsets ship the offline speech pack for English only, so Tamil / Hindi / Telugu / Kannada / Malayalam failed instantly. A bogus `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` string extra (the API expects a boolean) made it worse. | Offline is now requested only when there is genuinely no network. The bad extra is removed. `ERROR_LANGUAGE_NOT_SUPPORTED` / `ERROR_LANGUAGE_UNAVAILABLE` are handled as a distinct `Problem.LANGUAGE`, which shows a translated "install the voice pack, or type" message and switches to typing instead of thrashing the mic. |
| 37 | The app jumped to the results page while the spoken summary was still playing, so the explanation was heard but never seen. | New `Phase.SUMMARY`. Matching runs, the summary is appended to the transcript as a normal assistant turn and highlighted while it is read aloud, and only when the reading finishes does the app navigate. A "See my results" button skips ahead; a single guarded exit point prevents double navigation. |
| 38 | Settings showed a developer instruction — "Add a Groq API key in assets/config.json". | Replaced with a user-facing capability line ("Running fully on this phone. No internet needed."), translated. The confirmation dialog for "Clear my answers" now explains what it actually deletes instead of reusing the privacy blurb. |
| 39 | The **"Voice engine" setting did nothing** — the choice was persisted and never read. | Wired up: "On this phone only" restricts `TtsSpeaker` to voices that need no network, falling back to the full list if a language has no offline voice, so it can never go silent. |
| 40 | `AiConfig` and `config.json` still declared **Sarvam and Bhashini keys for code that had been deleted**, and `APP_SPEC.md` documented that dead architecture under the old product name. | All unread keys and their accessors removed; `config.json` now contains only the one key the app actually reads. `APP_SPEC.md` deleted. |


### Language-accuracy pass (v2.3) — second field test

| # | Problem | Fix |
|---|---|---|
| 41 | **The mic button said "Tap and speak" while the assistant was mid-sentence.** The label only distinguished listening from idle. | It now reports the actual phase: Speaking / Thinking / Listening / Tap and speak. |
| 42 | **"Anywhere in Tamilnadu" was recorded as "anywhere in my district".** Both realistic answers contain the word *anywhere*, and the generic term was tested before the specific ones. In Tamil it was worse: `மாநிலம்` (state) was absent from the vocabulary entirely, so answering "whole state" matched nothing and triggered "I didn't hear you". | Mobility is now resolved most-specific-first: explicit state, then explicit district, then local, and only then a bare *anywhere* (which means the widest option). `மாநிலம்` and its variants are in the lexicon, with equivalents in all six languages. |
| 43 | **A wrong slot value could be locked in by an unrelated sentence.** `extract()` ran the loose preference and mobility detectors on *every* utterance, so "my father works in the district office" could overwrite a travel answer. | General utterances now use strict (exact-phrase) detectors for those two slots; the loose detectors run only for the question actually being answered, and a slot-directed answer overwrites any earlier guess. |
| 44 | **Non-English accuracy was poor because the vocabulary was too thin and too rigid.** Tamil `டிப்ளமோ` — what Google's recogniser actually returns for *diploma* — did not match the dictionary form `டிப்ளோமா`, so the answer was rejected twice in a row. | Two changes. (a) All surface forms moved out of Kotlin into **`assets/lexicon.json`**: 36 categories, **1,480 forms**, up from roughly 350, covering regional wording and romanised code-mixed speech in all six languages. (b) New `Lexicon` matcher does substring matching first, then **token-level fuzzy matching** (bounded Levenshtein, tolerance scaled by word length), so close ASR spellings resolve. Digits are exempt from fuzzy matching — `10th` and `12th` are one edit apart but mean different things, a false positive the new test suite caught. |
| 45 | No way to check language coverage without a handset. | `tools/check_nlu.py` replays 31 utterances across English, Tamil and Hindi through the **same JSON the app loads**, reimplementing the identical matcher. Adding slang is now a data edit plus a test run, with no Kotlin change. |
| 46 | Free-text answers were rendered verbatim as chips, so one chip could be a whole sentence. | Chips are truncated at 26 characters and de-duplicated case-insensitively. |
| 47 | **The recogniser's start/stop beeps were harsh and constant** — once per question, they became the dominant sound of the app. | New `MicEarcon`: the platform earcon streams are muted for the duration of each recognition session and replaced with a quiet synthesised sine blip — rising when the mic opens, falling when it closes — shaped by a raised-cosine envelope so there is no click. No audio asset ships. Streams are always restored, including on cancel and release. A Settings toggle turns the cue off entirely; the loud system beeps stay suppressed either way. |
| 48 | **Occupation chips stayed in English in every language.** The NLU writes a canonical label such as *Dairy farming* onto the profile, and the chip row printed it verbatim — so a Tamil session showed "Dairy farming" sitting next to `பால் பண்ணை`. | The profile still stores the canonical English string, because role matching and the cloud prompt both key off it; only the **display** is translated, through a new `occupation` map in `i18n.json` (17 labels × 6 languages). Chips and the "matches your family work" line on Results now render in the session language, and free text the user typed passes through untouched. `tools/check_nlu.py` now also fails if any label the NLU can emit is missing a translation. |


---

## B. Added (40)

- `app/src/main/assets/config.json`
- `app/src/main/java/in/jandwar/app/JanDwarApp.kt`
- `app/src/main/java/in/jandwar/app/ai/ConversationEngine.kt`
- `app/src/main/java/in/jandwar/app/ai/GroqClient.kt`
- `app/src/main/java/in/jandwar/app/ai/HybridNlu.kt`
- `app/src/main/java/in/jandwar/app/ai/InterviewFlow.kt`
- `app/src/main/java/in/jandwar/app/ai/NluEngine.kt`
- `app/src/main/java/in/jandwar/app/ai/OnDeviceNlu.kt`
- `app/src/main/java/in/jandwar/app/ai/TtsSpeaker.kt`
- `app/src/main/java/in/jandwar/app/ai/VoiceListener.kt`
- `app/src/main/java/in/jandwar/app/util/NetworkMonitor.kt`
- `app/src/main/res/drawable-xhdpi/ic_brand_emblem.png`
- `app/src/main/res/drawable-xxhdpi/ic_brand_emblem.png`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-hdpi/ic_launcher.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher_monochrome.png`
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_monochrome.png`
- `app/src/main/res/mipmap-mdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_monochrome.png`
- `app/src/main/res/mipmap-xhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_monochrome.png`
- `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_monochrome.png`
- `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`

## C. Modified (37)

- `.gitignore`
- `README.md`
- `app/build.gradle`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/i18n.json`
- `app/src/main/java/in/jandwar/app/MainActivity.kt`
- `app/src/main/java/in/jandwar/app/ai/AiConfig.kt`
- `app/src/main/java/in/jandwar/app/ai/ProfileFragment.kt`
- `app/src/main/java/in/jandwar/app/data/model/Models.kt`
- `app/src/main/java/in/jandwar/app/data/repository/AppRepository.kt`
- `app/src/main/java/in/jandwar/app/ui/components/CommonComponents.kt`
- `app/src/main/java/in/jandwar/app/ui/navigation/NavGraph.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/CoursesScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/DetailScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/HomeScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/IntakeScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/LanguageScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/OnboardingScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/ResultsScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/SettingsScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/SplashScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/screens/VoiceScreen.kt`
- `app/src/main/java/in/jandwar/app/ui/theme/Color.kt`
- `app/src/main/java/in/jandwar/app/ui/theme/Theme.kt`
- `app/src/main/java/in/jandwar/app/ui/theme/Type.kt`
- `app/src/main/java/in/jandwar/app/ui/viewmodel/AppViewModel.kt`
- `app/src/main/java/in/jandwar/app/ui/viewmodel/VoiceViewModel.kt`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `build.gradle`
- `data/i18n.json`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`
- `gradlew.bat`
- `settings.gradle`

## D. Deleted (44)

- `AI_IMPLEMENTATION.md`
- `BHASHINI_KEYS_VERIFIED.md`
- `CONFIG_GUIDE.md`
- `PROJECT_STATUS.md`
- `PROMPT_for_AndroidAI.md`
- `Thozhil-Thunai-FIXED-v29-JDK25-GROQ-EXPLAIN.zip`
- `Thozhil-Thunai-FIXED-v30-LATEST-DOCS.zip`
- `app/src/main/java/in/jandwar/app/MainActivity.java`
- `app/src/main/java/in/jandwar/app/ThozhilThunaiApp.kt`
- `app/src/main/java/in/jandwar/app/ai/AiConfig.java`
- `app/src/main/java/in/jandwar/app/ai/AndroidSpeechGateway.kt`
- `app/src/main/java/in/jandwar/app/ai/AndroidTtsSpeaker.kt`
- `app/src/main/java/in/jandwar/app/ai/BhashiniGateway.java`
- `app/src/main/java/in/jandwar/app/ai/BhashiniGateway.kt`
- `app/src/main/java/in/jandwar/app/ai/ConversationController.java`
- `app/src/main/java/in/jandwar/app/ai/ConversationController.kt`
- `app/src/main/java/in/jandwar/app/ai/DeterministicParser.kt`
- `app/src/main/java/in/jandwar/app/ai/GroqExtractor.java`
- `app/src/main/java/in/jandwar/app/ai/GroqExtractor.kt`
- `app/src/main/java/in/jandwar/app/ai/NluExtractor.java`
- `app/src/main/java/in/jandwar/app/ai/NluExtractor.kt`
- `app/src/main/java/in/jandwar/app/ai/ProfileFragment.java`
- `app/src/main/java/in/jandwar/app/ai/SarvamGateway.java`
- `app/src/main/java/in/jandwar/app/ai/SarvamGateway.kt`
- `app/src/main/java/in/jandwar/app/ai/SpeechGateway.kt`
- `app/src/main/java/in/jandwar/app/ai/TieredNluExtractor.java`
- `app/src/main/java/in/jandwar/app/ai/TieredNluExtractor.kt`
- `app/src/main/java/in/jandwar/app/util/NetworkUtils.kt`
- `app/src/main/res/drawable/button_primary.xml`
- `app/src/main/res/drawable/card_bg.xml`
- `app/src/main/res/drawable/chip_plain.xml`
- `app/src/main/res/drawable/chip_selected.xml`
- `app/src/main/res/drawable/splash_bg.xml`
- `app/src/main/res/values/styles.xml`
- `get_groq_models.js`
- `gradle-wrapper.properties`
- `list_models.js`
- `local.properties`
- `patch-v29-JDK25-GROQ-EXPLAIN.zip`
- `patch-v30-LATEST-DOCS.zip`
- `test_bhashini.js`
- `test_bhashini_tts.js`
- `test_groq.js`
- `test_matching.py`

## E. Renamed (1)

- `docs_JanDwar_Proposal.md`

---

## Notes for the build

* Heap is set to `-Xmx2048m` in `gradle.properties`. Raise it to `4096m` on a
  16 GB or larger machine for faster builds.
* `minSdk` was raised from 23 to 24. Avoid `java.time` APIs unless you enable
  core-library desugaring.
* AGP is pinned to **8.7.3** (not 8.8.x) as the safest match for
  Gradle 8.14.3 + Kotlin 2.0.21 + KSP 2.0.21-1.0.28.
* `android.enableJetifier` was removed: deprecated, and it slows every build.
