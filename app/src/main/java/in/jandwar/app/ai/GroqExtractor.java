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
    private static final String MODEL = "openai/gpt-oss-120b";
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GroqExtractor() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public boolean isConfigured() {
        return AiConfig.groqEnabled();
    }

    @Override
    public void extract(String text, String langCode, ProfileFragment currentProfile, boolean isOnline, NluExtractor.Callback callback) {
        if (!isConfigured()) {
            callback.onError("Groq not configured");
            return;
        }

        String prompt = buildPrompt(text, currentProfile, langCode);
        JSONObject body = new JSONObject();
        try {
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", prompt);
            messages.put(msg);
            body.put("model", MODEL);
            body.put("messages", messages);
            body.put("temperature", 0.7); // higher temperature for natural questions
            body.put("max_tokens", 250);
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
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    try {
                        JSONObject errJson = new JSONObject(responseBody);
                        String errMsg = errJson.getJSONObject("error").getString("message");
                        mainHandler.post(() -> callback.onError("API Error: " + errMsg));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("HTTP Error: " + response.code()));
                    }
                    return;
                }
                
                try {
                    JSONObject resp = new JSONObject(responseBody);
                    String content = resp
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    
                    // The content might have markdown json blocks, strip it if necessary
                    content = content.trim();
                    if (content.startsWith("```json")) {
                        content = content.substring(7).trim();
                    } else if (content.startsWith("```")) {
                        content = content.substring(3).trim();
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length()-3).trim();
                    }
                    
                    int start = content.indexOf('{');
                    int end = content.lastIndexOf('}');
                    if (start >= 0 && end > start) {
                        content = content.substring(start, end + 1);
                    }
                    
                    ProfileFragment frag = new ProfileFragment();
                    try {
                        JSONObject outJson = new JSONObject(content);
                        if (outJson.has("edu") && !outJson.isNull("edu")) frag.edu = outJson.getString("edu");
                        if (outJson.has("preference") && !outJson.isNull("preference")) frag.preference = outJson.getString("preference");
                        if (outJson.has("district") && !outJson.isNull("district")) frag.district = outJson.getString("district");
                        if (outJson.has("mobility") && !outJson.isNull("mobility")) frag.mobility = outJson.getString("mobility");
                        if (outJson.has("interests") && !outJson.isNull("interests")) {
                            JSONArray arr = outJson.getJSONArray("interests");
                            frag.interests = new java.util.ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                frag.interests.add(arr.getString(i));
                            }
                        }
                        if (outJson.has("next_question_native") && !outJson.isNull("next_question_native")) {
                            frag.nextQuestion = outJson.getString("next_question_native");
                        }
                    } catch (Exception ignored) {}

                    mainHandler.post(() -> callback.onResult(frag));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("Groq parse error: " + e.getMessage()));
                }
            }
        });
    }

    private String buildPrompt(String userText, ProfileFragment profile, String langCode) {
        String missing = "";
        if (profile != null) {
            if (!profile.hasEdu()) missing += "education, ";
            if (!profile.hasPref()) missing += "preference (self-employment vs wage), ";
            if (!profile.hasInterests()) missing += "work interests/skills, ";
            if (!profile.hasDistrict()) missing += "district in Tamil Nadu, ";
            if (!profile.hasMobility()) missing += "mobility (local vs district vs state), ";
        }
        
        return "You are a highly empathetic AI livelihood assistant conducting a conversational interview with a rural Indian beneficiary. " +
                "Extract structured profile data from their spoken answer AND generate a natural, empathetic follow-up question to ask them next. " +
                "They are missing the following information: " + missing + "\n\n" +
                "Extract ONLY these fields if mentioned in the answer (use null if not mentioned):\n" +
                "- edu: The user's education level. MUST be one of: [\"none\", \"class5\", \"class8\", \"class10\", \"class12\", \"graduate\"].\n" +
                "- preference: What they prefer to do. MUST be one of: [\"pref_self\", \"pref_wage\"].\n" +
                "- interests: Array of strings representing work interests. Valid strings: [\"dairy\", \"cattle\", \"goat\", \"poultry\", \"farming\", \"food\", \"machine\", \"textile\", \"construction\", \"tailor\"].\n" +
                "- district: The district in Tamil Nadu they are from (e.g., \"Madurai\", \"Chennai\").\n" +
                "- mobility: How far they can travel. MUST be one of: [\"local\", \"district\", \"state\"].\n" +
                "- next_question_native: A natural, conversational, and empathetic follow-up question (written directly in the user's regional language code '" + langCode + "') to ask for one of the missing pieces of information. This question will be spoken directly to the user, so it must be completely in the target regional language, NOT English. Make it a SHORT spoken utterance (1-2 simple sentences max, warm tone), never a paragraph.\n\n" +
                "Respond ONLY with valid JSON. No markdown. Example:\n" +
                "{\"edu\":\"class8\",\"preference\":null,\"interests\":[\"cattle\"],\"district\":null,\"mobility\":null, \"next_question_native\": \"உங்கள் கல்வி தகுதி என்ன?\"}\n\n" +
                "User's answer: \"" + userText + "\"";
    }
}
