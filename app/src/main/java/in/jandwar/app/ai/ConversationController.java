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
import android.os.Build;
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

    // Hardcoded arrays completely removed. 
    // Pure AI Conversation Flow.

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
            tts.setLanguage(Locale.ENGLISH);
            locale = Locale.ENGLISH;
        }
        
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String ttsEngine = prefs.getString("tts_engine", "auto");
        boolean forceOffline = "android_offline".equals(ttsEngine);

        try {
            android.speech.tts.Voice bestVoice = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v.getLocale().getLanguage().equals(locale.getLanguage())) {
                    if (forceOffline) {
                        if (!v.isNetworkConnectionRequired() && !v.getName().contains("network")) {
                            bestVoice = v;
                            break;
                        }
                    } else {
                        if (v.isNetworkConnectionRequired() || v.getName().contains("network")) {
                            bestVoice = v;
                            break; 
                        }
                    }
                }
            }
            if (bestVoice != null) {
                tts.setVoice(bestVoice);
            }
        } catch (Exception ignored) {}
    }

    // ── Step logic ────────────────────────────────────────────────────────────

    // ── Step logic ────────────────────────────────────────────────────────────

    private void askCurrentStep() {
        if (stopped) return;
        // Sending an empty answer triggers the AI to generate the first question based on missing data.
        processAnswer("");
    }

    private void askDynamicStep(String dynamicQuestionNative) {
        if (stopped) return;
        if (profile.isComplete()) {
            listener.onDone(profile);
            return;
        }
        if (dynamicQuestionNative != null) {
            listener.onQuestion(dynamicQuestionNative);
            speakThenListen(dynamicQuestionNative, true);
        } else {
            // If the AI failed to generate a question but there was no fatal error, ask them to continue
            String fallback = "en".equals(langCode) ? "Could you please elaborate?" : 
                              "hi".equals(langCode) ? "क्या आप विस्तार से बता सकते हैं?" : 
                              "ta".equals(langCode) ? "மேலும் விவரங்களைச் சொல்ல முடியுமா?" : 
                              "te".equals(langCode) ? "దయచేసి మరిన్ని వివరాలు చెప్పగలరా?" : "Could you please elaborate?";
            listener.onQuestion(fallback);
            speakThenListen(fallback, true);
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
        
        if (profile.isComplete()) {
            listener.onDone(profile);
        } else {
            askDynamicStep(dynamicQuestionNative);
        }
    }

    public void processAnswer(String answer) {
        if (stopped) return;
        listener.onStatus(getThinkingText());
        listener.onSpeaking(false);

        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
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
                listener.onStatus("AI Error: " + reason);
                
                // Speak a proper error apology instead of a dumb fallback question
                String errorApology = "en".equals(langCode) ? "Sorry, I am facing a connection issue." : 
                                      "hi".equals(langCode) ? "क्षमा करें, कनेक्शन में समस्या है।" : 
                                      "ta".equals(langCode) ? "மன்னிக்கவும், நெட்வொர்க் பிழை ஏற்பட்டுள்ளது." : 
                                      "te".equals(langCode) ? "క్షమించండి, నెట్‌వర్క్ సమస్య ఉంది." : "Connection error.";
                
                // Speak the error and do NOT start listening again immediately to prevent looping
                speakThenListen(errorApology, false);
            }
        });
    }

    // ── TTS speak ─────────────────────────────────────────────────────────────

    private String formatForTts(String text) {
        if (text == null) return "";
        // Strip markdown and special characters
        text = text.replace("*", "").replace("#", "").replace("`", "");
        text = text.replace("\n", " ");
        // Remove English characters if this is a regional language
        if (!"en".equals(langCode)) {
            text = text.replaceAll("[a-zA-Z]", "");
        }
        // Normalize multiple spaces
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    private void speakThenListen(String text, boolean listenAfter) {
        if (stopped) return;
        listener.onSpeaking(true);

        String cleanText = formatForTts(text);
        if (cleanText.isEmpty()) {
            if (listenAfter) startListening();
            return;
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String ttsEngine = prefs.getString("tts_engine", "auto");

        boolean useBhashini = false;
        boolean useSarvam = false;
        
        if ("bhashini".equals(ttsEngine)) {
            useBhashini = true;
        } else if ("sarvam".equals(ttsEngine)) {
            useSarvam = true;
        } else if ("auto".equals(ttsEngine)) {
            if (!"en".equals(langCode) && !"hi".equals(langCode)) {
                useBhashini = true; // Use Bhashini for ta/te/kn/ml
            }
        }

        Runnable onSpeechDone = () -> {
            if (!stopped && listenAfter) {
                startListening();
            }
        };

        if (useBhashini) {
            bhashini.tts(cleanText, langCode, new BhashiniGateway.TtsCallback() {
                @Override public void onAudio(byte[] audio) {
                    playAudio(audio, onSpeechDone);
                }
                @Override public void onError(String err) {
                    speakWithAndroidTts(cleanText, onSpeechDone);
                }
            });
        } else if (useSarvam) {
            new SarvamGateway().synthesize(cleanText, langCode, new SarvamGateway.Callback() {
                @Override public void onSuccess(byte[] audio) {
                    playAudio(audio, onSpeechDone);
                }
                @Override public void onError(String error) {
                    speakWithAndroidTts(cleanText, onSpeechDone);
                }
            });
        } else {
            // Android TTS (network or offline based on setTtsLanguage)
            speakWithAndroidTts(cleanText, onSpeechDone);
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
                    if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        listener.onStatus("No internet. Please tap to try again or use text input.");
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
            String bcp47Lang = speechLanguageTag();
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47Lang);
            intent.putExtra("android.speech.extra.LANGUAGE_PREFERENCE", bcp47Lang);
            intent.putExtra("android.speech.extra.ONLY_RETURN_LANGUAGE_PREFERENCE", true);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isNetworkAvailable()) {
                intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
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

    private String speechLanguageTag() {
        switch (langCode) {
            case "ta": return "ta-IN";
            case "hi": return "hi-IN";
            case "te": return "te-IN";
            case "kn": return "kn-IN";
            case "ml": return "ml-IN";
            default:   return "en-IN";
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
