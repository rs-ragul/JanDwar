"""
Port of ai/OnDeviceNlu.kt -- slot extraction for all six languages.

Deterministic, offline, instant. This is the floor the service always has:
when Ollama is not running, or the model returns nonsense, or the call has to
be answered in 300 ms, this is what understands the caller.

Kept deliberately faithful to the Kotlin, including detector precedence, so
the same sentence fills the same slots on every channel.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass, field

from . import data
from .lexicon import Lexicon

NO_CONSTRAINT = "None"

#: STATE sits immediately before DISTRICT: the catalogue spans five states, so
#: the district question has to be asked inside a known state rather than
#: against one flat list of 187 names.
SLOTS = ["EDUCATION", "FAMILY_OCCUPATION", "CURRENT_LIVELIHOOD", "INTERESTS",
         "PREFERENCE", "MOBILITY", "CONSTRAINTS", "STATE", "DISTRICT"]

#: English state name -> spoken forms across the six languages. Mirrors
#: ai/OnDeviceNlu.kt::STATE_FORMS.
STATE_FORMS = {
    "Tamil Nadu": ["tamil nadu", "tamilnadu", "tamil naadu", "tn", "tamil",
                   "தமிழ்நாடு", "தமிழ் நாடு", "तमिलनाडु", "तमिल नाडु",
                   "తమిళనాడు", "ತಮಿಳುನಾಡು", "തമിഴ്നാട്", "തമിഴ്‌നാട്"],
    "Kerala": ["kerala", "keralam", "kerela",
               "கேரளா", "கேரளம்", "केरल", "केरला",
               "కేరళ", "ಕೇರಳ", "കേരളം", "കേരള"],
    "Karnataka": ["karnataka", "karnatak", "karnataka state",
                  "கர்நாடகா", "கர்நாடகம்", "कर्नाटक", "कर्नाटका",
                  "కర్ణాటక", "ಕರ್ನಾಟಕ", "കർണാടക", "കർണ്ണാടക"],
    "Andhra Pradesh": ["andhra pradesh", "andhrapradesh", "andhra", "ap", "andra",
                       "ஆந்திரப் பிரதேசம்", "ஆந்திரா", "आंध्र प्रदेश", "आंध्रप्रदेश", "आंध्रा",
                       "ఆంధ్రప్రదేశ్", "ఆంధ్ర", "ಆಂಧ್ರಪ್ರದೇಶ", "ಆಂಧ್ರ",
                       "ആന്ധ്രാപ്രദേശ്", "ആന്ധ്ര"],
    "Uttar Pradesh": ["uttar pradesh", "uttarpradesh", "up", "u p", "uttra pradesh",
                      "utter pradesh", "உத்தரப் பிரதேசம்", "உத்திரப் பிரதேசம்",
                      "उत्तर प्रदेश", "उत्तरप्रदेश", "यूपी",
                      "ఉత్తరప్రదేశ్", "ಉತ್ತರ ಪ್ರದೇಶ", "ഉത്തർപ്രദേശ്", "ഉത്തര്‍പ്രദേശ്"],
}

#: Only honoured when the state question was the one just asked. "up to 10th"
#: is not Uttar Pradesh and "I speak Tamil" is not Tamil Nadu.
AMBIGUOUS_STATE_FORMS = {"tn", "ap", "up", "u p", "tamil"}

EDU_ORDER = [
    ("edu.none", "class5"),
    ("edu.iti_diploma", "iti_diploma"),
    ("edu.graduate", "graduate"),
    ("edu.class12", "class12"),
    ("edu.class10", "class10"),
    ("edu.class8", "class8"),
    ("edu.class5", "class5"),
]

INTEREST_KEYS = ["dairy", "cattle", "goat", "poultry", "farming",
                 "food", "machine", "textile", "construction", "tailor"]

OCCUPATION_ORDER = [
    ("occ.government", "Government service"),
    ("occ.driver", "Driving"),
    ("occ.fishing", "Fishing"),
    ("occ.coolie", "Daily wage labour"),
    ("occ.shop", "Small shop"),
    ("interest.dairy", "Dairy farming"),
    ("interest.cattle", "Cattle rearing"),
    ("interest.goat", "Goat rearing"),
    ("interest.poultry", "Poultry"),
    ("interest.tailor", "Tailoring"),
    ("interest.textile", "Weaving"),
    ("interest.construction", "Construction work"),
    ("interest.food", "Cooking / catering"),
    ("interest.machine", "Machine work / repair"),
    ("interest.farming", "Farming"),
]

NON_ANSWERS = {"yes", "no", "ok", "okay", "hmm", "nothing", "dont know", "don't know",
               "சரி", "இல்லை", "தெரியாது", "हाँ", "नहीं", "पता नहीं"}

DISTRICT_ALIASES = {
    "Ariyalur": ["அரியலூர்", "अरियलूर"],
    "Chengalpattu": ["செங்கல்பட்டு", "chengalpet", "चेंगलपट्टू"],
    "Chennai": ["சென்னை", "चेन्नई", "madras", "చెన్నై", "ಚೆನ್ನೈ", "ചെന്നൈ"],
    "Coimbatore": ["கோயம்புத்தூர்", "कोयंबटूर", "kovai", "கோவை", "కోయంబత్తూరు", "ಕೊಯಮತ್ತೂರು"],
    "Cuddalore": ["கடலூர்", "कुड्डालोर"],
    "Dharmapuri": ["தர்மபுரி", "धर्मपुरी"],
    "Dindigul": ["திண்டுக்கல்", "डिंडीगुल"],
    "Erode": ["ஈரோடு", "इरोड", "ఈరోడ్", "ಈರೋಡ್"],
    "Kallakurichi": ["கள்ளக்குறிச்சி", "कल्लाकुरिची"],
    "Kancheepuram": ["காஞ்சிபுரம்", "कांचीपुरम", "kanchipuram"],
    "Kanniyakumari": ["கன்னியாகுமரி", "कन्याकुमारी", "kanyakumari", "nagercoil"],
    "Karur": ["கரூர்", "करूर"],
    "Krishnagiri": ["கிருஷ்ணகிரி", "कृष्णागिरी"],
    "Madurai": ["மதுரை", "मदुरै", "మదురై", "ಮಧುರೈ", "മധുര"],
    "Mayiladuthurai": ["மயிலாடுதுறை", "मयिलादुथुरै"],
    "Nagapattinam": ["நாகப்பட்டினம்", "नागपट्टिनम"],
    "Namakkal": ["நாமக்கல்", "नामक्कल"],
    "Perambalur": ["பெரம்பலூர்", "पेरम्बलूर"],
    "Pudukkottai": ["புதுக்கோட்டை", "पुदुक्कोट्टई"],
    "Ramanathapuram": ["இராமநாதபுரம்", "रामनाथपुरम", "ramnad"],
    "Ranipet": ["இராணிப்பேட்டை", "रानीपेट"],
    "Salem": ["சேலம்", "सेलम", "సేలం", "ಸೇಲಂ"],
    "Sivaganga": ["சிவகங்கை", "शिवगंगा", "sivagangai"],
    "Tenkasi": ["தென்காசி", "तेनकासी"],
    "Thanjavur": ["தஞ்சாவூர்", "तंजावुर", "tanjore"],
    "The Nilgiris": ["நீலகிரி", "नीलगिरी", "nilgiris", "ooty", "உதகமண்டலம்", "udhagamandalam"],
    "Theni": ["தேனி", "थेनी"],
    "Thoothukudi": ["தூத்துக்குடி", "तूतुकुडी", "tuticorin"],
    "Tiruchirappalli": ["திருச்சிராப்பள்ளி", "तिरुचिरापल्ली", "trichy", "திருச்சி"],
    "Tirunelveli": ["திருநெல்வேலி", "तिरुनेलवेली", "nellai"],
    "Tirupathur": ["திருப்பத்தூர்", "तिरुपत्तूर", "tirupattur"],
    "Tiruppur": ["திருப்பூர்", "तिरुपुर", "tirupur"],
    "Tiruvallur": ["திருவள்ளூர்", "तिरुवल्लूर"],
    "Tiruvannamalai": ["திருவண்ணாமலை", "तिरुवन्नामलै"],
    "Tiruvarur": ["திருவாரூர்", "तिरुवारूर"],
    "Vellore": ["வேலூர்", "वेल्लोर"],
    "Viluppuram": ["விழுப்புரம்", "विलुप्पुरम", "villupuram"],
    "Virudhunagar": ["விருதுநகர்", "विरुधुनगर"],
    "Alappuzha": ["ആലപ്പുഴ", "alleppey"],
    "Ernakulam": ["എറണാകുളം", "kochi", "cochin", "കൊച്ചി"],
    "Idukki": ["ഇടുക്കി"],
    "Kannur": ["കണ്ണൂർ", "cannanore"],
    "Kasaragod": ["കാസർഗോഡ്", "kasargod", "kasaragode"],
    "Kollam": ["കൊല്ലം", "quilon"],
    "Kottayam": ["കോട്ടയം"],
    "Kozhikode": ["കോഴിക്കോട്", "calicut"],
    "Malappuram": ["മലപ്പുറം"],
    "Palakkad": ["പാലക്കാട്", "palghat"],
    "Pathanamthitta": ["പത്തനംതിട്ട"],
    "Thiruvananthapuram": ["തിരുവനന്തപുരം", "trivandrum"],
    "Thrissur": ["തൃശ്ശൂർ", "trichur", "തൃശൂർ"],
    "Wayanad": ["വയനാട്", "kalpetta"],
    "Bagalkot": ["ಬಾಗಲಕೋಟೆ", "bagalkote"],
    "Ballari": ["ಬಳ್ಳಾರಿ", "bellary"],
    "Belagavi": ["ಬೆಳಗಾವಿ", "belgaum"],
    "Bengaluru Rural": ["ಬೆಂಗಳೂರು ಗ್ರಾಮಾಂತರ", "bangalore rural"],
    "Bengaluru Urban": ["ಬೆಂಗಳೂರು ನಗರ", "ಬೆಂಗಳೂರು", "bangalore", "bengaluru"],
    "Bidar": ["ಬೀದರ್"],
    "Chamarajanagar": ["ಚಾಮರಾಜನಗರ"],
    "Chikkaballapur": ["ಚಿಕ್ಕಬಳ್ಳಾಪುರ", "chikballapur"],
    "Chikkamagaluru": ["ಚಿಕ್ಕಮಗಳೂರು", "chikmagalur"],
    "Chitradurga": ["ಚಿತ್ರದುರ್ಗ"],
    "Dakshina Kannada": ["ದಕ್ಷಿಣ ಕನ್ನಡ", "mangalore", "mangaluru", "ಮಂಗಳೂರು"],
    "Davanagere": ["ದಾವಣಗೆರೆ", "davangere"],
    "Dharwad": ["ಧಾರವಾಡ", "hubli", "ಹುಬ್ಬಳ್ಳಿ", "hubballi"],
    "Gadag": ["ಗದಗ"],
    "Hassan": ["ಹಾಸನ"],
    "Haveri": ["ಹಾವೇರಿ"],
    "Kalaburagi": ["ಕಲಬುರಗಿ", "gulbarga"],
    "Kodagu": ["ಕೊಡಗು", "coorg", "madikeri"],
    "Kolar": ["ಕೋಲಾರ"],
    "Koppal": ["ಕೊಪ್ಪಳ"],
    "Mandya": ["ಮಂಡ್ಯ"],
    "Mysuru": ["ಮೈಸೂರು", "mysore"],
    "Raichur": ["ರಾಯಚೂರು"],
    "Ramanagara": ["ರಾಮನಗರ"],
    "Shivamogga": ["ಶಿವಮೊಗ್ಗ", "shimoga"],
    "Tumakuru": ["ತುಮಕೂರು", "tumkur"],
    "Udupi": ["ಉಡುಪಿ"],
    "Uttara Kannada": ["ಉತ್ತರ ಕನ್ನಡ", "karwar", "ಕಾರವಾರ"],
    "Vijayanagara": ["ವಿಜಯನಗರ", "hosapete", "hospet"],
    "Vijayapura": ["ವಿಜಯಪುರ", "bijapur"],
    "Yadgir": ["ಯಾದಗಿರಿ", "yadgiri"],
    "Alluri Sitharama Raju": ["అల్లూరి సీతారామరాజు", "alluri", "paderu"],
    "Anakapalli": ["అనకాపల్లి", "anakapalle"],
    "Anantapur": ["అనంతపురం", "anantapuramu", "ananthapuram"],
    "Annamayya": ["అన్నమయ్య", "rayachoti"],
    "Bapatla": ["బాపట్ల"],
    "Chittoor": ["చిత్తూరు", "chittor"],
    "Dr. B.R. Ambedkar Konaseema": ["కోనసీమ", "konaseema", "amalapuram"],
    "East Godavari": ["తూర్పు గోదావరి", "rajahmundry", "rajamahendravaram"],
    "Eluru": ["ఏలూరు"],
    "Guntur": ["గుంటూరు"],
    "Kakinada": ["కాకినాడ"],
    "Krishna": ["కృష్ణా", "machilipatnam", "bandar"],
    "Kurnool": ["కర్నూలు", "kurnoolu"],
    "Madanapalle": ["మదనపల్లె", "madanapalli"],
    "Markapuram": ["మార్కాపురం", "markapur"],
    "NTR": ["ఎన్టీఆర్", "vijayawada", "విజయవాడ"],
    "Nandyal": ["నంద్యాల", "nandyala"],
    "Palnadu": ["పల్నాడు", "narasaraopet"],
    "Parvathipuram Manyam": ["పార్వతీపురం మన్యం", "parvathipuram"],
    "Polavaram": ["పోలవరం"],
    "Prakasam": ["ప్రకాశం", "ongole"],
    "Sri Potti Sriramulu Nellore": ["నెల్లూరు", "nellore"],
    "Sri Sathya Sai": ["శ్రీ సత్యసాయి", "puttaparthi", "sathya sai"],
    "Srikakulam": ["శ్రీకాకుళం"],
    "Tirupati": ["తిరుపతి"],
    "Visakhapatnam": ["విశాఖపట్నం", "vizag", "vishakhapatnam"],
    "Vizianagaram": ["విజయనగరం", "vijayanagaram"],
    "West Godavari": ["పశ్చిమ గోదావరి", "bhimavaram"],
    "YSR Kadapa": ["కడప", "kadapa", "cuddapah", "ysr"],
    "Agra": ["आगरा"],
    "Aligarh": ["अलीगढ़"],
    "Ambedkar Nagar": ["अम्बेडकर नगर", "akbarpur", "ambedkarnagar"],
    "Amethi": ["अमेठी"],
    "Amroha": ["अमरोहा"],
    "Auraiya": ["औरैया"],
    "Ayodhya": ["अयोध्या", "faizabad"],
    "Azamgarh": ["आजमगढ़"],
    "Baghpat": ["बागपत", "bagpat"],
    "Bahraich": ["बहराइच"],
    "Ballia": ["बलिया"],
    "Balrampur": ["बलरामपुर"],
    "Banda": ["बांदा"],
    "Barabanki": ["बाराबंकी"],
    "Bareilly": ["बरेली"],
    "Basti": ["बस्ती"],
    "Bhadohi": ["भदोही", "sant ravidas nagar"],
    "Bijnor": ["बिजनौर"],
    "Budaun": ["बदायूँ", "badaun"],
    "Bulandshahr": ["बुलंदशहर"],
    "Chandauli": ["चंदौली"],
    "Chitrakoot": ["चित्रकूट"],
    "Deoria": ["देवरिया"],
    "Etah": ["एटा"],
    "Etawah": ["इटावा"],
    "Farrukhabad": ["फर्रुखाबाद", "fatehgarh"],
    "Fatehpur": ["फतेहपुर"],
    "Firozabad": ["फिरोजाबाद"],
    "Gautam Buddha Nagar": ["गौतम बुद्ध नगर", "noida", "greater noida"],
    "Ghaziabad": ["गाजियाबाद"],
    "Ghazipur": ["गाजीपुर"],
    "Gonda": ["गोंडा"],
    "Gorakhpur": ["गोरखपुर"],
    "Hamirpur": ["हमीरपुर"],
    "Hapur": ["हापुड़"],
    "Hardoi": ["हरदोई"],
    "Hathras": ["हाथरस"],
    "Jalaun": ["जालौन", "orai"],
    "Jaunpur": ["जौनपुर"],
    "Jhansi": ["झांसी"],
    "Kannauj": ["कन्नौज"],
    "Kanpur Dehat": ["कानपुर देहात", "akbarpur maati"],
    "Kanpur Nagar": ["कानपुर", "kanpur"],
    "Kasganj": ["कासगंज"],
    "Kaushambi": ["कौशाम्बी", "manjhanpur"],
    "Kushinagar": ["कुशीनगर", "padrauna"],
    "Lakhimpur Kheri": ["लखीमपुर खीरी", "kheri", "lakhimpur"],
    "Lalitpur": ["ललितपुर"],
    "Lucknow": ["लखनऊ"],
    "Maharajganj": ["महराजगंज", "mahrajganj"],
    "Mahoba": ["महोबा"],
    "Mainpuri": ["मैनपुरी"],
    "Mathura": ["मथुरा"],
    "Mau": ["मऊ", "maunath bhanjan"],
    "Meerut": ["मेरठ"],
    "Mirzapur": ["मिर्जापुर"],
    "Moradabad": ["मुरादाबाद"],
    "Muzaffarnagar": ["मुजफ्फरनगर"],
    "Pilibhit": ["पीलीभीत"],
    "Pratapgarh": ["प्रतापगढ़"],
    "Prayagraj": ["प्रयागराज", "allahabad", "इलाहाबाद"],
    "Raebareli": ["रायबरेली", "rae bareli"],
    "Rampur": ["रामपुर"],
    "Saharanpur": ["सहारनपुर"],
    "Sambhal": ["संभल", "bhim nagar"],
    "Sant Kabir Nagar": ["संत कबीर नगर", "khalilabad"],
    "Shahjahanpur": ["शाहजहाँपुर"],
    "Shamli": ["शामली"],
    "Shravasti": ["श्रावस्ती", "bhinga"],
    "Siddharthnagar": ["सिद्धार्थनगर", "siddharth nagar", "navgarh"],
    "Sitapur": ["सीतापुर"],
    "Sonbhadra": ["सोनभद्र", "robertsganj"],
    "Sultanpur": ["सुल्तानपुर"],
    "Unnao": ["उन्नाव"],
    "Varanasi": ["वाराणसी", "banaras", "benares", "काशी", "kashi"],
}


@dataclass
class Fragment:
    """Mirror of ai/ProfileFragment.kt."""
    edu: str | None = None
    state: str | None = None
    district: str | None = None
    family_occupation: str | None = None
    current_livelihood: str | None = None
    interests: list[str] = field(default_factory=list)
    skills: list[str] = field(default_factory=list)
    preference: str | None = None
    mobility: str | None = None
    physical_constraints: str | None = None
    local_opportunity: str | None = None

    def is_filled(self, slot: str) -> bool:
        return {
            "EDUCATION": bool(self.edu),
            "FAMILY_OCCUPATION": bool(self.family_occupation),
            "CURRENT_LIVELIHOOD": bool(self.current_livelihood),
            "INTERESTS": bool(self.interests) or bool(self.skills),
            "PREFERENCE": bool(self.preference),
            "MOBILITY": bool(self.mobility),
            "CONSTRAINTS": bool(self.physical_constraints),
            "STATE": bool(self.state),
            "DISTRICT": bool(self.district),
        }[slot]

    def missing_slots(self) -> list[str]:
        return [s for s in SLOTS if not self.is_filled(s)]

    def filled_count(self) -> int:
        return sum(1 for s in SLOTS if self.is_filled(s))

    #: slot name -> the Fragment field it owns
    SLOT_FIELD = {
        "EDUCATION": "edu",
        "FAMILY_OCCUPATION": "family_occupation",
        "CURRENT_LIVELIHOOD": "current_livelihood",
        "PREFERENCE": "preference",
        "MOBILITY": "mobility",
        "CONSTRAINTS": "physical_constraints",
        "STATE": "state",
        "DISTRICT": "district",
    }

    def merge(self, other: "Fragment", answering: str | None = None) -> "Fragment":
        """
        Fold `other` in.

        Only the slot actually being answered may *overwrite* an existing
        value. Everything else may only fill a gap. Multi-slot extraction is
        worth having -- one sentence can fill three slots -- but without this
        rule an incidental mention later in the interview silently rewrites an
        earlier answer: "I am interested in dairy" turned a stated Class 10
        into Class 12 (via "inter") and replaced "coolie work" with "dairy
        farming".
        """
        owned = self.SLOT_FIELD.get(answering or "")
        for f in ("edu", "state", "district", "family_occupation", "current_livelihood",
                  "preference", "mobility", "physical_constraints", "local_opportunity"):
            v = getattr(other, f)
            if v and (not getattr(self, f) or f == owned):
                setattr(self, f, v)
        for i in other.interests:
            if i not in self.interests:
                self.interests.append(i)
        for s in other.skills:
            if s not in self.skills:
                self.skills.append(s)
        return self

    def to_dict(self) -> dict:
        return {
            "education": self.edu, "state": self.state, "district": self.district,
            "familyOccupation": self.family_occupation,
            "currentLivelihood": self.current_livelihood,
            "interests": self.interests, "skills": self.skills,
            "preference": self.preference, "mobility": self.mobility,
            "physicalConstraints": self.physical_constraints,
            "localOpportunity": self.local_opportunity,
        }


_PUNCT = re.compile(
    "[" + re.escape("".join(chr(i) for i in range(0x21, 0x7F)
                            if unicodedata.category(chr(i)).startswith("P")
                            and chr(i) != "+")) + "]")


def normalise(t: str) -> str:
    return re.sub(r"\s+", " ", _PUNCT.sub(" ", t.lower())).strip()


class Nlu:
    def __init__(self):
        self.lex = Lexicon(data.lexicon_forms())
        self._districts = self._build_district_index()
        self._state_of_district = self._build_state_of_district()

    # ── helpers ────────────────────────────────────────────────────────────
    def has(self, s, key):
        return self.lex.has(s, key)

    def has_exact(self, s, key):
        return self.lex.has_exact(s, key)

    def _build_district_index(self):
        out = []
        for d in data.districts().get("all", []):
            forms = [d.lower()] + [a.lower() for a in DISTRICT_ALIASES.get(d, [])]
            for f in forms:
                out.append((d, f))
        out.sort(key=lambda p: -len(p[1]))
        return out

    def _build_state_of_district(self):
        out = {}
        for st, v in (data.districts().get("by_state") or {}).items():
            for d in v.get("all", []):
                out[d.lower()] = st
        return out

    def districts_of_state(self, state):
        by = data.districts().get("by_state") or {}
        return (by.get(state) or {}).get("all", [])

    def state_of_district(self, district):
        return self._state_of_district.get((district or "").strip().lower())

    # ── detectors ──────────────────────────────────────────────────────────
    def detect_district(self, s, original, state=None):
        """
        `state` scopes the search. District names are not unique across India
        and the catalogue now spans five states, so an unscoped hit could send
        someone to a centre 2000 km away.
        """
        lo = original.lower()
        allowed = None
        if state:
            allowed = {d for d in self.districts_of_state(state)}
            if not allowed:
                allowed = None
        for name, form in self._districts:
            if allowed is not None and name not in allowed:
                continue
            if form in s or form in lo:
                return name
        return None

    def detect_state(self, s, original, allow_ambiguous=False):
        lo = original.lower()
        seq = [t for t in re.split(r"[^\w]+", s + " " + lo) if t]
        tokens = set(seq)
        # "u p" is spoken as two tokens but stored de-spaced, so make the
        # concatenation of each adjacent pair reachable too.
        tokens |= {seq[i] + seq[i + 1] for i in range(len(seq) - 1)}
        pairs = sorted(
            ((n, f) for n, forms in STATE_FORMS.items() for f in forms),
            key=lambda p: -len(p[1]),
        )
        for name, form in pairs:
            if form in AMBIGUOUS_STATE_FORMS:
                if allow_ambiguous and form.replace(" ", "") in tokens:
                    return name
            elif form in s or form in lo:
                return name
        return None

    # What a person has *completed* when they are still mid-course. Someone in
    # their second year of a degree has finished Class 12, not the degree.
    IN_PROGRESS_DOWNGRADE = {
        "graduate": "class12",
        "iti_diploma": "class10",
        "class12": "class10",
        "class10": "class8",
        "class8": "class5",
        "class5": "class5",
    }

    def is_in_progress(self, s) -> bool:
        """Still enrolled -- 'college 2nd year', 'studying', 'final year'."""
        return self.has(s, "marker.in_progress")

    def is_dropout(self, s) -> bool:
        """Left before finishing. Also completed only the level below."""
        return self.has(s, "marker.dropout")

    def detect_education(self, s):
        # Specificity, not list order: "pre university" must not be read as
        # "university". Falls back to EDU_ORDER precedence on a tie.
        cat = self.lex.best_match(s, [k for k, _ in EDU_ORDER])
        if cat:
            return dict(EDU_ORDER)[cat]
        return None

    def detect_education_completed(self, s):
        """
        The level actually *completed*.

        `detect_education` answers "what qualification was mentioned?".
        This answers "what have they finished?", which is the one the NSQF
        eligibility check needs. "college 2nd year" mentions a degree but has
        completed Class 12; "12th dropout" mentions Class 12 but completed
        Class 10. Reading either as the mentioned level overstates the
        qualification and offers courses the person cannot yet enrol in.
        """
        edu = self.detect_education(s)
        if not edu:
            return None
        if self.is_in_progress(s) or self.is_dropout(s):
            return self.IN_PROGRESS_DOWNGRADE.get(edu, edu)
        return edu

    def detect_education_loose(self, s):
        e = self.detect_education(s)
        if e:
            return e
        m = re.search(r"\b(\d{1,2})\b", s)
        if m:
            n = int(m.group(1))
            if n >= 13:
                return "graduate"
            if n in (11, 12):
                return "class12"
            if n in (9, 10):
                return "class10"
            if 6 <= n <= 8:
                return "class8"
            if 1 <= n <= 5:
                return "class5"
        if self.has(s, "marker.no"):
            return "class5"
        return None

    def detect_preference(self, s):
        if self.has(s, "pref.self_strong"):
            return "pref_self"
        if self.has(s, "pref.wage_strong"):
            return "pref_wage"
        if self.has(s, "pref.self_weak"):
            return "pref_self"
        if self.has(s, "pref.wage_weak"):
            return "pref_wage"
        return None

    def detect_preference_strict(self, s):
        if self.has_exact(s, "pref.self_strong"):
            return "pref_self"
        if self.has_exact(s, "pref.wage_strong"):
            return "pref_wage"
        return None

    def detect_preference_loose(self, s):
        p = self.detect_preference(s)
        if p:
            return p
        if any(w in s for w in ("first", "one", "1", "முதல்", "पहला")):
            return "pref_self"
        if any(w in s for w in ("second", "two", "2", "இரண்டு", "दूसरा")):
            return "pref_wage"
        return None

    # Longest matched form wins, exactly as for education.
    #
    # Precedence cannot express specificity, and here that was inverting the
    # answer. "வெளியூர் போக முடியாது" -- *cannot* go out of town -- contains
    # the bare word "வெளியூர்" (out-of-town), which is a mob.state form, so a
    # state-first scan recorded someone who cannot leave their village as
    # willing to travel anywhere in the state. Same for "बाहर नहीं जा सकता"
    # (contains "बाहर") and "cannot go far". The negated phrase is always the
    # longer match, so longest-wins reads the negation correctly.
    _MOB = [("mob.state", "state"), ("mob.district", "district"),
            ("mob.local", "local"), ("mob.anywhere", "state")]

    def detect_mobility(self, s):
        cat = self.lex.best_match(s, [k for k, _ in self._MOB])
        if cat:
            return dict(self._MOB)[cat]
        return None

    def detect_mobility_strict(self, s):
        if self.has_exact(s, "mob.state"):
            return "state"
        if self.has_exact(s, "mob.district"):
            return "district"
        if self.has_exact(s, "mob.local"):
            return "local"
        return None

    def detect_mobility_loose(self, s):
        m = self.detect_mobility(s)
        if m:
            return m
        if self.has(s, "marker.yes"):
            return "district"
        if self.has(s, "marker.no"):
            return "local"
        return None

    def detect_interests(self, s):
        return [k for k in INTEREST_KEYS if self.has(s, "interest." + k)]

    def mentions_family(self, s):
        return self.has(s, "marker.family")

    def detect_occupation(self, s):
        for key, label in OCCUPATION_ORDER:
            if self.has(s, key):
                return label
        return None

    def detect_student_status(self, s):
        if self.has(s, "marker.student"):
            return "Student"
        if self.has(s, "marker.unemployed"):
            return "Looking for work"
        return None

    def detect_constraints(self, s, original):
        return original.strip()[:120] if self.has(s, "marker.constraint") else None

    @staticmethod
    def free_text(original):
        t = original.strip()
        if len(t) < 2 or len(t) > 140:
            return None
        if t.lower() in NON_ANSWERS:
            return None
        return t[0].upper() + t[1:]

    # ── extraction ─────────────────────────────────────────────────────────
    def extract(self, raw: str, lang: str = "en") -> Fragment:
        frag = Fragment()
        text = (raw or "").strip()
        if not text:
            return frag
        s = normalise(text)

        frag.state = self.detect_state(s, text)
        frag.district = self.detect_district(s, text, frag.state)
        if not frag.state and frag.district:
            frag.state = self.state_of_district(frag.district)
        frag.edu = self.detect_education(s)
        # Strict: this utterance may be answering something else entirely.
        frag.preference = self.detect_preference_strict(s)
        frag.mobility = self.detect_mobility_strict(s)
        for i in self.detect_interests(s):
            if i not in frag.interests:
                frag.interests.append(i)
        frag.physical_constraints = self.detect_constraints(s, text)

        occ = self.detect_occupation(s)
        if occ:
            if self.mentions_family(s):
                frag.family_occupation = occ
            else:
                frag.current_livelihood = occ
        if not frag.current_livelihood:
            st = self.detect_student_status(s)
            if st:
                frag.current_livelihood = st
        return frag

    def extract_for_slot(self, raw: str, lang: str, slot: str,
                         state_hint: str | None = None) -> Fragment:
        frag = self.extract(raw, lang)
        text = (raw or "").strip()
        if not text:
            return frag
        s = normalise(text)

        if slot == "EDUCATION":
            v = self.detect_education_loose(s)
            if v:
                # Record what was actually *completed*: "college 2nd year"
                # mentions a degree but has finished Class 12, and reading it
                # as `graduate` put the person at the top education rank and
                # offered courses they cannot yet enrol in.
                if self.is_in_progress(s) or self.is_dropout(s):
                    v = self.IN_PROGRESS_DOWNGRADE.get(v, v)
                frag.edu = v
            # Still enrolled is also a fact about their livelihood, and it is
            # the answer to a question we would otherwise ask again.
            if self.is_in_progress(s) and not self.is_dropout(s) \
                    and not frag.current_livelihood:
                frag.current_livelihood = "Student"
        elif slot == "FAMILY_OCCUPATION":
            if not frag.family_occupation:
                frag.family_occupation = frag.current_livelihood or self.free_text(text)
                if frag.current_livelihood and self.mentions_family(s):
                    frag.current_livelihood = None
        elif slot == "CURRENT_LIVELIHOOD":
            if not frag.current_livelihood:
                frag.current_livelihood = self.free_text(text)
        elif slot == "INTERESTS":
            if not frag.interests:
                ft = self.free_text(text)
                if ft:
                    frag.skills.append(ft)
        elif slot == "PREFERENCE":
            v = self.detect_preference_loose(s)
            if v:
                frag.preference = v
        elif slot == "MOBILITY":
            v = self.detect_mobility_loose(s)
            if v:
                frag.mobility = v
        elif slot == "CONSTRAINTS":
            # "No" is a real answer here, not a failure to understand.
            if self.has_exact(s, "marker.constraint"):
                frag.physical_constraints = text[:120]
            elif self.has(s, "marker.no"):
                frag.physical_constraints = NO_CONSTRAINT
            elif self.has(s, "marker.yes"):
                frag.physical_constraints = text[:120]
            elif len(text) >= 2:
                frag.physical_constraints = text[:120]
        elif slot == "STATE":
            if not frag.state:
                frag.state = self.detect_state(s, text, allow_ambiguous=True)
        elif slot == "DISTRICT":
            # Read inside the state the caller already has, when it has one.
            if not frag.district and state_hint:
                frag.district = self.detect_district(s, text, state_hint)
        return frag
