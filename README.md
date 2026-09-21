# ThozhilThunai — தொழில்துணை

> **Your livelihood companion** — a native Android app helping rural, low-literacy job-seekers in Tamil Nadu discover government skill-training courses (NSQF Qualification Packs) and the nearest verified training centres, in their own language, mostly offline.

---

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository |
| DI | Hilt |
| Local DB | In-memory (assets JSON) |
| Navigation | Navigation3 |
| Images | Coil |
| Preferences | DataStore |
| Speech | Android SpeechRecognizer (Bhashini seam ready) |

- **minSdk**: 24 · **targetSdk**: 34

---

## How to Run

1. Open `g:\Project\Hackathons\ThozhilThunai` in **Android Studio Hedgehog** or newer.
2. Sync Gradle (`File → Sync Project with Gradle Files`).
3. Run on an emulator (API 24+) or physical device.
4. No internet connection required for browsing roles and centres.

---

## Project Structure

```
app/src/main/java/com/thozhilthunai/app/
├── ThozhilThunaiApp.kt           # Hilt @HiltAndroidApp
├── MainActivity.kt
├── data/
│   ├── model/Models.kt           # JobRole, Centre, District, EducationLevel, etc.
│   └── repository/
│       ├── AssetDataSource.kt    # Reads JSON from assets/
│       ├── DataRepository.kt     # In-memory cache + Flow exposure
│       └── UserPreferencesRepository.kt  # DataStore: language, text size
├── domain/
│   ├── MatcherUseCase.kt         # Deterministic on-device matching logic
│   └── TextParser.kt             # TextParser interface + DeterministicTextParser
├── speech/
│   └── SpeechGateway.kt         # SpeechGateway interface + AndroidSpeechGateway
├── di/
│   └── AppModule.kt             # Hilt bindings
└── ui/
    ├── AppViewModel.kt           # App-wide language + data-loaded state
    ├── AppNavigation.kt          # Nav3 navigation graph
    ├── theme/                    # Color, Type, Theme
    ├── components/               # Shared composables
    └── screens/
        ├── splash/
        ├── language/
        ├── onboarding/
        ├── home/
        ├── intake/               # Intake + IntakeViewModel
        ├── results/              # Results + ResultsViewModel
        ├── detail/               # Detail + DetailViewModel
        └── settings/
```

---

## Where the Data Lives

All data is bundled in `app/src/main/assets/data/`:

| File | Contents |
|---|---|
| `job_roles.json` | 516 NSQF Qualification Packs (qp_code, job_role, nsqf_level, notional_hours, ssc, sector) |
| `centres.json` | 20 verified TANUVAS/VUTRC training centres (district, name, address, phone, trades) |
| `districts.json` | 38 TN districts with `has_centre` flag |
| `i18n.json` | All UI strings + interest chip labels in 6 languages (ta, en, hi, te, kn, ml) |

---

## How to Add a Language

1. Open `data/i18n.json`.
2. Add a new entry `["xx", "NativeName"]` to the `langs` array.
3. Add a `"xx": { ... }` block in both `strings` and `interest` objects, covering **all** keys present in the other languages.
4. Add the new `LanguageOption` to `LANGUAGES` list in `LanguageScreen.kt`.
5. No code changes elsewhere — `AppViewModel.str()` and `interestLabel()` resolve dynamically.

---

## How to Wire Bhashini

1. Obtain a Bhashini API key.
2. Create `app/src/main/assets/config.json`:
   ```json
   { "bhashini_key": "YOUR_KEY_HERE" }
   ```
3. Implement a `BhashiniSpeechGateway : SpeechGateway` class in `speech/`.
4. In `AppModule.kt`, change the `@Binds` for `SpeechGateway` to point to `BhashiniSpeechGateway`.
5. The key is **never** hardcoded in the APK. Read it via `AssetDataSource` or a local config reader.

---

## Matching Logic

Implemented in `MatcherUseCase.kt`:

1. **Education gate**: a role's NSQF level must be ≤ the user's education level's maximum NSQF.
2. **Interest affinity**: user interests are matched to role sectors; each match adds +10 to the score.
3. **Tie-break**: lower NSQF level preferred for lower-literacy users (promotes accessible roles).
4. **Centres**: only shown if the centre's district matches the user's district exactly.
5. **CENTRE_DATA_GAP**: if no centre found, the app shows a "confirm with TAHDCO" note instead of fabricating one.

---

## Legal Disclaimers

Shown on every Detail screen:
- *"Course/centre details are indicative; confirm eligibility, fees and empanelment with the training centre / TAHDCO / the relevant Skill Council before enrolling."*
- *"Asset subsidy: up to Rs.50,000 or 50% of asset cost (with loan), whichever is lower."*

---

## Running Tests

```bash
./gradlew test
```

Tests cover `TextParserTest` (10 cases) and `MatcherUseCaseTest` (6 cases).
