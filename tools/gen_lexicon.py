# -*- coding: utf-8 -*-
"""
Generates app/src/main/assets/lexicon.json — the multilingual surface forms
the on-device NLU matches against.

Why a JSON asset instead of Kotlin constants:
  * field slang can be added without touching code or recompiling,
  * tools/check_nlu.py validates against the exact same data the app loads.

Every entry is a *surface form* that a speech recogniser plausibly returns.
That includes misspellings the ASR actually produces (e.g. Tamil "டிப்ளமோ"
for diploma), and romanised code-mixed speech, which Indian recognisers emit
constantly for mixed sentences.
"""
import json
import collections

L = collections.OrderedDict()


def k(key, **langs):
    """key -> flat, de-duplicated list of surface forms."""
    forms = []
    for _, v in langs.items():
        for w in v:
            w = w.strip().lower()
            if w and w not in forms:
                forms.append(w)
    L[key] = forms


# ───────────────────────── EDUCATION ─────────────────────────────────────
k("edu.none",
  en=["did not study", "didnt study", "did not go to school", "no school",
      "never went to school", "illiterate", "not educated", "no education",
      "cannot read", "can not read", "unschooled", "zero education",
      "i have not studied", "never studied"],
  ta=["படிக்கல", "படிக்கவில்லை", "படிச்சதில்ல", "படிக்கலை", "பள்ளி செல்லவில்லை",
      "ஸ்கூல் போகல", "ஸ்கூல் போகவில்லை", "எழுத படிக்க தெரியாது", "படிப்பு இல்லை",
      "padikkala", "padikkalai", "padikala", "padichathilla", "school pogala"],
  hi=["नहीं पढ़ा", "नहीं पढ़ी", "स्कूल नहीं", "पढ़ाई नहीं", "अनपढ़", "निरक्षर",
      "padha nahi", "nahi padha", "school nahi gaya"],
  te=["చదవలేదు", "బడికి వెళ్లలేదు", "చదువు లేదు", "నిరక్షరాస్యుడు",
      "chadavaledu", "badiki vellaledu"],
  kn=["ಓದಿಲ್ಲ", "ಶಾಲೆಗೆ ಹೋಗಿಲ್ಲ", "ವಿದ್ಯಾಭ್ಯಾಸ ಇಲ್ಲ", "ಅನಕ್ಷರಸ್ಥ",
      "odilla", "shalege hogilla"],
  ml=["പഠിച്ചിട്ടില്ല", "സ്കൂളിൽ പോയിട്ടില്ല", "വിദ്യാഭ്യാസം ഇല്ല", "നിരക്ഷരൻ",
      "padichittilla", "schoolil poyittilla"])

k("edu.class5",
  en=["5th", "5 th", "fifth", "class 5", "class five", "std 5", "standard 5",
      "upto 5", "up to 5", "primary school", "only primary"],
  ta=["ஐந்தாம்", "ஐந்தாவது", "அஞ்சாம்", "அஞ்சு", "ஐந்து", "ஐந்தாம் வகுப்பு",
      "அஞ்சாம் வகுப்பு", "அஞ்சாங்கிளாஸ்", "anju", "anjam", "ainthu", "ainthaam"],
  hi=["पांचवीं", "पाँचवीं", "पाँच", "पांच", "कक्षा 5", "paanchvi", "panchvi"],
  te=["ఐదవ", "ఐదు", "ఐదో తరగతి", "aidava", "aidu"],
  kn=["ಐದನೇ", "ಐದು", "ಐದನೇ ತರಗತಿ", "aidane", "aidu"],
  ml=["അഞ്ചാം", "അഞ്ച്", "അഞ്ചാം ക്ലാസ്", "anjaam", "anju"])

k("edu.class8",
  en=["8th", "8 th", "eighth", "class 8", "class eight", "std 8", "standard 8",
      "upto 8", "up to 8", "middle school"],
  ta=["எட்டாம்", "எட்டாவது", "எட்டு", "எட்டாம் வகுப்பு", "எட்டாங்கிளாஸ்",
      "எட்டாம் கிளாஸ்", "ettu", "ettam", "ettaam", "ettavathu"],
  hi=["आठवीं", "आठ", "8वीं", "कक्षा 8", "aathvi", "aatvi", "aath"],
  te=["ఎనిమిదవ", "ఎనిమిది", "ఎనిమిదో తరగతి", "enimidava", "enimidi"],
  kn=["ಎಂಟನೇ", "ಎಂಟು", "ಎಂಟನೇ ತರಗತಿ", "entane", "entu"],
  ml=["എട്ടാം", "എട്ട്", "എട്ടാം ക്ലാസ്", "ettaam", "ettu"])

k("edu.class10",
  en=["10th", "10 th", "tenth", "class 10", "class ten", "std 10", "standard 10",
      "sslc", "s s l c", "matric", "matriculation", "upto 10", "up to 10",
      "high school", "secondary school", "passed 10", "completed 10"],
  ta=["பத்தாம்", "பத்தாவது", "பத்து", "பத்தாம் வகுப்பு", "பத்தாங்கிளாஸ்",
      "பத்தாம் கிளாஸ்", "பத்தாம்பு", "எஸ்எஸ்எல்சி", "எஸ் எஸ் எல் சி",
      "பத்தாம் படித்தேன்", "patthu", "pathu", "pathaam", "pathavathu", "pathhu"],
  hi=["दसवीं", "दस", "10वीं", "कक्षा 10", "हाई स्कूल", "मैट्रिक",
      "dasvi", "dasvin", "das"],
  te=["పదవ", "పది", "పదో తరగతి", "పదవ తరగతి", "padava", "padi", "pattava"],
  kn=["ಹತ್ತನೇ", "ಹತ್ತು", "ಹತ್ತನೇ ತರಗತಿ", "hattane", "hattu"],
  ml=["പത്താം", "പത്ത്", "പത്താം ക്ലാസ്", "pathaam", "pathu"])

k("edu.class12",
  en=["12th", "12 th", "twelfth", "class 12", "class twelve", "std 12",
      "standard 12", "hsc", "h s c", "plus two", "plus 2", "+2", "puc",
      "higher secondary", "intermediate", "upto 12", "up to 12", "pre university"],
  ta=["பன்னிரண்டாம்", "பன்னிரண்டு", "பன்னிரெண்டு", "பன்னெண்டு", "பன்னிரண்டாவது",
      "பன்னிரண்டாம் வகுப்பு", "மேல்நிலை", "பிளஸ் டூ", "பிளஸ் 2", "எச்எஸ்சி",
      "panniradu", "panniendu", "plus two", "plustwo", "pannirendu"],
  hi=["बारहवीं", "बारह", "12वीं", "कक्षा 12", "इंटर", "इंटरमीडिएट",
      "barahvi", "barah", "inter"],
  te=["పన్నెండు", "పన్నెండవ", "ఇంటర్", "ఇంటర్మీడియట్", "pannendu", "inter"],
  kn=["ಹನ್ನೆರಡು", "ಹನ್ನೆರಡನೇ", "ಪಿಯುಸಿ", "hanneradu", "puc"],
  ml=["പന്ത്രണ്ട്", "പന്ത്രണ്ടാം", "പ്ലസ് ടു", "plus two", "panthrandu"])

k("edu.iti_diploma",
  en=["iti", "i t i", "i.t.i", "industrial training", "diploma", "diplamo",
      "diplomo", "polytechnic", "poly technic", "technical course",
      "trade certificate", "apprentice", "apprenticeship"],
  # "டிப்ளமோ" is what Google's Tamil recogniser actually returned in testing.
  ta=["டிப்ளோமா", "டிப்ளமோ", "டிப்புளோமா", "டிப்ளோமோ", "டிப்லோமா", "டிப்ளோமா படித்தேன்",
      "பாலிடெக்னிக்", "பாலிடெக்னிக்கு", "ஐடிஐ", "ஐ டி ஐ", "ஐ.டி.ஐ", "தொழிற்பயிற்சி",
      "diploma padichen", "diplamo", "iti mudichen"],
  hi=["डिप्लोमा", "डिप्लामो", "पॉलिटेक्निक", "आईटीआई", "आई टी आई", "औद्योगिक प्रशिक्षण",
      "diploma kiya", "iti kiya"],
  te=["డిప్లొమా", "డిప్లమో", "పాలిటెక్నిక్", "ఐటీఐ", "ఐ టి ఐ", "diploma chesanu"],
  kn=["ಡಿಪ್ಲೊಮಾ", "ಡಿಪ್ಲಮೊ", "ಪಾಲಿಟೆಕ್ನಿಕ್", "ಐಟಿಐ", "ಐ ಟಿ ಐ", "diploma madide"],
  ml=["ഡിപ്ലോമ", "ഡിപ്ലമോ", "പോളിടെക്നിക്", "ഐടിഐ", "ഐ ടി ഐ", "diploma cheythu"])

k("edu.graduate",
  en=["graduate", "graduation", "degree", "bachelor", "bachelors", "master",
      "masters", "b tech", "btech", "b.tech", "be degree", "b sc", "bsc",
      "b com", "bcom", "bca", "mca", "mba", "bba", "b a degree", "ba degree",
      "m sc", "msc", "engineering", "college degree", "university", "phd",
      "nursing degree", "pharmacy", "post graduate", "pg"],
  ta=["பட்டம்", "பட்டப்படிப்பு", "பட்டதாரி", "டிகிரி", "டிகிரி படித்தேன்",
      "கல்லூரி படித்தேன்", "பி.ஏ", "பி.எஸ்.சி", "பி.காம்", "இஞ்சினியரிங்",
      "பொறியியல்", "degree padichen", "degree mudichen", "college padichen"],
  hi=["डिग्री", "स्नातक", "बीए", "बीएससी", "बीकॉम", "इंजीनियरिंग", "कॉलेज",
      "स्नातकोत्तर", "degree kiya", "graduation kiya"],
  te=["డిగ్రీ", "పట్టభద్రుడు", "బీఏ", "బీఎస్సీ", "ఇంజనీరింగ్", "కళాశాల",
      "degree chesanu"],
  kn=["ಪದವಿ", "ಪದವೀಧರ", "ಬಿಎ", "ಬಿಎಸ್ಸಿ", "ಇಂಜಿನಿಯರಿಂಗ್", "ಕಾಲೇಜು",
      "degree madide"],
  ml=["ബിരുദം", "ബിരുദധാരി", "ബിഎ", "ബിഎസ്‌സി", "എൻജിനീയറിംഗ്", "കോളേജ്",
      "degree cheythu"])

# ───────────────────────── PREFERENCE ────────────────────────────────────
# Strong forms settle the slot on their own. "work for" is deliberately NOT
# here — it matches inside "work for myself" and caused a real misread.
k("pref.self_strong",
  en=["for myself", "by myself", "on my own", "of my own", "my own", "myself",
      "own business", "own shop", "own work", "own farm", "own unit",
      "self employ", "self-employ", "selfemploy", "self employment",
      "start my own", "start a shop", "start a business", "be my own boss",
      "entrepreneur", "freelance", "independent", "i want to start",
      "work independently", "run my own"],
  ta=["சொந்த", "சொந்தமா", "சொந்தமாக", "சொந்த தொழில்", "சொந்த கடை", "சொந்த வேலை",
      "சுயதொழில்", "சுய தொழில்", "எனக்கே", "நானே", "நானே செய்ய", "தனியா",
      "sontha", "sonthama", "sontha thozhil", "suya thozhil", "naane"],
  hi=["अपना काम", "अपना व्यवसाय", "अपना बिज़नेस", "अपनी दुकान", "खुद का",
      "खुद का काम", "स्वरोजगार", "अपने लिए", "अपना धंधा",
      "apna kaam", "khud ka", "apna business"],
  te=["సొంత", "నా సొంత", "సొంత వ్యాపారం", "సొంత దుకాణం", "స్వయం ఉపాధి",
      "నాకోసం", "sontha vyaparam", "sontham"],
  kn=["ಸ್ವಂತ", "ನನ್ನ ಸ್ವಂತ", "ಸ್ವಂತ ವ್ಯಾಪಾರ", "ಸ್ವಂತ ಅಂಗಡಿ", "ಸ್ವಯಂ ಉದ್ಯೋಗ",
      "swanta", "swanta vyapara"],
  ml=["സ്വന്തം", "എന്റെ സ്വന്തം", "സ്വന്തം ബിസിനസ്", "സ്വന്തം കട",
      "സ്വയം തൊഴിൽ", "swantham", "swayam thozhil"])

k("pref.wage_strong",
  en=["work for someone", "work for a company", "work for others",
      "work for them", "work under", "under someone", "for an employer",
      "for a company", "employer", "salary", "salaried", "wage", "wages",
      "placement", "company job", "factory job", "get a job", "want a job",
      "need a job", "monthly salary", "government job", "private job"],
  ta=["வேலைக்கு போக", "வேலைக்கு போகணும்", "கம்பெனி வேலை", "கம்பெனியில்",
      "நிறுவனத்தில்", "சம்பளம்", "மாத சம்பளம்", "ஒரு வேலை வேணும்",
      "velaikku poganum", "company velai", "sambalam"],
  hi=["नौकरी", "नौकरी चाहिए", "वेतन", "तनख्वाह", "कंपनी में", "कंपनी की नौकरी",
      "naukri", "naukri chahiye", "company mein"],
  te=["ఉద్యోగం", "ఉద్యోగం కావాలి", "జీతం", "కంపెనీలో", "కంపెనీ ఉద్యోగం",
      "udyogam", "jeetham"],
  kn=["ಕೆಲಸಕ್ಕೆ", "ಕೆಲಸ ಬೇಕು", "ಸಂಬಳ", "ಕಂಪನಿಯಲ್ಲಿ", "ಕಂಪನಿ ಕೆಲಸ",
      "kelasa beku", "sambala"],
  ml=["ജോലി വേണം", "ശമ്പളം", "കമ്പനിയിൽ", "കമ്പനി ജോലി",
      "joli venam", "shampalam"])

k("pref.self_weak",
  en=["business", "startup", "enterprise", "shop", "venture", "trade of my"],
  ta=["தொழில்", "வியாபாரம்", "கடை"],
  hi=["व्यवसाय", "धंधा", "दुकान"],
  te=["వ్యాపారం", "దుకాణం"],
  kn=["ವ್ಯಾಪಾರ", "ಅಂಗಡಿ"],
  ml=["ബിസിനസ്", "കട"])

k("pref.wage_weak",
  en=["job", "company", "factory", "employment", "work somewhere"],
  ta=["வேலை", "கம்பெனி", "தொழிற்சாலை"],
  hi=["काम", "कंपनी", "फैक्टरी"],
  te=["పని", "కంపెనీ", "ఫ్యాక్టరీ"],
  kn=["ಕೆಲಸ", "ಕಂಪನಿ", "ಕಾರ್ಖಾನೆ"],
  ml=["ജോലി", "കമ്പനി", "ഫാക്ടറി"])

# ───────────────────────── MOBILITY ──────────────────────────────────────
# Checked most-specific first: an explicit "state" or "district" word beats
# the generic "anywhere", which used to make "anywhere in my district"
# resolve to state-wide travel.
k("mob.state",
  en=["state", "whole state", "across the state", "anywhere in the state",
      "tamil nadu", "tamilnadu", "tamil naadu", "other district",
      "another district", "outside district", "outside the district",
      "far away", "anywhere far", "any district", "all over"],
  ta=["மாநிலம்", "மாநிலம் முழுவதும்", "மாநிலத்தில்", "மாநிலம் எங்கும்",
      "தமிழ்நாடு", "தமிழ்நாடு முழுவதும்", "தமிழ்நாட்டில்", "வெளியூர்",
      "வெளி மாவட்டம்", "எங்க வேணா", "எங்கு வேண்டுமானாலும்", "தூரம் போகலாம்",
      "maanilam", "tamilnadu", "veliyoor", "enga venalum"],
  hi=["राज्य", "पूरे राज्य", "राज्य भर", "तमिलनाडु", "दूसरे जिले", "बाहर",
      "कहीं दूर", "rajya", "poore rajya"],
  te=["రాష్ట్రం", "రాష్ట్రం అంతటా", "తమిళనాడు", "వేరే జిల్లా", "దూరంగా",
      "rashtram"],
  kn=["ರಾಜ್ಯ", "ರಾಜ್ಯದಾದ್ಯಂತ", "ತಮಿಳುನಾಡು", "ಬೇರೆ ಜಿಲ್ಲೆ", "ದೂರ",
      "rajya"],
  ml=["സംസ്ഥാനം", "സംസ്ഥാനത്ത്", "തമിഴ്നാട്", "വേറെ ജില്ല", "ദൂരെ",
      "samsthanam"])

k("mob.district",
  en=["district", "my district", "in my district", "within the district",
      "whole district", "anywhere in my district", "same district",
      "nearby town", "taluk", "taluka"],
  ta=["மாவட்டம்", "மாவட்ட", "மாவட்டத்தில்", "மாவட்டம் முழுவதும்",
      "எங்க மாவட்டம்", "என் மாவட்டம்", "வட்டம்", "தாலுகா",
      "maavattam", "mavattam", "maavatta"],
  hi=["जिला", "जिले", "जिले में", "पूरे जिले", "अपने जिले",
      "jila", "jile mein"],
  te=["జిల్లా", "జిల్లాలో", "మా జిల్లా", "jilla"],
  kn=["ಜಿಲ್ಲೆ", "ಜಿಲ್ಲೆಯಲ್ಲಿ", "ನಮ್ಮ ಜಿಲ್ಲೆ", "jille"],
  ml=["ജില്ല", "ജില്ലയിൽ", "ഞങ്ങളുടെ ജില്ല", "jilla"])

k("mob.local",
  en=["village", "my village", "only my village", "nearby", "near by",
      "near my home", "close by", "local", "locally", "walking distance",
      "cannot travel", "cant travel", "can not travel", "unable to travel",
      "only here", "stay here", "not go far", "same place"],
  ta=["ஊர்", "ஊரில்", "ஊருக்குள்ள", "என் ஊர்", "எங்க ஊர்", "கிராமம்",
      "பக்கத்துல", "பக்கத்தில்", "அருகில்", "அருகாமையில்",
      "வெளியே போக முடியாது", "தூரம் போக முடியாது", "இங்கேயே",
      "oor", "ooril", "pakkathula", "engaloor"],
  hi=["गाँव", "गांव", "मेरा गाँव", "पास", "पास में", "नज़दीक", "यहीं",
      "दूर नहीं जा", "नहीं जा सकता", "gaon", "paas"],
  te=["ఊరు", "మా ఊరు", "గ్రామం", "దగ్గర", "దగ్గరలో", "ఇక్కడే",
      "వెళ్లలేను", "ooru", "daggara"],
  kn=["ಊರು", "ನಮ್ಮ ಊರು", "ಗ್ರಾಮ", "ಹತ್ತಿರ", "ಹತ್ತಿರದಲ್ಲಿ", "ಇಲ್ಲಿಯೇ",
      "ooru", "hattira"],
  ml=["ഗ്രാമം", "എന്റെ ഗ്രാമം", "അടുത്ത്", "അടുത്തുള്ള", "ഇവിടെ തന്നെ",
      "gramam", "aduthu"])

# Generic "anywhere" with no state/district word attached → widest option.
k("mob.anywhere",
  en=["anywhere", "any place", "anyplace", "wherever", "any where", "no problem travel"],
  ta=["எங்கும்", "எங்கேயும்", "எங்க வேணும்னாலும்", "எங்கும் போகலாம்"],
  hi=["कहीं भी", "कहीं पर भी", "kahin bhi"],
  te=["ఎక్కడైనా", "ఎక్కడికైనా", "ekkadaina"],
  kn=["ಎಲ್ಲಿಯಾದರೂ", "ಎಲ್ಲಿಗಾದರೂ", "elliyadaru"],
  ml=["എവിടെയും", "എവിടെയെങ്കിലും", "evideyum"])

# ───────────────────────── INTERESTS ─────────────────────────────────────
k("interest.dairy",
  en=["dairy", "milk", "milch", "milk business", "milk dairy"],
  ta=["பால்", "பால் பண்ணை", "பால் தொழில்", "பாலாடை", "paal", "paal pannai"],
  hi=["डेयरी", "दूध", "दूध का काम", "doodh", "dairy ka kaam"],
  te=["పాలు", "పాల వ్యాపారం", "డెయిరీ", "paalu"],
  kn=["ಹಾಲು", "ಹೈನುಗಾರಿಕೆ", "ಡೈರಿ", "haalu"],
  ml=["പാൽ", "ക്ഷീര", "ഡെയറി", "paal"])

k("interest.cattle",
  en=["cattle", "cow", "cows", "buffalo", "livestock", "cattle rearing"],
  ta=["மாடு", "மாடுகள்", "கால்நடை", "எருமை", "பசு", "maadu", "kaalnadai"],
  hi=["गाय", "पशु", "भैंस", "मवेशी", "gaay", "pashu"],
  te=["ఆవు", "పశువులు", "గేదె", "aavu", "pashuvulu"],
  kn=["ಹಸು", "ಜಾನುವಾರು", "ಎಮ್ಮೆ", "hasu"],
  ml=["പശു", "കന്നുകാലി", "എരുമ", "pashu"])

k("interest.goat",
  en=["goat", "goats", "sheep", "goat rearing", "goat farming"],
  ta=["ஆடு", "ஆடுகள்", "செம்மறி", "வெள்ளாடு", "aadu", "aadugal"],
  hi=["बकरी", "भेड़", "बकरा", "bakri"],
  te=["మేక", "గొర్రె", "meka"],
  kn=["ಮೇಕೆ", "ಕುರಿ", "meke"],
  ml=["ആട്", "ചെമ്മരിയാട്", "aadu"])

k("interest.poultry",
  en=["poultry", "chicken", "hen", "broiler", "egg", "poultry farm"],
  ta=["கோழி", "கோழி பண்ணை", "முட்டை", "kozhi", "kozhi pannai"],
  hi=["मुर्गी", "मुर्गी पालन", "अंडा", "murgi"],
  te=["కోడి", "కోళ్ల పెంపకం", "గుడ్డు", "kodi"],
  kn=["ಕೋಳಿ", "ಕೋಳಿ ಸಾಕಣೆ", "ಮೊಟ್ಟೆ", "koli"],
  ml=["കോഴി", "കോഴി വളർത്തൽ", "മുട്ട", "kozhi"])

k("interest.farming",
  en=["farm", "farming", "agri", "agriculture", "crop", "cultivate",
      "cultivation", "field work", "paddy", "harvest"],
  ta=["விவசாய", "விவசாயம்", "பயிர்", "நெல்", "வயல்", "தோட்டம்", "பண்ணை",
      "vivasayam", "payir", "vayal"],
  hi=["खेती", "किसानी", "फसल", "खेत", "kheti", "fasal"],
  te=["వ్యవసాయ", "వ్యవసాయం", "పంట", "పొలం", "vyavasayam"],
  kn=["ಕೃಷಿ", "ಬೆಳೆ", "ಹೊಲ", "krushi"],
  ml=["കൃഷി", "വിള", "പാടം", "krishi"])

k("interest.food",
  en=["food", "food processing", "bakery", "baker", "cooking", "cook",
      "snack", "snacks", "catering", "pickle", "sweets", "tiffin"],
  ta=["உணவு", "உணவு பதப்படுத்துதல்", "பேக்கரி", "சமையல்", "தின்பண்டம்",
      "ஊறுகாய்", "unavu", "samayal", "bakery"],
  hi=["खाद्य", "बेकरी", "खाना", "खाना बनाना", "अचार", "नाश्ता",
      "khana", "bakery"],
  te=["ఆహార", "బేకరీ", "వంట", "ఊరగాయ", "aahara", "vanta"],
  kn=["ಆಹಾರ", "ಬೇಕರಿ", "ಅಡುಗೆ", "ಉಪ್ಪಿನಕಾಯಿ", "aduge"],
  ml=["ഭക്ഷ്യ", "ബേക്കറി", "പാചകം", "അച്ചാർ", "pachakam"])

k("interest.machine",
  en=["machine", "machinery", "mechanic", "mechanical", "repair", "repairing",
      "electric", "electrical", "electronic", "electronics", "motor",
      "technician", "welding", "welder", "fitter", "turner", "lathe",
      "two wheeler", "bike repair", "ac repair", "mobile repair", "wiring"],
  ta=["இயந்திர", "இயந்திரம்", "மெக்கானிக்", "பழுது", "பழுதுபார்ப்பு", "மின்",
      "மின்சாரம்", "எலக்ட்ரிக்", "வெல்டிங்", "மோட்டார்", "ரிப்பேர்",
      "iyanthiram", "mechanic", "repair", "welding"],
  hi=["मशीन", "मरम्मत", "मैकेनिक", "बिजली", "इलेक्ट्रिक", "वेल्डिंग", "मोटर",
      "machine", "repair"],
  te=["యంత్ర", "మరమ్మతు", "మెకానిక్", "విద్యుత్", "వెల్డింగ్", "మోటార్"],
  kn=["ಯಂತ್ರ", "ದುರಸ್ತಿ", "ಮೆಕ್ಯಾನಿಕ್", "ವಿದ್ಯುತ್", "ವೆಲ್ಡಿಂಗ್"],
  ml=["യന്ത്ര", "അറ്റകുറ്റപ്പണി", "മെക്കാനിക്", "വൈദ്യുതി", "വെൽഡിംഗ്"])

k("interest.textile",
  en=["textile", "weaving", "weave", "loom", "handloom", "powerloom",
      "spinning", "yarn", "cloth", "fabric"],
  ta=["நெசவு", "தறி", "கைத்தறி", "ஜவுளி", "நூல்", "துணி",
      "nesavu", "thari", "javuli"],
  hi=["बुनाई", "करघा", "कपड़ा", "हथकरघा", "धागा", "bunai", "kapda"],
  te=["నేత", "మగ్గం", "వస్త్ర", "దారం", "netha"],
  kn=["ನೇಯ್ಗೆ", "ಮಗ್ಗ", "ಬಟ್ಟೆ", "neyge"],
  ml=["നെയ്ത്ത്", "തറി", "തുണി", "neythu"])

k("interest.construction",
  en=["construction", "mason", "masonry", "building", "builder", "plumbing",
      "plumber", "carpenter", "carpentry", "painting", "painter", "bar bending",
      "tiles", "civil work", "site work"],
  ta=["கட்டுமான", "கட்டுமானம்", "கொத்தனார்", "கட்டிட", "பிளம்பர்", "தச்சு",
      "தச்சர்", "பெயிண்டிங்", "மேஸ்திரி",
      "kattumanam", "kothanar", "mesthiri"],
  hi=["निर्माण", "मिस्त्री", "राजमिस्त्री", "भवन", "प्लंबर", "बढ़ई", "पेंटिंग",
      "nirman", "mistri"],
  te=["నిర్మాణ", "మేస్త్రీ", "భవన", "ప్లంబర్", "వడ్రంగి", "nirmana"],
  kn=["ನಿರ್ಮಾಣ", "ಮೇಸ್ತ್ರಿ", "ಕಟ್ಟಡ", "ಪ್ಲಂಬರ್", "ಬಡಗಿ", "nirmana"],
  ml=["നിർമാണ", "മേസ്തിരി", "കെട്ടിടം", "പ്ലംബർ", "ആശാരി", "nirmanam"])

k("interest.tailor",
  en=["tailor", "tailoring", "stitch", "stitching", "sewing", "garment",
      "garments", "embroidery", "boutique", "dress making"],
  ta=["தையல்", "தையற்கலை", "சிலாய்", "ஆடை", "எம்பிராய்டரி", "டெய்லர்",
      "thaiyal", "tailor"],
  hi=["सिलाई", "दर्जी", "कपड़े सिलना", "कढ़ाई", "silai", "darzi"],
  te=["కుట్టు", "టైలర్", "దుస్తులు", "kuttu"],
  kn=["ಹೊಲಿಗೆ", "ಟೈಲರ್", "ಬಟ್ಟೆ ಹೊಲಿ", "holige"],
  ml=["തയ്യൽ", "ടെയ്ലർ", "വസ്ത്രം", "thayyal"])

# ───────────────────────── OCCUPATION LABELS ─────────────────────────────
# Reuses the interest forms where they overlap, plus livelihood-only trades.
k("occ.coolie",
  en=["coolie", "daily wage", "daily wages", "labour", "labourer", "laborer",
      "construction labour", "nrega", "mgnrega", "hundred days work"],
  ta=["கூலி", "கூலி வேலை", "நாள் கூலி", "தொழிலாளி", "நூறு நாள் வேலை",
      "kooli", "kooli velai"],
  hi=["मजदूरी", "दिहाड़ी", "मजदूर", "mazdoori"],
  te=["కూలీ", "రోజు కూలీ", "koolie"],
  kn=["ಕೂಲಿ", "ದಿನಗೂಲಿ", "kooli"],
  ml=["കൂലി", "ദിവസക്കൂലി", "kooli"])

k("occ.driver",
  en=["driver", "driving", "auto", "auto rickshaw", "lorry", "truck", "taxi",
      "cab", "tempo"],
  ta=["ஓட்டுநர்", "டிரைவர்", "ஆட்டோ", "லாரி", "வண்டி ஓட்ட",
      "driver", "auto"],
  hi=["ड्राइवर", "ऑटो", "गाड़ी चलाना", "ट्रक", "driver"],
  te=["డ్రైవర్", "ఆటో", "లారీ", "driver"],
  kn=["ಚಾಲಕ", "ಆಟೋ", "ಲಾರಿ", "driver"],
  ml=["ഡ്രൈവർ", "ഓട്ടോ", "ലോറി", "driver"])

k("occ.shop",
  en=["shop", "shopkeeper", "petty shop", "grocery", "kirana", "small store",
      "vendor", "selling", "street vendor"],
  ta=["கடை", "சிறு கடை", "மளிகை", "வியாபாரம்", "விற்பனை",
      "kadai", "malligai"],
  hi=["दुकान", "किराना", "बिक्री", "dukan"],
  te=["దుకాణం", "కిరాణా", "అమ్మకం", "dukanam"],
  kn=["ಅಂಗಡಿ", "ಕಿರಾಣಿ", "ಮಾರಾಟ", "angadi"],
  ml=["കട", "പലചരക്ക്", "വിൽപ്പന", "kada"])

k("occ.fishing",
  en=["fishing", "fisherman", "fish", "fishery"],
  ta=["மீன்", "மீன் பிடி", "மீனவர்", "meen"],
  hi=["मछली", "मछुआरा", "machli"],
  te=["చేప", "మత్స్యకార", "chepa"],
  kn=["ಮೀನು", "ಮೀನುಗಾರ", "meenu"],
  ml=["മീൻ", "മത്സ്യ", "meen"])

k("occ.government",
  en=["government", "govt", "government service", "government job",
      "government office", "panchayat office", "public sector"],
  ta=["அரசு", "அரசு வேலை", "அரசாங்க", "கவர்மெண்ட்", "பஞ்சாயத்து",
      "arasu", "government"],
  hi=["सरकारी", "सरकारी नौकरी", "सरकार", "sarkari"],
  te=["ప్రభుత్వ", "ప్రభుత్వ ఉద్యోగం", "prabhutva"],
  kn=["ಸರ್ಕಾರಿ", "ಸರ್ಕಾರ", "sarkari"],
  ml=["സർക്കാർ", "സർക്കാർ ജോലി", "sarkar"])

# ───────────────────────── MARKERS ───────────────────────────────────────
k("marker.family",
  en=["family", "father", "mother", "parents", "traditional", "ancestral",
      "my dad", "my mom", "forefathers", "generations", "at home we"],
  ta=["குடும்ப", "குடும்பம்", "அப்பா", "அம்மா", "தந்தை", "தாய்", "பெற்றோர்",
      "பாரம்பரிய", "முன்னோர்", "appa", "amma", "kudumba"],
  hi=["परिवार", "पिता", "माता", "माँ", "पापा", "पारंपरिक", "पुश्तैनी",
      "parivar", "pita"],
  te=["కుటుంబ", "నాన్న", "అమ్మ", "తల్లిదండ్రులు", "సాంప్రదాయ", "kutumba"],
  kn=["ಕುಟುಂಬ", "ಅಪ್ಪ", "ಅಮ್ಮ", "ಪೋಷಕರು", "ಸಾಂಪ್ರದಾಯಿಕ", "kutumba"],
  ml=["കുടുംബ", "അച്ഛൻ", "അമ്മ", "മാതാപിതാക്കൾ", "പരമ്പരാഗത", "kudumba"])

k("marker.student",
  en=["student", "studying", "still studying", "pursuing", "in college",
      "in school", "final year"],
  ta=["படிக்கிறேன்", "படிக்கிறாள்", "மாணவ", "மாணவர்", "கல்லூரியில்",
      "padikkiren", "manavan"],
  hi=["पढ़ रहा", "पढ़ रही", "पढ़ाई", "छात्र", "विद्यार्थी", "padh raha"],
  te=["చదువుతున్న", "విద్యార్థి", "chaduvutunna"],
  kn=["ಓದುತ್ತಿದ್ದೇನೆ", "ವಿದ್ಯಾರ್ಥಿ", "oduttiddene"],
  ml=["പഠിക്കുന്നു", "വിദ്യാർത്ഥി", "padikkunnu"])

k("marker.unemployed",
  en=["no work", "without work", "unemployed", "jobless", "looking for work",
      "searching for job", "sitting idle", "nothing right now", "no job"],
  ta=["வேலை இல்லை", "வேலை இல்ல", "வேலை தேடு", "வேலையில்லை", "சும்மா இருக்கேன்",
      "velai illai", "velai thedu"],
  hi=["काम नहीं", "बेरोजगार", "नौकरी नहीं", "खाली बैठा", "kaam nahi"],
  te=["పని లేదు", "ఉద్యోగం లేదు", "నిరుద్యోగి", "pani ledu"],
  kn=["ಕೆಲಸ ಇಲ್ಲ", "ನಿರುದ್ಯೋಗಿ", "kelasa illa"],
  ml=["ജോലി ഇല്ല", "തൊഴിൽ ഇല്ല", "joli illa"])

k("marker.constraint",
  en=["cannot lift", "cant lift", "can not lift", "heavy work not possible",
      "back pain", "knee pain", "disability", "disabled", "handicap",
      "differently abled", "injury", "injured", "surgery", "cannot stand",
      "cannot walk", "weak health", "asthma", "blind", "deaf"],
  ta=["தூக்க முடியாது", "கனமான வேலை", "முதுகு வலி", "கால் வலி", "வலி",
      "ஊனம்", "மாற்றுத்திறனாளி", "காயம்", "நிற்க முடியாது", "நடக்க முடியாது",
      "thooka mudiyathu", "vali"],
  hi=["नहीं उठा", "भारी काम नहीं", "कमर दर्द", "दर्द", "विकलांग", "चोट",
      "खड़ा नहीं", "nahi utha"],
  te=["ఎత్తలేను", "బరువు పని", "నొప్పి", "వికలాంగ", "గాయం", "ettalenu"],
  kn=["ಎತ್ತಲಾಗದು", "ಭಾರ ಕೆಲಸ", "ನೋವು", "ಅಂಗವಿಕಲ", "ಗಾಯ", "ettalagadu"],
  ml=["ഉയർത്താൻ കഴിയില്ല", "ഭാരം", "വേദന", "വികലാംഗ", "പരിക്ക്", "vedana"])

k("marker.yes",
  en=["yes", "yeah", "yep", "ok", "okay", "sure", "correct", "right", "fine",
      "can", "i can", "no problem"],
  ta=["ஆம்", "ஆமாம்", "சரி", "சரிங்க", "முடியும்", "ஓகே",
      "aama", "sari", "mudiyum"],
  hi=["हाँ", "हां", "जी", "ठीक", "ठीक है", "बिल्कुल", "haan", "theek"],
  te=["అవును", "సరే", "ఔను", "avunu", "sare"],
  kn=["ಹೌದು", "ಸರಿ", "houdu", "sari"],
  ml=["അതെ", "ശരി", "athe", "sari"])

k("marker.no",
  en=["no", "nope", "not", "cannot", "can not", "cant", "never", "not possible"],
  ta=["இல்லை", "இல்ல", "முடியாது", "வேண்டாம்",
      "illai", "illa", "mudiyathu"],
  hi=["नहीं", "ना", "नही", "नहीं हो", "nahi", "nahin"],
  te=["కాదు", "లేదు", "వద్దు", "kadu", "ledu"],
  kn=["ಇಲ್ಲ", "ಬೇಡ", "illa", "beda"],
  ml=["ഇല്ല", "വേണ്ട", "illa", "venda"])


out = {
    "_readme": [
        "Surface forms the on-device NLU matches against.",
        "Matching is substring-first, then token-level fuzzy (edit distance),",
        "so close ASR misspellings still resolve.",
        "Add regional slang by appending to the relevant list - no code change",
        "is needed. Keep everything lowercase.",
        "Regenerate with work/gen_lexicon.py; validate with tools/check_nlu.py."
    ],
    "forms": L,
}

path = "/home/user/Thozhil-Thunai/app/src/main/assets/lexicon.json"
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)

total = sum(len(v) for v in L.values())
print("categories:", len(L))
print("surface forms:", total)
dupes = [key for key, v in L.items() if len(v) != len(set(v))]
print("duplicate forms within a category:", dupes if dupes else "none")
empty = [key for key, v in L.items() if not v]
print("empty categories:", empty if empty else "none")
