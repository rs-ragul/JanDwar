package in.jandwar.app.ai;

/**
 * Extracts a structured ProfileFragment from a spoken (or translated) text.
 * Three implementations: GroqExtractor (Tier 1), GemmaExtractor (Tier 2),
 * DeterministicParser (Tier 3 / JSON validator).
 */
public interface NluExtractor {
    void extract(String text, String langCode, Callback callback);

    interface Callback {
        void onResult(ProfileFragment fragment);
        void onError(String reason);
    }
}
