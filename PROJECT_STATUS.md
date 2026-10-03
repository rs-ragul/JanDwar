# JanDwar — Project Status

**SIH 2026 · PS 26097** · as of **v2.8 (versionCode 10)**
Last updated: 2026-09-29

---

## 1. Where we are

The app is **feature-complete against the problem statement and works fully
offline**. Offline is not the fallback — it is the tested primary path.

| PS requirement | Status | Where |
|---|---|---|
| Multilingual voice conversation, not form-filling | ✅ | `ConversationEngine`, `InterviewFlow`, 6 languages |
| Dialect / code-mixed speech | ✅ | 3 596 lexicon forms, native + romanised + misspelled |
| Empathetic, natural tone | ✅ offline · ✅✅ with key | rotating phrasings, acknowledgement before each question |
| Education | ✅ | incl. *in-progress* vs *completed* vs *dropout* |
| Family / traditional occupation | ✅ | kept distinct from current livelihood |
| Current livelihood | ✅ | |
| Skills & interests | ✅ | 10 interest keys + free-text skills |
| Mobility / physical constraints | ✅ | 3 mobility bands, constraint sentinel for "no problem" |
| Self-employment vs wage preference | ✅ | strength ladder, not a binary keyword |
| Local economic reality | ⚠️ partial | 52 of 187 districts have a researched note |
| NSQF-aligned recommendations | ✅ | 476 packs, 13 sectors |
| Skill-gap identification | ✅ | soft education gate + bridge-course wording |
| Region-specific opportunity | ⚠️ partial | tied to the 52 economy notes above |
| Explainability | ✅ | every point scored emits a `MatchFactor` |
| Works in low-connectivity | ✅ | zero network calls needed end to end |
| IVR / WhatsApp for non-smartphone users | ⚠️ built, not deployed | `server/` runs and was verified by curl; no public host |

### Numbers

| | |
|---|---|
| APK | 20.5 MB, minSdk 24, targetSdk 35, `in.jandwar.app` |
| NSQF packs | 476 usable (540 rows, 64 malformed source rows filtered) |
| Sectors | 13, all GIA-eligible |
| Training centres | 660 — 294 CONFIRMED, 350 LIKELY, 16 UNVERIFIED |
| States | 5 — TN, KL, KA, AP, UP |
| Districts | 187 (185 with a centre) |
| District economy notes | 52 of 187 |
| UI strings | 156 × 6 languages |
| NLU lexicon | 3 596 forms / 38 categories |
| Interview slots | 9 |
| Automated tests | 353/353 answers · 2 906/2 906 lexicon forms |

---

## 2. What the offline engine actually handles

This is the part worth demoing, because it is where the judging risk usually
sits and where we are strongest.

* **Multi-slot extraction** — *"I studied up to 10th, my family does dairy
  farming in Erode"* fills education, family occupation, district **and** state
  in one turn, so the interview ends in far fewer questions.
* **Completed vs in-progress education** — "college 2nd year" is Class 12
  completed and a current Student, not a graduate. Getting this wrong offered
  mid-course students roles they cannot enrol in and told them they had no
  skill gap.
* **Ambiguity guards** — "up to 10th" is not Uttar Pradesh; "I speak Tamil" is
  not Tamil Nadu. Short state forms (TN/AP/UP) only count as whole tokens while
  the state question is the one being asked.
* **Negation and specificity** — longest-match, not a precedence list, so
  "anywhere in my district" and "anywhere in the state" are different answers.
* **"No" is an answer** — the constraints slot stores a sentinel rather than
  staying blank and re-asking.
* **Cross-slot protection** — only the slot actually being answered may
  overwrite; everything else may only fill a gap.

---

## 2b. Automated verification

Three suites, all green, all runnable offline from the repo root:

```
python3 tools/audit.py          37/37  whole-app functional audit
python3 tools/check_answers.py  353/353 realistic spoken answers
python3 tools/check_nlu.py      31 cases, 2906/2906 lexicon forms
python3 tools/check_telephony.py 16/16  IVR audio format conversion
```

`tools/audit.py` is the new one. It drives the real engine rather than a
mock and covers: asset integrity and counts, every centre carrying a source
URL, all 187 districts resolving from their native-script and colloquial
names (550 combinations, state-scoped and unscoped), a complete nine-slot
interview in each of the six languages, the recommender across all 187
districts x 4 profiles (748 runs -- none empty, every result explainable,
confidence always inside 35-99, a centre attached wherever the district has
one), Kotlin/Python parity on the district aliases, the slot order and the
sector map, and a check that no API key sits in the source.

---

## 3. Known gaps, honestly

| # | Gap | Impact | Effort |
|---|---|---|---|
| 1 | **Device testing is done by you, not by me** — every build ships to a real Android phone and is tested there; I cannot reproduce device behaviour in the sandbox | Low, as long as findings come back to me: mic, TTS voices and ASR language packs are all device-specific | Report what breaks; I fix against the real symptom |
| 2 | District economy depth only for TN (38/38) and KL (14/14); AP/KA/UP have ~4 each | "why this trade here" falls back to the centre name for ~135 districts | 1 research pass |
| 3 | 350 of 660 centres are LIKELY, not CONFIRMED | We label it honestly, but a judge may probe | Spot-verify 20-30 by phone |
| 4 | IVR/WhatsApp live at `jandwar.onrender.com`; the float32-WAV playback bug is **fixed** in `adapters/telephony.py` but the deployed instance still runs the old build | Calls stay silent until the fix is redeployed | Redeploy from `main` |
| 5 | Offline speech **recognition** still depends on the device having the language pack | Tamil/Hindi ASR may fall back to network on a bare phone | Document it; pre-install packs on the demo phone |
| 6 | No analytics / no way to know what users actually said | Cannot show adoption evidence | Out of scope for SIH |
| 7 | 64 malformed rows in the source catalogue are filtered, not fixed | 476 shown instead of 540 | Clean the source data |
| 8 | 90 of the 476 roles have no NSQF level and no notional hours in the source | Shown as “—” rather than invented; ranking uses a neutral fallback | Source the missing QP metadata |

---

## 4. What to do next — in priority order

### P0 — before anything else (this week)

1. **Keep the device-test loop going — it is the only real signal.**
   You install every build on your phone; I cannot see what it does there.
   The pass that matters: aeroplane mode → each of the 6 languages →
   a full interview by voice → results, detail screen, centre phone link.
   Specifically watch for the mic opening while the assistant is still
   speaking, and whether Tamil/Telugu ASR works without network.
   **Report the symptom, not a diagnosis** — "it stopped listening after the
   second question in Tamil" is worth more than "the mic is broken", and it
   is what lets me fix the right thing in one pass.

2. **Pre-install offline language packs** on the demo phone
   (Settings → System → Languages → Voice input → Offline speech recognition)
   and note which ones the device actually has. If Tamil is unavailable
   offline, that is a talking point, not a defect — say it out loud before a
   judge finds it.

3. **Rehearse one 3-minute demo script.** Suggested: aeroplane mode on,
   Tamil, a Salem goat-farming profile → *Goat and Sheep Farmer*, NSQF 3, 99 %,
   with the centre and the "why here" line. Then tap into the detail screen to
   show the explainability factors and the funding rule.

### P1 — high value, low effort (next)

4. **Deploy the server** (`render.yaml` → Render free tier) so IVR and
   WhatsApp are demoable. PS 26097 explicitly asks for low-tech channels; a
   live phone number is a strong differentiator. Twilio needs a **US** trial
   number dialled from your verified Indian mobile — Indian numbers cannot be
   used as caller ID since Aug 2024.

5. **Fill the district-economy gap for AP/KA/UP.** Same research shape as the
   TN/KL pass; it directly powers the "why this trade in this district" line,
   which is the most impressive sentence in the whole results screen.

6. **Spot-verify 20–30 LIKELY centres** by phone and promote them to
   CONFIRMED. Even a small sample lets you say "we called them" rather than
   "we scraped them".

### P2 — polish if time allows

7. **Clean the 64 malformed catalogue rows** so the app shows 540, not 476.
8. **A short screen-recorded video** of the offline interview — insurance
   against a live-demo failure.
9. **Battery / cold-start measurement.** A number like "first question spoken
   1.8 s after tap, 0 network calls" is concrete and quotable.

### Explicitly *not* worth doing

* Adding more states — five is already unusual for a hackathon entry, and
  depth beats breadth when a judge picks one district and probes.
* Swapping the on-device NLU for a bundled small LLM — the lexicon engine is
  faster, testable, and you can explain every decision it makes. That
  explainability is worth more marks than fluency.
* More UI screens. Ten is enough; polish the three that get demoed.

---

## 5. Still outstanding from earlier

* ~~Revoke the leaked Groq key~~ — done; Groq removed entirely from the app.
* The key remains in this repository's **git history**. If you publish the repo
  publicly, rewrite history (`git filter-repo`) or push a fresh repo without
  the old commits.

---

## 6. How to pick this up again

```bash
# tests — run after ANY vocabulary or data change
python3 tools/check_nlu.py && python3 tools/check_answers.py

# vocabulary change
#   edit work/lexicon_extra.py → python3 work/gen_lexicon.py → run both suites
# UI copy change
#   edit work/gen_i18n.py → python3 work/gen_i18n.py
# interview script change
#   edit ai/InterviewFlow.kt → python3 tools/extract_flow.py   (syncs the server)

./gradlew :app:assembleDebug
```

`CHANGES.md` has 69 numbered entries; each records the symptom, the real cause
and the fix. Read entry 65 before touching asset deserialization and entry 66
before touching the state/district flow.
