package in.jandwar.app.ai;

import android.content.Context;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Loads API keys from assets/config.json — never hardcoded in source. */
public final class AiConfig {

    public static String bhashiniUserId = "";
    public static String bhashiniInferenceKey = "";
    public static String bhashiniAppId = "";
    public static String groqApiKey = "";

    private static boolean loaded = false;

    public static void load(Context ctx) {
        if (loaded) return;
        try {
            InputStream is = ctx.getAssets().open("config.json");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            is.close();
            JSONObject obj = new JSONObject(os.toString("UTF-8"));
            bhashiniUserId      = obj.optString("bhashini_user_id", "");
            bhashiniInferenceKey = obj.optString("bhashini_inference_key", "");
            bhashiniAppId       = obj.optString("bhashini_app_id", "");
            groqApiKey          = obj.optString("groq_api_key", "");
            loaded = true;
        } catch (Exception ignored) { /* keys stay empty; app degrades gracefully */ }
    }

    public static boolean bhashiniEnabled() {
        return !bhashiniUserId.isEmpty() && !bhashiniInferenceKey.isEmpty();
    }

    public static boolean groqEnabled() {
        return !groqApiKey.isEmpty();
    }
}
