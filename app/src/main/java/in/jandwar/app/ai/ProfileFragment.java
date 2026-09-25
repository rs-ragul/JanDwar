package in.jandwar.app.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured profile data extracted from one spoken answer.
 * null = field not found; will be asked again next turn.
 */
public class ProfileFragment {
    public String edu;              // none | class5 | class8 | class10 | class12 | graduate
    public String preference;       // self_employment | wage_employment
    public List<String> interests;  // dairy, cattle, goat, poultry, farming, food, machine, textile, construction, tailor
    public String district;         // district name (Tamil Nadu)
    public String mobility;         // local | district | state

    public ProfileFragment() {
        interests = new ArrayList<>();
    }

    public boolean hasEdu()       { return edu != null && !edu.isEmpty(); }
    public boolean hasPref()      { return preference != null && !preference.isEmpty(); }
    public boolean hasInterests() { return interests != null && !interests.isEmpty(); }
    public boolean hasDistrict()  { return district != null && !district.isEmpty(); }
    public boolean hasMobility()  { return mobility != null && !mobility.isEmpty(); }

    /** Merge another fragment's non-null fields into this one (accumulate). */
    public void merge(ProfileFragment other) {
        if (other == null) return;
        if (other.hasEdu())       edu = other.edu;
        if (other.hasPref())      preference = other.preference;
        if (other.hasDistrict())  district = other.district;
        if (other.hasMobility())  mobility = other.mobility;
        if (other.hasInterests()) {
            if (interests == null) interests = new ArrayList<>();
            for (String i : other.interests) {
                if (!interests.contains(i)) interests.add(i);
            }
        }
    }

    public boolean isComplete() {
        return hasEdu() && hasPref() && hasDistrict() && hasMobility() && hasInterests();
    }
}
