# JanDwar — what changed and why

Every file below was touched while fixing the project for SIH 2026 PS 26097.
**40 added · 37 modified · 44 deleted · 1 renamed.**

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
