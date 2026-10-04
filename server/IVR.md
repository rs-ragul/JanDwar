# Getting a real phone call working

## How IVR actually works

You were right. Here it is in full:

```
  Your phone  ──dials──►  a phone number you rent
                          from a telephony company (Twilio)
                                    │
                    Twilio answers, then asks OUR server
                    "someone called, what do I say?"
                                    │
                                    ▼
                    POST https://your-server/ivr/voice
                                    │
                    our server replies with XML:
                    <Say>Press 1 for English, தமிழுக்கு 2</Say>
                                    │
                    Twilio SPEAKS that to the caller
                                    │
                    caller presses 2 or talks
                                    │
                    Twilio POSTs that back to our server
                                    │
                    server runs the interview, replies with
                    the next question as XML  ──► loop
```

**We never touch audio.** Twilio does the speaking and the listening. Our
server only ever sends back text wrapped in XML. That is the whole trick, and
it is already built and working — you can see the exact XML in the browser
console's IVR tab.

### Who speaks, in what voice

Two options, both already coded:

| | Who does the speech | When |
|---|---|---|
| **Default** | Twilio's Google voices — `Google.ta-IN-Standard-C` for Tamil, `hi-IN` for Hindi, etc. | Works immediately, nothing to configure |
| **Better** | Our server generates the audio with **your Bhashini account** and Twilio just plays the file | When you set the 3 Bhashini env vars |

Listening works the same way: Twilio's speech recogniser handles `ta-IN` and
posts us the text. If you'd rather use Bhashini for that too, the code path is
there.

---

## The one genuinely hard part

**You must rent a phone number, and in India that is the only step that is not
free or instant.** TRAI rules mean Indian voice numbers need business KYC —
Exotel, Knowlarity and the rest all require a registered company. That is not
happening for a student project this week.

So for the demo you use a **Twilio trial**:

- Free trial credit, no company needed
- You get **one** number (a US one — Twilio won't sell individuals an Indian voice number)
- Trial rule: it will only accept calls **from a number you've verified**, which
  will be your own mobile. Perfect for a demo, useless for the public — and
  that's fine, because you're demonstrating the system, not launching it.
- Calling a US number from your Indian SIM costs *your carrier's* international
  rate. Use a calling app or just accept ~₹10/min for a 3-minute demo.

Be upfront about this with the judges. "The number is a trial US number because
Indian DID numbers need company KYC; in production this is an Exotel number at
about ₹1,000/month" is a *strong* answer — it shows you know what real
deployment costs.

---

## Step by step

### 1 — Put the server on the public internet

Twilio has to be able to reach it. The preview URL in this chat will **not**
work; it's locked to your browser. Two choices:

**Easiest — Render (free, permanent URL):**

1. Push this repo to GitHub (you already have `rs-ragul/JanDwar`).
2. Go to render.com → sign in with GitHub → **New → Blueprint**.
3. Pick the repo. It reads `render.yaml` automatically. Click **Apply**.
4. Wait ~3 minutes. You get `https://jandwar.onrender.com`.
5. Open `https://jandwar.onrender.com/api/health` — if you see JSON, it's live.

> The free instance sleeps after 15 min idle and takes ~40 s to wake. Load the
> page once right before you demo.

**Or — your own laptop + a tunnel** (no GitHub, but the URL changes each run):

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Then in a second terminal:

```bash
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
chmod +x cloudflared
./cloudflared tunnel --url http://localhost:8000
```

It prints a public `https://....trycloudflare.com` URL. Use that below, and set
`JANDWAR_PUBLIC_URL` to it before starting the server.

### 2 — Twilio

1. twilio.com/try-twilio → sign up. Verify your mobile when asked. **That
   verification is also what lets you call in later**, so use the phone you'll
   demo with.
2. Console → **Phone Numbers → Buy a number** → country **United States** →
   tick **Voice** → buy. It's free on trial credit.
3. Click the number you bought. Scroll to **Voice Configuration**.
4. **A call comes in** → set to **Webhook**, method **HTTP POST**, URL:
   ```
   https://jandwar.onrender.com/ivr/voice
   ```
5. **Save.**

### 3 — Call it

Dial the number from your verified mobile. You should hear:

> "Welcome to Jan Dwar, the livelihood helper. For English press 1.
> தமிழுக்கு 2. हिंदी के लिए 3 …"

Press **2**. From there it's the full interview in Tamil, and every question
takes either your voice or a keypad digit.

### 4 — If something goes wrong

- **Call connects then drops instantly** → Twilio Console → **Monitor → Logs →
  Errors**. It shows the exact HTTP response it got from your server.
- **Silence on an Indic language** → the `voice` attribute and `language` must
  agree. That's handled in `app/channels/ivr.py` (`VOICE` map); override with
  e.g. `IVR_VOICE_TA=Google.ta-IN-Wavenet-C`.
- **"Application error"** → your server is asleep (Render free tier) or
  unreachable. Open `/api/health` in a browser first.
- **Nothing reaches your server** → the URL must be `https`, must be POST, and
  the tunnel must still be running in its terminal.

---

## What to test without a phone

You do not need Twilio to check the logic. This drives the real webhooks:

```bash
# the call arrives
curl -X POST localhost:8000/ivr/voice

# caller presses 2 (Tamil) — copy the sid= out of the reply
curl -X POST localhost:8000/ivr/lang -d "Digits=2&CallSid=TEST1"

# caller presses 4 (10th standard)
curl -X POST "localhost:8000/ivr/turn?sid=PASTE_SID" -d "Digits=4&CallSid=TEST1"

# caller speaks
curl -X POST "localhost:8000/ivr/turn?sid=PASTE_SID" \
     --data-urlencode "SpeechResult=எங்க குடும்பம் பால் பண்ணை" -d "CallSid=TEST1"
```

The browser console's **IVR call** tab does exactly this and shows you the XML.

---

## Keypad map

Every question accepts speech *or* a digit, and the digits are read out. This
is why the call works even with no speech recognition at all.

| Question | Keys |
|---|---|
| Language | 1 English · 2 தமிழ் · 3 हिंदी · 4 తెలుగు · 5 ಕನ್ನಡ · 6 മലയാളം |
| Education | 1 didn't study · 2 5th · 3 8th · 4 10th · 5 12th · 6 ITI · 7 degree |
| Own work or job | 1 own business · 2 job |
| How far you can travel | 1 village · 2 district · 3 state |
| Health problem | 1 no · 2 yes |

Family work, current work, interests and district are spoken answers — they're
free text, so there's nothing sensible to map to a digit.
