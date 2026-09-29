# JanDwar — what changed and why

Every file below was touched while fixing the project for SIH 2026 PS 26097.
**40 added · 37 modified · 44 deleted · 1 renamed.**

APK in this bundle: **versionCode 9 / versionName 2.7** — includes the post-install UI fixes 26 to 29, the field-test fixes 30 to 40, the language-accuracy pass 41 to 48, and the third field test 49 to 52, and the vocabulary expansion 53 to 57 below.

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

### Third field test (v2.4)

| # | Problem | Fix |
|---|---|---|
| 49 | **The new cue played when the mic opened, but the old harsh beep still played when it closed.** `closeMic()` unmuted the earcon streams *first* and then played our blip — and the platform emits its stop earcon at exactly that moment, so it went straight through the reopened streams. | The mute now outlives the recognition session. `MicEarcon` owns the lifecycle: `releaseAfterCue()` keeps the streams muted for a further 260 ms, so the platform's stop beep is emitted into silence and discarded, then unmutes and plays our own falling blip. `releaseNow()` restores audio with no cue on cancel and teardown. `TtsSpeaker.speak()` calls `releaseForSpeech()`, which guarantees the reply is never swallowed by our own mute and pulls a still-pending blip forward so it lands *before* the reply instead of over it. Release is keyed to a new `sessionOpen` flag rather than `micLive`, so a session that fails before the mic goes live still restores the streams. |
| 50 | **The launcher icon was the whole badge shrunk down.** The adaptive foreground contained the logo's own bezel and rounded square, which Android then masked again — a rounded square inside a rounded square, with the emblem filling only **17%** of the canvas and sitting 29 px left of centre. | The emblem is now lifted off its background properly. `tools/gen_icons.py` fits a bilinear model of the blue-to-teal gradient from background pixels only, derives alpha from each pixel's distance to that model, then *unmixes* `P = aF + (1-a)B` so antialiased edges carry no blue fringe. The foreground is the bare emblem, centred to the half-pixel and filling **57%** of the canvas — just inside Android's 61% safe zone. The adaptive background is a vector gradient sampled from the real badge (`#001AA8 → #0267A8 → #05D2BC`), and the legacy pre-API-26 icons are composed and masked rather than nested. |
| 51 | The in-app emblem was one 144 px asset used at 84 dp, so it upscaled and blurred on 3x and 4x screens. | Generated per density from the source artwork: 96/144/192/288/384 px for mdpi through xxxhdpi. |
| 52 | **The PS asks for "mobility *and physical constraints*", but the interview never asked about constraints** — they were only captured if the user happened to volunteer them mid-sentence, and the scoring penalty for a limiting condition therefore almost never fired. | New `CONSTRAINTS` slot between mobility and district, phrased so that "no" is an easy and normal answer, in all six languages. "No" is recorded as a real answer (the `None` sentinel) rather than a failure to understand, so the interview does not loop on it; a stated difficulty is kept verbatim, shown back as a chip and fed to the existing scoring rule. Profile completeness is now 8 fields, not 7. |



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

---

## Vocabulary expansion (53 to 57) — v2.5

The offline understanding was Latin-heavy: per category Tamil had 3 to 13
surface forms and Telugu, Kannada and Malayalam often only 2 to 6. A person
answering in their own words was frequently not understood at all.

**The lexicon went from 1,480 to 3,234 surface forms across all 36 categories
and all 6 languages** (`work/lexicon_extra.py`, merged by `work/gen_lexicon.py`).

Three kinds of form were added for every category, and the third is the one
usually missed:

1. the dictionary word — ஐந்தாம் வகுப்பு, पाँचवीं कक्षा;
2. the spoken contraction — அஞ்சாங்கிளாஸ், ಐದನೇ ಕ್ಲಾಸ್;
3. the **romanisation** — "anjaam class", "aidane class". One English word
   mid-sentence flips many recognisers into Latin output for the whole
   utterance, so every native form needs a romanised twin.

Regional exam names are treated as first-class vocabulary, because nobody
answers "class twelve": **SSLC** (TN/Karnataka Class 10), **PUC** (Karnataka
Class 12), **HSC** and **+2** (TN), **Matric** (Hindi belt), **Intermediate**
(Telangana/Andhra).

| # | Problem found by the new audit | Fix |
|---|---|---|
| 53 | `detect_mobility` checked `mob.state` first, so **"வெளியூர் போக முடியாது"** — *cannot* go out of town — matched the bare word *வெளியூர்* and was recorded as **willing to travel anywhere in the state**. Hindi "बाहर नहीं जा सकता" and English "cannot go far" failed identically. The recommender then offered a centre in another district to someone who cannot leave their village. | `detect_mobility` now uses longest-match (`best_match`), like education. The negated phrase is always the longer match, so the negation is read correctly without a separate negation parser. Fixed in both the Kotlin and Python engines. |
| 54 | The tokeniser split on a character class that discarded `+`, so the token `2` could never equal the surface form `+2` — **Class 12 in Tamil Nadu was dead data**. | Split on whitespace only; `fold()` has already reduced every punctuation mark except `+` to a space. Both engines now tokenise identically. |
| 55 | Romanised Indic vocabulary made the Latin fuzzy floor unsafe. At tolerance 1, **"pass"** in *"PUC pass aagiruken"* matched **"pasu"** (cow) and recorded a cattle-rearing interest; at tolerance 2, **"appuram"** (afterwards) matched **"appalam"** (a snack) and recorded food processing. | Latin tolerance floor raised: ≤4 chars → 0 edits, ≤7 → 1, else 2. |
| 56 | Short Indic forms matched inside unrelated longer words: **ಹೊಲ** (field, farming) sits inside **ಹೊಲಿಗೆ** (sewing), so *"ಹೊಲಿಗೆ ಕೆಲಸ ಗೊತ್ತು"* — I know tailoring — also reported an interest in agriculture. | Indic forms under four codepoints must now match as a whole token; at four or more the agglutinative-suffix substring rule still applies (மாடு still reaches மாடுகள்). |
| 57 | The two matchers disagreed: Kotlin computed fuzzy tolerance from the **shorter** string, Python from the **longer** one, so the app and the IVR line could classify the same sentence differently. | Kotlin aligned to `maxOf`. |

### Regression

`tools/check_nlu.py` gained `check_every_form_resolves()`, which asserts that
**every surface form resolves to its own detector**. This is deliberate
pressure on future vocabulary changes: slang is the one part of the system
that will keep growing, and each new word is a chance to collide with an
existing category. A category that cannot recognise its own vocabulary is
worse than an empty one, because it fails silently.

```
$ python3 tools/check_nlu.py
all 31 NLU cases pass (3234 surface forms in lexicon)
all 17 occupation labels translated into 6 languages
all 2673 surface forms resolve to their own detector
```

To add more slang: edit `work/lexicon_extra.py`, re-run `work/gen_lexicon.py`,
then run `tools/check_nlu.py`. Never hand-edit `lexicon.json`.

### Answer coverage (58 to 60) — the test that actually matters

`check_nlu.py` audits the lexicon against *itself*: every surface form must
resolve to its own detector. That proves internal consistency and nothing
about real speech, because it only ever feeds the engine words it already
knows. Passing it at 100% is compatible with failing every real sentence.

**`tools/check_answers.py`** asks the opposite question, and it is the one the
PS cares about: *for each question the interview asks, is a realistic spoken
answer understood?* 306 utterances written as people speak — full sentences,
code-mixed, contracted, hedged, negated, with filler and politeness — across
all 8 questions and all 6 languages. None were copied from the lexicon.

A slot counts only if the engine fills **that** slot when **that** question
was asked; filling some other slot does not count, because the interview
would still re-ask something the person already answered. For the three enum
slots the **value** is asserted too — filling `mobility` with the wrong value
is worse than leaving it empty, since the interview moves on satisfied and
the recommender then scores against a fact the person never said.

| # | Gap it caught | Fix |
|---|---|---|
| 58 | "i didn't go to school at all" was not understood. | `fold()` rewrites `didn't` to `didn t` for the stored form and the utterance alike, so the natural spelling with the apostrophe is what belongs in the generator. Added that plus "never went to school", "illiterate" and the five Indic equivalents. |
| 59 | Telugu **"మా ఊర్లో మాత్రమే"** and Kannada **"ನಮ್ಮ ಊರಲ್ಲಿ ಮಾತ್ರ"** — *only in my village* — were not understood. The stem was present but the separate word carrying *only* (మాత్రమే / ಮಾತ್ರ) was never paired with it. | Added the paired forms for all six languages. |
| 60 | "i can go anywhere in **the** district" was scored `state`, not `district`: a phrase match is a plain substring, the stored phrase omitted the article, so only the bare word "anywhere" matched and the answer was promoted to a wider radius than the person offered. "i can't travel, small children at home" was scored `district`. | Added the articled variants and the contraction spellings to `mob.district` / `mob.local`. |

```
$ python3 tools/check_answers.py
  EDUCATION             56/56   100.0%
  FAMILY_OCCUPATION     46/46   100.0%
  CURRENT_LIVELIHOOD    47/47   100.0%
  INTERESTS             48/48   100.0%
  PREFERENCE            35/35   100.0%
  MOBILITY              36/36   100.0%
  CONSTRAINTS           38/38   100.0%

  TOTAL                306/306  100.0%
```

Both suites should be run after any vocabulary change:
`python3 tools/check_nlu.py && python3 tools/check_answers.py`.

---

## In-progress study and technical vocabulary (61 to 64) — v2.7

### 61. "college 2nd year" was read as a completed degree

Reported from the field: a second-year college student was being classified
wrongly. The actual behaviour was worse than mis-reading it as school —
`college 2nd year` resolved to **`graduate`**, the *top* education rank.

The recommender uses that rank for NSQF entry eligibility, so a student two
years from finishing was judged eligible for roles that require a completed
degree, and the "skill gap" line told them they had none. Bare `2nd year`,
`second year` and `1st year` were not understood at all, and the Tamil-script
spelling **காலேஜ்** was missing entirely — only the Latin "college" was stored.

The fix models what a person has actually *completed*:

| Said | Completed | Also recorded |
|---|---|---|
| college 2nd year / B.Tech 3rd year / final year btech | `class12` | Student |
| 1st year polytechnic / diploma 2nd year | `class10` | Student |
| studying in 10th | `class8` | Student |
| **12th dropout** | `class10` | *not* a student |
| college dropout | `class12` | *not* a student |
| I completed my degree | `graduate` | — |

Two new categories carry it: `marker.in_progress` (Nth year, semester,
pursuing, appearing, still studying, and the Indic equivalents) and
`marker.dropout` (dropped out, discontinued, left studies). Dropping out is
deliberately distinct from still being enrolled — both completed the level
below, but only one is currently a student, and that is the answer to the
livelihood question we would otherwise ask again.

Native-script forms for "college" were added for all six languages, so
`இரண்டாம் வருடம் காலேஜ்`, `कॉलेज दूसरा साल`, `ಕಾಲೇಜು ಎರಡನೇ ವರ್ಷ`,
`കോളേജ് രണ്ടാം വർഷം` and `కాలేజీ రెండవ సంవత్సరం` all resolve correctly.

### 62. Engineering and technical vocabulary

The catalogue is full of machine, electrical and fabrication roles, but the
lexicon could barely recognise anyone describing that work. Added across all
six languages:

- **Degree** — BE, B.Tech, M.Tech, mechanical, civil, EEE, ECE, CSE,
  automobile, mechatronics, instrumentation, chemical, aeronautical.
- **ITI/polytechnic trades as DGT names them** — fitter, turner, machinist,
  tool and die maker, draughtsman, surveyor, electronics mechanic, mechanic
  diesel, motor vehicle mechanic, RAC technician, wireman, sheet metal
  worker, foundryman, pattern maker, lineman, COPA. People answer with the
  trade, not the certificate.
- **Shop-floor skills** — CNC, VMC, lathe, milling, arc/MIG/TIG welding,
  soldering, PCB assembly, motor rewinding, panel wiring, switchgear, PLC,
  SCADA, VFD, hydraulics, pneumatics, HVAC, solar panel installation,
  inverter and UPS repair, CCTV, networking, computer hardware, AutoCAD,
  3D printing, JCB/crane/forklift operation, vernier and micrometer.

### 63. Lexicon 3,234 → 3,596 forms, 36 → 38 categories.

### 64. The test suite gained an in-progress corpus

`tools/check_answers.py` now carries `EDUCATION_IN_PROGRESS`, asserting the
**completed** level rather than the level the sentence names — the assertion
that would have caught bug 61 on the day it was written.

```
$ python3 tools/check_nlu.py
all 31 NLU cases pass (3596 surface forms in lexicon)
all 17 occupation labels translated into 6 languages
all 2906 surface forms resolve to their own detector

$ python3 tools/check_answers.py
  EDUCATION             56/56   100.0%
  EDUCATION_IN_PROGRESS 20/20   100.0%
  FAMILY_OCCUPATION     46/46   100.0%
  CURRENT_LIVELIHOOD    47/47   100.0%
  INTERESTS             48/48   100.0%
  PREFERENCE            35/35   100.0%
  MOBILITY              36/36   100.0%
  CONSTRAINTS           38/38   100.0%

  TOTAL                326/326  100.0%
```

---

## v2.8 — versionCode 10

### 65. The whole catalogue was silently empty — `null` in `job_roles.json`

**Symptom reported:** "Find my options" and "Browse all" both showed *no
match found*; the assistant ended every interview with *"I could not find a
confident match."*

**Cause.** Not the recommender. `job_roles.json` carries
`"nsqf_level": null` on 9 rows and `"notional_hours": null` on 19 (plus 11
values written as JSON numbers rather than strings) — artefacts of the
five-state research merge. `JobRole` declares both as non-nullable `String`,
and kotlinx.serialization treats a `null` for a non-nullable property as a
hard error. Because the file is decoded in **one** `decodeFromString<List<JobRole>>`
call, those 28 values aborted the whole decode. The `catch` returned
`emptyList()`, so all 540 roles vanished and every screen that lists or
scores a role had nothing to work with. No crash, no visible error — just
zero results everywhere.

**Fix, in three layers** so it cannot recur:

1. `job_roles.json` normalised — every value a string, nulls to `""`.
2. `Json { coerceInputValues = true }` in `AssetDataSource`, which maps a
   null onto the property default instead of throwing.
3. A row-by-row `org.json` fallback for `job_roles.json` and `centres.json`.
   If the strict decode ever fails again, a bad row costs one course rather
   than the entire catalogue, and the reason is logged.

### 66. State is now asked, never assumed

The catalogue grew to five states in v2.6 but every code path still behaved
as though everyone lived in Tamil Nadu:

* the intake form offered **one flat list of 187 districts**, so a user in
  Kerala scrolled past 75 Uttar Pradesh districts to find their own;
* the interview jumped straight to *"which district do you live in?"*;
* the Groq system prompt literally said *"in Tamil Nadu"*;
* `getCentreForDistrict()` matched on district name alone, across states.

Changes:

* `UserProfile.state`, `ProfileFragment.state`, `Fragment.state` (server).
* New slot `STATE`, ordered immediately before `DISTRICT` — nine slots now.
* `districts.json`'s `by_state` block is finally parsed (`DistrictsData.by_state`,
  `StateDistricts`); it was being discarded by `ignoreUnknownKeys`.
* Intake is a **six**-step form: state, then district filtered to that state.
  Each state tile shows its district and centre counts.
* The district question names the state: *"And which district in Kerala is
  your home?"* — `InterviewFlow.DISTRICT_IN_STATE`, state names localised
  into all six languages via `STATE_LABELS`.
* `OnDeviceNlu.STATE_FORMS` — 5 states × spoken forms in all six scripts.
  `AMBIGUOUS_STATE_FORMS` (`tn`, `ap`, `up`, `u p`, `tamil`) are matched as
  **whole tokens and only while answering the state question**, so
  *"I studied up to 10th"* is not Uttar Pradesh and *"I speak Tamil"* is not
  Tamil Nadu.
* District detection is scoped to the known state, and a district that
  belongs to another state can no longer sit under the wrong state name.
* A recognised district back-fills the state (`stateOfDistrict`), so saying
  "I'm in Ernakulam" does not trigger a redundant question.
* Centre lookup takes the state and prefers a `CONFIRMED` record.
* Groq prompt rewritten: five states, an explicit instruction never to infer
  the state, and `canonicalState()` to snap "TN"/"andhra"/"uttarpradesh"
  onto catalogue names.
* Server mirrors all of it: `nlu.py` (`STATE_FORMS`, `detect_state`,
  scoped `detect_district`, `state_of_district`), `engine.py`
  (`INFERABLE` + `DISTRICT_IN_STATE` + `state_label`), `data.py`
  (`states`, `districts_of_state`, `state_of_district`, state-scoped
  `centre_for_district`), `recommend.py`. `flow.json` regenerated — 9 slots,
  108 prompts.

### 67. Home and Settings statistics were wrong

The "centres" tile counted **districts that have a centre** (185) and
labelled it *Verified centres*, understating the catalogue by 475. The
districts tile was labelled *TN districts* after the data had covered five
states for two releases.

Now: **Roles · Fundable · States (5) · Districts (187) · Centres (660)**,
plus a provenance line — *"294 of 660 centres are confirmed against the
institution's own published page."* Settings shows the same figures with the
confirmed count inline. New i18n keys `stat_states`, `stat_confirmed`,
`q_state`, `select_state`, `state`, `district`, `confirmed` in all six
languages; `stat_districts` and `stat_centres` retranslated.

### 68. Tests

`tools/check_answers.py` gains a 27-utterance `STATE` corpus with
`EXPECTED_STATE` value assertions across all six languages, including the
ambiguous short forms.

```
$ python3 tools/check_nlu.py
all 31 NLU cases pass (3596 surface forms in lexicon)
all 17 occupation labels translated into 6 languages
all 2906 surface forms resolve to their own detector

$ python3 tools/check_answers.py
  EDUCATION             56/56   100.0%
  EDUCATION_IN_PROGRESS 20/20   100.0%
  FAMILY_OCCUPATION     46/46   100.0%
  CURRENT_LIVELIHOOD    47/47   100.0%
  INTERESTS             48/48   100.0%
  PREFERENCE            35/35   100.0%
  MOBILITY              36/36   100.0%
  CONSTRAINTS           38/38   100.0%
  STATE                 27/27   100.0%

  TOTAL                353/353  100.0%
```

### 69. 20 roles were falsely labelled "not funded under PM-AJAY GIA"

`ANNEXURE1_SECTOR` mapped the seven sectors the catalogue had **before** the
five-state research drop. That drop brought six more — `automotive`,
`healthcare`, `leather`, `tourism_hospitality`, `fisheries`,
`aerospace_aviation` — and they were never added, so `isFundable()` returned
false for the 20 roles in them. The Detail screen printed that as a flat
statement of fact to the beneficiary, and the recommender docked those roles
26 points.

`assets/gia_funding_rules.json` contradicts it directly. For three of the
five states the researched `qp_whitelist` field reads *"none stated —
central GIA guidelines do not restrict sectors/QPs"*. All 13 sectors are now
mapped, in `Models.kt` and `server/app/core/data.py`.

Because every sector is now eligible, the home tile "Fundable packs" would
simply have repeated the role count, so it shows **Sectors (13)** instead —
a figure that says something. Settings still lists both.

This is the same mistake as entry 55, one release later and one size
smaller: a whitelist that has to be edited whenever the data grows. The
lesson holds — derive from the data, or the data will outgrow the code.

### 70. Documentation brought back in line with the code

`README.md` had drifted badly — it still claimed 516 roles, 20 centres,
38 Tamil Nadu districts and 136 UI strings, figures from before the five-state
research drop. Rewritten against the actual assets: 476 packs / 13 sectors,
660 centres across 5 states, 187 districts, 156 × 6 strings, 3 596 lexicon
forms, 9 interview slots. Added a section on the offline NLU (matcher rules
and the cases it gets right), the per-state coverage table, and the test
figures.

Added `PROJECT_STATUS.md`: PS-requirement checklist, current numbers, an
honest list of the seven known gaps, and a prioritised next-steps plan.

### 71. Data-update package for the IVR/WhatsApp service; device-testing note corrected

The centre research landed after the IVR service was already being built
against the old catalogue, so the two had drifted. Packaged a drop-in update
(`JanDwar-IVR-data-update.zip`): the eight asset JSONs, the regenerated
`server/data/flow.json`, the server core/channel modules as a reference to
diff against, the two test suites, and a dependency-free `verify.py` that
checks a drop in about a second.

Four of the changes are not merely "more rows" and will misbehave silently
against old code:

* `districts.json` decodes to an **object** now (`all`, `with_centre`,
  `without_centre`, `by_state`, `note`), not a flat list. Code that iterates
  it gets five keys instead of 187 districts.
* The interview gained a **STATE** slot *before* DISTRICT — nine slots — and
  district matching is state-scoped. An older `flow.json` has no STATE
  question at all.
* **268 of the 660 centres have no phone number**, distributed very unevenly
  (Tamil Nadu 168/180 have one; Uttar Pradesh 17/109). A voice channel has to
  branch on this or it reads out dead air. Numbers are also frequently two
  values joined by `/`.
* `job_roles.json` carries 540 rows of which **476** survive `isValidName()`.
  If the server's filter differs from the app's by even one clause, the phone
  and the phone-call recommend different courses.

Separately, gap 1 in `PROJECT_STATUS.md` claimed the app had never been run
on a physical device. That was wrong: every build is installed and tested on
a real Android phone by the team. Corrected — the gap is not "untested
hardware", it is that device behaviour (mic, TTS voices, ASR language packs)
is only observable on that side, so the fix loop depends on symptoms being
reported back.

### 72. All 187 districts now understood in their own script; full audit added

`DISTRICT_ALIASES` held native-script spellings for Tamil Nadu's 38 districts
and nothing for the other 149 — every district in Andhra Pradesh, Karnataka,
Kerala and Uttar Pradesh. It was written when the app was Tamil-Nadu-only and
was never extended after the five-state research drop. A Telugu speaker
saying "అనంతపురం", a Kannada speaker saying "ಬೆಳಗಾವಿ", a Malayalam speaker
saying "ആലപ്പുഴ" or a Hindi speaker saying "वाराणसी" got nothing: the last
question of the interview failed, retried twice, skipped, and the profile
ended with no district — so no centre was shown.

Worse, three of the Tamil Nadu keys did not match `districts.json` at all
("Nilgiris" vs "The Nilgiris", "Tirupattur" vs "Tirupathur", "Villupuram" vs
"Viluppuram"), so those three districts silently had no aliases either. 152
districts in total.

All 187 now carry their name in the script of their own state's language,
plus the old or colloquial English name where one is still in use — Bangalore,
Bellary, Trivandrum, Calicut, Vizag, Allahabad, Banaras, Gulbarga, Mysore,
Shimoga, Bijapur, Faizabad, Noida, Kanpur. 363 forms, all 550 name+alias
combinations verified to resolve to the right district, both state-scoped and
unscoped.

The data now lives in one place (`work/district_aliases.py`) and is written
into `ai/OnDeviceNlu.kt` and `server/app/core/nlu.py` by
`work/gen_district_aliases.py`, which refuses to write unless the key set
exactly equals `districts.json`. That is what makes the "Nilgiris" class of
silent miss impossible to reintroduce — the same lesson as entries 55 and 69,
finally enforced by a generator instead of a promise.

**`tools/audit.py`** — new. 37 checks across assets, geography resolution, the
interview in all six languages, the recommender over all 187 districts, and
Kotlin/Python parity. It found three further defects, now fixed:

* `district_economy.json` keyed one district "Bhadohi (Sant Ravidas Nagar)"
  while `districts.json` calls it "Bhadohi", so that district's economy note
  never loaded.
* `"u p"` was listed as a spoken form of Uttar Pradesh but could never match:
  the detector strips the space from the form and looks the result up among
  the input tokens, and "u p" tokenises to "u" and "p". Both engines now also
  index the concatenation of each adjacent token pair.
* `durationLabel()` printed "300 hrs · ~2 months" for the 90 roles whose
  notional hours the source catalogue does not state. 300 is the ranking
  fallback, not a fact about the course. It now returns empty and every call
  site renders a dash, matching how `nsqf_level` was already handled.
