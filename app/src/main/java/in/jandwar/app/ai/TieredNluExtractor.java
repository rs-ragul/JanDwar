package in.jandwar.app.ai;

/**
 * Tier 1 + 3 orchestrator.
 * Online: translate via Bhashini NMT → extract via Groq → validate via DeterministicParser.
 * Offline: extract via DeterministicParser directly.
 * (Tier 2 / Gemma on-device added later via settings download.)
 */
public class TieredNluExtractor implements NluExtractor {

    private final GroqExtractor groq;
    private final BhashiniGateway bhashini;

    public TieredNluExtractor(BhashiniGateway bhashiniGateway) {
        this.groq = new GroqExtractor();
        this.bhashini = bhashiniGateway;
    }

    @Override
    public void extract(String text, String langCode, ProfileFragment currentProfile, boolean isOnline, Callback callback) {
        if (isOnline && groq.isConfigured()) {
            runGroq(text, langCode, currentProfile, callback);
        } else {
            // Offline - user requested NO deterministic parser. Only AI.
            callback.onError("AI requires internet connection. Please connect to Wi-Fi or use mobile data.");
        }
    }

    private void runGroq(String text, String langCode, ProfileFragment currentProfile, Callback callback) {
        String srcLang = (langCode == null || langCode.isEmpty()) ? "ta" : langCode;
        groq.extract(text, srcLang, currentProfile, true, new Callback() {
            @Override
            public void onResult(ProfileFragment fragment) {
                callback.onResult(fragment);
            }
            @Override
            public void onError(String reason) {
                callback.onError(reason);
            }
        });
    }

}
