# PROMPT — paste this into Antigravity / Codex to build the premium native Android app

> You are building a production-quality, **native** Android application.
> DO NOT use a WebView or embed HTML. This must be a first-class Kotlin app with a
> premium, modern UI. Follow every instruction below and use the data + assets that ship
> in the same folder as this prompt.

## 0. Project identity
- App name: **ThozhilThunai** (Tamil: தொழில்துணை, "your livelihood companion").
- Purpose: help rural, low-literacy job-seekers in Tamil Nadu discover government
  skill-training courses (NSQF Qualification Packs) and the nearest verified training
  centres — in their own language, mostly offline.
- Target users: school dropouts to graduates, rural, often prefer voice, small phones,
  may have patchy internet. Design for thumbs and large tap targets.

## 1. Stack (non-negotiable)
- Language: **Kotlin**. UI: **Jetpack Compose** + **Material 3** (Material You).
- minSdk 24, targetSdk 34. Architecture: MVVM + Repository. Dependency injection: Hilt.
- Local data: read the bundled JSON in `data/` at startup (no network needed).
  Optionally cache in Room; do NOT fetch the core dataset from the internet.
- Navigation: Jetpack Navigation Compose. Images: Coil. Icons: Material Symbols.
- Build must succeed in Android Studio (Gradle Kotlin DSL), zero warnings.

## 2. Design system — make it feel PREMIUM (this is the point)
- Brand colours (from the provided logo): indigo `#1B3A8C` → teal `#0FA3A3` gradient;
  saffron `#FF8A1F` accent; near-white surface, deep-navy text.
- Use Material 3 dynamic colour but seed it with the brand indigo/teal.
- Rounded-2xl cards, soft elevation shadows, generous 16–24dp padding, smooth
  spring animations, subtle hero transitions between screens.
- Typography: large, high-contrast. Tamil-first: use a font that renders Tamil cleanly
  (Noto Sans Tamil); support font-size scaling (accessibility).
- Empty/loading/error states must be designed, not afterthoughts.
- Dark mode support.
- The provided `brand/logo_premium.png` is the launcher icon + splash logo.

## 3. Information architecture / screens
1. **Splash** (logo, brand gradient).
2. **Language select** — FIRST screen. Show 6 languages in their own script with a native
   name each: Tamil தமிழ், English English, Hindi हिन्दी, Telugu తెలుగు, Kannada ಕನ್ನಡ,
   Malayalam മലയാളം. Once chosen, the ENTIRE app renders in that one language only —
   never mix two languages on screen. Persist the choice; allow switching in Settings.
3. **Onboarding** (3 short illustrated slides: what it is / answer 3 questions / see
   courses + centres). Skip-able.
4. **Home** — greeting + "Find my course" primary card + quick stats (X courses, Y centres).
5. **Intake** (the matcher). Ask 3 things, big friendly controls:
   - Education level (chips: below 8th, 8th, 10th, 12th, ITI/Diploma, Graduate+).
   - District (searchable dropdown of 38 TN districts).
   - Interests (multi-select chips: dairy, poultry, tailoring/apparel, food processing,
     electrical, IT/digital, beauty, driving/logistics, retail, handicrafts).
   - Optional free-text / voice line ("I studied 10th in Erode, I like milk work") that the
     parser turns into the fields above.
6. **Results** — ranked list of matched job roles. Each card: role name, NSQF level badge,
   notional hours, sector, funding tag. Tap → detail.
7. **Detail** — role info, eligibility, and the nearest **verified training centres** for
   the chosen district (name, address, phone with tap-to-call, trades). If none in the
   district, show the gentle "confirm with TAHDCO" note (see disclaimers).
8. **Settings** — change language, text size, about, disclaimer.

## 4. Data (already verified — use as-is)
- `data/job_roles.json` — 516 real NSQF Qualification Packs
  (qp_code, job_role, nsqf_level, notional_hours, ssc, sector).
- `data/centres.json` — 20 verified training centres (district, name, address, phone,
  trades, dairy_course, confidence).
- `data/districts.json` — 38 districts; which have a centre vs which don't.
- `data/i18n.json` — all UI strings + interest chips in 6 languages. **Use these; do not
  invent your own translations. Add any new string to all 6 languages.**

## 5. Matching logic (on-device, deterministic — mirror these rules)
- Filter job roles by the chosen education level using each role's stated entry level
  (read-and-write < 8th < 10th < 12th < graduate).
- Prefer roles matching selected interests/sector (dairy/animal-husbandry, apparel, food
  processing, electrical, IT, etc.).
- Rank by closeness of match; never invent a level that isn't in the data (if the level is
  inferred, mark it "(inferred)").
- Centres: only show a centre if it is genuinely in the user's district; dairy roles map to
  the verified TANUVAS/VUTRC centres; if the district has no centre show the
  CENTRE_DATA_GAP note instead of fabricating one.

## 6. Voice + AI (with graceful offline fallback)
- Voice input: try **Bhashini** (speech-to-text + translation across Indian languages) via a
  small interface `SpeechGateway`. If no Bhashini key is configured, fall back to Android
  `SpeechRecognizer`, then to plain typing. Never crash without internet.
- Free-text parsing: implement a deterministic on-device parser that reads a sentence and
  fills education/district/interests (keyword tables are in `data/` + spec). Leave a clean
  seam `TextParser.parse(sentence): Fields` so a real AI model can replace it later.
- Bhashini/AI keys must NOT be hard-coded in the APK — read from a local config; design for a
  backend swap.

## 7. Disclaimers (legal safety — show where indicated)
- "Course/centre details are indicative; confirm eligibility, fees and empanelment with the
  training centre / TAHDCO / the relevant Skill Council before enrolling."
- Show asset-subsidy rule only as: "up to Rs.50,000 or 50% (with loan), whichever is lower" —
  never state a per-trainee cap that isn't in the data.

## 8. Quality bar
- Unit tests for the matcher + parser. Compose previews for key screens.
- Lint clean. No deprecated APIs. Kotlin coroutines/Flow. No blocking main thread.
- Provide a README with: how to run, where the data lives, how to add a language,
  how to wire Bhashini.

Deliver the complete, runnable Android Studio project in this folder.
