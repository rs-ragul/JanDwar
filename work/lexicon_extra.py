# -*- coding: utf-8 -*-
"""
Expansion pack for lexicon.json.

`gen_lexicon.py` established the skeleton. This file is where the *spoken*
language lives: the words people actually say in a village, the way a speech
recogniser actually spells them, and the code-mixed romanisation that Indian
ASR emits constantly for mixed sentences.

Three kinds of form matter here, and the third is the one usually missed:

  1. **The dictionary word** — ஐந்தாம் வகுப்பு, पाँचवीं कक्षा.
  2. **The spoken contraction** — அஞ்சாங்கிளாஸ், पाँचवी, ಐದನೇ ಕ್ಲಾಸ್.
  3. **The romanisation** — "anjaam class", "paanchvi", "aidane class".
     A Tamil speaker saying one English word mid-sentence flips many
     recognisers into Latin output for the whole utterance, so every native
     form needs a romanised twin or the match is lost.

Regional exam names are first-class vocabulary, not synonyms: **SSLC** is what
Class 10 is called across Tamil Nadu and Karnataka, **PUC** is Class 12 in
Karnataka, **HSC** and **+2** in Tamil Nadu, **Matric** in the Hindi belt,
**Intermediate** in Telangana and Andhra. Someone answering "how far did you
study?" will say the certificate name, never "class twelve".

Everything here is merged into the base by `gen_lexicon.py`, de-duplicated,
then validated: `tools/check_nlu.py` asserts every surface form still resolves
to its own detector, so a word added here that collides with another category
fails the build rather than silently mis-classifying someone.
"""

EXTRA = {}


def x(key, **langs):
    EXTRA.setdefault(key, {})
    for lang, words in langs.items():
        EXTRA[key].setdefault(lang, [])
        EXTRA[key][lang].extend(words)


# ───────────────────────────── EDUCATION ─────────────────────────────────
# The commonest real answers are certificate names and "<n> varai" ("up to n").

x("edu.none",
  en=["not studied", "no studies", "uneducated", "never schooled",
      "did not attend school", "left before school", "no formal education",
      "only signature", "just signature", "thumb impression", "cannot write",
      "i cant read", "i cannot write", "not gone to school", "zero schooling"],
  ta=["ஒன்னும் படிக்கல", "ஒன்றும் படிக்கவில்லை", "கையெழுத்து மட்டும்",
      "எழுத தெரியாது", "படிக்க தெரியாது", "பள்ளிக்கூடம் போகல",
      "ஸ்கூலே போகல", "படிப்பே இல்ல", "onnum padikkala", "ezhutha theriyathu",
      "padikka theriyathu", "kaiyezhuthu", "schoole pogala", "padippe illa"],
  hi=["कुछ नहीं पढ़ा", "पढ़ा लिखा नहीं", "अंगूठा छाप", "स्कूल नहीं गया",
      "स्कूल नहीं गयी", "लिखना नहीं आता", "पढ़ना नहीं आता",
      "kuch nahi padha", "padha likha nahi", "angutha chaap",
      "likhna nahi aata", "padhna nahi aata", "school nahi gayi"],
  te=["ఏమీ చదవలేదు", "చదువు రాదు", "రాయడం రాదు", "బడి చూడలేదు",
      "emi chadavaledu", "chaduvu radu", "rayadam radu", "badi chudaledu"],
  kn=["ಏನೂ ಓದಿಲ್ಲ", "ಓದೋಕೆ ಬರಲ್ಲ", "ಬರಿಯೋಕೆ ಬರಲ್ಲ", "ಶಾಲೆ ನೋಡಿಲ್ಲ",
      "enu odilla", "odoke baralla", "bariyoke baralla", "shale nodilla"],
  ml=["ഒന്നും പഠിച്ചിട്ടില്ല", "എഴുതാൻ അറിയില്ല", "വായിക്കാൻ അറിയില്ല",
      "സ്കൂളിൽ പോയില്ല", "onnum padichittilla", "ezhuthan ariyilla",
      "vayikkan ariyilla", "schoolil poyilla"])

x("edu.class5",
  en=["5th standard", "fifth standard", "fifth class", "upto fifth",
      "up to fifth", "5 varai", "5th fail", "fifth fail", "till 5th",
      "only 5th", "primary only", "elementary school"],
  ta=["ஐந்தாம் வகுப்பு வரை", "அஞ்சு வரை", "அஞ்சாங்கிளாஸ்", "அஞ்சாம் கிளாஸ்",
      "ஐந்து வரை", "அஞ்சாவது", "ஐந்தாம் வரை", "anju varai", "anjaam class",
      "anjavathu", "ainthaam vaguppu", "ainthu varai", "fifth varai"],
  hi=["पाँचवीं तक", "पांचवी तक", "पाँचवी कक्षा", "पाँचवीं पास", "५वीं",
      "paanchvi tak", "panchvi pass", "paachvi", "paanchvi class"],
  te=["ఐదవ తరగతి", "ఐదో క్లాస్", "ఐదు వరకు", "ఐదవ తరగతి వరకు",
      "aidava taragati", "aido class", "aidu varaku"],
  kn=["ಐದನೇ ಕ್ಲಾಸ್", "ಐದನೇ ತರಗತಿ ವರೆಗೆ", "ಐದರ ವರೆಗೆ", "ಐದನೆಯ",
      "aidane class", "aidane taragati", "aidara varege"],
  ml=["അഞ്ചാം ക്ലാസ് വരെ", "അഞ്ചു വരെ", "അഞ്ചാം തരം",
      "anjaam class vare", "anju vare", "anjaam tharam"])

x("edu.class8",
  en=["8th standard", "eighth standard", "eighth class", "upto eighth",
      "up to eighth", "8 varai", "8th fail", "eighth fail", "till 8th",
      "only 8th", "middle school", "upper primary"],
  ta=["எட்டாம் வகுப்பு வரை", "எட்டு வரை", "எட்டாங்கிளாஸ்", "எட்டாம் கிளாஸ்",
      "எட்டாவது", "ettu varai", "ettaam class", "ettavathu",
      "ettaam vaguppu", "eighth varai"],
  hi=["आठवीं तक", "आठवी तक", "आठवीं कक्षा", "आठवीं पास", "८वीं",
      "aathvi tak", "aathvin", "aathvi pass", "aathvi class"],
  te=["ఎనిమిదవ తరగతి", "ఎనిమిదో క్లాస్", "ఎనిమిది వరకు",
      "enimidava taragati", "enimido class", "enimidi varaku"],
  kn=["ಎಂಟನೇ ಕ್ಲಾಸ್", "ಎಂಟನೇ ತರಗತಿ", "ಎಂಟರ ವರೆಗೆ",
      "entane class", "entane taragati", "entara varege"],
  ml=["എട്ടാം ക്ലാസ് വരെ", "എട്ടു വരെ", "എട്ടാം തരം",
      "ettaam class vare", "ettu vare", "ettaam tharam"])

x("edu.class10",
  # SSLC is what Class 10 is CALLED in Tamil Nadu and Karnataka. Matric in the
  # Hindi belt. These are the answers people actually give.
  en=["sslc", "s s l c", "matric", "matriculation", "10th standard",
      "tenth standard", "tenth class", "10th pass", "tenth pass", "10th fail",
      "tenth fail", "upto tenth", "up to tenth", "till 10th", "only 10th",
      "secondary school", "high school", "10 varai", "board exam"],
  ta=["பத்தாம் வகுப்பு வரை", "பத்து வரை", "பத்தாங்கிளாஸ்", "பத்தாம் கிளாஸ்",
      "பத்தாவது", "எஸ் எஸ் எல் சி", "பத்தாம் படித்தேன்",
      "pathu varai", "pathaam class", "pathavathu", "paththaam vaguppu",
      "patthu varai", "tenth varai", "sslc mudichen"],
  hi=["दसवीं तक", "दसवी तक", "दसवीं कक्षा", "दसवीं पास", "मैट्रिक", "हाई स्कूल",
      "१०वीं", "dasvi tak", "dasvin", "dasvi pass", "matric pass",
      "high school pass"],
  te=["పదవ తరగతి", "పదో క్లాస్", "పది వరకు", "ఎస్ ఎస్ సి", "పదవ తరగతి పాస్",
      "padava taragati", "pado class", "padi varaku", "ssc pass"],
  kn=["ಹತ್ತನೇ ಕ್ಲಾಸ್", "ಹತ್ತನೇ ತರಗತಿ", "ಹತ್ತರ ವರೆಗೆ", "ಎಸ್ ಎಸ್ ಎಲ್ ಸಿ",
      "hattane class", "hattane taragati", "hattara varege", "sslc pass"],
  ml=["പത്താം ക്ലാസ് വരെ", "പത്തു വരെ", "പത്താം തരം", "എസ് എസ് എൽ സി",
      "pathaam class vare", "pathu vare", "pathaam tharam", "sslc jayichu"])

x("edu.class12",
  # PUC = Karnataka. HSC / +2 = Tamil Nadu. Intermediate = Telugu states.
  en=["hsc", "h s c", "puc", "p u c", "pre university", "pre-university",
      "plus two", "plus 2", "+2", "+ 2", "12th standard", "twelfth standard",
      "twelfth class", "12th pass", "twelfth pass", "12th fail",
      "higher secondary", "senior secondary", "intermediate pass",
      "upto twelfth", "till 12th", "only 12th", "junior college"],
  ta=["பன்னிரண்டாம் வகுப்பு", "பன்னிரெண்டு வரை", "பிளஸ் டூ", "பிளஸ் 2",
      "மேல்நிலை", "பன்னிரண்டாவது", "எச் எஸ் சி",
      "plus two mudichen", "pannirandaam vaguppu", "pannirendu varai",
      "melnilai", "twelfth varai", "hsc mudichen"],
  hi=["बारहवीं तक", "बारहवी तक", "बारहवीं कक्षा", "बारहवीं पास",
      "इंटर", "इंटरमीडिएट", "सीनियर सेकेंडरी", "१२वीं",
      "barahvi tak", "barahvin", "barahvi pass", "inter pass",
      "intermediate pass"],
  te=["పన్నెండవ తరగతి", "పన్నెండో క్లాస్", "ఇంటర్", "ఇంటర్మీడియట్",
      "ఇంటర్ పాస్", "panneendava taragati", "panneendo class",
      "inter pass", "intermediate ayindi"],
  kn=["ಹನ್ನೆರಡನೇ ಕ್ಲಾಸ್", "ಪಿಯುಸಿ", "ಪಿ ಯು ಸಿ", "ದ್ವಿತೀಯ ಪಿಯುಸಿ",
      "hannerdane class", "puc pass", "dwitiya puc", "second puc"],
  ml=["പന്ത്രണ്ടാം ക്ലാസ്", "പ്ലസ് ടു", "പ്ലസ് 2", "ഹയർ സെക്കൻഡറി",
      "panthrandaam class", "plus two kazhinju", "higher secondary kazhinju"])

x("edu.iti_diploma",
  en=["iti", "i t i", "industrial training", "polytechnic", "poly technic",
      "diploma", "diploma holder", "diploma course", "dme", "deee", "dece",
      "civil diploma", "mechanical diploma", "electrical diploma",
      "fitter trade", "welder trade", "electrician trade", "turner trade",
      "trade certificate", "ncvt", "scvt", "apprenticeship"],
  ta=["ஐ டி ஐ", "ஐடிஐ", "பாலிடெக்னிக்", "டிப்ளோமா", "டிப்ளமோ", "டிப்ளோமா முடிச்சேன்",
      "தொழிற்பயிற்சி", "iti mudichen", "polytechnic mudichen",
      "diploma mudichen", "thozhil payirchi"],
  hi=["आई टी आई", "आईटीआई", "पॉलिटेक्निक", "डिप्लोमा", "डिप्लोमा किया",
      "औद्योगिक प्रशिक्षण", "iti kiya", "polytechnic kiya", "diploma kiya"],
  te=["ఐటిఐ", "ఐ టి ఐ", "పాలిటెక్నిక్", "డిప్లొమా", "డిప్లొమా చేశాను",
      "iti chesanu", "polytechnic chesanu", "diploma chesanu"],
  kn=["ಐಟಿಐ", "ಐ ಟಿ ಐ", "ಪಾಲಿಟೆಕ್ನಿಕ್", "ಡಿಪ್ಲೊಮಾ", "ಡಿಪ್ಲೊಮಾ ಮಾಡಿದೆ",
      "iti madide", "polytechnic madide", "diploma madide"],
  ml=["ഐടിഐ", "ഐ ടി ഐ", "പോളിടെക്നിക്", "ഡിപ്ലോമ", "ഡിപ്ലോമ ചെയ്തു",
      "iti cheythu", "polytechnic cheythu", "diploma cheythu"])

x("edu.graduate",
  en=["ba", "b a", "bsc", "b sc", "b.sc", "bcom", "b com", "bba", "bca",
      "be", "b e", "btech", "b tech", "bed", "b ed", "ma", "msc", "mcom",
      "mba", "mca", "degree", "degree holder", "graduate", "graduation",
      "college", "college finished", "completed degree", "under graduate",
      "post graduate", "pg", "arts college", "engineering college"],
  ta=["பட்டப்படிப்பு", "பட்டம்", "பி ஏ", "பி எஸ் சி", "பி காம்", "பொறியியல்",
      "கல்லூரி", "கல்லூரி முடிச்சேன்", "டிகிரி", "டிகிரி முடிச்சேன்",
      "pattappadippu", "kalluri mudichen", "degree mudichen", "bsc mudichen"],
  hi=["स्नातक", "ग्रेजुएट", "डिग्री", "बी ए", "बी एस सी", "बी कॉम",
      "कॉलेज", "कॉलेज किया", "डिग्री की", "snatak", "degree ki",
      "college kiya", "graduate hoon"],
  te=["డిగ్రీ", "పట్టభద్రుడు", "బి ఏ", "బి ఎస్ సి", "కళాశాల", "కాలేజ్",
      "degree chesanu", "college chesanu", "digree"],
  kn=["ಪದವಿ", "ಡಿಗ್ರಿ", "ಬಿ ಎ", "ಬಿ ಎಸ್ ಸಿ", "ಕಾಲೇಜು", "ಕಾಲೇಜ್ ಮಾಡಿದೆ",
      "padavi", "degree madide", "college madide"],
  ml=["ബിരുദം", "ഡിഗ്രി", "ബി എ", "ബി എസ് സി", "കോളേജ്", "കോളേജ് കഴിഞ്ഞു",
      "birudam", "degree kazhinju", "college kazhinju"])

# ───────────────────────────── MARKERS ───────────────────────────────────

x("marker.yes",
  en=["yes", "yeah", "yep", "correct", "right", "true", "ok", "okay",
      "sure", "of course", "definitely", "that is right", "thats right",
      "i do", "i have", "there is", "present"],
  ta=["ஆமா", "ஆமாம்", "ஆம்", "சரி", "சரிங்க", "இருக்கு", "உண்டு", "ஓகே",
      "நிச்சயமா", "aama", "aamaam", "seri", "sari", "irukku", "undu",
      "seringa", "nichayama"],
  hi=["हाँ", "हा", "जी", "जी हाँ", "ठीक", "ठीक है", "सही", "बिलकुल", "है",
      "haan", "ha", "ji haan", "theek hai", "sahi", "bilkul", "hai"],
  te=["అవును", "ఔను", "సరే", "ఉంది", "అవునండి", "సరి",
      "avunu", "sare", "undi", "avunandi"],
  kn=["ಹೌದು", "ಹೌದ್ರಿ", "ಸರಿ", "ಇದೆ", "ಆಯ್ತು",
      "houdu", "haudu", "sari", "ide", "aytu"],
  ml=["അതെ", "ഉവ്വ്", "ശരി", "ഉണ്ട്", "ആയി",
      "athe", "uvvu", "sari", "undu"])

x("marker.no",
  en=["no", "nope", "not", "none", "nothing", "never", "no problem",
      "not at all", "i dont", "i do not", "there is no", "nil", "negative"],
  ta=["இல்ல", "இல்லை", "இல்லீங்க", "ஒன்னும் இல்ல", "கிடையாது", "பிரச்சனை இல்ல",
      "illa", "illai", "illinga", "onnum illa", "kidaiyathu",
      "prachanai illa"],
  hi=["नहीं", "नही", "ना", "कुछ नहीं", "बिलकुल नहीं", "कोई नहीं",
      "कोई दिक्कत नहीं", "nahi", "nahin", "na", "kuch nahi",
      "koi nahi", "koi dikkat nahi"],
  te=["లేదు", "కాదు", "ఏమీ లేదు", "సమస్య లేదు",
      "ledu", "kadu", "emi ledu", "samasya ledu"],
  kn=["ಇಲ್ಲ", "ಅಲ್ಲ", "ಏನೂ ಇಲ್ಲ", "ತೊಂದರೆ ಇಲ್ಲ",
      "illa", "alla", "enu illa", "tondare illa"],
  ml=["ഇല്ല", "അല്ല", "ഒന്നും ഇല്ല", "പ്രശ്നം ഇല്ല",
      "illa", "alla", "onnum illa", "prashnam illa"])

x("marker.family",
  en=["my family", "our family", "my father", "my mother", "my parents",
      "father does", "mother does", "at home", "family work",
      "traditional work", "ancestral work", "my people", "we do",
      "our people", "family business", "father's work", "fathers work"],
  ta=["எங்க குடும்பம்", "எங்க வீட்ல", "என் அப்பா", "என் அம்மா", "அப்பா அம்மா",
      "பரம்பரை", "பரம்பரை தொழில்", "குடும்ப தொழில்", "வீட்ல",
      "enga kudumbam", "enga veetla", "en appa", "en amma", "parambarai",
      "kudumba thozhil", "veetla"],
  hi=["मेरा परिवार", "हमारा परिवार", "मेरे पिता", "मेरी माँ", "घर में",
      "पुश्तैनी", "पुश्तैनी काम", "पारिवारिक काम", "घर का काम",
      "mera parivar", "ghar mein", "pushtaini kaam", "mere pita"],
  te=["మా కుటుంబం", "మా ఇంట్లో", "మా నాన్న", "మా అమ్మ", "వంశపారంపర్య",
      "ma kutumbam", "ma intlo", "ma nanna", "ma amma"],
  kn=["ನಮ್ಮ ಕುಟುಂಬ", "ನಮ್ಮ ಮನೆಯಲ್ಲಿ", "ನಮ್ಮ ಅಪ್ಪ", "ನಮ್ಮ ಅಮ್ಮ", "ವಂಶಪಾರಂಪರ್ಯ",
      "namma kutumba", "namma maneyalli", "namma appa"],
  ml=["ഞങ്ങളുടെ കുടുംബം", "വീട്ടിൽ", "എന്റെ അച്ഛൻ", "എന്റെ അമ്മ", "പരമ്പരാഗത",
      "njangalude kudumbam", "veettil", "ente achan", "ente amma"])

x("marker.student",
  en=["studying", "student", "i study", "still studying", "in college",
      "in school", "doing my degree", "continuing studies", "final year"],
  ta=["படிக்கிறேன்", "படிச்சிட்டு இருக்கேன்", "மாணவன்", "மாணவி", "கல்லூரியில",
      "இன்னும் படிக்கிறேன்", "padikkiren", "padichittu irukken",
      "maanavan", "innum padikkiren"],
  hi=["पढ़ रहा हूँ", "पढ़ रही हूँ", "छात्र", "विद्यार्थी", "कॉलेज में",
      "padh raha hoon", "padh rahi hoon", "chhatra", "college mein"],
  te=["చదువుతున్నాను", "విద్యార్థి", "కాలేజీలో",
      "chaduvutunnanu", "vidyarthi", "collegelo"],
  kn=["ಓದುತ್ತಿದ್ದೇನೆ", "ವಿದ್ಯಾರ್ಥಿ", "ಕಾಲೇಜಿನಲ್ಲಿ",
      "oduttiddene", "vidyarthi", "collegenalli"],
  ml=["പഠിക്കുന്നു", "വിദ്യാർത്ഥി", "കോളേജിൽ",
      "padikkunnu", "vidyarthi", "collegil"])

x("marker.unemployed",
  en=["no work", "no job", "jobless", "unemployed", "sitting idle",
      "looking for work", "searching job", "searching for work",
      "at home doing nothing", "nothing right now", "work is not there",
      "lost my job", "no income"],
  ta=["வேலை இல்ல", "வேலை இல்லை", "வேலை தேடுறேன்", "வேலை தேடிட்டு இருக்கேன்",
      "சும்மா இருக்கேன்", "வீட்ல இருக்கேன்", "வேலை போச்சு",
      "vela illa", "velai illai", "vela theduren", "summa irukken",
      "veetla irukken", "vela pochu"],
  hi=["काम नहीं", "नौकरी नहीं", "बेरोजगार", "खाली बैठा हूँ", "काम ढूंढ रहा हूँ",
      "नौकरी ढूंढ रहा हूँ", "kaam nahi", "naukri nahi", "berozgar",
      "khali baitha hoon", "kaam dhoond raha hoon"],
  te=["పని లేదు", "ఉద్యోగం లేదు", "ఖాళీగా ఉన్నాను", "పని వెతుకుతున్నాను",
      "pani ledu", "udyogam ledu", "khaliga unnanu", "pani vetukutunnanu"],
  kn=["ಕೆಲಸ ಇಲ್ಲ", "ಉದ್ಯೋಗ ಇಲ್ಲ", "ಖಾಲಿ ಇದ್ದೇನೆ", "ಕೆಲಸ ಹುಡುಕುತ್ತಿದ್ದೇನೆ",
      "kelasa illa", "udyoga illa", "khali iddene", "kelasa hudukuttiddene"],
  ml=["ജോലി ഇല്ല", "പണി ഇല്ല", "വെറുതെ ഇരിക്കുന്നു", "ജോലി അന്വേഷിക്കുന്നു",
      "joli illa", "pani illa", "veruthe irikkunnu", "joli anveshikkunnu"])

x("marker.constraint",
  en=["back pain", "leg pain", "knee pain", "cannot lift", "cant lift",
      "cannot stand long", "cannot walk far", "cannot carry heavy",
      "heavy work not possible", "operation", "surgery", "accident",
      "fracture", "asthma", "breathing problem", "bp", "blood pressure",
      "sugar", "diabetes", "eye problem", "cannot see properly",
      "hearing problem", "hard of hearing", "disability", "handicapped",
      "differently abled", "weak health", "not well", "health problem",
      "pregnant", "old age"],
  ta=["முதுகு வலி", "கால் வலி", "முழங்கால் வலி", "தூக்க முடியாது",
      "நிக்க முடியாது", "நடக்க முடியாது", "கனமான வேலை முடியாது",
      "ஆபரேஷன்", "விபத்து", "மூச்சு திணறல்", "சர்க்கரை நோய்", "பிபி",
      "கண் தெரியல", "காது கேட்காது", "ஊனம்", "உடம்பு சரியில்ல",
      "muthugu vali", "kaal vali", "thooka mudiyathu", "nikka mudiyathu",
      "nadakka mudiyathu", "operation aachu", "udambu sariyilla",
      "kan theriyala", "kaathu kekkathu"],
  hi=["कमर दर्द", "पैर दर्द", "घुटने में दर्द", "उठा नहीं सकता",
      "खड़ा नहीं हो सकता", "चल नहीं सकता", "भारी काम नहीं", "ऑपरेशन",
      "दुर्घटना", "दमा", "शुगर", "बीपी", "आँख की समस्या", "सुनाई नहीं देता",
      "विकलांग", "तबियत ठीक नहीं", "kamar dard", "pair dard",
      "utha nahi sakta", "bhari kaam nahi", "tabiyat theek nahi"],
  te=["నడుము నొప్పి", "కాలు నొప్పి", "ఎత్తలేను", "నిలబడలేను", "నడవలేను",
      "బరువు పని కాదు", "ఆపరేషన్", "షుగర్", "బీపీ", "వికలాంగుడు",
      "ఒంట్లో బాగోలేదు", "nadumu noppi", "kaalu noppi", "ettalenu",
      "nadavalenu", "ontlo bagoledu"],
  kn=["ಸೊಂಟ ನೋವು", "ಕಾಲು ನೋವು", "ಎತ್ತಲಾರೆ", "ನಿಲ್ಲಲಾರೆ", "ನಡೆಯಲಾರೆ",
      "ಭಾರ ಕೆಲಸ ಆಗಲ್ಲ", "ಆಪರೇಷನ್", "ಸಕ್ಕರೆ ಕಾಯಿಲೆ", "ಬಿಪಿ", "ವಿಕಲಾಂಗ",
      "ಆರೋಗ್ಯ ಸರಿ ಇಲ್ಲ", "sonta novu", "kaalu novu", "ettalare",
      "nadeyalare", "arogya sari illa"],
  ml=["നടുവേദന", "കാൽ വേദന", "ഉയർത്താൻ പറ്റില്ല", "നിൽക്കാൻ പറ്റില്ല",
      "നടക്കാൻ പറ്റില്ല", "ഭാരം എടുക്കാൻ പറ്റില്ല", "ഓപ്പറേഷൻ", "പ്രമേഹം",
      "ബിപി", "വികലാംഗൻ", "സുഖമില്ല", "naduvedana", "kaal vedana",
      "uyarthan pattilla", "nadakkan pattilla", "sukhamilla"])

# ─────────────────────────── PREFERENCE ──────────────────────────────────

x("pref.self_strong",
  en=["own business", "my own business", "own work", "my own work",
      "start a shop", "open a shop", "start my own", "self employment",
      "self employed", "be my own boss", "entrepreneur", "start a unit",
      "own enterprise", "work for myself", "independent work",
      "small business", "own farm", "own dairy"],
  ta=["சொந்த தொழில்", "சொந்தமா தொழில்", "சொந்த வேலை", "சொந்தமா",
      "கடை போடணும்", "கடை வைக்கணும்", "சுயதொழில்", "சொந்தமா பண்ணனும்",
      "தானா செய்யணும்", "sontha thozhil", "sonthama thozhil",
      "kadai podanum", "kadai vaikkanum", "suya thozhil", "sonthama pannanum"],
  hi=["अपना काम", "अपना धंधा", "अपना बिजनेस", "दुकान खोलना", "खुद का काम",
      "स्वरोजगार", "अपनी दुकान", "खुद का धंधा",
      "apna kaam", "apna dhandha", "apna business", "dukan kholna",
      "khud ka kaam", "swarozgar"],
  te=["సొంత వ్యాపారం", "సొంత పని", "దుకాణం పెట్టాలి", "స్వయం ఉపాధి",
      "నా సొంత పని", "sontha vyaparam", "sontha pani", "dukanam pettali",
      "swayam upadhi"],
  kn=["ಸ್ವಂತ ವ್ಯಾಪಾರ", "ಸ್ವಂತ ಕೆಲಸ", "ಅಂಗಡಿ ಹಾಕಬೇಕು", "ಸ್ವಯಂ ಉದ್ಯೋಗ",
      "swanta vyapara", "swanta kelasa", "angadi hakabeku", "swayam udyoga"],
  ml=["സ്വന്തം ബിസിനസ്", "സ്വന്തം ജോലി", "കട തുടങ്ങണം", "സ്വയം തൊഴിൽ",
      "swantham business", "swantham joli", "kada thudangnam", "swayam thozhil"])

x("pref.self_weak",
  en=["maybe own", "thinking of business", "if possible own", "prefer own",
      "would like my own", "own if possible"],
  ta=["சொந்தமா முடிஞ்சா", "சொந்தமா யோசிக்கிறேன்", "சொந்தம் நல்லா இருக்கும்",
      "sonthama mudinja", "sonthama yosikkiren"],
  hi=["अपना हो तो अच्छा", "सोच रहा हूँ अपना", "apna ho to achha",
      "soch raha hoon apna"],
  te=["సొంతం అయితే బాగుంటుంది", "sontham ayite bagunthundi"],
  kn=["ಸ್ವಂತ ಆದರೆ ಒಳ್ಳೆಯದು", "swanta adare olleyadu"],
  ml=["സ്വന്തം ആയാൽ നല്ലത്", "swantham ayal nallathu"])

x("pref.wage_strong",
  en=["want a job", "need a job", "company job", "factory job", "salary job",
      "monthly salary", "permanent job", "government job", "work under someone",
      "employment", "employee", "regular income", "fixed salary",
      "job is better", "any job", "wage work"],
  ta=["வேலை வேணும்", "வேலைக்கு போகணும்", "கம்பெனி வேலை", "பேக்டரி வேலை",
      "சம்பளம் வேணும்", "மாச சம்பளம்", "நிரந்தர வேலை", "அரசு வேலை",
      "vela venum", "velaikku poganum", "company vela", "sambalam venum",
      "maasa sambalam", "arasu vela"],
  hi=["नौकरी चाहिए", "नौकरी करनी है", "कंपनी की नौकरी", "फैक्ट्री का काम",
      "तनख्वाह", "महीने की तनख्वाह", "पक्की नौकरी", "सरकारी नौकरी",
      "naukri chahiye", "naukri karni hai", "company ki naukri",
      "tankhwah", "pakki naukri", "sarkari naukri"],
  te=["ఉద్యోగం కావాలి", "కంపెనీ ఉద్యోగం", "జీతం కావాలి", "నెల జీతం",
      "ప్రభుత్వ ఉద్యోగం", "udyogam kavali", "company udyogam",
      "jeetham kavali", "nela jeetham"],
  kn=["ಕೆಲಸ ಬೇಕು", "ಕಂಪನಿ ಕೆಲಸ", "ಸಂಬಳ ಬೇಕು", "ತಿಂಗಳ ಸಂಬಳ", "ಸರ್ಕಾರಿ ಕೆಲಸ",
      "kelasa beku", "company kelasa", "sambala beku", "sarkari kelasa"],
  ml=["ജോലി വേണം", "കമ്പനി ജോലി", "ശമ്പളം വേണം", "മാസ ശമ്പളം", "സർക്കാർ ജോലി",
      "joli venam", "company joli", "shambalam venam", "sarkar joli"])

x("pref.wage_weak",
  en=["job is fine", "any work is ok", "whatever job", "job also ok",
      "dont mind a job", "job if available"],
  ta=["வேலையும் சரி", "எது வேலையா இருந்தாலும் சரி", "வேலை கிடைச்சா சரி",
      "velaiyum sari", "vela kidaicha sari"],
  hi=["नौकरी भी ठीक", "जो भी काम मिले", "naukri bhi theek", "jo bhi kaam mile"],
  te=["ఉద్యోగం అయినా సరే", "udyogam ayina sare"],
  kn=["ಕೆಲಸ ಆದರೂ ಸರಿ", "kelasa adaru sari"],
  ml=["ജോലി ആയാലും മതി", "joli ayalum mathi"])

# ─────────────────────────── MOBILITY ────────────────────────────────────

x("mob.local",
  en=["my village only", "only my village", "in my village", "near my house",
      "close to home", "walking distance", "nearby only", "same village",
      "cannot travel", "cant travel", "cannot go far", "must be near",
      "within my area", "my street", "my town only", "local only"],
  ta=["என் ஊர்ல மட்டும்", "ஊர்ல மட்டும்", "வீட்டுக்கு பக்கத்துல",
      "பக்கத்துல மட்டும்", "தூரம் போக முடியாது", "வெளியூர் போக முடியாது",
      "நடந்து போற தூரம்", "இதே ஊர்ல", "en oorla mattum", "oorla mattum",
      "veetuku pakkathula", "pakkathula mattum", "thooram poga mudiyathu",
      "veliyoor poga mudiyathu"],
  hi=["मेरे गाँव में", "गाँव में ही", "घर के पास", "पास में ही",
      "दूर नहीं जा सकता", "बाहर नहीं जा सकता", "नजदीक ही",
      "mere gaon mein", "gaon mein hi", "ghar ke paas", "paas mein hi",
      "door nahi ja sakta", "najdeek hi"],
  te=["మా ఊర్లోనే", "ఇంటి దగ్గర", "దగ్గరలోనే", "దూరం వెళ్లలేను",
      "ma oorlone", "inti daggara", "daggaralone", "dooram vellalenu"],
  kn=["ನಮ್ಮ ಊರಲ್ಲೇ", "ಮನೆ ಹತ್ತಿರ", "ಹತ್ತಿರದಲ್ಲೇ", "ದೂರ ಹೋಗಲಾರೆ",
      "namma oorralle", "mane hattira", "hattiradalle", "doora hogalare"],
  ml=["എന്റെ നാട്ടിൽ", "വീടിനടുത്ത്", "അടുത്ത് മാത്രം", "ദൂരെ പോകാൻ പറ്റില്ല",
      "ente nattil", "veetinadutthu", "adutthu mathram", "doore pokan pattilla"])

x("mob.district",
  en=["my district", "within district", "anywhere in district",
      "in the district", "district level", "nearby town", "taluk",
      "can travel in district", "up to district"],
  ta=["என் மாவட்டம்", "மாவட்டத்துல", "மாவட்டம் முழுக்க", "ஜில்லாவுல",
      "பக்கத்து டவுன்", "வட்டம்", "en maavattam", "maavattathula",
      "maavattam muzhukka", "jillavula", "pakkathu town"],
  hi=["मेरा जिला", "जिले में", "जिले के अंदर", "पूरे जिले में", "पास का शहर",
      "mera jila", "jile mein", "jile ke andar", "poore jile mein"],
  te=["మా జిల్లా", "జిల్లాలో", "జిల్లా అంతా", "ma jilla", "jillalo",
      "jilla antha"],
  kn=["ನಮ್ಮ ಜಿಲ್ಲೆ", "ಜಿಲ್ಲೆಯಲ್ಲಿ", "ಜಿಲ್ಲೆ ಪೂರ್ತಿ", "namma jille",
      "jilleyalli", "jille poorti"],
  ml=["എന്റെ ജില്ല", "ജില്ലയിൽ", "ജില്ല മുഴുവൻ", "ente jilla", "jillayil",
      "jilla muzhuvan"])

x("mob.state",
  en=["anywhere in state", "whole state", "any district", "can go anywhere",
      "ready to travel", "willing to travel", "no problem travelling",
      "can stay outside", "hostel is fine", "can relocate", "outstation ok"],
  ta=["மாநிலம் முழுக்க", "எங்க வேணும்னாலும்", "எங்கேயும் போவேன்",
      "தூரம் போக தயார்", "வெளியூர் போகலாம்", "ஹாஸ்டல் இருந்தா சரி",
      "maanilam muzhukka", "enga venumnalum", "engeyum poven",
      "thooram poga thayar", "veliyoor pogalam"],
  hi=["पूरे राज्य में", "कहीं भी", "कहीं भी जा सकता हूँ", "सफर कर सकता हूँ",
      "बाहर रह सकता हूँ", "हॉस्टल चलेगा",
      "poore rajya mein", "kahin bhi", "kahin bhi ja sakta hoon",
      "safar kar sakta hoon", "bahar reh sakta hoon"],
  te=["రాష్ట్రం అంతా", "ఎక్కడైనా", "ఎక్కడికైనా వెళ్తాను", "ప్రయాణం చేయగలను",
      "rashtram antha", "ekkadaina", "ekkadikaina veltanu"],
  kn=["ರಾಜ್ಯ ಪೂರ್ತಿ", "ಎಲ್ಲಿಯಾದರೂ", "ಎಲ್ಲಿಗಾದರೂ ಹೋಗುತ್ತೇನೆ", "ಪ್ರಯಾಣ ಮಾಡಬಲ್ಲೆ",
      "rajya poorti", "elliyadaru", "elligadaru hoguttene"],
  ml=["സംസ്ഥാനം മുഴുവൻ", "എവിടെയും", "എവിടെയും പോകാം", "യാത്ര ചെയ്യാം",
      "samsthanam muzhuvan", "evideyum", "evideyum pokam", "yathra cheyyam"])

x("mob.anywhere",
  en=["anywhere", "any place", "no restriction", "doesnt matter where",
      "does not matter", "wherever", "any location"],
  ta=["எங்கேயும்", "எந்த இடமும்", "எங்க இருந்தாலும் சரி",
      "engeyum", "entha idamum"],
  hi=["कहीं पर भी", "कोई भी जगह", "जहाँ भी", "kahin par bhi", "koi bhi jagah"],
  te=["ఎక్కడైనా సరే", "ఏ ప్రాంతమైనా", "ekkadaina sare"],
  kn=["ಎಲ್ಲಿಯಾದರೂ ಸರಿ", "ಯಾವ ಸ್ಥಳವಾದರೂ", "elliyadaru sari"],
  ml=["എവിടെയായാലും", "ഏത് സ്ഥലവും", "evideyayalum"])

# ────────────────────────── OCCUPATIONS ──────────────────────────────────

x("occ.coolie",
  en=["coolie work", "daily wage", "daily wages", "labour work", "labor work",
      "manual labour", "construction labour", "nrega", "mgnrega",
      "hundred days work", "100 days work", "casual labour", "load work",
      "loading unloading", "headload"],
  ta=["கூலி வேலை", "நாள் கூலி", "தினக்கூலி", "உழைப்பு வேலை", "நூறு நாள் வேலை",
      "நரேகா", "சுமை தூக்கும் வேலை", "kooli vela", "naal kooli",
      "thinakkooli", "nooru naal vela", "coolie vela"],
  hi=["मजदूरी", "दिहाड़ी", "दिहाड़ी मजदूरी", "मजदूर", "नरेगा", "मनरेगा",
      "सौ दिन का काम", "बोझा ढोना", "mazdoori", "dihadi", "mazdoor",
      "narega", "manrega", "sau din ka kaam"],
  te=["కూలి పని", "రోజు కూలి", "కూలీ", "ఉపాధి హామీ", "నరేగా",
      "kooli pani", "roju kooli", "upadhi hami"],
  kn=["ಕೂಲಿ ಕೆಲಸ", "ದಿನಗೂಲಿ", "ಕೂಲಿಕಾರ", "ನರೇಗಾ", "ಉದ್ಯೋಗ ಖಾತ್ರಿ",
      "kooli kelasa", "dinagooli", "narega"],
  ml=["കൂലി പണി", "ദിവസക്കൂലി", "തൊഴിലുറപ്പ്", "നരേഗ",
      "kooli pani", "divasakkooli", "thozhilurappu"])

x("occ.driver",
  en=["driver", "driving", "auto driver", "taxi driver", "lorry driver",
      "truck driver", "bus driver", "cab driver", "tempo driver",
      "tractor driver", "two wheeler", "delivery boy", "ola uber",
      "swiggy zomato"],
  ta=["ஓட்டுநர்", "டிரைவர்", "ஆட்டோ ஓட்டுறேன்", "லாரி ஓட்டுறேன்",
      "கார் ஓட்டுறேன்", "டிராக்டர் ஓட்டுறேன்", "டெலிவரி வேலை",
      "auto ottuten", "lorry ottuten", "driver vela", "car ottuten"],
  hi=["ड्राइवर", "गाड़ी चलाता हूँ", "ऑटो चलाता हूँ", "ट्रक चलाता हूँ",
      "टैक्सी", "डिलीवरी का काम", "gaadi chalata hoon", "auto chalata hoon",
      "truck chalata hoon", "delivery ka kaam"],
  te=["డ్రైవర్", "ఆటో నడుపుతాను", "లారీ నడుపుతాను", "కారు నడుపుతాను",
      "auto nadupatanu", "lorry nadupatanu"],
  kn=["ಡ್ರೈವರ್", "ಆಟೋ ಓಡಿಸುತ್ತೇನೆ", "ಲಾರಿ ಓಡಿಸುತ್ತೇನೆ", "ಕಾರು ಓಡಿಸುತ್ತೇನೆ",
      "auto odisuttene", "lorry odisuttene"],
  ml=["ഡ്രൈവർ", "ഓട്ടോ ഓടിക്കുന്നു", "ലോറി ഓടിക്കുന്നു", "ടാക്സി",
      "auto odikkunnu", "lorry odikkunnu"])

x("occ.shop",
  en=["shop", "small shop", "petty shop", "grocery shop", "tea shop",
      "provision store", "vegetable selling", "street vending", "hawker",
      "shop keeper", "shopkeeper", "kirana", "business", "trading",
      "selling", "market"],
  ta=["கடை", "சின்ன கடை", "பெட்டிக்கடை", "மளிகை கடை", "டீ கடை",
      "காய்கறி விக்கிறேன்", "வியாபாரம்", "கடை வச்சிருக்கேன்",
      "kadai", "petti kadai", "malligai kadai", "tea kadai",
      "vyaparam", "kaikari vikkiren"],
  hi=["दुकान", "छोटी दुकान", "किराना दुकान", "चाय की दुकान", "सब्जी बेचता हूँ",
      "व्यापार", "धंधा", "ठेला", "dukan", "kirana dukan", "chai ki dukan",
      "sabzi bechta hoon", "vyapar", "dhandha", "thela"],
  te=["దుకాణం", "చిన్న షాప్", "కిరాణా షాప్", "టీ కొట్టు", "కూరగాయలు అమ్ముతాను",
      "వ్యాపారం", "dukanam", "kirana shop", "tea kottu", "vyaparam"],
  kn=["ಅಂಗಡಿ", "ಸಣ್ಣ ಅಂಗಡಿ", "ಕಿರಾಣಿ ಅಂಗಡಿ", "ಟೀ ಅಂಗಡಿ", "ತರಕಾರಿ ಮಾರುತ್ತೇನೆ",
      "ವ್ಯಾಪಾರ", "angadi", "kirani angadi", "tea angadi", "vyapara"],
  ml=["കട", "ചെറിയ കട", "പലചരക്ക് കട", "ചായക്കട", "പച്ചക്കറി വിൽക്കുന്നു",
      "കച്ചവടം", "kada", "palacharakku kada", "chayakkada", "kachavadam"])

x("occ.government",
  en=["government job", "govt job", "government service", "panchayat work",
      "anganwadi", "asha worker", "village assistant", "peon", "attender",
      "sweeper", "government staff", "public sector"],
  ta=["அரசு வேலை", "அரசாங்க வேலை", "பஞ்சாயத்து வேலை", "அங்கன்வாடி",
      "கிராம உதவியாளர்", "அரசு ஊழியர்", "arasu vela", "arasanga vela",
      "panchayathu vela", "anganwadi vela"],
  hi=["सरकारी नौकरी", "सरकारी काम", "पंचायत का काम", "आंगनवाड़ी", "आशा कार्यकर्ता",
      "चपरासी", "sarkari naukri", "sarkari kaam", "panchayat ka kaam",
      "anganwadi", "asha karyakarta"],
  te=["ప్రభుత్వ ఉద్యోగం", "పంచాయతీ పని", "అంగన్వాడీ", "ఆశా వర్కర్",
      "prabhutva udyogam", "panchayati pani"],
  kn=["ಸರ್ಕಾರಿ ಕೆಲಸ", "ಪಂಚಾಯತ್ ಕೆಲಸ", "ಅಂಗನವಾಡಿ", "ಆಶಾ ಕಾರ್ಯಕರ್ತೆ",
      "sarkari kelasa", "panchayat kelasa"],
  ml=["സർക്കാർ ജോലി", "പഞ്ചായത്ത് ജോലി", "അങ്കണവാടി", "ആശാ വർക്കർ",
      "sarkar joli", "panchayath joli"])

x("occ.fishing",
  en=["fishing", "fisherman", "fisher", "catching fish", "fish selling",
      "boat work", "sea work", "prawn", "aquaculture", "fish farming"],
  ta=["மீன் பிடிக்கிறேன்", "மீனவர்", "மீன் விக்கிறேன்", "படகு வேலை",
      "கடல் வேலை", "இறால்", "meen pidikkiren", "meenavar", "meen vikkiren",
      "padagu vela", "kadal vela"],
  hi=["मछली पकड़ना", "मछुआरा", "मछली बेचना", "नाव का काम",
      "machli pakadna", "machuara", "machli bechna", "naav ka kaam"],
  te=["చేపలు పట్టడం", "మత్స్యకారుడు", "చేపలు అమ్మడం",
      "chepalu pattadam", "matsyakarudu"],
  kn=["ಮೀನು ಹಿಡಿಯುವುದು", "ಮೀನುಗಾರ", "ಮೀನು ಮಾರಾಟ",
      "meenu hidiyuvudu", "meenugara"],
  ml=["മീൻ പിടിക്കൽ", "മത്സ്യത്തൊഴിലാളി", "മീൻ വിൽപ്പന",
      "meen pidikkal", "matsyathozhilali"])

# ──────────────────────────── INTERESTS ──────────────────────────────────

x("interest.dairy",
  en=["dairy", "dairy farming", "milk business", "milk selling", "milking",
      "milk society", "milk cooperative", "aavin", "amul", "curd", "ghee",
      "butter", "milk products"],
  ta=["பால் பண்ணை", "பால் வியாபாரம்", "பால் விக்கிறேன்", "பால் கறக்கிறேன்",
      "ஆவின்", "தயிர்", "நெய்", "paal pannai", "paal vyaparam",
      "paal vikkiren", "aavin", "thayir"],
  hi=["डेयरी", "दूध का काम", "दूध बेचना", "दूध डेयरी", "दही", "घी",
      "doodh ka kaam", "doodh bechna", "dairy ka kaam"],
  te=["పాల వ్యాపారం", "పాడి పరిశ్రమ", "పాలు అమ్మడం", "పెరుగు",
      "pala vyaparam", "padi parishrama", "palu ammadam"],
  kn=["ಹಾಲಿನ ವ್ಯಾಪಾರ", "ಹೈನುಗಾರಿಕೆ", "ಹಾಲು ಮಾರಾಟ", "ಮೊಸರು",
      "haalina vyapara", "hainugarike", "haalu marata"],
  ml=["പാൽ വ്യാപാരം", "ക്ഷീര കർഷകൻ", "പാൽ വിൽപ്പന", "തൈര്",
      "paal vyaparam", "ksheera karshakan"])

x("interest.cattle",
  en=["cow", "cows", "buffalo", "buffaloes", "cattle", "livestock",
      "animal husbandry", "rearing cattle", "bull", "calf", "veterinary"],
  ta=["மாடு", "மாடுகள்", "பசு", "எருமை", "கால்நடை", "கன்று", "காளை",
      "மாடு வளர்க்கிறேன்", "maadu", "pasu", "erumai", "kaalnadai",
      "maadu valarkkiren"],
  hi=["गाय", "भैंस", "पशु", "मवेशी", "पशुपालन", "बैल", "बछड़ा",
      "gaay", "bhains", "pashu", "maveshi", "pashupalan"],
  te=["ఆవు", "గేదె", "పశువులు", "పశుపోషణ", "ఎద్దు",
      "aavu", "gede", "pashuvulu", "pashuposhana"],
  kn=["ಹಸು", "ಎಮ್ಮೆ", "ದನ", "ಜಾನುವಾರು", "ಪಶುಸಂಗೋಪನೆ",
      "hasu", "emme", "dana", "januvaru", "pashusangopane"],
  ml=["പശു", "എരുമ", "കന്നുകാലി", "മൃഗപരിപാലനം",
      "pashu", "eruma", "kannukali", "mrigaparipalanam"])

x("interest.goat",
  en=["goat", "goats", "sheep", "goat rearing", "sheep rearing",
      "goat farming", "mutton", "lamb", "kid rearing"],
  ta=["ஆடு", "ஆடுகள்", "வெள்ளாடு", "செம்மறி ஆடு", "ஆடு வளர்ப்பு",
      "ஆடு வளர்க்கிறேன்", "aadu", "vellaadu", "semmari aadu",
      "aadu valarppu", "aadu valarkkiren"],
  hi=["बकरी", "बकरियां", "भेड़", "बकरी पालन", "भेड़ पालन",
      "bakri", "bakri palan", "bhed", "bhed palan"],
  te=["మేక", "మేకలు", "గొర్రె", "మేక పెంపకం",
      "meka", "mekalu", "gorre", "meka pempakam"],
  kn=["ಮೇಕೆ", "ಆಡು", "ಕುರಿ", "ಮೇಕೆ ಸಾಕಾಣಿಕೆ",
      "meke", "kuri", "meke sakanike"],
  ml=["ആട്", "ആടുകൾ", "ചെമ്മരിയാട്", "ആട് വളർത്തൽ",
      "aadu", "chemmariyadu", "aadu valarthal"])

x("interest.poultry",
  en=["poultry", "chicken", "hen", "broiler", "layer", "egg", "eggs",
      "poultry farm", "country chicken", "duck", "quail"],
  ta=["கோழி", "கோழி வளர்ப்பு", "நாட்டுக்கோழி", "முட்டை", "வாத்து", "காடை",
      "kozhi", "kozhi valarppu", "naattu kozhi", "muttai", "vaathu"],
  hi=["मुर्गी", "मुर्गी पालन", "अंडा", "देसी मुर्गी", "बतख",
      "murgi", "murgi palan", "anda", "desi murgi", "batakh"],
  te=["కోడి", "కోళ్ల పెంపకం", "గుడ్డు", "నాటు కోడి",
      "kodi", "kolla pempakam", "guddu", "natu kodi"],
  kn=["ಕೋಳಿ", "ಕೋಳಿ ಸಾಕಾಣಿಕೆ", "ಮೊಟ್ಟೆ", "ನಾಟಿ ಕೋಳಿ",
      "koli", "koli sakanike", "motte", "nati koli"],
  ml=["കോഴി", "കോഴി വളർത്തൽ", "മുട്ട", "നാടൻ കോഴി",
      "kozhi", "kozhi valarthal", "mutta", "nadan kozhi"])

x("interest.farming",
  en=["farming", "agriculture", "cultivation", "crop", "crops", "paddy",
      "rice cultivation", "sugarcane", "cotton", "vegetables", "horticulture",
      "organic farming", "irrigation", "tractor work", "land", "field work",
      "coconut", "banana", "turmeric", "groundnut", "millet"],
  ta=["விவசாயம்", "வேளாண்மை", "பயிர்", "நெல்", "கரும்பு", "பருத்தி",
      "காய்கறி சாகுபடி", "தோட்டம்", "நிலம்", "வயல் வேலை", "தென்னை",
      "வாழை", "மஞ்சள்", "கடலை", "vivasayam", "velaanmai", "nel",
      "karumbu", "vayal vela", "thottam", "nilam"],
  hi=["खेती", "कृषि", "फसल", "धान", "गन्ना", "कपास", "सब्जी की खेती",
      "बागवानी", "जमीन", "खेत का काम", "मूंगफली", "हल्दी",
      "kheti", "krishi", "fasal", "dhan", "ganna", "khet ka kaam"],
  te=["వ్యవసాయం", "సాగు", "పంట", "వరి", "చెరకు", "పత్తి", "కూరగాయల సాగు",
      "పొలం", "భూమి", "vyavasayam", "sagu", "panta", "vari", "polam"],
  kn=["ಕೃಷಿ", "ವ್ಯವಸಾಯ", "ಬೆಳೆ", "ಭತ್ತ", "ಕಬ್ಬು", "ಹತ್ತಿ", "ತರಕಾರಿ ಬೆಳೆ",
      "ಹೊಲ", "ಜಮೀನು", "krishi", "vyavasaya", "bele", "bhatta", "hola"],
  ml=["കൃഷി", "വിള", "നെല്ല്", "കരിമ്പ്", "പരുത്തി", "പച്ചക്കറി കൃഷി",
      "പാടം", "ഭൂമി", "തെങ്ങ്", "വാഴ", "krishi", "vila", "nellu", "padam"])

x("interest.food",
  en=["cooking", "food", "food processing", "bakery", "baking", "catering",
      "hotel work", "restaurant", "chef", "cook", "snacks", "pickle",
      "papad", "sweets", "tiffin", "canteen", "mess", "food packing",
      "flour mill", "oil mill", "spices"],
  ta=["சமையல்", "சமைக்கிறேன்", "உணவு", "பேக்கரி", "கேட்டரிங்", "ஹோட்டல் வேலை",
      "தின்பண்டம்", "ஊறுகாய்", "அப்பளம்", "இனிப்பு", "டிபன்", "மாவு மில்",
      "samayal", "samaikkiren", "unavu", "bakery vela", "hotel vela",
      "oorugai", "appalam"],
  hi=["खाना बनाना", "खाना", "बेकरी", "कैटरिंग", "होटल का काम", "रसोइया",
      "अचार", "पापड़", "मिठाई", "नाश्ता", "आटा चक्की",
      "khana banana", "bakery ka kaam", "hotel ka kaam", "rasoiya",
      "achar", "papad", "mithai"],
  te=["వంట", "వంట చేయడం", "ఆహారం", "బేకరీ", "క్యాటరింగ్", "హోటల్ పని",
      "ఊరగాయ", "అప్పడం", "స్వీట్లు", "vanta", "aharam", "hotel pani"],
  kn=["ಅಡುಗೆ", "ಆಹಾರ", "ಬೇಕರಿ", "ಕ್ಯಾಟರಿಂಗ್", "ಹೋಟೆಲ್ ಕೆಲಸ", "ಉಪ್ಪಿನಕಾಯಿ",
      "ಹಪ್ಪಳ", "ಸಿಹಿತಿಂಡಿ", "aduge", "ahara", "hotel kelasa"],
  ml=["പാചകം", "ഭക്ഷണം", "ബേക്കറി", "കാറ്ററിംഗ്", "ഹോട്ടൽ ജോലി", "അച്ചാർ",
      "പപ്പടം", "മധുരം", "pachakam", "bhakshanam", "hotel joli"])

x("interest.tailor",
  en=["tailoring", "tailor", "stitching", "sewing", "sewing machine",
      "embroidery", "boutique", "garment stitching", "blouse stitching",
      "dress making", "fashion designing", "zari work", "aari work"],
  ta=["தையல்", "தையல் வேலை", "தச்சு வேலை", "தையல் மிஷின்", "எம்பிராய்டரி",
      "ஜரிகை வேலை", "ஆரி வேலை", "பிளவுஸ் தைக்கிறேன்", "புடவை தைக்கிறேன்",
      "thaiyal", "thaiyal vela", "thaiyal machine", "aari vela",
      "blouse thaikkiren"],
  hi=["सिलाई", "दर्जी", "सिलाई मशीन", "कढ़ाई", "कपड़े सिलना", "बुटीक",
      "जरी का काम", "silai", "darzi", "silai machine", "kadhai",
      "kapde silna"],
  te=["కుట్టు పని", "టైలరింగ్", "కుట్టు మిషన్", "ఎంబ్రాయిడరీ", "బట్టలు కుట్టడం",
      "kuttu pani", "tailoring", "kuttu machine", "battalu kuttadam"],
  kn=["ಹೊಲಿಗೆ", "ಟೈಲರಿಂಗ್", "ಹೊಲಿಗೆ ಯಂತ್ರ", "ಕಸೂತಿ", "ಬಟ್ಟೆ ಹೊಲಿಯುವುದು",
      "holige", "tailoring", "holige yantra", "kasuti"],
  ml=["തയ്യൽ", "ടെയ്‌ലറിംഗ്", "തയ്യൽ മെഷീൻ", "എംബ്രോയ്ഡറി", "വസ്ത്രം തയ്ക്കൽ",
      "thayyal", "thayyal machine", "vasthram thaykkal"])

x("interest.textile",
  en=["weaving", "handloom", "powerloom", "loom", "yarn", "spinning",
      "dyeing", "printing", "textile", "textile mill", "silk", "cotton mill",
      "saree weaving", "warping", "sizing", "knitting"],
  ta=["நெசவு", "கைத்தறி", "விசைத்தறி", "தறி", "நூல்", "சாயம்", "பட்டு",
      "புடவை நெசவு", "ஜவுளி", "nesavu", "kaithari", "visaithari", "thari",
      "nool", "sayam", "pattu", "javuli"],
  hi=["बुनाई", "हथकरघा", "पावरलूम", "करघा", "धागा", "रंगाई", "छपाई",
      "कपड़ा मिल", "रेशम", "bunai", "hathkargha", "powerloom", "kargha",
      "dhaga", "rangai", "kapda mill"],
  te=["నేత", "చేనేత", "మగ్గం", "దారం", "రంగులు", "పట్టు", "వస్త్ర",
      "netha", "chenetha", "maggam", "daram", "pattu"],
  kn=["ನೇಯ್ಗೆ", "ಕೈಮಗ್ಗ", "ಮಗ್ಗ", "ದಾರ", "ಬಣ್ಣ ಹಾಕುವುದು", "ರೇಷ್ಮೆ",
      "neyge", "kaimagga", "magga", "dara", "reshme"],
  ml=["നെയ്ത്ത്", "കൈത്തറി", "തറി", "നൂൽ", "ചായം", "പട്ട്",
      "neythu", "kaitharis", "thari", "nool", "chayam"])

x("interest.construction",
  en=["construction", "mason", "masonry", "building work", "civil work",
      "bar bending", "shuttering", "carpentry", "carpenter", "plumbing",
      "plumber", "painting", "painter", "tiles", "tile laying", "centring",
      "scaffolding", "site work", "contractor work", "cement work"],
  ta=["கட்டிட வேலை", "கொத்தனார்", "மேஸ்திரி", "தச்சு வேலை", "தச்சர்",
      "பிளம்பிங்", "பெயிண்டிங்", "டைல்ஸ்", "செண்டரிங்", "சிமெண்ட் வேலை",
      "kattida vela", "kothanar", "mesthiri", "thachu vela", "centering vela"],
  hi=["निर्माण", "राजमिस्त्री", "मिस्त्री", "बढ़ई", "प्लंबर", "पेंटर",
      "टाइल्स", "सेंटरिंग", "सीमेंट का काम", "बिल्डिंग का काम",
      "rajmistri", "mistri", "badhai", "plumber", "painter",
      "cement ka kaam", "building ka kaam"],
  te=["నిర్మాణం", "మేస్త్రీ", "తాపీ పని", "వడ్రంగి", "ప్లంబర్", "పెయింటర్",
      "సెంట్రింగ్", "nirmanam", "mestri", "tapi pani", "vadrangi"],
  kn=["ಕಟ್ಟಡ ಕೆಲಸ", "ಮೇಸ್ತ್ರಿ", "ಗಾರೆ ಕೆಲಸ", "ಬಡಗಿ", "ಪ್ಲಂಬರ್", "ಪೇಂಟರ್",
      "kattada kelasa", "mestri", "gare kelasa", "badagi"],
  ml=["നിർമ്മാണം", "മേസ്തിരി", "കൊത്തുപണി", "ആശാരി", "പ്ലംബർ", "പെയിന്റർ",
      "nirmanam", "mesthiri", "kothupani", "aashari"])

x("interest.machine",
  en=["machine", "machine operator", "welding", "welder", "fitter", "turner",
      "lathe", "mechanic", "motor mechanic", "auto mechanic", "electrician",
      "wiring", "electrical work", "electronics", "mobile repair",
      "ac repair", "refrigeration", "cnc", "workshop", "factory work",
      "assembly", "solar", "pump repair", "two wheeler mechanic"],
  ta=["மிஷின்", "இயந்திரம்", "வெல்டிங்", "பிட்டர்", "லேத்", "மெக்கானிக்",
      "எலெக்ட்ரீஷியன்", "வயரிங்", "மின் வேலை", "மொபைல் ரிப்பேர்",
      "ஏசி ரிப்பேர்", "பேக்டரி வேலை", "பட்டறை",
      "welding vela", "mechanic vela", "wiring vela", "min vela",
      "mobile repair", "pattarai"],
  hi=["मशीन", "वेल्डिंग", "फिटर", "खराद", "मैकेनिक", "इलेक्ट्रीशियन",
      "बिजली का काम", "वायरिंग", "मोबाइल रिपेयर", "एसी रिपेयर",
      "फैक्ट्री का काम", "वर्कशॉप", "machine ka kaam", "welding",
      "mechanic", "bijli ka kaam", "wiring"],
  te=["మిషన్", "వెల్డింగ్", "ఫిట్టర్", "మెకానిక్", "ఎలక్ట్రీషియన్",
      "వైరింగ్", "కరెంట్ పని", "మొబైల్ రిపేర్", "ఫ్యాక్టరీ పని",
      "welding pani", "mechanic pani", "current pani"],
  kn=["ಯಂತ್ರ", "ವೆಲ್ಡಿಂಗ್", "ಫಿಟ್ಟರ್", "ಮೆಕ್ಯಾನಿಕ್", "ಎಲೆಕ್ಟ್ರಿಷಿಯನ್",
      "ವೈರಿಂಗ್", "ಕರೆಂಟ್ ಕೆಲಸ", "ಮೊಬೈಲ್ ರಿಪೇರಿ", "ಫ್ಯಾಕ್ಟರಿ ಕೆಲಸ",
      "welding kelasa", "mechanic kelasa", "current kelasa"],
  ml=["മെഷീൻ", "വെൽഡിംഗ്", "ഫിറ്റർ", "മെക്കാനിക്ക്", "ഇലക്ട്രീഷ്യൻ",
      "വയറിംഗ്", "കറന്റ് പണി", "മൊബൈൽ റിപ്പയർ", "ഫാക്ടറി ജോലി",
      "welding pani", "mechanic pani", "current pani"])

# ── gaps found by tools/check_answers.py ─────────────────────────────────
# These came from testing realistic spoken answers rather than dictionary
# words. Note the apostrophes are left in: fold() rewrites "didn't" to
# "didn t" for BOTH the stored form and the utterance, so writing the form
# naturally is correct and storing a pre-mangled version would be wrong.

x("edu.none",
  en=["didn't go to school", "didnt go to school", "never went to school",
      "never been to school", "did not go to school", "no school at all",
      "haven't studied", "havent studied", "i have not studied",
      "not educated", "illiterate"],
  ta=["ஸ்கூலுக்கு போகவே இல்ல", "படிக்கவே இல்ல", "schoolukku pogave illa"],
  hi=["स्कूल कभी नहीं गया", "पढ़ाई नहीं हुई", "school kabhi nahi gaya"],
  te=["బడికి వెళ్లలేదు", "చదువుకోలేదు", "badiki vellaledu", "chaduvukoledu"],
  kn=["ಶಾಲೆಗೆ ಹೋಗಿಲ್ಲ", "ಓದಿಕೊಂಡಿಲ್ಲ", "shalege hogilla"],
  ml=["സ്കൂളിൽ പോയിട്ടില്ല", "പഠിച്ചിട്ടില്ല", "schoolil poyittilla"])

# "only in my village" -- the *only* is carried by a separate word in Telugu
# (మాత్రమే) and Kannada (ಮಾತ್ರ), and those pairings were missing.
x("mob.local",
  te=["మా ఊర్లో మాత్రమే", "ఊర్లో మాత్రమే", "మా ఊరిలో", "మా ఊరిలోనే",
      "ఇక్కడే", "ఇక్కడ మాత్రమే", "మా గ్రామంలో", "మా గ్రామంలోనే",
      "ma oorlo matrame", "ikkade", "ma gramamlo"],
  kn=["ನಮ್ಮ ಊರಲ್ಲಿ ಮಾತ್ರ", "ಊರಲ್ಲಿ ಮಾತ್ರ", "ನಮ್ಮ ಹಳ್ಳಿಯಲ್ಲಿ",
      "ನಮ್ಮ ಹಳ್ಳಿಯಲ್ಲೇ", "ಇಲ್ಲೇ", "ಇಲ್ಲಿ ಮಾತ್ರ", "ನಮ್ಮ ಊರಿನಲ್ಲಿ",
      "namma ooralli matra", "illey", "namma halliyalli"],
  ta=["என் ஊர்ல மட்டுமே", "இங்க மட்டும்", "இங்கயே", "எங்க கிராமத்துல",
      "inga mattum", "engaye", "enga gramathula"],
  ml=["എന്റെ നാട്ടിൽ മാത്രം", "ഇവിടെ മാത്രം", "ഇവിടെത്തന്നെ",
      "ente nattil mathram", "ivide mathram"],
  hi=["सिर्फ अपने गाँव में", "यहीं पर", "इसी गाँव में",
      "sirf apne gaon mein", "isi gaon mein"],
  en=["only in my village", "in my village only", "just my village",
      "only around here", "right here only", "same village only"])

# "anywhere in THE district" -- the stored phrase omitted the article, and a
# phrase match is a plain substring, so the article broke it and only the bare
# word "anywhere" matched, promoting a district answer to state.
x("mob.district",
  en=["anywhere in the district", "anywhere in my district",
      "any place in the district", "all over the district",
      "anywhere within the district", "around the district",
      "in and around the district", "district wide"])

# Contractions: fold() rewrites "can't" to "can t" for the stored form and the
# utterance alike, so the apostrophe spelling has to be present as well.
x("mob.local",
  en=["can't travel", "can't go far", "can't go outside", "can't move around",
      "unable to travel", "not able to travel", "travel is difficult",
      "small children at home", "children at home", "have to be at home",
      "someone at home to look after", "cannot leave home"])


# ── in-progress study ────────────────────────────────────────────────────
# "college 2nd year" used to resolve to `graduate`, i.e. a completed degree.
# That is wrong in the expensive direction: graduate is the top education
# rank, so the recommender judged a second-year student eligible for courses
# that require a finished degree. Someone mid-course has *completed* the
# level below and is currently a student, and both facts matter.
#
# Bare "2nd year" / "second year" were not understood at all.

x("marker.in_progress",
  en=["1st year", "first year", "2nd year", "second year", "3rd year",
      "third year", "4th year", "fourth year", "5th year", "final year",
      "last year of", "pre final year", "prefinal year", "semester",
      "1st sem", "2nd sem", "3rd sem", "4th sem", "5th sem", "6th sem",
      "studying", "still studying", "currently studying", "pursuing",
      "doing my", "i am doing", "appearing", "appearing for",
      "in college", "in school", "going to college", "going to school",
      "course is going on", "not yet completed", "yet to complete",
      "will finish", "halfway", "ongoing"],
  ta=["முதல் வருடம்", "இரண்டாம் வருடம்", "மூன்றாம் வருடம்", "இறுதி வருடம்",
      "படிச்சிட்டு இருக்கேன்", "படிக்கிறேன்", "இன்னும் முடியல",
      "முடிக்கல", "செமஸ்டர்", "கல்லூரியில படிக்கிறேன்",
      "first year padikkiren", "second year padikkiren",
      "innum mudiyala", "padichittu irukken", "college la padikkiren"],
  hi=["पहला साल", "दूसरा साल", "तीसरा साल", "आखिरी साल", "पढ़ रहा हूँ",
      "पढ़ रही हूँ", "अभी चल रहा है", "पूरा नहीं हुआ", "सेमेस्टर",
      "कॉलेज में पढ़ रहा हूँ", "padh raha hoon", "abhi chal raha hai",
      "pura nahi hua", "doosra saal"],
  te=["మొదటి సంవత్సరం", "రెండవ సంవత్సరం", "మూడవ సంవత్సరం", "చివరి సంవత్సరం",
      "చదువుతున్నాను", "ఇంకా పూర్తి కాలేదు", "సెమిస్టర్",
      "chaduvutunnanu", "inka purthi kaledu", "rendava samvatsaram"],
  kn=["ಮೊದಲ ವರ್ಷ", "ಎರಡನೇ ವರ್ಷ", "ಮೂರನೇ ವರ್ಷ", "ಕೊನೆಯ ವರ್ಷ",
      "ಓದುತ್ತಿದ್ದೇನೆ", "ಇನ್ನೂ ಮುಗಿದಿಲ್ಲ", "ಸೆಮಿಸ್ಟರ್",
      "oduttiddene", "innu mugidilla", "eradane varsha"],
  ml=["ഒന്നാം വർഷം", "രണ്ടാം വർഷം", "മൂന്നാം വർഷം", "അവസാന വർഷം",
      "പഠിക്കുന്നു", "ഇനിയും കഴിഞ്ഞിട്ടില്ല", "സെമസ്റ്റർ",
      "padikkunnu", "randaam varsham", "iniyum kazhinjittilla"])

# Dropping out is not the same as finishing, and not the same as still
# being enrolled. "12th dropout" completed Class 10, and is not a student.
x("marker.dropout",
  en=["dropout", "drop out", "dropped out", "discontinued", "left studies",
      "left college", "left school", "stopped studying", "could not continue",
      "had to stop", "failed and stopped", "incomplete"],
  ta=["படிப்பை நிறுத்திட்டேன்", "பாதியில நிறுத்திட்டேன்", "கல்லூரியை விட்டுட்டேன்",
      "தொடர முடியல", "padippai niruthitten", "pathiyila niruthitten"],
  hi=["पढ़ाई छोड़ दी", "बीच में छोड़ दिया", "जारी नहीं रख सका",
      "padhai chod di", "beech mein chod diya"],
  te=["చదువు మానేశాను", "మధ్యలో ఆపేశాను", "chaduvu manesanu"],
  kn=["ಓದು ನಿಲ್ಲಿಸಿದೆ", "ಮಧ್ಯದಲ್ಲಿ ಬಿಟ್ಟೆ", "odu nilliside"],
  ml=["പഠനം നിർത്തി", "ഇടയ്ക്ക് നിർത്തി", "padanam nirthi"])


# ── engineering and technical vocabulary ─────────────────────────────────
# Degree-level engineering. These belong to `graduate` only when finished;
# combined with marker.in_progress the engine downgrades them correctly.
x("edu.graduate",
  en=["be", "b e", "b.e", "btech", "b tech", "b.tech", "bachelor of engineering",
      "engineering degree", "engineering graduate", "mtech", "m tech", "me",
      "mechanical engineering", "civil engineering", "electrical engineering",
      "electronics engineering", "computer science", "cse", "ece", "eee",
      "information technology", "automobile engineering", "mechatronics",
      "instrumentation", "chemical engineering", "aeronautical",
      "production engineering", "industrial engineering", "agricultural engineering"],
  ta=["பொறியியல் பட்டம்", "இன்ஜினியரிங் முடிச்சேன்", "மெக்கானிக்கல் இன்ஜினியரிங்",
      "சிவில் இன்ஜினியரிங்", "engineering mudichen", "be mudichen"],
  hi=["इंजीनियरिंग की डिग्री", "बी टेक किया", "मैकेनिकल इंजीनियरिंग",
      "सिविल इंजीनियरिंग", "engineering ki degree", "btech kiya"],
  te=["ఇంజనీరింగ్ డిగ్రీ", "బీటెక్ చేశాను", "engineering chesanu"],
  kn=["ಇಂಜಿನಿಯರಿಂಗ್ ಪದವಿ", "ಬಿಟೆಕ್ ಮಾಡಿದೆ", "engineering madide"],
  ml=["എൻജിനീയറിംഗ് ബിരുദം", "ബിടെക് കഴിഞ്ഞു", "engineering kazhinju"])

# ITI / polytechnic trades, as DGT names them. People answer with the trade,
# not the certificate: "I did fitter", "turner trade".
x("edu.iti_diploma",
  en=["fitter", "turner", "machinist", "machinist grinder", "tool and die maker",
      "draughtsman civil", "draughtsman mechanical", "surveyor",
      "electronics mechanic", "instrument mechanic", "mechanic diesel",
      "mechanic motor vehicle", "motor vehicle mechanic", "diesel mechanic",
      "refrigeration and air conditioning", "rac technician", "wireman",
      "sheet metal worker", "foundryman", "pattern maker", "lineman",
      "computer operator and programming assistant", "copa",
      "mechanic agricultural machinery", "dress making iti",
      "polytechnic diploma", "civil diploma", "mech diploma",
      "diploma in engineering", "dee", "dce", "dmech"],
  ta=["பிட்டர் டிரேட்", "டர்னர் டிரேட்", "வயர்மேன்", "டிராப்ட்ஸ்மேன்",
      "fitter trade mudichen", "turner trade", "copa mudichen"],
  hi=["फिटर ट्रेड", "टर्नर ट्रेड", "वायरमैन", "ड्राफ्ट्समैन", "कोपा",
      "fitter trade kiya", "copa kiya"],
  te=["ఫిట్టర్ ట్రేడ్", "టర్నర్ ట్రేడ్", "వైర్‌మెన్", "fitter trade chesanu"],
  kn=["ಫಿಟ್ಟರ್ ಟ್ರೇಡ್", "ಟರ್ನರ್ ಟ್ರೇಡ್", "ವೈರ್‌ಮನ್", "fitter trade madide"],
  ml=["ഫിറ്റർ ട്രേഡ്", "ടർണർ ട്രേഡ്", "വയർമാൻ", "fitter trade cheythu"])

# Shop-floor and technical skills, for the interests/skills slot.
x("interest.machine",
  en=["cnc", "cnc operator", "cnc programming", "vmc", "lathe operator",
      "milling", "grinding", "drilling", "boring", "press operator",
      "injection moulding", "die casting", "sheet metal", "fabrication",
      "arc welding", "mig welding", "tig welding", "gas cutting",
      "soldering", "brazing", "pcb assembly", "smt", "wire harness",
      "motor rewinding", "transformer winding", "armature winding",
      "panel wiring", "switchgear", "plc", "scada", "vfd", "drives",
      "hydraulics", "pneumatics", "bearing", "gearbox", "pump repair",
      "compressor", "boiler", "turbine", "hvac", "chiller",
      "solar panel installation", "solar technician", "inverter repair",
      "battery", "ups repair", "cctv installation", "networking",
      "computer hardware", "laptop repair", "printer repair",
      "autocad", "auto cad", "cad", "cam", "3d printing", "cnc lathe",
      "quality inspection", "vernier", "micrometer", "measuring instruments",
      "preventive maintenance", "breakdown maintenance", "tool room",
      "assembly line", "production operator", "machine operator",
      "earth moving", "jcb operator", "crane operator", "forklift"],
  ta=["சிஎன்சி", "லேத் ஆபரேட்டர்", "வெல்டிங் வேலை", "மோட்டார் ரீவைண்டிங்",
      "பேனல் வயரிங்", "சோலார் பேனல்", "இன்வெர்ட்டர் ரிப்பேர்",
      "சிசிடிவி", "கம்ப்யூட்டர் ஹார்டுவேர்", "ஆட்டோகேட்", "ஜேசிபி",
      "cnc operator", "lathe vela", "motor rewinding", "panel wiring",
      "solar panel", "jcb ottuten", "autocad theriyum"],
  hi=["सीएनसी", "खराद ऑपरेटर", "वेल्डिंग का काम", "मोटर रिवाइंडिंग",
      "पैनल वायरिंग", "सोलर पैनल", "इन्वर्टर रिपेयर", "सीसीटीवी",
      "कंप्यूटर हार्डवेयर", "ऑटोकैड", "जेसीबी",
      "cnc operator", "motor rewinding", "solar panel ka kaam"],
  te=["సీఎన్‌సీ", "లేత్ ఆపరేటర్", "మోటార్ రీవైండింగ్", "ప్యానెల్ వైరింగ్",
      "సోలార్ ప్యానెల్", "సీసీటీవీ", "ఆటోకాడ్",
      "cnc operator", "motor rewinding", "solar panel pani"],
  kn=["ಸಿಎನ್‌ಸಿ", "ಲೇತ್ ಆಪರೇಟರ್", "ಮೋಟಾರ್ ರಿವೈಂಡಿಂಗ್", "ಪ್ಯಾನೆಲ್ ವೈರಿಂಗ್",
      "ಸೋಲಾರ್ ಪ್ಯಾನೆಲ್", "ಸಿಸಿಟಿವಿ", "ಆಟೋಕ್ಯಾಡ್",
      "cnc operator", "motor rewinding", "solar panel kelasa"],
  ml=["സിഎൻസി", "ലേത്ത് ഓപ്പറേറ്റർ", "മോട്ടോർ റീവൈൻഡിംഗ്", "പാനൽ വയറിംഗ്",
      "സോളാർ പാനൽ", "സിസിടിവി", "ഓട്ടോകാഡ്",
      "cnc operator", "motor rewinding", "solar panel joli"])


# Native-script spellings of the English loanwords people actually use.
# "இரண்டாம் வருடம் காலேஜ்" was not understood because only the Latin
# "college" was stored -- but a Tamil speaker's recogniser writes காலேஜ்.
x("edu.graduate",
  ta=["காலேஜ்", "காலேஜ்ல", "கல்லூரி படிக்கிறேன்", "டிகிரி காலேஜ்"],
  hi=["कॉलेज", "कालेज", "डिग्री कॉलेज"],
  te=["కాలేజీ", "కాలేజ్", "డిగ్రీ కాలేజీ"],
  kn=["ಕಾಲೇಜು", "ಕಾಲೇಜ್", "ಡಿಗ್ರಿ ಕಾಲೇಜು"],
  ml=["കോളേജ്", "കോളജ്", "ഡിഗ്രി കോളേജ്"])
