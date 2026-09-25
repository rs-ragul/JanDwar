package in.jandwar.app.ai;

import android.os.Handler;
import android.os.Looper;
import okhttp3.Call;
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
 * Tier 1: Uses Groq (Llama 3.1 8B) to extract a structured ProfileFragment
 * from English text. Groq is free up to 14,400 requests/day.
 */
public class GroqExtractor implements NluExtractor {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final DeterministicParser validator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GroqExtractor() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        validator = new DeterministicParser();
    }

    public boolean isConfigured() {
        return AiConfig.groqEnabled();
    }

    @Override
    public void extract(String text, String langCode, NluExtractor.Callback callback) {
        if (!isConfigured()) {
            callback.onError("Groq not configured");
            return;
        }

        String prompt = buildPrompt(text);
        JSONObject body = new JSONObject();
        try {
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("temperature", 0.1);
            body.put("max_tokens", 150);
        } catch (Exception e) {
            callback.onError("JSON build error: " + e.getMessage());
            return;
        }

        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + AiConfig.groqApiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_TYPE))
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Groq network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject resp = new JSONObject(responseBody);
                    String content = resp
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    ProfileFragment frag = validator.fromJson(content);
                    ProfileFragment validated = validator.validate(frag, text);
                    mainHandler.post(() -> callback.onResult(validated));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Groq parse error: " + e.getMessage()));
                }
            }
        });
    }

    private String buildPrompt(String userText) {
        return "You are an AI assistant helping extract structured profile data from a rural Indian " +
                "user's spoken answer. Extract ONLY these fields if present (use null if not mentioned):\n" +
                "- edu: one of [none, class5, class8, class10, class12, graduate]\n" +
                "- preference: one of [self_employment, wage_employment]\n" +
                "- interests: array from [dairy, cattle, goat, poultry, farming, food, machine, textile, construction, tailor]\n" +
                "- district: district name in Tamil Nadu (null if not a TN district name)\n" +
                "- mobility: one of [local, district, state]\n\n" +
                "Respond ONLY with valid JSON. No explanation. No markdown. Example:\n" +
                "{\"edu\":\"class8\",\"preference\":null,\"interests\":[\"cattle\"],\"district\":null,\"mobility\":null}\n\n" +
                "User's answer: \"" + userText + "\"";
    }
}
