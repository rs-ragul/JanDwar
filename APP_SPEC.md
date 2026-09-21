# ThozhilThunai — App Spec & Verified Facts (read before coding)

This file is the single source of truth for the Android build. Everything here was
verified against official sources during research (Sept 2026). Do not invent numbers,
levels, centres, or subsidy figures that are not stated here or in `data/`.

## The real numbers (audited)
- **516** NSQF Qualification Packs (job roles), 0 duplicate codes, across 7 Sector Skill Councils.
- Of those, **510** are embedded in-app; **343** are fundable under PM-AJAY-type schemes.
- **20** verified training centres in Tamil Nadu (TANUVAS / VUTRC network).
- **38** Tamil Nadu districts; **20** have a verified centre, **18** do not.
- Levels: **430** official NSQF levels + **86** marked "(inferred)" where the source didn't state one.
- NOTE: a figure like "1892" does NOT exist in this project. If you see it referenced
  anywhere, it is stale — the authoritative counts are the ones above.

## PM-AJAY / funding (say only this)
- 100% centrally funded; no per-trainee cap to quote.
- Asset subsidy: up to **Rs.50,000 or 50% of asset cost (with loan), whichever is lower** — only
  alongside a loan. Do not state other amounts.
- Long-term courses need Class 10; short-term do not.

## Tamil Nadu specifics
- TN implements these via **TAHDCO**, not PMKVY branding.
- Dairy / livestock job roles resolve to the verified TANUVAS/VUTRC centres in `data/centres.json`.

## Matching rules (implement on-device)
1. Education gate: a role's entry requirement must be <= the user's level. Order:
   read-and-write < below-8th < 8th < 10th < 12th < ITI/Diploma < graduate.
2. Interest/sector affinity: match user's chosen interests to role sector
   (dairy/animal-husbandry, apparel, food processing, electrical, IT/digital, beauty,
   logistics, retail, handicrafts).
3. Rank by closeness; tie-break by lower NSQF level for lower-literacy users.
4. Never display a level that isn't in the data; if inferred, suffix "(inferred)".
5. Centres: show only if truly in the user's district. If none, render the CENTRE_DATA_GAP
   note ("confirm with TAHDCO") — do not substitute a centre from another district.

## Known data cautions (surface as gentle notes, not errors)
- Some QPs are retired without their pages being removed; re-verify before recommending a
  role for enrolment. (Flag animal-husbandry roles as "confirm availability".)
- Animal-husbandry training partners need an NOC from the state AH department (ASCI gate).
- A few centre addresses/phones were transcribed as published; verify before dialling in
  production.

## i18n (critical UX requirement)
- Language is chosen FIRST and then the entire UI is in that ONE language. No bilingual
  mixing anywhere. `data/i18n.json` holds all strings + interest chips for:
  ta, en, hi, te, kn, ml. When adding a string, add it to all six.
- Tamil shaping: use Noto Sans Tamil; test that long Tamil words wrap (overflow-wrap).

## Screens / flow (premium, native Compose)
Splash → Language select → Onboarding(3) → Home → Intake(education/district/interests +
optional voice/text) → Results(ranked) → Detail(role + verified centres + disclaimer) → Settings.
See `PROMPT_for_AndroidAI.md` for the full build prompt and design system.

## Voice / AI seam
- `SpeechGateway` interface: Bhashini (primary) → Android SpeechRecognizer → typing.
- `TextParser.parse(sentence): Fields(education, district, interests)` deterministic now,
  swappable for a real model later. No keys in the APK.

## Brand
- Logo: `brand/logo_premium.png` (indigo #1B3A8C → teal #0FA3A3, saffron #FF8A1F accent).
- Launcher icon + splash use it. No text in the logo.

## Files in this pack
- `PROMPT_for_AndroidAI.md` — the build prompt (paste into Antigravity/Codex).
- `APP_SPEC.md` — this file.
- `data/job_roles.json` (516) · `data/centres.json` (20) · `data/districts.json` (38) · `data/i18n.json` (6 langs).
- `brand/logo_premium.png` — the premium logo.
