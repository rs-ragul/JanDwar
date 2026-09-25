# ThozhilThunai — AI Implementation Plan (Final)

_Last updated: 2026-09-25_

Read `PROJECT_STATUS.md` + `README.md` first.

Guiding principle: **AI is the interface (voice + language); the rule-layer + verified data is the
product.** Everything must be FREE and must DEGRADE gracefully offline.

---

## Architecture: 3-Tier Brain

```
┌──────────────────────────────────────────────────────────────┐
│                    TIER 1 — ONLINE (Best)                    │
│  Bhashini ASR → Bhashini NMT (→ English) → Groq Llama 3.1   │
│  Bhashini TTS speaks response back in user's language        │
│  Cost: FREE (Bhashini = Govt of India, Groq = 14,400/day)   │
├──────────────────────────────────────────────────────────────┤
│                 TIER 2 — OFFLINE, ON-DEVICE AI               │
│  Android SpeechRecognizer → Gemma 1.1 1B via MediaPipe      │
│  Android TextToSpeech speaks response                        │
│  Model: ~500MB download on first use (not in APK)           │
│  Cost: FREE forever, no internet needed                      │
├──────────────────────────────────────────────────────────────┤
│              TIER 3 — LAST RESORT + JSON VALIDATOR           │
│  DeterministicParser — keyword rules on extracted text       │
│  ALSO validates JSON from Tier 1 & Tier 2 before trusting   │
│  Cost: FREE, fully offline, zero dependencies                │
└──────────────────────────────────────────────────────────────┘
```

**Judge narrative:** "We use Bhashini (Ministry of Electronics & IT, Govt of India) for multilingual
voice, and Meta's open-source Llama 3 (free via Groq) for AI understanding online. Offline, we use
Google's open-source Gemma 1.1 1B running entirely on the device — no internet, no cost, real AI."

---

## 1. Core Interfaces (The Seams)

```java
public interface SpeechGateway {           // voice → text
    void start(); void stop();
    void setListener(Listener l);
    interface Listener {
        void onPartial(String s); void onResult(String s); void onError(String e);
    }
}
public interface Speaker {                 // text → voice (native language)
    void speak(String text, Runnable onDone); void stop();
}
public interface Translator {              // regional text → English
    void translate(String text, String fromLang, Callback cb);
    interface Callback { void onResult(String translated); void onError(String e); }
}
public interface NluExtractor {            // THE KEY SEAM — text → structured profile
    void extract(String spokenText, String langCode, Callback cb);
    interface Callback {
        void onResult(ProfileFragment fragment);
        void onError(String reason);
    }
}
```

Implementations:
- `BhashiniAsrGateway`  → SpeechGateway (Tier 1)
- `LocalSpeechGateway`  → SpeechGateway (Tier 2/3, uses existing Android SpeechRecognizer)
- `BhashiniTtsGateway`  → Speaker (Tier 1)
- `LocalTts`            → Speaker (Tier 2/3, uses Android TextToSpeech)
- `BhashiniTranslator`  → Translator (Tier 1)
- `GroqExtractor`       → NluExtractor (Tier 1, Llama 3.1 8B)
- `GemmaExtractor`      → NluExtractor (Tier 2, Gemma 1.1 1B via MediaPipe)
- `DeterministicParser` → NluExtractor (Tier 3, also JSON validator)
- `TieredNluExtractor`  → NluExtractor (orchestrates all 3 tiers)

A `GatewayFactory` wires everything: returns Bhashini* if keys configured, else Local*.

---

## 2. ProfileFragment (Structured Output from One Answer)

```java
public class ProfileFragment {
    public String edu;             // "none","class5","class8","class10","class12","graduate"
    public String preference;      // "self_employment","wage_employment"
    public List<String> interests; // ["dairy","cattle","textile",...]
    public String district;        // district name string
    public String mobility;        // "local","district","state"
    // null = field not found in this answer
}
```

---

## 3. TieredNluExtractor (Orchestrates Tiers)

```java
public class TieredNluExtractor implements NluExtractor {
    public void extract(String text, String langCode, Callback cb) {
        if (NetworkUtils.isConnected() && groq.isConfigured()) {
            groq.extract(text, langCode, result -> {
                cb.onResult(deterministic.validate(result, text)); // validate Tier 1 output
            }, error -> tryGemma(text, langCode, cb));             // on failure → Tier 2
        } else {
            tryGemma(text, langCode, cb);
        }
    }
    private void tryGemma(String text, String langCode, Callback cb) {
        if (gemma.isReady()) {
            gemma.extract(text, langCode, result -> {
                cb.onResult(deterministic.validate(result, text)); // validate Tier 2 output
            }, error -> cb.onResult(deterministic.parse(text)));   // on failure → Tier 3
        } else {
            cb.onResult(deterministic.parse(text));                // Tier 3 directly
        }
    }
}
```

---

## 4. Groq NLU Prompt (Tier 1)

```
System: You are an AI assistant helping extract structured profile data from a rural Indian
user's spoken answer. Extract ONLY these fields if present (null if not mentioned):
- edu: one of [none, class5, class8, class10, class12, graduate]
- preference: one of [self_employment, wage_employment]
- interests: array from [dairy, cattle, goat, poultry, farming, food, machine, textile, construction, tailor]
- district: district name in Tamil Nadu
- mobility: one of [local, district, state]
Respond ONLY with valid JSON. No explanation. No markdown.
Example: {"edu":"class8","preference":null,"interests":["cattle"],"district":null,"mobility":null}

User: {USER_ANSWER_IN_ENGLISH}
```

API: POST https://api.groq.com/openai/v1/chat/completions
Model: llama-3.1-8b-instant
Header: Authorization: Bearer {GROQ_API_KEY}
Free tier: 14,400 requests/day

---

## 5. Gemma Prompt (Tier 2, on-device)

Shorter prompt for smaller model:
```
Extract profile from: "{USER_ANSWER}"
Fields: edu(none/class5/class8/class10/class12/graduate),
preference(self_employment/wage_employment),
interests(dairy/cattle/goat/poultry/farming/food/machine/textile/construction/tailor),
mobility(local/district/state)
JSON only:
```

MediaPipe dependency:
```gradle
implementation 'com.google.mediapipe:tasks-genai:0.10.14'
```
Model: Gemma 1.1 1B INT4 (~500MB, downloaded on first use, stored in getFilesDir())

---

## 6. BhashiniConfig

```java
public final class BhashiniConfig {
    public static String userId = "";
    public static String ulcaApiKey = "";
    public static String inferenceApiKey = "";
    public static String asrServiceId = "";
    public static String nmtServiceId = "";
    public static String ttsServiceId = "";
    public static final String BASE_URL =
        "https://dhruva-api.bhashini.gov.in/services/inference/pipeline";
    public static boolean enabled() {
        return !userId.isEmpty() && !ulcaApiKey.isEmpty();
    }
}
```
Load from `assets/config.json` (gitignored). NEVER hardcode keys in source.

---

## 7. ConversationController

Drives the interview. Asks questions via Speaker, listens via SpeechGateway,
extracts via NluExtractor, confirms, moves to next question.

Questions (in order):
1. Education → extracts `edu`
2. Family occupation + current livelihood → extracts `interests`
3. Self-employment or wage? → extracts `preference`
4. Can you travel for work? → extracts `mobility`
5. Which district? → extracts `district`
6. Any other interests? → fills remaining `interests`

After all fields collected → call existing `match()` → show results screen.

---

## 8. Order of Work

1. Interfaces + ProfileFragment + GatewayFactory skeleton
2. BhashiniConfig (load from config.json)
3. LocalSpeechGateway (refactor existing SpeechRecognizer code)
4. LocalTts (refactor existing TextToSpeech code)
5. GroqExtractor (Tier 1)
6. DeterministicParser (Tier 3, also JSON validator)
7. TieredNluExtractor (wires Tiers 1 + 2 + 3)
8. ConversationController + voice interview UI screen
9. BhashiniAsrGateway + BhashiniTtsGateway + BhashiniTranslator
10. GemmaExtractor (Tier 2, MediaPipe)
11. GatewayFactory wires everything
12. Test: Tamil voice → profile → results

---

## 9. Deployment Channels (Architecture, not code yet)

- **Mobile/kiosk:** Current app — primary channel
- **IVR (feature phones):** Same ConversationController driven by telephony IVR tree
- **WhatsApp voice-notes:** Same gateway behind WhatsApp Business webhook


Read `PROJECT_STATUS.md` + `README.md` first. This file adds the AI layer.
Guiding principle: **AI is the interface (voice + language); the rule-layer + verified data is the
product.** Everything must be FREE (no paid API in the shipped APK) and must DEGRADE to offline.

---

## 1. The seams (create these interfaces first; UI never changes afterwards)

```java
public interface SpeechGateway {           // voice -> text
    void start(); void stop();
    void setListener(L l);
    interface L { void onPartial(String s); void onResult(String s); void onError(String e); }
}
public interface Speaker {                 // text -> voice (native language)
    void speak(String text, Runnable onDone); void stop();
}
public interface Translator { String toLang(String text, String langCode); }
public interface ProfileParser { Profile parse(String sentence); }   // AI/ML profiling seam
```

Implementations (all free):
- `LocalSpeechGateway` = the EXISTING Android `SpeechRecognizer` (keep as default/fallback).
- `LocalTts` = Android `TextToSpeech` (set language to the chosen one).
- `DeterministicParser` = the EXISTING `parseSentence()` (offline profiler/guardrail).
- `BhashiniGateway` = implements SpeechGateway+Translator+Speaker via Bhashini ULCA (ASR/NMT/TTS).
  Used ONLY when keys are present; otherwise the Local* classes are used. Keys come from config,
  NEVER hard-coded.

A `GatewayFactory` returns Bhashini* if keys exist else Local*. The rest of the app calls only interfaces.

## 2. Bhashini gateway (fill base URL/endpoints from the Bhashini portal docs)

```java
public final class BhashiniConfig {
    public static String userId = "";      // load from config, not source
    public static String ulcaApiKey = "";
    public static String baseUrl = "";     // from Bhashini ULCA docs
    public static boolean enabled(){ return !userId.isEmpty() && !ulcaApiKey.isEmpty() && !baseUrl.isEmpty(); }
}
```
`BhashiniGateway` posts the recorded audio (ASR) / text (NMT) / text (TTS) to the three ULCA services with
`Authorization: Bearer <ulcaApiKey>` and the userId, and returns text / translated text / audio stream.
Wrap every call so a network failure falls back to the Local* implementation (do not crash offline).

## 3. The conversational interview (the PS's core ask)

A `ConversationController` state-machine over the 7 PS fields. It ASKS via `Speaker`, LISTENS via
`SpeechGateway`, EXTRACTS via `ProfileParser`, CONFIRMS, then next question. Taps remain the fallback.

```java
public final class ConversationController {
    public interface Done { void onProfile(Profile p); }
    private final Speaker sp; private final SpeechGateway asr; private final ProfileParser parse;
    private final List<Q> qs = List.of(
        q("education",        "cv_edu"),
        q("familyOccupation","cv_family"),
        q("currentLivelihood","cv_now"),
        q("interests",       "cv_interest"),
        q("mobility",        "cv_mobility"),
        q("preference",      "cv_pref"),
        q("district",        "cv_district"));
    private int i=0; private final Profile p=new Profile();
    public void start(){ ask(); }
    private void ask(){
        if(i>=qs.size()){ asr.stop(); done.onProfile(p); return; }
        sp.speak(t(qs.get(i).promptKey), this::listen);   // ask aloud in the user's language
    }
    private void listen(){
        asr.setListener(new SpeechGateway.L(){
            public void onPartial(String s){ /* show live transcript */ }
            public void onResult(String s){ apply(s); }
            public void onError(String e){ /* offer tap fallback */ }
        });
        asr.start();
    }
    private void apply(String s){
        Profile part = parse.parse(s);            // AI/ML or deterministic
        merge(p, part, qs.get(i).field);
        sp.speak(t("cv_confirm"), ()-> { i++; ask(); });
    }
}
```
`Profile` = the existing match() inputs (education, preference, travel, district, interests) plus the new
fields (familyOccupation, currentLivelihood, mobility).

## 4. AI/ML profiling + skill-gap output
- The ASR/NMT/TTS (Bhashini) IS the AI/ML layer; the `ProfileParser` is the profiling seam. Keep
  `DeterministicParser` as the offline profiler and guardrail. Optionally swap in an on-device small LLM
  (MediaPipe LLM Inference / llama.cpp with Gemma-2-2B) behind the same `ProfileParser` interface later.
- In results, add a **skill-gap** line per recommendation: compare the user's education/skills with the
  role's entry requirement, e.g. "Entry: read & write Tamil — you qualify" or "Needs Class 10 for this
  long-term course; see the 1-month option instead."

## 5. Deployment channels named in the PS (add to architecture/proposal, not yet code)
- IVR (feature phones): the SAME ConversationController driven by a telephony IVR tree.
- WhatsApp voice-notes: the same gateway behind a WhatsApp Business webhook.
- Lightweight mobile/kiosk: the current app.

## 6. Order of work
1. Add the four interfaces + Local* implementations (refactor existing code behind them).
2. Add ConversationController + voice interview UI (reuse the listening orb screen).
3. Add skill-gap line to results.
4. Add BhashiniGateway + BhashiniConfig; wire via GatewayFactory once keys arrive.
5. Unit-test match()/parse()/ConversationController transitions.
6. Test on a small phone in Tamil + Malayalam; verify continuous listening until Stop and Back behavior.

## 7. If Bhashini is not available — free on-device fallbacks (no paid API)
- ASR offline: Vosk / Sherpa-onnx (JNI) or whisper.cpp (open weights).
- Translation: IndicTrans2 (open, AI4Bharat).
- TTS: Android TTS / Piper.
- Optional LLM: MediaPipe LLM Inference / MLC with Gemma/Llama small models.

**Judge narrative:** "The AI (Bhashini/open models) is only the voice-and-language interface; the originality is
our PM-AJAY rule-layer and the curated, verified dataset, which runs offline with zero AI and zero internet."
