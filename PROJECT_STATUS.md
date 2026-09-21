# ThozhilThunai (தொழில்துணை) — Complete Project Status & Handover Document

> **Handover Notice for AI/Developer**: This document is the single, complete, up-to-date snapshot of the **ThozhilThunai** codebase, architecture, current health status, and operational instructions as of September 21, 2026. If the conversation or AI context is reset, feeding this file to any AI assistant provides 100% context on the project.

---

## 1. Project Overview & Identity
- **App Name**: ThozhilThunai (Tamil: தொழில்துணை, "Your Livelihood Companion").
- **Purpose**: A production-quality, **native** Android application built to assist rural, low-literacy job-seekers in Tamil Nadu in discovering government skill-training courses (NSQF Qualification Packs) and nearby verified training centres in their native language, mostly offline.
- **Target Audience**: School dropouts to graduates in rural TN; prefers voice input, large touch targets, and operates seamlessly on small devices with patchy internet connectivity.

---

## 2. Tech Stack & Dependencies
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose + Material 3 (Material You styling with Brand Indigo `#1B3A8C`, Teal `#0FA3A3`, and Saffron `#FF8A1F`).
- **SDK Targets**: `minSdk 24`, `targetSdk 34`
- **Architecture**: MVVM + Repository Pattern
- **Dependency Injection**: Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `AppModule`)
- **Navigation**: Navigation 3 (`NavBackStack`, `NavDisplay`, `NavKey` routes)
- **Local Persistence**: Preferences DataStore (`UserPreferencesRepository` for language, text size, onboarding status)
- **Data Source**: Bundled JSON datasets in `app/src/main/assets/data/` parsed at runtime via Gson with in-memory caching in `DataRepository`.
- **Image Loading**: Coil
- **Speech & AI**: `SpeechGateway` interface (`AndroidSpeechGateway` active; seam ready for Bhashini API integration) and `TextParser` interface (`DeterministicTextParser` active; keyword matching across Tamil & English).

---

## 3. Data Audit & Single Source of Truth
All core datasets are bundled inside `app/src/main/assets/data/`:
1. **`job_roles.json`**: **516** real NSQF Qualification Packs (510 embedded for matching).
   - Fields: `qp_code`, `job_role`, `nsqf_level` (430 official + 86 inferred), `notional_hours`, `ssc`, `sector`.
2. **`centres.json`**: **20** verified TANUVAS / VUTRC training centres across Tamil Nadu.
   - Fields: `district`, `name`, `address`, `phone`, `trades`, `dairy_course`, `confidence`.
3. **`districts.json`**: **38** Tamil Nadu districts (20 with verified centres, 18 with data gaps).
4. **`i18n.json`**: Full UI strings and interest chip translations across **6 Indian languages**:
   - **Tamil (ta)** தமிழ்
   - **English (en)** English
   - **Hindi (hi)** हिन्दी
   - **Telugu (te)** తెలుగు
   - **Kannada (kn)** ಕನ್ನಡ
   - **Malayalam (ml)** മലയാളം

---

## 4. Architecture & File Structure

```
g:\Project\Hackathons\ThozhilThunai\
├── PROMPT_for_AndroidAI.md         # Original master prompt
├── APP_SPEC.md                      # Functional spec & audited numbers
├── README.md                        # Developer quickstart guide
├── PROJECT_STATUS.md                # THIS HANDOVER FILE
├── brand/
│   └── logo_premium.png             # Official brand logo asset
└── app/src/
    ├── main/
    │   ├── assets/data/             # job_roles.json, centres.json, districts.json, i18n.json
    │   ├── res/drawable/            # logo_premium.png
    │   └── java/com/thozhilthunai/app/
    │       ├── ThozhilThunaiApp.kt          # Hilt Application entry point
    │       ├── MainActivity.kt               # Single activity hosting Jetpack Compose
    │       ├── data/
    │       │   ├── model/Models.kt          # JobRole, Centre, District, IntakeFields, etc.
    │       │   └── repository/
    │       │       ├── AssetDataSource.kt   # Asset JSON reader via Gson
    │       │       ├── DataRepository.kt    # In-memory reactive state flows & snapshots
    │       │       └── UserPreferencesRepository.kt # DataStore preference storage
    │       ├── domain/
    │       │   ├── MatcherUseCase.kt        # Deterministic course & centre matching engine
    │       │   └── TextParser.kt            # TextParser interface & DeterministicTextParser
    │       ├── speech/
    │       │   └── SpeechGateway.kt         # SpeechGateway interface & AndroidSpeechGateway
    │       ├── di/
    │       │   └── AppModule.kt            # Hilt dependency injection bindings
    │       └── ui/
    │           ├── AppViewModel.kt          # Global state (language, data loading state)
    │           ├── AppNavigation.kt         # Nav3 navigation graph & backstack manager
    │           ├── components/Components.kt # Custom UI components (Gradient, Badges, Chips)
    │           ├── theme/                   # Color.kt, Type.kt, Theme.kt (Brand palette)
    │           └── screens/
    │               ├── splash/SplashScreen.kt
    │               ├── language/LanguageScreen.kt
    │               ├── onboarding/OnboardingScreen.kt
    │               ├── home/HomeScreen.kt
    │               ├── intake/IntakeScreen.kt
    │               ├── results/ResultsScreen.kt
    │               ├── detail/DetailScreen.kt
    │               └── settings/SettingsScreen.kt
    └── test/java/com/thozhilthunai/app/
        ├── TextParserTest.kt               # 10 unit tests for natural text parsing
        └── MatcherUseCaseTest.kt           # 6 unit tests for course matching logic
```

---

## 5. Application Flow & Screen Specs

1. **Splash Screen**: Brand gradient (`#1B3A8C` → `#0FA3A3`), smooth spring logo scale, and title animation. Auto-routes based on persisted DataStore state.
2. **Language Select Screen**: **FIRST screen** on fresh install. 2-column grid of 6 languages in native script. Pure single-language rendering across entire app after selection.
3. **Onboarding Screen**: 3 animated slides outlining app benefits. Skip-able. Persists completion in DataStore.
4. **Home Screen**: Welcoming header, quick statistics (516 job roles, 20 centres), and "Find My Course" primary call-to-action card.
5. **Intake (Matcher) Screen**:
   - Education level chips (below 8th, 8th, 10th, 12th, ITI/Diploma, Graduate+).
   - Searchable District dropdown (38 TN districts).
   - Interest multi-select chips (dairy, poultry, tailoring, food, electrical, IT, etc.).
   - Free-text / Voice line input parsed on-device by `TextParser`.
6. **Results Screen**: Ranked list of matching job roles with NSQF level badges, notional hours, sector tag, and eligibility indicator.
7. **Detail Screen**: Full job role info, eligibility rules, TAHDCO / ASCI disclaimers, asset subsidy rules, and nearest verified training centres for user's district.
8. **Settings Screen**: Language switcher, text size adjustment, about info, legal disclaimers.

---

## 6. Current Status & Health Verification

- **Gradle Build**: **SUCCESSFUL** (`./gradlew test` passes 100%).
- **Unit Tests**: **16 / 16 PASSING**.
  - `TextParserTest`: 10 cases verifying Tamil/English keyword matching, district alias resolution (e.g. Nagercoil -> Kanyakumari), and education level extraction.
  - `MatcherUseCaseTest`: 6 cases verifying NSQF education level gating, interest affinity boosting, and tie-breaking.
- **Fixed Issues**:
  - `MatcherUseCaseTest` mock resolution: `whenever(dataRepository.getJobRoleSnapshot()).thenReturn(sampleRoles)` updated to match `MatcherUseCase` snapshot API call.
- **Lint & Warnings**: Kapt language compatibility warning handles fallback gracefully; deprecation warning for `menuAnchor` identified in `IntakeScreen.kt`.

---

## 7. Instructions for Future AI / Developer Continuation

If you are an AI assistant taking over this project:
1. **Do not modify the data counts**: The counts (516 roles, 20 centres, 38 districts, 6 languages) are verified facts.
2. **Keep strict single-language UI**: Ensure no bilingual text mixing occurs on any screen.
3. **Voice/Bhashini extension**: To wire Bhashini API, add `app/src/main/assets/config.json` with `{"bhashini_key": "KEY"}` and swap the `@Binds` implementation in `AppModule.kt`.
4. **Run build/test command**: Verify any code changes with `powershell -Command "./gradlew test"`.
