package in.jandwar.app.ai;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Drives the full conversational interview.
 * Asks questions via TTS → listens via ASR → extracts profile → repeats until done.
 * Works in all 6 languages. Degrades gracefully offline.
 */
public class ConversationController {

    public interface Listener {
        void onQuestion(String questionText);
        void onPartialTranscript(String partial);
        void onTranscriptResult(String full);
        void onFieldExtracted(ProfileFragment updated);
        void onDone(ProfileFragment finalProfile);
        void onStatus(String statusText);
        void onSpeaking(boolean isSpeaking);
    }

    private final Context context;
    private final String langCode;
    private final NluExtractor nlu;
    private final BhashiniGateway bhashini;
    private final Listener listener;
    private final ProfileFragment profile = new ProfileFragment();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile boolean stopped = false;
    private int currentStep = 0;

    // Conversation question arrays: [fieldKey, question, retry-question]
    private static final String[][] STEPS_EN = {
        {"edu",
         "Hello! I am your livelihood assistant. What is your education level? For example: 8th standard, 10th, 12th, or graduate?",
         "What is your highest education? Like 8th, 10th, 12th, or graduate?"},
        {"interests",
         "What work does your family do? Or what kind of work interests you? For example: farming, dairy, cattle, textile, construction, or tailoring?",
         "What work interests you most? Farming, dairy, cattle, textiles, or something else?"},
        {"preference",
         "Do you want to start your own business, or work for a salary at a company?",
         "Would you prefer self-employment or a job with salary?"},
        {"mobility",
         "How far can you travel for work or training? Only in your village, anywhere in your district, or even further?",
         "Can you travel locally, within the district, or to any state?"},
        {"district",
         "Which district in Tamil Nadu are you from?",
         "Which Tamil Nadu district do you live in?"}
    };

    private static final String[][] STEPS_TA = {
        {"edu",
         "வணக்கம்! நான் உங்கள் வாழ்வாதார உதவியாளர். நீங்கள் என்ன படிப்பு படித்தீர்கள்? 8வது, 10வது, 12வது, அல்லது பட்டப்படிப்பு?",
         "நீங்கள் என்ன வரை படித்தீர்கள்?"},
        {"interests",
         "உங்கள் குடும்பம் என்ன தொழில் செய்கிறது? விவசாயம், பால் வியாபாரம், கால்நடை, நெசவு, அல்லது தையல்?",
         "என்ன தொழிலில் ஆர்வம் இருக்கிறது?"},
        {"preference",
         "நீங்கள் சொந்தமாக தொழில் தொடங்க விரும்புகிறீர்களா, அல்லது வேலையில் ஊதியம் பெற விரும்புகிறீர்களா?",
         "சொந்த தொழிலா, இல்லை வேலையா?"},
        {"mobility",
         "பயிற்சிக்கு எவ்வளவு தூரம் பயணிக்க முடியும்? கிராமத்தில் மட்டுமா, மாவட்டத்தில் எங்கும், அல்லது தொலைவில்?",
         "கிராமத்தில் மட்டுமா, மாவட்டத்தில், அல்லது வெளியூரா?"},
        {"district",
         "நீங்கள் தமிழ்நாட்டில் எந்த மாவட்டத்தைச் சேர்ந்தவர்?",
         "உங்கள் மாவட்டம் என்ன?"}
    };

    private static final String[][] STEPS_HI = {
        {"edu",
         "नमस्ते! मैं आपका आजीविका सहायक हूँ। आपकी शिक्षा कितनी है? 8वीं, 10वीं, 12वीं, या ग्रेजुएट?",
         "आपने कितनी पढ़ाई की है?"},
        {"interests",
         "आपका परिवार क्या काम करता है? खेती, डेयरी, पशुपालन, कपड़ा, या दर्जीगिरी?",
         "किस काम में रुचि है?"},
        {"preference",
         "क्या आप अपना खुद का व्यवसाय शुरू करना चाहते हैं, या नौकरी में तनख्वाह पाना चाहते हैं?",
         "खुद का काम चाहिए, या नौकरी?"},
        {"mobility",
         "काम के लिए कितना दूर जा सकते हैं? केवल गाँव में, जिले में, या और दूर?",
         "गाँव में ही, जिले में, या और दूर?"},
        {"district",
         "आप तमिलनाडु के किस जिले से हैं?",
         "आपका जिला क्या है?"}
    };

    private static final String[][] STEPS_TE = {
        {"edu",
         "నమస్కారం! నేను మీ జీవనోపాధి సహాయకుడిని. మీ విద్యార్హత ఏమిటి? 8వ తరగతి, 10వ, 12వ, లేదా పట్టభద్రుడు?",
         "మీరు ఎంత వరకు చదివారు?"},
        {"interests",
         "మీ కుటుంబం ఏమి చేస్తుంది? వ్యవసాయం, పాల వ్యాపారం, పశుపోషణ, వస్త్రాలు, లేదా టైలరింగ్?",
         "మీకు ఏ పని ఆసక్తి?"},
        {"preference",
         "మీరు స్వంత వ్యాపారం ప్రారంభించాలనుకుంటున్నారా, లేదా జీతంతో ఉద్యోగం కావాలా?",
         "స్వయం ఉపాధి కావాలా, ఉద్యోగం కావాలా?"},
        {"mobility",
         "పని కోసం ఎంత దూరం వెళ్ళగలరు? గ్రామంలో మాత్రమే, జిల్లాలో, లేదా ఇంకా దూరంగా?",
         "స్థానికంగా, జిల్లాలో, లేదా రాష్ట్రంలో?"},
        {"district",
         "మీరు తమిళనాడులో ఏ జిల్లా నుండి?",
         "మీ జిల్లా పేరు ఏమిటి?"}
    };

    private static final String[][] STEPS_KN = {
        {"edu",
         "ನಮಸ್ಕಾರ! ನಾನು ನಿಮ್ಮ ಜೀವನೋಪಾಯ ಸಹಾಯಕ. ನಿಮ್ಮ ವಿದ್ಯಾರ್ಹತೆ ಏನು? 8ನೇ, 10ನೇ, 12ನೇ, ಅಥವಾ ಪದವಿ?",
         "ನೀವು ಎಷ್ಟು ವರೆಗೆ ಓದಿದ್ದೀರಿ?"},
        {"interests",
         "ನಿಮ್ಮ ಕುಟುಂಬ ಏನು ಮಾಡುತ್ತದೆ? ಕೃಷಿ, ಹಾಲಿನ ವ್ಯಾಪಾರ, ಜಾನುವಾರು, ಜವಳಿ, ಅಥವಾ ಹೊಲಿಗೆ?",
         "ಯಾವ ಕೆಲಸದಲ್ಲಿ ಆಸಕ್ತಿ ಇದೆ?"},
        {"preference",
         "ನೀವು ಸ್ವಂತ ವ್ಯವಹಾರ ಪ್ರಾರಂಭಿಸಲು ಬಯಸುತ್ತೀರಾ, ಅಥವಾ ವೇತನದ ಉದ್ಯೋಗ ಬೇಕಾ?",
         "ಸ್ವ-ಉದ್ಯೋಗ ಬೇಕಾ, ಉದ್ಯೋಗ ಬೇಕಾ?"},
        {"mobility",
         "ಕೆಲಸಕ್ಕೆ ಎಷ್ಟು ದೂರ ಹೋಗಬಲ್ಲೀರಿ? ಗ್ರಾಮದಲ್ಲೇ, ಜಿಲ್ಲೆಯಲ್ಲಿ, ಅಥವಾ ಇನ್ನೂ ದೂರ?",
         "ಸ್ಥಳೀಯ, ಜಿಲ್ಲೆ, ಅಥವಾ ರಾಜ್ಯ?"},
        {"district",
         "ನೀವು ತಮಿಳುನಾಡಿನ ಯಾವ ಜಿಲ್ಲೆಯಿಂದ?",
         "ನಿಮ್ಮ ಜಿಲ್ಲೆ ಹೆಸರೇನು?"}
    };

    private static final String[][] STEPS_ML = {
        {"edu",
         "നമസ്കാരം! ഞാൻ നിങ്ങളുടെ ഉപജീവന സഹായി. നിങ്ങളുടെ വിദ്യാഭ്യാസ യോഗ്യത എന്ത്? 8ാം ക്ലാസ്, 10ാം, 12ാം, അല്ലെങ്കിൽ ബിരുദം?",
         "നിങ്ങൾ എത്ര വരെ പഠിച്ചു?"},
        {"interests",
         "നിങ്ങളുടെ കുടുംബം എന്ത് ചെയ്യുന്നു? കൃഷി, പാൽ വ്യാപാരം, കന്നുകാലി, തുണി, അല്ലെങ്കിൽ തയ്യൽ?",
         "ഏത് ജോലിയിൽ താൽപ്പര്യം?"},
        {"preference",
         "നിങ്ങൾ സ്വന്തം ബിസിനസ് തുടങ്ങാൻ ആഗ്രഹിക്കുന്നോ, അതോ ശമ്പളത്തിൽ ജോലി വേണോ?",
         "സ്വ-തൊഴിൽ വേണോ, ജോലി വേണോ?"},
        {"mobility",
         "ജോലിക്ക് എത്ര ദൂരം പോകാൻ കഴിയും? ഗ്രാമത്തിൽ മാത്രം, ജില്ലയിൽ, അല്ലെങ്കിൽ ഇനിയും ദൂരം?",
         "പ്രാദേശികം, ജില്ല, അല്ലെങ്കിൽ സംസ്ഥാനം?"},
        {"district",
         "നിങ്ങൾ തമിഴ്നാട്ടിലെ ഏത് ജില്ലയിൽ നിന്നാണ്?",
         "നിങ്ങളുടെ ജില്ലയുടെ പേര് എന്ത്?"}
    };

    public ConversationController(Context context, String langCode,
                                   NluExtractor nlu, BhashiniGateway bhashini,
                                   Listener listener) {
        this.context = context.getApplicationContext(); // prevent Activity leaks
        this.langCode = langCode != null ? langCode : "en";
        this.nlu = nlu;
        this.bhashini = bhashini;
        this.listener = listener;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        stopped = false;
        currentStep = 0;
        // Initialise TTS on main thread BEFORE asking first question
        initTts(() -> askCurrentStep());
    }

    public void stop() {
        stopped = true;
        listening = false;
        destroySpeechRecognizer();
        stopAudioTrack();
        destroyTts();
    }

    private volatile boolean listening = false;

    // ── TTS initialisation (one-time, called before first question) ───────────

    private void initTts(Runnable onReady) {
        if (tts != null && ttsReady) {
            mainHandler.post(onReady);
            return;
        }
        // Must be called on main thread
        mainHandler.post(() -> {
            tts = new TextToSpeech(context, status -> {
                // onInit is called on main thread on most devices, but post to be safe
                mainHandler.post(() -> {
                    if (stopped) return;
                    ttsReady = (status == TextToSpeech.SUCCESS);
                    if (ttsReady) {
                        setTtsLanguage();
                    }
                    onReady.run();
                });
            });
        });
    }

    private void setTtsLanguage() {
        if (tts == null) return;
        Locale locale = speechLocale();
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if regional voice not installed
            tts.setLanguage(Locale.ENGLISH);
        }
    }

    // ── Step logic ────────────────────────────────────────────────────────────

    private void askCurrentStep() {
        if (stopped) return;
        if (currentStep >= getSteps().length) {
            listener.onDone(profile);
            return;
        }
        String question = getSteps()[currentStep][1];
        question = cleanOfflineText(question);
        listener.onQuestion(getSteps()[currentStep][1]);
        speakThenListen(question);
    }

    private void askDynamicStep(String dynamicQuestionNative) {
        if (stopped) return;
        if (currentStep >= getSteps().length) {
            listener.onDone(profile);
            return;
        }
        if (dynamicQuestionNative != null) {
            listener.onQuestion(dynamicQuestionNative);
            speakThenListen(dynamicQuestionNative);
        } else {
            askCurrentStep();
        }
    }

    private String cleanOfflineText(String t) {
        if (t == null) return "";
        t = t.replace("!", "").replace("?", "");
        if ("en".equals(langCode)) {
            t = t.replace("8th", "eighth").replace("8", "eight")
                 .replace("10th", "tenth").replace("10", "ten")
                 .replace("12th", "twelfth").replace("12", "twelve");
        }
        return t;
    }

    private void nextStep(String dynamicQuestionNative) {
        if (stopped) return;
        if (!profile.hasEdu()) currentStep = 0;
        else if (!profile.hasInterests()) currentStep = 1;
        else if (!profile.hasPref()) currentStep = 2;
        else if (!profile.hasMobility()) currentStep = 3;
        else if (!profile.hasDistrict()) currentStep = 4;
        else currentStep = 99; // Done

        if (currentStep == 99) {
            listener.onDone(profile);
        } else {
            askDynamicStep(dynamicQuestionNative);
        }
    }

    private void processAnswer(String answer) {
        if (stopped) return;
        listener.onStatus(getThinkingText());
        listener.onSpeaking(false);

        android.content.SharedPreferences prefs = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        boolean isOnline = isNetworkAvailable();

        nlu.extract(answer, langCode, profile, isOnline, new NluExtractor.Callback() {
            @Override
            public void onResult(ProfileFragment fragment) {
                if (stopped) return;
                profile.merge(fragment);
                listener.onFieldExtracted(profile);
                if (fragment.nextQuestion != null) {
                    nextStep(fragment.nextQuestion);
                } else {
                    nextStep(null);
                }
            }
            @Override
            public void onError(String reason) {
                if (stopped) return;
                nextStep(null);
            }
        });
    }

    // ── TTS speak ─────────────────────────────────────────────────────────────

    private void speakThenListen(String text) {
        if (stopped) return;
        listener.onSpeaking(true);

        if (bhashini != null && bhashini.isAvailable()) {
            bhashini.tts(text, langCode, new BhashiniGateway.TtsCallback() {
                @Override
                public void onAudio(byte[] audioBytes) {
                    if (stopped) return;
                    playAudio(audioBytes, () -> {
                        if (!stopped) {
                            listener.onSpeaking(false);
                            startListening();
                        }
                    });
                }
                @Override
                public void onError(String reason) {
                    // Bhashini TTS failed → use Android TTS
                    speakWithAndroidTts(text, () -> { if (!stopped) startListening(); });
                }
            });
        } else {
            speakWithAndroidTts(text, () -> { if (!stopped) startListening(); });
        }
    }

    /** Speak using Android TTS. MUST be called on main thread. */
    private void speakWithAndroidTts(String text, Runnable onDone) {
        if (stopped) return;
        if (!ttsReady || tts == null) {
            // TTS not available — skip speaking, go straight to listening
            listener.onSpeaking(false);
            mainHandler.post(onDone);
            return;
        }
        mainHandler.post(() -> {
            if (stopped) return;
            String utteranceId = "utt_" + currentStep + "_" + System.currentTimeMillis();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {
                    mainHandler.post(() -> listener.onSpeaking(true));
                }
                @Override public void onDone(String id) {
                    mainHandler.post(() -> {
                        listener.onSpeaking(false);
                        onDone.run();
                    });
                }
                @Override public void onError(String id) {
                    mainHandler.post(() -> {
                        listener.onSpeaking(false);
                        onDone.run();
                    });
                }
            });
            Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        });
    }

    // ── ASR listen ────────────────────────────────────────────────────────────

    public void resumeListening() {
        if (!stopped && !listening) {
            startListening();
        }
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void startListening() {
        if (stopped) return;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onStatus("Voice input not available on this device");
            return;
        }
        listening = true;
        listener.onStatus(getListeningText());

        mainHandler.post(() -> {
            if (stopped) return;
            // Destroy previous recognizer before creating a new one
            destroySpeechRecognizer();

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {
                    listener.onStatus(getListeningText());
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rms) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {
                    listener.onStatus(getThinkingText());
                }
                @Override public void onError(int error) {
                    if (stopped || !listening) return;
                    listening = false;
                    if (!isNetworkAvailable()) {
                        listener.onStatus("Offline voice failed. Please turn on Wi-Fi.");
                    } else {
                        listener.onStatus("Didn't catch that. Tap Orb to speak.");
                    }
                }
                @Override public void onResults(Bundle results) {
                    if (stopped) return;
                    listening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty() && !matches.get(0).trim().isEmpty()) {
                        String answer = matches.get(0);
                        listener.onTranscriptResult(answer);
                        processAnswer(answer);
                    } else {
                        // Empty result — prompt to tap
                        listener.onStatus("Didn't catch that. Tap Orb to speak.");
                    }
                }
                @Override public void onPartialResults(Bundle partial) {
                    ArrayList<String> matches = partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) listener.onPartialTranscript(matches.get(0));
                }
                @Override public void onEvent(int type, Bundle params) {}
            });

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale().toString());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
            
            if (!isNetworkAvailable()) {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
                }
            }

            speechRecognizer.startListening(intent);
        });
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }
    }

    private void destroyTts() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception ignored) {}
            tts = null;
            ttsReady = false;
        }
    }

    // ── Audio playback (Bhashini TTS bytes) ──────────────────────────────────

    private android.media.MediaPlayer currentPlayer;

    private void playAudio(byte[] audioBytes, Runnable onDone) {
        try {
            // Write to a temporary file since MediaPlayer needs a file descriptor or URI
            java.io.File tempFile = java.io.File.createTempFile("bhashini_tts", ".wav", context.getCacheDir());
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            fos.write(audioBytes);
            fos.close();

            currentPlayer = new android.media.MediaPlayer();
            currentPlayer.setDataSource(tempFile.getAbsolutePath());
            currentPlayer.setOnCompletionListener(mp -> {
                stopAudioTrack();
                mainHandler.post(onDone);
            });
            currentPlayer.setOnErrorListener((mp, what, extra) -> {
                stopAudioTrack();
                mainHandler.post(onDone);
                return true;
            });
            currentPlayer.prepare();
            currentPlayer.start();
        } catch (Exception e) {
            mainHandler.post(onDone);
        }
    }

    private void stopAudioTrack() {
        if (currentPlayer != null) {
            try {
                if (currentPlayer.isPlaying()) currentPlayer.stop();
                currentPlayer.release();
            } catch (Exception ignored) {}
            currentPlayer = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String[][] getSteps() {
        switch (langCode) {
            case "ta": return STEPS_TA;
            case "hi": return STEPS_HI;
            case "te": return STEPS_TE;
            case "kn": return STEPS_KN;
            case "ml": return STEPS_ML;
            default:   return STEPS_EN;
        }
    }

    private Locale speechLocale() {
        switch (langCode) {
            case "ta": return new Locale("ta", "IN");
            case "hi": return new Locale("hi", "IN");
            case "te": return new Locale("te", "IN");
            case "kn": return new Locale("kn", "IN");
            case "ml": return new Locale("ml", "IN");
            default:   return Locale.ENGLISH;
        }
    }

    private String getListeningText() {
        switch (langCode) {
            case "ta": return "கேட்கிறேன்…";
            case "hi": return "सुन रहा हूँ…";
            case "te": return "వింటున్నాను…";
            case "kn": return "ಆಲಿಸುತ್ತಿದ್ದೇನೆ…";
            case "ml": return "കേൾക്കുന്നു…";
            default:   return "Listening…";
        }
    }

    private String getThinkingText() {
        switch (langCode) {
            case "ta": return "யோசிக்கிறேன்…";
            case "hi": return "सोच रहा हूँ…";
            case "te": return "ఆలోచిస్తున్నాను…";
            case "kn": return "ಯೋಚಿಸುತ್ತಿದ್ದೇನೆ…";
            case "ml": return "ചിന്തിക്കുന്നു…";
            default:   return "Thinking…";
        }
    }
}
