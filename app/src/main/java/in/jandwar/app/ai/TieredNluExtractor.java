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
    public void extract(String text, String langCode, ProfileFragment currentProfile, boolean isOnline, Callback callback) {
        if (isOnline && groq.isConfigured()) {
            runGroq(text, langCode, currentProfile, callback);
        } else {
            // Offline → Tier 3 (deterministic)
            callback.onResult(deterministic.parse(text));
        }
    }

    private void runGroq(String text, String langCode, ProfileFragment currentProfile, Callback callback) {
        String srcLang = (langCode == null || langCode.isEmpty()) ? "ta" : langCode;
        groq.extract(text, srcLang, currentProfile, true, new Callback() {
            @Override
            public void onResult(ProfileFragment fragment) {
                // Validate against original text too
                ProfileFragment validated = deterministic.validate(fragment, text);
                // Keep nextQuestion which might have been lost in validation
                if (fragment.nextQuestion != null) validated.nextQuestion = fragment.nextQuestion;
                callback.onResult(validated);
            }
            @Override
            public void onError(String reason) {
                // Groq failed → fall back to Tier 3
                callback.onResult(deterministic.parse(text));
            }
        });
    }

}
