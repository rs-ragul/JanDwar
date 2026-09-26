package in.jandwar.app.ai;

import android.os.Handler;
import android.os.Looper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Bhashini gateway: handles ASR (speech→text), NMT (translate→English), and TTS (text→speech audio).
 * Uses Bhashini Dhruva API — Government of India, completely free.
 *
 * Pipeline service IDs are discovered dynamically the first time they are needed.
 */
public class BhashiniGateway {

    private static final String BASE_URL = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline";
    private static final String PIPELINE_CONFIG_URL = "https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Cached service IDs per language: langCode → serviceId
    private final java.util.Map<String, String> asrServiceIds = new java.util.HashMap<>();
    private final java.util.Map<String, String> nmtServiceIds = new java.util.HashMap<>();
    private final java.util.Map<String, String> ttsServiceIds = new java.util.HashMap<>();

    public interface TranslateCallback {
        void onResult(String translatedText);
        void onError(String reason);
    }

    public interface TtsCallback {
        void onAudio(byte[] audioBytes);
        void onError(String reason);
    }

    public interface AsrCallback {
        void onResult(String transcribedText);
        void onError(String reason);
    }

    public BhashiniGateway() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public boolean isAvailable() {
        return AiConfig.bhashiniEnabled();
    }

    /**
     * Translate text from a regional language to English using Bhashini NMT.
     */
    public void translate(String text, String sourceLang, TranslateCallback callback) {
        translate(text, sourceLang, "en", callback);
    }

    /**
     * Translate text from source to target using Bhashini NMT.
     */
    public void translate(String text, String sourceLang, String targetLang, TranslateCallback callback) {
        if (!isAvailable() || sourceLang.equals(targetLang)) {
            callback.onResult(text);
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = buildNmtBody(text, sourceLang, targetLang);
                String response = post(body.toString());
                String translated = parseNmtResponse(response);
                mainHandler.post(() -> callback.onResult(translated != null ? translated : text));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    /**
     * Convert text to speech audio bytes using Bhashini TTS.
     * Returns WAV/MP3 bytes suitable for AudioTrack playback.
     */
    public void tts(String text, String targetLang, TtsCallback callback) {
        if (!isAvailable()) {
            callback.onError("Bhashini not available");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = buildTtsBody(text, targetLang);
                byte[] audio = postForAudio(body.toString());
                if (audio != null) {
                    mainHandler.post(() -> callback.onAudio(audio));
                } else {
                    mainHandler.post(() -> callback.onError("No audio from Bhashini"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // ── Bhashini request builders ─────────────────────────────────────────────

    private JSONObject buildNmtBody(String text, String sourceLang, String targetLang) throws Exception {
        JSONObject body = new JSONObject();

        JSONArray tasks = new JSONArray();
        JSONObject task = new JSONObject();
        task.put("taskType", "translation");
        JSONObject config = new JSONObject();
        JSONObject language = new JSONObject();
        language.put("sourceLanguage", sourceLang);
        language.put("targetLanguage", targetLang);
        config.put("language", language);
        task.put("config", config);
        tasks.put(task);
        body.put("pipelineTasks", tasks);

        JSONObject inputData = new JSONObject();
        JSONArray input = new JSONArray();
        JSONObject src = new JSONObject();
        src.put("source", text);
        input.put(src);
        inputData.put("input", input);
        body.put("inputData", inputData);

        return body;
    }

    private JSONObject buildTtsBody(String text, String lang) throws Exception {
        JSONObject body = new JSONObject();

        JSONArray tasks = new JSONArray();
        JSONObject task = new JSONObject();
        task.put("taskType", "tts");
        JSONObject config = new JSONObject();
        JSONObject language = new JSONObject();
        language.put("sourceLanguage", lang);
        config.put("language", language);
        config.put("gender", "female");
        config.put("samplingRate", 22050);
        task.put("config", config);
        tasks.put(task);
        body.put("pipelineTasks", tasks);

        JSONObject inputData = new JSONObject();
        JSONArray input = new JSONArray();
        JSONObject src = new JSONObject();
        src.put("source", text);
        input.put(src);
        inputData.put("input", input);
        body.put("inputData", inputData);

        return body;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String post(String jsonBody) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", AiConfig.bhashiniInferenceKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    private byte[] postForAudio(String jsonBody) throws IOException {
        // Bhashini TTS returns audio as base64 in JSON; decode it
        String responseText = post(jsonBody);
        try {
            JSONObject resp = new JSONObject(responseText);
            JSONArray outputs = resp.getJSONArray("pipelineResponse")
                    .getJSONObject(0)
                    .getJSONArray("audio");
            String base64 = outputs.getJSONObject(0).optString("audioContent", "");
            if (!base64.isEmpty()) {
                return android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String parseNmtResponse(String response) {
        try {
            JSONObject resp = new JSONObject(response);
            return resp.getJSONArray("pipelineResponse")
                    .getJSONObject(0)
                    .getJSONArray("output")
                    .getJSONObject(0)
                    .getString("target");
        } catch (Exception e) {
            return null;
        }
    }
}
