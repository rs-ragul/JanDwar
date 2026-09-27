package in.jandwar.app.ai;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SarvamGateway {
    private static final String URL = "https://api.sarvam.ai/text-to-speech";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey = AiConfig.sarvamApiKey;

    public interface Callback {
        void onSuccess(byte[] audioData);
        void onError(String error);
    }

    public SarvamGateway() {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public void synthesize(String text, String langCode, Callback callback) {
        String sarvamLang = mapLangCode(langCode);
        
        JSONObject json = new JSONObject();
        try {
            JSONObject inputs = new JSONObject();
            inputs.put("text", text);
            json.put("inputs", new JSONArray().put(inputs));
            json.put("target_language_code", sarvamLang);
            json.put("speaker", "meera");
            json.put("enable_preprocessing", true);
            json.put("model", "bulbul:v1");
        } catch (Exception e) {
            callback.onError("JSON error");
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(URL)
                .addHeader("api-subscription-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onError("Network error"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String respStr = response.body().string();
                        JSONObject respJson = new JSONObject(respStr);
                        String base64Audio = respJson.getJSONArray("audios").getString(0);
                        byte[] audioData = Base64.decode(base64Audio, Base64.DEFAULT);
                        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(audioData));
                    } catch (Exception e) {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onError("Parse error"));
                    }
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onError("API error: " + response.code()));
                }
            }
        });
    }

    private String mapLangCode(String lang) {
        switch (lang) {
            case "ta": return "ta-IN";
            case "hi": return "hi-IN";
            case "te": return "te-IN";
            case "kn": return "kn-IN";
            case "ml": return "ml-IN";
            case "en": return "en-IN";
            default: return "hi-IN";
        }
    }
}
