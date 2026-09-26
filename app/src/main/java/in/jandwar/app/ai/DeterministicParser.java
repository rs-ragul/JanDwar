package in.jandwar.app.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Tier 3: Deterministic keyword-based extractor.
 * Also used to VALIDATE and fill gaps in JSON from Tier 1/2.
 * Always works — no network, no model needed.
 */
public class DeterministicParser implements NluExtractor {

    private static final Set<String> INTEREST_KEYS = new HashSet<>(Arrays.asList(
            "dairy", "cattle", "goat", "poultry", "farming",
            "food", "machine", "textile", "construction", "tailor"
    ));

    @Override
    public void extract(String text, String langCode, ProfileFragment currentProfile, boolean isOnline, Callback callback) {
        callback.onResult(parse(text));
    }

    /** Parse raw text and return a ProfileFragment (fields may be null if not detected). */
    public ProfileFragment parse(String rawText) {
        ProfileFragment frag = new ProfileFragment();
        if (rawText == null || rawText.isEmpty()) return frag;
        String s = rawText.toLowerCase(Locale.ROOT).trim();

        // Education
        frag.edu = parseEdu(s);

        // Preference
        if (s.contains("self") || s.contains("own business") || s.contains("business") ||
                s.contains("sonta thozhil") || s.contains("swayam") || s.contains("khud") ||
                s.contains("udyog") || s.contains("swarozgar") || s.contains("entrepreneur") ||
                s.contains("farm") || s.contains("agriculture")) {
            frag.preference = "self_employment";
        } else if (s.contains("job") || s.contains("wage") || s.contains("salary") ||
                s.contains("employ") || s.contains("naukri") || s.contains("velai") ||
                s.contains("company") || s.contains("factory") || s.contains("work for")) {
            frag.preference = "wage_employment";
        }

        // Mobility
        if (s.contains("anywhere") || s.contains("state") || s.contains("india") ||
                s.contains("national") || s.contains("veliyoor") || s.contains("desh")) {
            frag.mobility = "state";
        } else if (s.contains("district") || s.contains("maavatam") || s.contains("jile") ||
                s.contains("zila") || s.contains("nearby") || s.contains("adutha")) {
            frag.mobility = "district";
        } else if (s.contains("local") || s.contains("village") || s.contains("ur") ||
                s.contains("gram") || s.contains("here") || s.contains("nearby only") ||
                s.contains("oorla")) {
            frag.mobility = "local";
        }

        // Interests (keyword scan)
        for (String key : INTEREST_KEYS) {
            if (s.contains(key)) frag.interests.add(key);
        }
        // Additional regional keyword maps
        if (s.contains("paal") || s.contains("milk") || s.contains("dudh")) frag.interests.add("dairy");
        if (s.contains("maadu") || s.contains("gai") || s.contains("cattle") || s.contains("cow")) frag.interests.add("cattle");
        if (s.contains("aadu") || s.contains("bakra") || s.contains("sheep") || s.contains("goat")) frag.interests.add("goat");
        if (s.contains("kozhi") || s.contains("murgi") || s.contains("chicken") || s.contains("poultry")) frag.interests.add("poultry");
        if (s.contains("vivasayam") || s.contains("kheti") || s.contains("krishi") || s.contains("farming")) frag.interests.add("farming");
        if (s.contains("stitching") || s.contains("tailoring") || s.contains("sewing") || s.contains("tholi")) frag.interests.add("tailor");
        if (s.contains("weaving") || s.contains("kapda") || s.contains("cloth") || s.contains("silk")) frag.interests.add("textile");
        if (s.contains("welding") || s.contains("electrician") || s.contains("mechanic") || s.contains("machine")) frag.interests.add("machine");

        // Remove duplicates
        Set<String> seen = new HashSet<>();
        for (int i = frag.interests.size() - 1; i >= 0; i--) {
            if (!seen.add(frag.interests.get(i))) frag.interests.remove(i);
        }

        return frag;
    }

    /** Validate and fill gaps in an LLM-produced ProfileFragment using our rules. */
    public ProfileFragment validate(ProfileFragment llmResult, String originalText) {
        if (llmResult == null) return parse(originalText);
        ProfileFragment deterministic = parse(originalText);

        // Fill any null fields the LLM missed
        if (!llmResult.hasEdu() && deterministic.hasEdu())           llmResult.edu = deterministic.edu;
        if (!llmResult.hasPref() && deterministic.hasPref())         llmResult.preference = deterministic.preference;
        if (!llmResult.hasMobility() && deterministic.hasMobility()) llmResult.mobility = deterministic.mobility;
        if (!llmResult.hasDistrict() && deterministic.hasDistrict()) llmResult.district = deterministic.district;
        if (!llmResult.hasInterests() && deterministic.hasInterests()) llmResult.interests = deterministic.interests;

        // Removed strict enum validation to allow AI to extract any valid value

        return llmResult;
    }

    /** Try to parse a JSON string from LLM output into a ProfileFragment. */
    public ProfileFragment fromJson(String jsonString) {
        ProfileFragment frag = new ProfileFragment();
        if (jsonString == null) return frag;
        // Extract first JSON object from the response (LLMs sometimes prefix/suffix text)
        int start = jsonString.indexOf('{');
        int end = jsonString.lastIndexOf('}');
        if (start < 0 || end <= start) return frag;
        try {
            JSONObject obj = new JSONObject(jsonString.substring(start, end + 1));
            if (!obj.isNull("edu"))        frag.edu = obj.optString("edu", null);
            if (!obj.isNull("preference")) frag.preference = obj.optString("preference", null);
            if (!obj.isNull("district"))   frag.district = obj.optString("district", null);
            if (!obj.isNull("mobility"))   frag.mobility = obj.optString("mobility", null);
            JSONArray arr = obj.optJSONArray("interests");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.optString(i, "").trim();
                    if (!item.isEmpty()) frag.interests.add(item);
                }
            }
        } catch (Exception ignored) {}
        return frag;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String parseEdu(String s) {
        if (s.contains("graduate") || s.contains("degree") || s.contains("college") ||
                s.contains("b.a") || s.contains("b.sc") || s.contains("bsc") ||
                s.contains("pattam") || s.contains("graduation") || s.contains("snaatak")) {
            return "graduate";
        }
        if (s.contains("12") || s.contains("plus two") || s.contains("+2") ||
                s.contains("higher secondary") || s.contains("hsc") ||
                s.contains("plus 2") || s.contains("barahvi") || s.contains("12th") ||
                s.contains("12aam") || s.contains("intermediate")) {
            return "class12";
        }
        if (s.contains("10") || s.contains("sslc") || s.contains("matriculation") ||
                s.contains("10th") || s.contains("10aam") || s.contains("dasvi") ||
                s.contains("secondary school") || s.contains("high school")) {
            return "class10";
        }
        if (s.contains("8") || s.contains("eighth") || s.contains("8th") ||
                s.contains("ettaam") || s.contains("aathvi")) {
            return "class8";
        }
        if (s.contains("5") || s.contains("fifth") || s.contains("5th") ||
                s.contains("ainthaam") || s.contains("paanchvi")) {
            return "class5";
        }
        if (s.contains("no school") || s.contains("illiterate") || s.contains("padikka") ||
                s.contains("anpadh") || s.contains("never") || s.contains("not studied") ||
                s.contains("padikkalai") || s.contains("padikavillai")) {
            return "none";
        }
        return null; // not detected
    }
}
