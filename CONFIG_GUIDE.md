# Bhashini API Keys - Where to Put What

Based on your screenshot from https://bhashini.gov.in portal:

## Your Screenshot Shows:
- **App Name:** `thozhil_thunai`
- **App ID:** `d0bed4a44788464b82693c250f831bf` (under the app name)
- **UDYAT KEY:** `07e29****************` (starts with 07e29)
- **INFERENCE:** `n44PH****************` (starts with n44PH)

## Correct Mapping to config.json:

```json
{
  "bhashini_user_id": "07e29...YOUR UDYAT KEY... (from screenshot)",
  "bhashini_inference_key": "n44PH...YOUR INFERENCE KEY... (from screenshot)",
  "bhashini_app_id": "d0bed4a44788464b82693c250f831bf (your App ID)",
  "groq_api_key": "gsk_8T... (Groq - you have correct)",
  "sarvam_api_key": "sk_d7m... (Sarvam - you have correct)"
}
```

### What each key does:

1. **bhashini_user_id = UDYAT KEY (07e29...)**
   - This is your ULCA User ID / Udyat Key
   - Used for Bhashini translation (Tamil/Hindi input → English for AI)
   - In portal it's labeled "UDYAT KEY"
   - Your current value is CORRECT ✓

2. **bhashini_inference_key = INFERENCE (n44PH...)**
   - This is the Dhruva Inference API Key
   - Used as `Authorization` header for Bhashini pipeline
   - This is the MOST IMPORTANT key - without it Bhashini won't work
   - Your current value is CORRECT ✓

3. **bhashini_app_id = App ID (d0bed4...)**
   - Your application identifier
   - Optional for our implementation, but good to keep
   - Your current value is CORRECT ✓

4. **groq_api_key = Groq**
   - For AI conversation (understanding user speech)
   - Your value is CORRECT ✓

5. **sarvam_api_key = Sarvam**
   - BEST for Tamil/Hindi/Telugu TTS (most natural voice)
   - If you have this, set TTS Engine to "sarvam" in app Settings
   - Your value is CORRECT ✓

## Your Current Config is CORRECT!

```json
{
  "bhashini_user_id": "07e********************",
  "bhashini_inference_key": "n44PH... (your inference)",
  "bhashini_app_id": "d0b... (your app id)",
  "groq_api_key": "gsk_8T... (correct)",
  "sarvam_api_key": "sk_d7m... (correct)"
}
```

All 5 keys are in right places. Don't change!

## Why Tamil TTS was "worst"?

Not because keys are wrong, but because:

1. **Bhashini TTS quality** is focused on translation, not natural speech - it can sound robotic
2. **Android TTS** for Tamil is better but still not perfect
3. **Sarvam TTS** is BEST for Tamil/Hindi - uses Meera voice, very natural

### Solution (v10 fix):

In app, go to **Settings → TTS Engine** and select:
- **"sarvam"** ⭐ Best for Tamil/Hindi/Te/Kn/Ml - Natural Meera voice
- **"android"** - Good offline fallback
- **"auto"** - Now uses Sarvam if available, else Android (avoids Bhashini TTS which you reported worst)

Bhashini will still be used for **translation** (Tamil speech → English for AI), just not for speaking.

## Testing Bhashini:

If you want to test if Bhashini keys work:

1. Set TTS Engine to "bhashini" in Settings
2. Speak in Tamil
3. Check logs: `adb logcat | grep Bhashini`
4. Should see "Bhashini TTS success"

If you see "HTTP 401" or "HTTP 403" - keys are wrong or expired
If you see "HTTP 200" and audio size - keys work, but quality is just not as good as Sarvam

## Recommendation:

Keep your current config.json exactly as is - it's correct!
For best experience:
- Use **Sarvam** for TTS (Settings → TTS Engine → sarvam)
- Bhashini will auto-use for translation in background
- Groq for AI conversation
- All 3 together = best experience
