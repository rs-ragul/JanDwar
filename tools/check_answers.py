#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Answer coverage: does the offline engine understand what people actually say?

`check_nlu.py` audits the lexicon against *itself* -- every surface form must
resolve to its own detector. That proves internal consistency and nothing
about real speech, because it only ever feeds the engine words it already
knows.

This file asks the opposite question, and it is the one that matters in the
field: **for each question the interview asks, is a realistic spoken answer
understood?** The utterances below are written as a person would say them --
full sentences, code-mixed, contracted, hedged, negated, with the filler and
politeness that real answers carry -- not as bare dictionary entries. None of
them were copied from the lexicon.

A slot counts as understood only if the engine fills *that* slot when the
question was asked. Filling some other slot does not count: answering "how
far did you study?" by populating `interests` still leaves the interview
re-asking a question the person already answered.

Run:  python3 tools/check_answers.py
"""

import os
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.normpath(os.path.join(_HERE, "..", "server")))

from app.core import nlu as N          # noqa: E402
from app.core.nlu import Fragment      # noqa: E402

S = Fragment.Slot if hasattr(Fragment, "Slot") else None

# ── the corpus ───────────────────────────────────────────────────────────
# slot -> lang -> [utterances a real person would speak]

CORPUS = {
"EDUCATION": {
 "en": ["i studied till 10th", "i have done my sslc", "only 5th standard sir",
        "i didn't go to school at all", "completed my iti in fitter trade",
        "i am a bsc graduate", "plus two finished, then stopped",
        "i failed in 12th", "studied up to 8th only", "no schooling madam"],
 "ta": ["நான் பத்தாம் வகுப்பு வரை படிச்சிருக்கேன்", "எஸ்எஸ்எல்சி முடிச்சேன்",
        "அஞ்சு வரைதான் படிச்சேன்", "நான் ஸ்கூலே போகல",
        "ஐடிஐ முடிச்சிருக்கேன் சார்", "டிகிரி முடிச்சேன்",
        "பிளஸ் டூ முடிச்சுட்டு நிறுத்திட்டேன்", "எட்டாம் வகுப்பு வரைதான்",
        "padichathu pathu varai thaan", "sslc mudichen sir",
        "onnum padikkala amma", "iti fitter mudichen"],
 "hi": ["मैंने दसवीं तक पढ़ाई की है", "बारहवीं पास हूँ", "सिर्फ पाँचवीं तक पढ़ा",
        "मैं स्कूल नहीं गया", "आईटीआई किया है", "मैं ग्रेजुएट हूँ",
        "आठवीं में छोड़ दिया", "मैट्रिक पास हूँ साहब",
        "dasvi tak padha hoon", "kuch nahi padha sir"],
 "te": ["నేను పదవ తరగతి వరకు చదివాను", "ఇంటర్ పాస్ అయ్యాను",
        "ఐదవ తరగతి వరకే చదివాను", "నేను బడికి వెళ్లలేదు",
        "ఐటిఐ చేశాను", "డిగ్రీ పూర్తి చేశాను",
        "padi varaku chadivanu", "emi chadavaledu sir"],
 "kn": ["ನಾನು ಹತ್ತನೇ ತರಗತಿ ವರೆಗೆ ಓದಿದ್ದೇನೆ", "ಪಿಯುಸಿ ಮುಗಿಸಿದ್ದೇನೆ",
        "ಐದನೇ ಕ್ಲಾಸ್ ವರೆಗೆ ಮಾತ್ರ", "ನಾನು ಶಾಲೆಗೆ ಹೋಗಿಲ್ಲ",
        "ಐಟಿಐ ಮಾಡಿದ್ದೇನೆ", "ಡಿಗ್ರಿ ಮುಗಿಸಿದೆ",
        "sslc pass agide", "enu odilla sir"],
 "ml": ["ഞാൻ പത്താം ക്ലാസ് വരെ പഠിച്ചു", "പ്ലസ് ടു കഴിഞ്ഞു",
        "അഞ്ചാം ക്ലാസ് വരെ മാത്രം", "ഞാൻ സ്കൂളിൽ പോയിട്ടില്ല",
        "ഐടിഐ ചെയ്തിട്ടുണ്ട്", "ഡിഗ്രി കഴിഞ്ഞു",
        "pathaam class vare padichu", "onnum padichittilla"],
},

# Mid-course and dropped-out answers. These are separated out because the
# assertion is about the *completed* level, which is not the level the
# sentence names: "college 2nd year" names a degree and has completed Class
# 12. Getting this wrong puts the person at the wrong NSQF entry rank.
"EDUCATION_IN_PROGRESS": {
 "en": ["college 2nd year", "college second year", "i am in 2nd year college",
        "2nd year engineering", "B.Tech 3rd year", "final year btech",
        "1st year polytechnic", "diploma 2nd year", "12th dropout",
        "college dropout", "studying in 10th"],
 "ta": ["இரண்டாம் வருடம் காலேஜ்", "காலேஜ் முதல் வருடம்",
        "naan college 2nd year", "college la 2nd year"],
 "hi": ["कॉलेज दूसरा साल", "college 2nd year padh raha hoon"],
 "te": ["కాలేజీ రెండవ సంవత్సరం"],
 "kn": ["ಕಾಲೇಜು ಎರಡನೇ ವರ್ಷ"],
 "ml": ["കോളേജ് രണ്ടാം വർഷം"],
},

"FAMILY_OCCUPATION": {
 "en": ["my father is a farmer", "we do weaving at home", "my family rears goats",
        "parents do daily wage work", "our family runs a small shop",
        "father used to be a fisherman", "my mother does tailoring",
        "we have cattle at home", "traditionally we are potters"],
 "ta": ["எங்க அப்பா விவசாயம் பண்றார்", "வீட்ல நெசவு வேலை பண்றோம்",
        "எங்க குடும்பம் ஆடு வளர்க்குது", "அப்பா அம்மா கூலி வேலைக்கு போறாங்க",
        "எங்களுக்கு சின்ன கடை இருக்கு", "அப்பா மீன் பிடிக்கிற வேலை",
        "அம்மா தையல் வேலை பண்றாங்க", "வீட்ல மாடு வளர்க்கிறோம்",
        "enga appa vivasayam pandraar", "veetla nesavu vela"],
 "hi": ["मेरे पिता किसान हैं", "हम घर पर बुनाई करते हैं",
        "हमारा परिवार बकरी पालता है", "माँ बाप मजदूरी करते हैं",
        "हमारी छोटी दुकान है", "पिताजी मछली पकड़ते थे",
        "माँ सिलाई करती है", "ghar mein kheti karte hain"],
 "te": ["మా నాన్న రైతు", "మేము ఇంట్లో నేత పని చేస్తాము",
        "మా కుటుంబం మేకలు పెంచుతుంది", "అమ్మా నాన్న కూలి పని చేస్తారు",
        "మాకు చిన్న దుకాణం ఉంది", "అమ్మ కుట్టు పని చేస్తుంది",
        "ma nanna vyavasayam chestaru"],
 "kn": ["ನಮ್ಮ ಅಪ್ಪ ರೈತ", "ನಾವು ಮನೆಯಲ್ಲಿ ನೇಯ್ಗೆ ಮಾಡುತ್ತೇವೆ",
        "ನಮ್ಮ ಕುಟುಂಬ ಮೇಕೆ ಸಾಕುತ್ತದೆ", "ಅಪ್ಪ ಅಮ್ಮ ಕೂಲಿ ಕೆಲಸ ಮಾಡುತ್ತಾರೆ",
        "ನಮಗೆ ಸಣ್ಣ ಅಂಗಡಿ ಇದೆ", "namma appa krishi madtare"],
 "ml": ["എന്റെ അച്ഛൻ കർഷകനാണ്", "ഞങ്ങൾ വീട്ടിൽ നെയ്ത്ത് ചെയ്യുന്നു",
        "ഞങ്ങളുടെ കുടുംബം ആട് വളർത്തുന്നു", "അച്ഛനും അമ്മയും കൂലി പണി ചെയ്യുന്നു",
        "ഞങ്ങൾക്ക് ചെറിയ കട ഉണ്ട്", "ente achan krishi cheyyunnu"],
},

"CURRENT_LIVELIHOOD": {
 "en": ["right now i drive an auto", "i am doing coolie work these days",
        "currently i have no work", "i work in a garment factory",
        "i am still studying", "i sell vegetables in the market",
        "doing construction work now", "i lost my job last month",
        "i do tailoring from home", "working as a helper in a hotel"],
 "ta": ["இப்போ ஆட்டோ ஓட்டுறேன்", "இப்போதைக்கு கூலி வேலை பண்றேன்",
        "இப்போ வேலை ஒன்னும் இல்ல", "கார்மென்ட் கம்பெனில வேலை பாக்கிறேன்",
        "நான் இன்னும் படிச்சிட்டு இருக்கேன்", "காய்கறி விக்கிறேன்",
        "கட்டிட வேலைக்கு போறேன்", "வேலை போயிடுச்சு",
        "veetla irunthu thaiyal pandren", "hotel la helper ah irukken"],
 "hi": ["अभी मैं ऑटो चलाता हूँ", "आजकल मजदूरी कर रहा हूँ",
        "अभी कोई काम नहीं है", "कपड़े की फैक्ट्री में काम करता हूँ",
        "मैं अभी पढ़ रहा हूँ", "सब्जी बेचता हूँ",
        "निर्माण का काम करता हूँ", "abhi koi kaam nahi hai"],
 "te": ["ఇప్పుడు ఆటో నడుపుతున్నాను", "ఇప్పుడు కూలి పని చేస్తున్నాను",
        "ప్రస్తుతం పని లేదు", "ఫ్యాక్టరీలో పని చేస్తున్నాను",
        "నేను ఇంకా చదువుతున్నాను", "కూరగాయలు అమ్ముతాను",
        "ippudu pani ledu sir"],
 "kn": ["ಈಗ ಆಟೋ ಓಡಿಸುತ್ತೇನೆ", "ಈಗ ಕೂಲಿ ಕೆಲಸ ಮಾಡುತ್ತಿದ್ದೇನೆ",
        "ಸದ್ಯಕ್ಕೆ ಕೆಲಸ ಇಲ್ಲ", "ಫ್ಯಾಕ್ಟರಿಯಲ್ಲಿ ಕೆಲಸ ಮಾಡುತ್ತೇನೆ",
        "ನಾನು ಇನ್ನೂ ಓದುತ್ತಿದ್ದೇನೆ", "eega kelasa illa"],
 "ml": ["ഇപ്പോൾ ഓട്ടോ ഓടിക്കുന്നു", "ഇപ്പോൾ കൂലി പണി ചെയ്യുന്നു",
        "ഇപ്പോൾ ജോലി ഒന്നും ഇല്ല", "ഫാക്ടറിയിൽ ജോലി ചെയ്യുന്നു",
        "ഞാൻ ഇപ്പോഴും പഠിക്കുന്നു", "ippo joli onnum illa"],
},

"INTERESTS": {
 "en": ["i am interested in dairy farming", "i would like to learn tailoring",
        "i want to do goat rearing", "something with machines and welding",
        "poultry would be good for me", "i like cooking, maybe catering",
        "construction or mason work", "anything in agriculture",
        "i want to learn mobile repairing", "weaving, like my family does"],
 "ta": ["எனக்கு பால் பண்ணை ஆர்வம் இருக்கு", "தையல் கத்துக்கணும்",
        "ஆடு வளர்ப்பு பண்ணனும்", "மிஷின் வேலை, வெல்டிங் மாதிரி",
        "கோழி வளர்ப்பு நல்லா இருக்கும்", "சமையல் பிடிக்கும், கேட்டரிங்",
        "கட்டிட வேலை", "விவசாயம் சம்பந்தமா ஏதாவது",
        "mobile repair kathukkanum", "nesavu vela pidikkum"],
 "hi": ["मुझे डेयरी का काम पसंद है", "सिलाई सीखना चाहता हूँ",
        "बकरी पालन करना है", "मशीन और वेल्डिंग का काम",
        "मुर्गी पालन अच्छा रहेगा", "खाना बनाना पसंद है",
        "राजमिस्त्री का काम", "खेती से जुड़ा कुछ भी",
        "mobile repairing seekhna hai"],
 "te": ["నాకు పాడి పరిశ్రమ ఇష్టం", "కుట్టు పని నేర్చుకోవాలి",
        "మేకల పెంపకం చేయాలి", "మెషిన్ వెల్డింగ్ పని",
        "కోళ్ల పెంపకం బాగుంటుంది", "వంట చేయడం ఇష్టం",
        "vyavasayam sambandhinchina edaina"],
 "kn": ["ನನಗೆ ಹೈನುಗಾರಿಕೆ ಇಷ್ಟ", "ಹೊಲಿಗೆ ಕಲಿಯಬೇಕು",
        "ಮೇಕೆ ಸಾಕಾಣಿಕೆ ಮಾಡಬೇಕು", "ಮೆಷಿನ್ ವೆಲ್ಡಿಂಗ್ ಕೆಲಸ",
        "ಕೋಳಿ ಸಾಕಾಣಿಕೆ ಒಳ್ಳೆಯದು", "ಅಡುಗೆ ಇಷ್ಟ"],
 "ml": ["എനിക്ക് ക്ഷീര കൃഷി ഇഷ്ടമാണ്", "തയ്യൽ പഠിക്കണം",
        "ആട് വളർത്തൽ ചെയ്യണം", "മെഷീൻ വെൽഡിംഗ് ജോലി",
        "കോഴി വളർത്തൽ നല്ലതാണ്", "പാചകം ഇഷ്ടമാണ്"],
},

"PREFERENCE": {
 "en": ["i want to start my own business", "i would prefer a company job",
        "own shop would be better", "salary job is safer for me",
        "i want to be self employed", "any job with monthly pay",
        "i'd rather work for myself", "government job if possible"],
 "ta": ["எனக்கு சொந்த தொழில் ஆரம்பிக்கணும்", "கம்பெனி வேலை தான் வேணும்",
        "சொந்தமா கடை போட்டா நல்லா இருக்கும்", "மாச சம்பளம் வர்ற வேலை பாதுகாப்பு",
        "சுயதொழில் பண்ணனும்", "எந்த வேலையா இருந்தாலும் சம்பளம் வேணும்",
        "sontha thozhil venum", "company vela thaan venum"],
 "hi": ["मुझे अपना धंधा शुरू करना है", "मुझे कंपनी की नौकरी चाहिए",
        "अपनी दुकान बेहतर रहेगी", "तनख्वाह वाली नौकरी सुरक्षित है",
        "स्वरोजगार करना है", "apna kaam karna hai"],
 "te": ["నాకు సొంత వ్యాపారం ప్రారంభించాలి", "కంపెనీ ఉద్యోగం కావాలి",
        "సొంత దుకాణం మంచిది", "నెల జీతం ఉన్న ఉద్యోగం",
        "sontha vyaparam cheyyali"],
 "kn": ["ನಾನು ಸ್ವಂತ ವ್ಯಾಪಾರ ಶುರು ಮಾಡಬೇಕು", "ಕಂಪನಿ ಕೆಲಸ ಬೇಕು",
        "ಸ್ವಂತ ಅಂಗಡಿ ಒಳ್ಳೆಯದು", "ತಿಂಗಳ ಸಂಬಳ ಇರುವ ಕೆಲಸ"],
 "ml": ["എനിക്ക് സ്വന്തം ബിസിനസ് തുടങ്ങണം", "കമ്പനി ജോലി വേണം",
        "സ്വന്തം കട ആയിരിക്കും നല്ലത്", "മാസ ശമ്പളമുള്ള ജോലി"],
},

"MOBILITY": {
 "en": ["only in my village please", "i can go anywhere in the district",
        "i am ready to travel anywhere", "i cannot go far from home",
        "somewhere near my house", "hostel is fine, i can stay outside",
        "within my taluk is okay", "i can't travel, small children at home"],
 "ta": ["என் ஊர்ல மட்டும் தான்", "மாவட்டம் முழுக்க போகலாம்",
        "எங்க வேணும்னாலும் போக தயார்", "வீட்டை விட்டு தூரம் போக முடியாது",
        "வீட்டுக்கு பக்கத்துல இருந்தா நல்லது", "ஹாஸ்டல் இருந்தா சரி",
        "veliyoor poga mudiyathu", "oorla mattum thaan"],
 "hi": ["सिर्फ मेरे गाँव में", "पूरे जिले में जा सकता हूँ",
        "कहीं भी जाने को तैयार हूँ", "घर से दूर नहीं जा सकता",
        "घर के पास हो तो अच्छा", "हॉस्टल चलेगा",
        "bahar nahi ja sakta"],
 "te": ["మా ఊర్లో మాత్రమే", "జిల్లా అంతా వెళ్లగలను",
        "ఎక్కడికైనా వెళ్లడానికి సిద్ధం", "ఇంటికి దూరంగా వెళ్లలేను",
        "inti daggara ayite manchidi"],
 "kn": ["ನಮ್ಮ ಊರಲ್ಲಿ ಮಾತ್ರ", "ಜಿಲ್ಲೆ ಪೂರ್ತಿ ಹೋಗಬಲ್ಲೆ",
        "ಎಲ್ಲಿಗಾದರೂ ಹೋಗಲು ಸಿದ್ಧ", "ಮನೆಯಿಂದ ದೂರ ಹೋಗಲಾರೆ"],
 "ml": ["എന്റെ നാട്ടിൽ മാത്രം", "ജില്ല മുഴുവൻ പോകാം",
        "എവിടെയും പോകാൻ തയ്യാറാണ്", "വീട്ടിൽ നിന്ന് ദൂരെ പോകാൻ പറ്റില്ല"],
},

"CONSTRAINTS": {
 "en": ["no problem at all", "i have back pain, can't lift heavy",
        "nothing like that", "i had an operation last year",
        "my eyesight is weak", "i cannot stand for long hours",
        "no health issues", "i am differently abled",
        "i have asthma", "knee pain since two years"],
 "ta": ["ஒரு பிரச்சனையும் இல்ல", "முதுகு வலி இருக்கு, கனம் தூக்க முடியாது",
        "அப்படி ஒன்னும் இல்ல", "போன வருஷம் ஆபரேஷன் ஆச்சு",
        "கண் தெரியல சரியா", "ரொம்ப நேரம் நிக்க முடியாது",
        "உடம்பு சரியில்ல", "prachanai onnum illa"],
 "hi": ["कोई दिक्कत नहीं है", "कमर में दर्द है, भारी नहीं उठा सकता",
        "ऐसा कुछ नहीं", "पिछले साल ऑपरेशन हुआ था",
        "आँखों से कम दिखता है", "ज्यादा देर खड़ा नहीं हो सकता",
        "koi problem nahi"],
 "te": ["ఎలాంటి సమస్య లేదు", "నడుము నొప్పి ఉంది, బరువు ఎత్తలేను",
        "అలాంటిది ఏమీ లేదు", "గత సంవత్సరం ఆపరేషన్ అయింది",
        "samasya emi ledu"],
 "kn": ["ಯಾವುದೇ ತೊಂದರೆ ಇಲ್ಲ", "ಸೊಂಟ ನೋವು ಇದೆ, ಭಾರ ಎತ್ತಲಾರೆ",
        "ಹಾಗೇನೂ ಇಲ್ಲ", "ಕಳೆದ ವರ್ಷ ಆಪರೇಷನ್ ಆಯಿತು"],
 "ml": ["ഒരു പ്രശ്നവും ഇല്ല", "നടുവേദന ഉണ്ട്, ഭാരം എടുക്കാൻ പറ്റില്ല",
        "അങ്ങനെ ഒന്നും ഇല്ല", "കഴിഞ്ഞ വർഷം ഓപ്പറേഷൻ ആയിരുന്നു"],
},
"STATE": {
 "en": ["i live in tamil nadu", "kerala", "we are from karnataka",
        "andhra pradesh sir", "uttar pradesh", "TN", "AP", "UP",
        "my village is in andhra", "keralam"],
 "ta": ["\u0ba4\u0bae\u0bbf\u0bb4\u0bcd\u0ba8\u0bbe\u0b9f\u0bc1", "\u0ba8\u0bbe\u0ba9\u0bcd \u0b95\u0bc7\u0bb0\u0bb3\u0bbe\u0bb5\u0bbf\u0bb2\u0bcd \u0bb5\u0bb8\u0bbf\u0b95\u0bcd\u0b95\u0bbf\u0bb1\u0bc7\u0ba9\u0bcd",
        "\u0b95\u0bb0\u0bcd\u0ba8\u0bbe\u0b9f\u0b95\u0bbe \u0bae\u0bbe\u0ba8\u0bbf\u0bb2\u0bae\u0bcd", "\u0b86\u0ba8\u0bcd\u0ba4\u0bbf\u0bb0\u0baa\u0bcd \u0baa\u0bbf\u0bb0\u0ba4\u0bc7\u0b9a\u0bae\u0bcd"],
 "hi": ["\u0909\u0924\u094d\u0924\u0930 \u092a\u094d\u0930\u0926\u0947\u0936 \u092e\u0947\u0902 \u0930\u0939\u0924\u093e \u0939\u0942\u0901", "\u0915\u0947\u0930\u0932", "\u0915\u0930\u094d\u0928\u093e\u091f\u0915", "\u0924\u092e\u093f\u0932\u0928\u093e\u0921\u0941"],
 "te": ["\u0c06\u0c02\u0c27\u0c4d\u0c30\u0c2a\u0c4d\u0c30\u0c26\u0c47\u0c36\u0c4d", "\u0c24\u0c2e\u0c3f\u0c33\u0c28\u0c3e\u0c21\u0c41 \u0c32\u0c4b \u0c09\u0c02\u0c1f\u0c3e\u0c28\u0c41", "\u0c15\u0c47\u0c30\u0c33"],
 "kn": ["\u0c95\u0cb0\u0ccd\u0ca8\u0cbe\u0c9f\u0c95", "\u0ca4\u0cae\u0cbf\u0cb3\u0cc1\u0ca8\u0cbe\u0ca1\u0cc1", "\u0c95\u0cc7\u0cb0\u0cb3"],
 "ml": ["\u0d15\u0d47\u0d30\u0d33\u0d02", "\u0d24\u0d2e\u0d3f\u0d34\u0d4d\u200c\u0d28\u0d3e\u0d1f\u0d4d", "\u0d15\u0d7c\u0d23\u0d3e\u0d1f\u0d15"],
},
}


# Expected value for the three enum slots. Filling one of these with the
# WRONG value is worse than leaving it empty: the interview moves on
# satisfied, and the recommender then scores against a fact the person
# never said. Free-text slots have no expected value -- being filled at
# all is the whole requirement.
# The state answer is a gazetteer value: a wrong one routes the person to a
# training centre in a state they do not live in, which is worse than not
# understanding them at all.
EXPECTED_STATE = {
 "en": ["Tamil Nadu", "Kerala", "Karnataka", "Andhra Pradesh", "Uttar Pradesh",
        "Tamil Nadu", "Andhra Pradesh", "Uttar Pradesh", "Andhra Pradesh", "Kerala"],
 "ta": ["Tamil Nadu", "Kerala", "Karnataka", "Andhra Pradesh"],
 "hi": ["Uttar Pradesh", "Kerala", "Karnataka", "Tamil Nadu"],
 "te": ["Andhra Pradesh", "Tamil Nadu", "Kerala"],
 "kn": ["Karnataka", "Tamil Nadu", "Kerala"],
 "ml": ["Kerala", "Tamil Nadu", "Karnataka"],
}

EXPECTED_IN_PROGRESS = {
 "en": ["class12","class12","class12","class12","class12","class12",
        "class10","class10","class10","class12","class8"],
 "ta": ["class12","class12","class12","class12"],
 "hi": ["class12","class12"],
 "te": ["class12"],
 "kn": ["class12"],
 "ml": ["class12"],
}

EXPECTED = {'EDUCATION': {'en': ['class10', 'class10', 'class5', 'class5', 'iti_diploma', 'graduate', 'class12', 'class12', 'class8', 'class5'], 'ta': ['class10', 'class10', 'class5', 'class5', 'iti_diploma', 'graduate', 'class12', 'class8', 'class10', 'class10', 'class5', 'iti_diploma'], 'hi': ['class10', 'class12', 'class5', 'class5', 'iti_diploma', 'graduate', 'class8', 'class10', 'class10', 'class5'], 'te': ['class10', 'class12', 'class5', 'class5', 'iti_diploma', 'graduate', 'class10', 'class5'], 'kn': ['class10', 'class12', 'class5', 'class5', 'iti_diploma', 'graduate', 'class10', 'class5'], 'ml': ['class10', 'class12', 'class5', 'class5', 'iti_diploma', 'graduate', 'class10', 'class5']}, 'MOBILITY': {'en': ['local', 'district', 'state', 'local', 'local', 'state', 'district', 'local'], 'ta': ['local', 'district', 'state', 'local', 'local', 'state', 'local', 'local'], 'hi': ['local', 'district', 'state', 'local', 'local', 'state', 'local'], 'te': ['local', 'district', 'state', 'local', 'local'], 'kn': ['local', 'district', 'state', 'local'], 'ml': ['local', 'district', 'state', 'local']}, 'PREFERENCE': {'en': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self', 'pref_wage'], 'ta': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self', 'pref_wage'], 'hi': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self', 'pref_self'], 'te': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage', 'pref_self'], 'kn': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage'], 'ml': ['pref_self', 'pref_wage', 'pref_self', 'pref_wage']}}

# Fragment field behind each enum slot (the dataclass calls it `edu`).
FIELD = {"EDUCATION": "edu", "MOBILITY": "mobility",
         "PREFERENCE": "preference", "STATE": "state"}

def main():
    e = N.Nlu()
    total = ok = 0
    per_slot = {}
    misses, wrong = [], []

    for slot, langs in CORPUS.items():
        s_ok = s_tot = 0
        # The in-progress corpus is answered by the EDUCATION question; only
        # its expected values differ from the plain EDUCATION set.
        real_slot = "EDUCATION" if slot == "EDUCATION_IN_PROGRESS" else slot
        for lang, utts in langs.items():
            if slot == "EDUCATION_IN_PROGRESS":
                wants = EXPECTED_IN_PROGRESS.get(lang, [])
            elif slot == "STATE":
                wants = EXPECTED_STATE.get(lang, [])
            else:
                wants = EXPECTED.get(slot, {}).get(lang, [])
            for i, u in enumerate(utts):
                total += 1
                s_tot += 1
                frag = e.extract_for_slot(u, lang, real_slot)
                if not frag.is_filled(real_slot):
                    misses.append((slot, lang, u))
                    continue
                if wants and i < len(wants):
                    got = getattr(frag, FIELD[real_slot], None)
                    if got != wants[i]:
                        wrong.append((slot, lang, u, wants[i], got))
                        continue
                ok += 1
                s_ok += 1
        per_slot[slot] = (s_ok, s_tot)

    print("Answer coverage -- realistic spoken answers, per question\n")
    for slot, (a, b) in per_slot.items():
        bar = "#" * int(24 * a / b) if b else ""
        print("  %-20s %3d/%-3d  %5.1f%%  %s" % (slot, a, b, 100.0 * a / b, bar))
    print("\n  %-20s %3d/%-3d  %5.1f%%" % ("TOTAL", ok, total, 100.0 * ok / total))

    if misses:
        print("\nNot understood (%d):" % len(misses))
        for slot, lang, u in misses:
            print("   %-20s %-3s %s" % (slot, lang, u))
    if wrong:
        print("\nUnderstood but WRONG VALUE (%d):" % len(wrong))
        for slot, lang, u, want, got in wrong:
            print("   %-18s %-3s %-44s want=%-12s got=%s"
                  % (slot, lang, u[:42], want, got))
    return 0 if not (misses or wrong) else 1


if __name__ == "__main__":
    sys.exit(main())
