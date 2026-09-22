THOZHIL THUNAI (தொழில் துணை)

**A voice-first, offline-capable livelihood navigator for PM-AJAY beneficiaries**

Complete Project Proposal — Smart India Hackathon 2026

| **Problem Statement ID** | **SIH26097**                                                                                                                                 |
| ------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Problem Statement Title  | AI-Driven Voice Assistant for Livelihood Mapping and NSQF-Aligned Skilling Recommendations for SC Communities under GIA component of PM-AJAY |
| Organization             | Ministry of Social Justice & Empowerment (MoSJE)                                                                                             |
| Theme                    | Agriculture, FoodTech & Rural Development                                                                                                    |
| Category                 | Software                                                                                                                                     |
| Idea Title               | Thozhil Thunai — a voice-first livelihood navigator                                                                                          |
| Team Name / ID           | \[To be filled to match portal registration\]                                                                                                |

# 1\. Executive Summary

Thozhil Thunai ('livelihood companion') is a voice-first, offline-capable assistant that helps rural, low-literacy beneficiaries of PM-AJAY discover the right Government skill-training course (NSQF Qualification Pack) and the nearest verified training centre — in their own language. Instead of a form, the user answers with five large taps or one spoken sentence; the app returns three ranked, scheme-valid livelihood pathways, each tied to a real course code, a funding rule, and a real place to learn it with address and phone number.

The proposal is grounded in a curated, verified data asset that does not exist elsewhere: 516 real NSQF Qualification Packs harvested from 7 Sector Skill Councils, joined to 20 verified training centres across 20 Tamil Nadu districts, with 343 of 516 (66%) sitting in PM-AJAY-fundable domains. A PM-AJAY rule layer guarantees that no recommendation can look valid while being scheme-invalid. A working prototype already runs fully offline; voice is powered by Bhashini with a graceful on-device fallback.

# 2\. Problem Understanding

The PM-AJAY guidelines themselves state the failure we attack: a 'mismatch between enrolled training programs and the beneficiary's interests, capabilities, or local market demand, leading to high dropout rates and poor post-training employment outcomes.' MoSJE separately lists 'job placement issue after the skilling programme' among its ground-level issues.

For the intended user — often a low-literacy SC beneficiary in rural Tamil Nadu — the barriers are:

- Forms and portals assume literacy and connectivity she does not have.
- She cannot map her interests to a 500+ course catalogue, NSQF levels, or eligibility gates.
- There is no public, district-wise list of empanelled training centres, so 'where do I learn it' is unanswered.
- Scheme rules (Class-10 gate, fundable domains, asset subsidy) are scattered across a 46-page guideline.

Our answer: meet her in the only interface she can operate — voice and big buttons — and do the mapping, rule-checking and centre-finding for her, honestly and offline.

# 3\. Objectives

- Replace the form with a voice/tap intake usable at low literacy (Tamil + 5 more languages).
- Match each beneficiary to up to three NSQF-aligned, scheme-valid livelihood pathways.
- Tie every recommendation to a funding rule (PM-AJAY category, asset-subsidy rule) and a verified centre.
- Run fully offline so it works where connectivity fails; use Bhashini/AI only as an enhancement.
- Surface data gaps honestly instead of fabricating numbers (verified-honesty principle).
- Deliver a working, testable prototype plus a path to scale to more states, languages and councils.

# 4\. Proposed Solution

**User flow:**

- Language first: the app opens by asking the language; the whole UI then renders in that one language.
- Intake: five large taps — education level, own-business-or-job, travel range, district, interest — or one spoken sentence parsed into the same fields.
- Match: a deterministic engine filters by education ceiling and the Class-10 rule, then scores on course-category fit, self-employment fit, fundability, stated interest and course length.
- Result: three ranked pathways, each showing NSQF code, PM-AJAY category & duration, fundable-domain check, the asset-support rule she qualifies for, and a verified centre (address + tap-to-call phone).
- Honesty: where we lack a verified centre (18 of 38 districts) the app says so plainly and gives the TAHDCO route — it never shows a far-away centre as if local.

**Design principles:**

- AI is confined to one job — converting speech/text into structured fields. It never selects a course and never emits a QP code, so it cannot hallucinate a training programme.
- Every fact carries provenance (CONFIRMED / PARTIAL / NOT FOUND).
- Offline-first: the dataset ships inside the app; no internet is required to match.

# 5\. The Data Asset (verified)

| **Asset**                      | **Count** | **Notes**                                                       |
| ------------------------------ | --------- | --------------------------------------------------------------- |
| NSQF Qualification Packs       | 516       | 0 duplicate codes; harvested from 7 Sector Skill Councils       |
| Fundable under PM-AJAY domains | 343 (66%) | per Annexure-I fundable-domain mapping                          |
| Official NSQF levels           | 430       | \+ 86 marked '(inferred)' where source omitted a level          |
| Verified training centres (TN) | 20        | TANUVAS / VUTRC network, each with address + phone + confidence |
| Tamil Nadu districts           | 38        | 20 have a verified centre; 18 labelled 'no verified centre yet' |

Sector Skill Councils harvested: FICSI, CSDCI, MESC, IASC, ASCI, TSC (TexSkill), AMHSSC. Where a council omitted the NSQF level or hours we left the field blank rather than guess.

# 6\. The PM-AJAY Rule Layer

We encode the actual guidelines so a recommendation can never be scheme-invalid:

| **Rule**                 | **Encoded behaviour**                                                                                   |
| ------------------------ | ------------------------------------------------------------------------------------------------------- |
| Education ceiling        | A role's entry requirement must be ≤ the user's level; long-term courses hard-blocked below Class 10    |
| Course category by hours | Short-term vs long-term categorised per guideline; matched to user preference (wage vs self-employment) |
| Fundable domains         | Only Annexure-I domains are flagged fundable; apparel left unconfirmed and not claimed                  |
| Asset subsidy            | Shown only as 'up to Rs.50,000 or 50% of asset cost (with loan), whichever is lower'                    |
| Funding                  | 100% central; no per-trainee cap quoted (none is published)                                             |
| Women / SC earmarks      | Supports ≥15% grants to SC-women income schemes and ≥30% women in batches                               |

# 7\. Technical Approach

| **Layer**  | **Technology**                                    | **Notes**                                                |
| ---------- | ------------------------------------------------- | -------------------------------------------------------- |
| Harvest    | Python (urllib, regex table extraction)           | no paid APIs; normalise + repair; blanks over guesses    |
| Knowledge  | single sourced Python module                      | every fact tagged CONFIRMED / PARTIAL / NOT FOUND        |
| Matching   | deterministic scoring engine                      | no model training, no GPU                                |
| Voice / AI | Bhashini / IndicTrans2, Web Speech fallback       | keys via config, never hard-coded; seam for backend swap |
| Frontend   | one ~120 KB self-contained PWA                    | Tamil + 5 languages; installable; offline                |
| Storage    | none required                                     | data embedded; runs with no internet                     |
| Testing    | engine↔app parity harness + 22-assertion UI suite | automated agreement between engine and app               |

Implementation pipeline: Harvest → Curate → Match → Deliver. The tap path (offline) and the voice path (online) produce the same answer.

# 8\. Innovation & Uniqueness

- A PM-AJAY rule layer, not a generic recommender — recommendations are scheme-valid by construction.
- A curated dataset that does not exist elsewhere (QPs × fundability × verified centre delivery).
- Verified honesty — public gaps (no district centre list, no rupee cap) are surfaced in the UI, not fabricated.
- AI confined to speech→fields so it can never invent a course or code.
- Offline-first single-file delivery that 'cannot fail on stage'.

# 9\. Feasibility & Viability

**Already built, not proposed:**

- 516 QPs harvested and cleaned; 20 of 20 centres captured with confidence flags.
- Engine and app agree (parity verified automatically); 22 of 22 UI assertions passing.

**Defects found & fixed during the build:**

- Class-10 long-term course recommended to an 8th-pass → now hard-blocked by the guideline rule.
- Wage courses recommended to a self-employment seeker → category must match stated preference.
- Stale sector map marked 237 agriculture/textile QPs unfundable → corrected.
- Engine/app disagreed on showing a centre → one shared rule, enforced by parity test.
- A centre with no published course list was offered as bookable → filtered.

Viability: runs on low-end Android as an installable PWA; one kiosk per village Common Service Centre is sufficient; no licence cost; scales by adding council sites (29 councils remain).

Known limits, stated openly: 86 QPs carry '(inferred)' levels (labelled on screen); apparel fundability unconfirmed; councils retire QPs without deleting pages (re-verify before use); TNSDC portals returned HTTP 500 and dependent rows are flagged PARTIAL.

# 10\. Implementation Plan & Timeline

| **Phase**        | **Duration** | **Deliverable**                                          |
| ---------------- | ------------ | -------------------------------------------------------- |
| 0 Baseline       | Done         | 516 QPs + 20 centres + rule layer + offline prototype    |
| 1 Bhashini voice | Weeks 1-2    | live ASR/NMT/TTS behind SpeechGateway; fallback verified |
| 2 Native Android | Weeks 2-4    | Kotlin/Compose app; Play-Store-ready release build       |
| 3 Pilot          | Weeks 4-8    | Tamil Nadu pilot via TAHDCO / CSC kiosks; field feedback |
| 4 Scale          | Weeks 8-12   | more languages, more councils, backend for AI/Bhashini   |

Team (6 members + AI agent): Team Lead & coordination; Data & Research (harvest/provenance); Matching Engine; Frontend/UX (Compose, i18n); Voice/AI integration (Bhashini); Testing & Documentation. The AI agent acts as a force multiplier across all roles.

# 11\. Risks & Mitigations

| **Risk**                       | **Mitigation**                                                |
| ------------------------------ | ------------------------------------------------------------- |
| Patchy connectivity            | offline-first; data embedded; voice optional                  |
| Low literacy / trust           | voice + big UI; read-aloud; plain language; honesty notes     |
| No district centre list        | surface gap + TAHDCO route; never substitute another district |
| Retired QPs                    | provenance tags + re-verify pipeline before each release      |
| API key exposure (Bhashini/AI) | keys via config/backend, never in APK                         |
| Adoption                       | pilot with TAHDCO/CSC; align to scheme earmarks               |

# 12\. Impact & Benefits

**Worked example the system produces today:**

An 8th-pass woman in Erode seeking her own dairy business is matched to a 1-month Dairy Farming course at Rs.1,000, entry 'read & write in Tamil', at VUTRC Erode (306 Sathy Road, Erode — 0424-2291482), together with the Rs.50,000 / 50% asset-support rule she qualifies for.

| **Stakeholder**  | **Benefit**                                                                                         |
| ---------------- | --------------------------------------------------------------------------------------------------- |
| Beneficiary      | 3 concrete pathways in her language — fee, entry, phone; no form, no travel to enquire              |
| Ministry         | attacks stated dropout/placement failure; 66% fundable; audit-ready traceability; supports earmarks |
| Training centres | idle trained capacity converted to matched enrolments                                               |
| Nation           | better return on public skilling spend; reduced distress migration                                  |

# 13\. Scalability & Future Scope

- Languages: adding a language is a translation block; 6 already, all Indian languages via Bhashini.
- States: data-driven design — swap the centre network per state (Tamil Nadu is the pilot).
- Councils: harvester scales to the remaining 29 Sector Skill Councils.
- Backend: move Bhashini/AI behind a small hosted service for the public Play-Store build.
- Monetisation/adoption: government adoption (MoSJE/TAHDCO) + CSC kiosk deployment; no user fee.

# 14\. References & Sources

- PM-AJAY Guidelines, MoSJE, May 2023 (Revised), 46 pp — pmajay.dosje.gov.in
- MSDE Common Cost Norms — aspire.msme.gov.in
- TAHDCO (TN implementing agency) — tahdco.com; cross-checked with Tiruvarur district NIC portal
- TANUVAS VUTRC network — tanuvas.ac.in/vutrcs.php + 20 individual centre pages
- Sector Skill Council catalogues — ficsi.in, csdcindia.org, mescindia.org, iascsectorskillcouncil.in, asci-india.com, texskill.in, sscamh.com
- NSDC master index of SSCs — nsdcindia.org/sector-skill-councils

Data harvested 20 September 2026. QP versions, fees and centre details change without notice — re-verify before final submission.

Disclaimer: course/centre details are indicative; confirm eligibility, fees and empanelment with the training centre / TAHDCO / the relevant Skill Council before enrolling.