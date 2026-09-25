package in.jandwar.app.ai;

/**
 * Tier 1 + 3 orchestrator.
 * Online: translate via Bhashini NMT → extract via Groq → validate via DeterministicParser.
 * Offline: extract via DeterministicParser directly.
 * (Tier 2 / Gemma on-device added later via settings download.)
 */
public class TieredNluExtractor implements NluExtractor {

    private final GroqExtractor groq;
    private final DeterministicParser deterministic;
    private final BhashiniGateway bhashini;

    public TieredNluExtractor(BhashiniGateway bhashiniGateway) {
        this.groq = new GroqExtractor();
        this.deterministic = new DeterministicParser();
        this.bhashini = bhashiniGateway;
    }

    @Override
    public void extract(String text, String langCode, Callback callback) {
        boolean online = isOnline();

        if (online && groq.isConfigured()) {
            // Try Tier 1: translate to English first (if not English), then run Groq
            String srcLang = (langCode == null || langCode.isEmpty()) ? "en" : langCode;
            if ("en".equals(srcLang) || bhashini == null || !bhashini.isAvailable()) {
                // No translation needed or Bhashini unavailable — run Groq on original
                runGroq(text, text, callback);
            } else {
                bhashini.translate(text, srcLang, new BhashiniGateway.TranslateCallback() {
                    @Override
                    public void onResult(String translated) {
                        runGroq(translated, text, callback);
                    }
                    @Override
                    public void onError(String reason) {
                        // Translation failed → run Groq on original text anyway
                        runGroq(text, text, callback);
                    }
                });
            }
        } else {
            // Offline → Tier 3 (deterministic)
            callback.onResult(deterministic.parse(text));
        }
    }

    private void runGroq(String englishText, String originalText, Callback callback) {
        groq.extract(englishText, "en", new Callback() {
            @Override
            public void onResult(ProfileFragment fragment) {
                // Validate against original text too
                ProfileFragment validated = deterministic.validate(fragment, originalText);
                callback.onResult(validated);
            }
            @Override
            public void onError(String reason) {
                // Groq failed → fall back to Tier 3
                callback.onResult(deterministic.parse(originalText));
            }
        });
    }

    private boolean isOnline() {
        // Simple check — will be replaced with real network check via Context if needed
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName("api.groq.com");
            return !addr.toString().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
