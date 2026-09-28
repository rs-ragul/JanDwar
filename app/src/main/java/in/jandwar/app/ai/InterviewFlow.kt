package `in`.jandwar.app.ai

import `in`.jandwar.app.ai.ProfileFragment.Slot

/**
 * The conversational script used when the on-device engine is driving the
 * interview (no cloud key, or no connectivity).
 *
 * It is deliberately *not* a rigid form: each turn acknowledges what was just
 * understood, then asks about the single most important missing slot, using a
 * rotating set of phrasings so repeated runs do not sound identical.
 */
object InterviewFlow {

    private val LANGS = listOf("en", "ta", "hi", "te", "kn", "ml")

    private fun pick(map: Map<String, List<String>>, lang: String, seed: Int): String {
        val list = map[lang] ?: map["en"].orEmpty()
        if (list.isEmpty()) return ""
        return list[(seed.coerceAtLeast(0)) % list.size]
    }

    private fun one(map: Map<String, String>, lang: String): String =
        map[lang] ?: map["en"].orEmpty()

    // ── Opening ─────────────────────────────────────────────────────────────

    private val GREETING = mapOf(
        "en" to listOf(
            "Hello! I am JanDwar. I will ask you a few simple questions to find training that suits you. Take your time. First, how far did you study?",
            "Namaste! I am JanDwar, your livelihood helper. Let us talk for a minute and find the right course for you. To begin, how far did you study?"
        ),
        "ta" to listOf(
            "வணக்கம்! நான் JanDwar. உங்களுக்கு ஏற்ற பயிற்சியை கண்டறிய சில எளிய கேள்விகள் கேட்பேன். அவசரம் இல்லை. முதலில், நீங்கள் எவ்வளவு படித்திருக்கிறீர்கள்?",
            "வணக்கம்! நான் JanDwar, உங்கள் வாழ்வாதார உதவியாளர். சிறிது நேரம் பேசி உங்களுக்கு சரியான பயிற்சியை கண்டுபிடிப்போம். நீங்கள் எவ்வளவு படித்திருக்கிறீர்கள்?"
        ),
        "hi" to listOf(
            "नमस्ते! मैं JanDwar हूँ। आपके लिए सही प्रशिक्षण खोजने के लिए कुछ आसान सवाल पूछूँगा। कोई जल्दी नहीं है। पहले बताइए, आपने कहाँ तक पढ़ाई की है?",
            "नमस्ते! मैं JanDwar, आपका आजीविका सहायक। थोड़ी बात करके आपके लिए सही कोर्स ढूंढते हैं। आपने कहाँ तक पढ़ाई की है?"
        ),
        "te" to listOf(
            "నమస్కారం! నేను JanDwar. మీకు సరిపోయే శిక్షణ కనుగొనడానికి కొన్ని సులభమైన ప్రశ్నలు అడుగుతాను. తొందర లేదు. మొదట, మీరు ఎంతవరకు చదివారు?",
            "నమస్కారం! నేను JanDwar, మీ జీవనోపాధి సహాయకుడు. కాసేపు మాట్లాడి మీకు సరైన కోర్సు కనుక్కుందాం. మీరు ఎంతవరకు చదివారు?"
        ),
        "kn" to listOf(
            "ನಮಸ್ಕಾರ! ನಾನು JanDwar. ನಿಮಗೆ ಸೂಕ್ತವಾದ ತರಬೇತಿ ಹುಡುಕಲು ಕೆಲವು ಸುಲಭ ಪ್ರಶ್ನೆಗಳನ್ನು ಕೇಳುತ್ತೇನೆ. ಆತುರವಿಲ್ಲ. ಮೊದಲಿಗೆ, ನೀವು ಎಷ್ಟು ಓದಿದ್ದೀರಿ?",
            "ನಮಸ್ಕಾರ! ನಾನು JanDwar, ನಿಮ್ಮ ಜೀವನೋಪಾಯ ಸಹಾಯಕ. ಸ್ವಲ್ಪ ಮಾತನಾಡಿ ನಿಮಗೆ ಸರಿಯಾದ ಕೋರ್ಸ್ ಹುಡುಕೋಣ. ನೀವು ಎಷ್ಟು ಓದಿದ್ದೀರಿ?"
        ),
        "ml" to listOf(
            "നമസ്കാരം! ഞാൻ JanDwar. നിങ്ങൾക്ക് അനുയോജ്യമായ പരിശീലനം കണ്ടെത്താൻ ചില എളുപ്പ ചോദ്യങ്ങൾ ചോദിക്കാം. തിരക്കില്ല. ആദ്യം, നിങ്ങൾ എത്രത്തോളം പഠിച്ചു?",
            "നമസ്കാരം! ഞാൻ JanDwar, നിങ്ങളുടെ ഉപജീവന സഹായി. കുറച്ചു സംസാരിച്ച് നിങ്ങൾക്ക് ശരിയായ കോഴ്‌സ് കണ്ടെത്താം. നിങ്ങൾ എത്രത്തോളം പഠിച്ചു?"
        )
    )

    fun greeting(lang: String, seed: Int = 0): String = pick(GREETING, lang, seed)

    // ── Slot questions ──────────────────────────────────────────────────────

    private val QUESTIONS: Map<Slot, Map<String, List<String>>> = mapOf(
        Slot.EDUCATION to mapOf(
            "en" to listOf(
                "How far did you study? For example up to 8th, 10th, 12th, ITI, or a degree.",
                "Tell me about your schooling — which class did you finish?"
            ),
            "ta" to listOf(
                "நீங்கள் எவ்வளவு படித்திருக்கிறீர்கள்? உதாரணமாக 8, 10, 12, ITI அல்லது பட்டப்படிப்பு.",
                "உங்கள் படிப்பு பற்றி சொல்லுங்கள் — எந்த வகுப்பு வரை முடித்தீர்கள்?"
            ),
            "hi" to listOf(
                "आपने कहाँ तक पढ़ाई की है? जैसे 8वीं, 10वीं, 12वीं, ITI या डिग्री।",
                "अपनी पढ़ाई के बारे में बताइए — कौन सी कक्षा तक पूरी की?"
            ),
            "te" to listOf(
                "మీరు ఎంతవరకు చదివారు? ఉదాహరణకు 8, 10, 12, ITI లేదా డిగ్రీ.",
                "మీ చదువు గురించి చెప్పండి — ఏ తరగతి వరకు పూర్తి చేశారు?"
            ),
            "kn" to listOf(
                "ನೀವು ಎಷ್ಟು ಓದಿದ್ದೀರಿ? ಉದಾಹರಣೆಗೆ 8, 10, 12, ITI ಅಥವಾ ಪದವಿ.",
                "ನಿಮ್ಮ ವಿದ್ಯಾಭ್ಯಾಸದ ಬಗ್ಗೆ ಹೇಳಿ — ಯಾವ ತರಗತಿವರೆಗೆ ಮುಗಿಸಿದ್ದೀರಿ?"
            ),
            "ml" to listOf(
                "നിങ്ങൾ എത്രത്തോളം പഠിച്ചു? ഉദാഹരണത്തിന് 8, 10, 12, ITI അല്ലെങ്കിൽ ബിരുദം.",
                "നിങ്ങളുടെ പഠനത്തെക്കുറിച്ച് പറയൂ — ഏത് ക്ലാസ് വരെ പൂർത്തിയാക്കി?"
            )
        ),
        Slot.FAMILY_OCCUPATION to mapOf(
            "en" to listOf(
                "What work does your family do? Many families have a traditional trade.",
                "Tell me about your family's work — farming, weaving, construction, or something else?"
            ),
            "ta" to listOf(
                "உங்கள் குடும்பம் என்ன வேலை செய்கிறது? பல குடும்பங்களுக்கு பாரம்பரிய தொழில் உண்டு.",
                "உங்கள் குடும்ப வேலை பற்றி சொல்லுங்கள் — விவசாயம், நெசவு, கட்டுமானம் அல்லது வேறு ஏதாவது?"
            ),
            "hi" to listOf(
                "आपका परिवार क्या काम करता है? कई परिवारों का पारंपरिक काम होता है।",
                "अपने परिवार के काम के बारे में बताइए — खेती, बुनाई, निर्माण या कुछ और?"
            ),
            "te" to listOf(
                "మీ కుటుంబం ఏ పని చేస్తుంది? చాలా కుటుంబాలకు సంప్రదాయ వృత్తి ఉంటుంది.",
                "మీ కుటుంబ పని గురించి చెప్పండి — వ్యవసాయం, నేత, నిర్మాణం లేదా మరేదైనా?"
            ),
            "kn" to listOf(
                "ನಿಮ್ಮ ಕುಟುಂಬ ಯಾವ ಕೆಲಸ ಮಾಡುತ್ತದೆ? ಹಲವು ಕುಟುಂಬಗಳಿಗೆ ಸಾಂಪ್ರದಾಯಿಕ ವೃತ್ತಿ ಇರುತ್ತದೆ.",
                "ನಿಮ್ಮ ಕುಟುಂಬದ ಕೆಲಸದ ಬಗ್ಗೆ ಹೇಳಿ — ಕೃಷಿ, ನೇಯ್ಗೆ, ನಿರ್ಮಾಣ ಅಥವಾ ಬೇರೇನಾದರೂ?"
            ),
            "ml" to listOf(
                "നിങ്ങളുടെ കുടുംബം എന്ത് ജോലി ചെയ്യുന്നു? പല കുടുംബങ്ങൾക്കും പാരമ്പര്യ തൊഴിലുണ്ട്.",
                "നിങ്ങളുടെ കുടുംബത്തിന്റെ ജോലിയെക്കുറിച്ച് പറയൂ — കൃഷി, നെയ്ത്ത്, നിർമാണം അതോ മറ്റെന്തെങ്കിലുമോ?"
            )
        ),
        Slot.CURRENT_LIVELIHOOD to mapOf(
            "en" to listOf(
                "And what are you doing right now? Studying, daily wage work, farming, or looking for work?",
                "What keeps you busy these days?"
            ),
            "ta" to listOf(
                "இப்போது நீங்கள் என்ன செய்கிறீர்கள்? படிக்கிறீர்களா, கூலி வேலையா, விவசாயமா, அல்லது வேலை தேடுகிறீர்களா?",
                "இந்த நாட்களில் நீங்கள் என்ன செய்து வருகிறீர்கள்?"
            ),
            "hi" to listOf(
                "अभी आप क्या कर रहे हैं? पढ़ाई, दिहाड़ी मजदूरी, खेती या काम की तलाश?",
                "इन दिनों आप क्या करते हैं?"
            ),
            "te" to listOf(
                "ఇప్పుడు మీరు ఏమి చేస్తున్నారు? చదువు, రోజువారీ కూలి, వ్యవసాయం లేదా పని వెతుకుతున్నారా?",
                "ఈ రోజుల్లో మీరు ఏమి చేస్తున్నారు?"
            ),
            "kn" to listOf(
                "ಈಗ ನೀವು ಏನು ಮಾಡುತ್ತಿದ್ದೀರಿ? ಓದು, ದಿನಗೂಲಿ, ಕೃಷಿ ಅಥವಾ ಕೆಲಸ ಹುಡುಕುತ್ತಿದ್ದೀರಾ?",
                "ಈ ದಿನಗಳಲ್ಲಿ ನೀವು ಏನು ಮಾಡುತ್ತೀರಿ?"
            ),
            "ml" to listOf(
                "ഇപ്പോൾ നിങ്ങൾ എന്ത് ചെയ്യുന്നു? പഠനം, കൂലിപ്പണി, കൃഷി അതോ ജോലി തിരയുകയാണോ?",
                "ഈ ദിവസങ്ങളിൽ നിങ്ങൾ എന്ത് ചെയ്യുന്നു?"
            )
        ),
        Slot.INTERESTS to mapOf(
            "en" to listOf(
                "What kind of work interests you? For example dairy, goats, poultry, farming, food making, tailoring, weaving, construction or machines.",
                "Which work would you enjoy learning — animals, farming, food, tailoring, weaving, construction or machines?"
            ),
            "ta" to listOf(
                "எந்த மாதிரி வேலையில் உங்களுக்கு ஆர்வம்? உதாரணமாக பால் பண்ணை, ஆடு, கோழி, விவசாயம், உணவு தயாரிப்பு, தையல், நெசவு, கட்டுமானம் அல்லது இயந்திரம்.",
                "எந்த வேலையை கற்க விரும்புகிறீர்கள் — கால்நடை, விவசாயம், உணவு, தையல், நெசவு, கட்டுமானம் அல்லது இயந்திரம்?"
            ),
            "hi" to listOf(
                "आपको किस तरह के काम में रुचि है? जैसे डेयरी, बकरी, मुर्गी, खेती, खाना बनाना, सिलाई, बुनाई, निर्माण या मशीन।",
                "कौन सा काम सीखना पसंद करेंगे — पशु, खेती, खाद्य, सिलाई, बुनाई, निर्माण या मशीन?"
            ),
            "te" to listOf(
                "మీకు ఏ రకమైన పనిపై ఆసక్తి? ఉదాహరణకు పాడి, మేకలు, కోళ్లు, వ్యవసాయం, ఆహార తయారీ, కుట్టు, నేత, నిర్మాణం లేదా యంత్రాలు.",
                "ఏ పని నేర్చుకోవాలని ఉంది — పశువులు, వ్యవసాయం, ఆహారం, కుట్టు, నేత, నిర్మాణం లేదా యంత్రాలు?"
            ),
            "kn" to listOf(
                "ನಿಮಗೆ ಯಾವ ರೀತಿಯ ಕೆಲಸದಲ್ಲಿ ಆಸಕ್ತಿ? ಉದಾಹರಣೆಗೆ ಹೈನುಗಾರಿಕೆ, ಮೇಕೆ, ಕೋಳಿ, ಕೃಷಿ, ಆಹಾರ ತಯಾರಿಕೆ, ಹೊಲಿಗೆ, ನೇಯ್ಗೆ, ನಿರ್ಮಾಣ ಅಥವಾ ಯಂತ್ರ.",
                "ಯಾವ ಕೆಲಸ ಕಲಿಯಲು ಇಷ್ಟ — ಪ್ರಾಣಿಗಳು, ಕೃಷಿ, ಆಹಾರ, ಹೊಲಿಗೆ, ನೇಯ್ಗೆ, ನಿರ್ಮಾಣ ಅಥವಾ ಯಂತ್ರ?"
            ),
            "ml" to listOf(
                "നിങ്ങൾക്ക് ഏത് തരം ജോലിയിലാണ് താൽപ്പര്യം? ഉദാഹരണത്തിന് ക്ഷീരം, ആട്, കോഴി, കൃഷി, ഭക്ഷ്യ നിർമാണം, തയ്യൽ, നെയ്ത്ത്, നിർമാണം അല്ലെങ്കിൽ യന്ത്രം.",
                "ഏത് ജോലി പഠിക്കാൻ ഇഷ്ടം — മൃഗങ്ങൾ, കൃഷി, ഭക്ഷണം, തയ്യൽ, നെയ്ത്ത്, നിർമാണം അതോ യന്ത്രം?"
            )
        ),
        Slot.PREFERENCE to mapOf(
            "en" to listOf(
                "Would you like to start your own small business, or would a job with an employer suit you better?",
                "Do you want to work for yourself, or work for a company?"
            ),
            "ta" to listOf(
                "நீங்கள் சொந்தமாக ஒரு சிறு தொழில் தொடங்க விரும்புகிறீர்களா, அல்லது ஒரு நிறுவனத்தில் வேலை பொருத்தமாக இருக்குமா?",
                "சொந்தமாக வேலை செய்ய விரும்புகிறீர்களா, அல்லது நிறுவனத்தில் வேலையா?"
            ),
            "hi" to listOf(
                "आप अपना छोटा काम शुरू करना चाहेंगे, या किसी के यहाँ नौकरी बेहतर रहेगी?",
                "अपना काम करना है या कंपनी में नौकरी?"
            ),
            "te" to listOf(
                "మీరు సొంతంగా చిన్న వ్యాపారం ప్రారంభించాలనుకుంటున్నారా, లేదా ఉద్యోగం మంచిదా?",
                "సొంతంగా పని చేయాలా, లేదా కంపెనీలో ఉద్యోగమా?"
            ),
            "kn" to listOf(
                "ನೀವು ಸ್ವಂತ ಸಣ್ಣ ಉದ್ಯಮ ಪ್ರಾರಂಭಿಸಲು ಬಯಸುತ್ತೀರಾ, ಅಥವಾ ಕೆಲಸ ಉತ್ತಮವೇ?",
                "ಸ್ವಂತ ಕೆಲಸ ಮಾಡಬೇಕೆ, ಅಥವಾ ಕಂಪನಿಯಲ್ಲಿ ಕೆಲಸವೇ?"
            ),
            "ml" to listOf(
                "നിങ്ങൾക്ക് സ്വന്തമായി ചെറിയ ബിസിനസ്സ് തുടങ്ങണോ, അതോ ജോലി ആണോ നല്ലത്?",
                "സ്വന്തമായി ജോലി ചെയ്യണോ, അതോ കമ്പനിയിൽ ജോലിയോ?"
            )
        ),
        Slot.MOBILITY to mapOf(
            "en" to listOf(
                "How far can you travel for training? Only your village, anywhere in your district, or anywhere in the state?",
                "Can you travel for the course, or should it be close to home?"
            ),
            "ta" to listOf(
                "பயிற்சிக்காக எவ்வளவு தூரம் செல்ல முடியும்? உங்கள் ஊர் மட்டுமா, மாவட்டத்தில் எங்கும் மா, அல்லது மாநிலம் முழுவதுமா?",
                "பயிற்சிக்கு பயணம் செய்ய முடியுமா, அல்லது வீட்டுக்கு அருகில் இருக்க வேண்டுமா?"
            ),
            "hi" to listOf(
                "प्रशिक्षण के लिए कितनी दूर जा सकते हैं? सिर्फ अपने गाँव, जिले में कहीं भी, या पूरे राज्य में?",
                "कोर्स के लिए सफर कर सकते हैं, या घर के पास होना चाहिए?"
            ),
            "te" to listOf(
                "శిక్షణ కోసం ఎంత దూరం వెళ్లగలరు? మీ ఊరు మాత్రమేనా, జిల్లాలో ఎక్కడైనా, లేదా రాష్ట్రమంతటా?",
                "కోర్సు కోసం ప్రయాణం చేయగలరా, లేదా ఇంటికి దగ్గరగా ఉండాలా?"
            ),
            "kn" to listOf(
                "ತರಬೇತಿಗಾಗಿ ಎಷ್ಟು ದೂರ ಹೋಗಬಹುದು? ನಿಮ್ಮ ಊರು ಮಾತ್ರವೇ, ಜಿಲ್ಲೆಯಲ್ಲಿ ಎಲ್ಲಿಯಾದರೂ, ಅಥವಾ ರಾಜ್ಯದಾದ್ಯಂತವೇ?",
                "ಕೋರ್ಸ್‌ಗಾಗಿ ಪ್ರಯಾಣಿಸಬಹುದೇ, ಅಥವಾ ಮನೆಯ ಹತ್ತಿರ ಇರಬೇಕೇ?"
            ),
            "ml" to listOf(
                "പരിശീലനത്തിന് എത്ര ദൂരം പോകാൻ കഴിയും? നിങ്ങളുടെ ഗ്രാമം മാത്രമോ, ജില്ലയിൽ എവിടെയും, അതോ സംസ്ഥാനത്തുടനീളമോ?",
                "കോഴ്‌സിനായി യാത്ര ചെയ്യാൻ കഴിയുമോ, അതോ വീടിനടുത്ത് വേണോ?"
            )
        ),
        Slot.DISTRICT to mapOf(
            "en" to listOf(
                "Which district do you live in?",
                "Last question — which district is your home in?"
            ),
            "ta" to listOf(
                "நீங்கள் எந்த மாவட்டத்தில் வசிக்கிறீர்கள்?",
                "கடைசி கேள்வி — உங்கள் ஊர் எந்த மாவட்டத்தில் உள்ளது?"
            ),
            "hi" to listOf(
                "आप किस जिले में रहते हैं?",
                "आखिरी सवाल — आपका घर किस जिले में है?"
            ),
            "te" to listOf(
                "మీరు ఏ జిల్లాలో నివసిస్తున్నారు?",
                "చివరి ప్రశ్న — మీ ఇల్లు ఏ జిల్లాలో ఉంది?"
            ),
            "kn" to listOf(
                "ನೀವು ಯಾವ ಜಿಲ್ಲೆಯಲ್ಲಿ ವಾಸಿಸುತ್ತೀರಿ?",
                "ಕೊನೆಯ ಪ್ರಶ್ನೆ — ನಿಮ್ಮ ಮನೆ ಯಾವ ಜಿಲ್ಲೆಯಲ್ಲಿದೆ?"
            ),
            "ml" to listOf(
                "നിങ്ങൾ ഏത് ജില്ലയിലാണ് താമസിക്കുന്നത്?",
                "അവസാന ചോദ്യം — നിങ്ങളുടെ വീട് ഏത് ജില്ലയിലാണ്?"
            )
        )
    )

    fun question(slot: Slot, lang: String, seed: Int = 0): String =
        pick(QUESTIONS[slot] ?: emptyMap(), lang, seed)

    // ── Acknowledgements ────────────────────────────────────────────────────

    private val ACK = mapOf(
        "en" to listOf("Got it.", "Thank you.", "I understand.", "Okay, noted."),
        "ta" to listOf("சரி, புரிந்தது.", "நன்றி.", "புரிகிறது.", "சரி, குறித்துக் கொண்டேன்."),
        "hi" to listOf("समझ गया।", "धन्यवाद।", "ठीक है।", "नोट कर लिया।"),
        "te" to listOf("అర్థమైంది.", "ధన్యవాదాలు.", "సరే.", "గుర్తుపెట్టుకున్నాను."),
        "kn" to listOf("ಅರ್ಥವಾಯಿತು.", "ಧನ್ಯವಾದ.", "ಸರಿ.", "ಗಮನಿಸಿದೆ."),
        "ml" to listOf("മനസ്സിലായി.", "നന്ദി.", "ശരി.", "കുറിച്ചു.")
    )

    fun acknowledgement(lang: String, seed: Int = 0): String = pick(ACK, lang, seed)

    // ── Re-prompt when nothing was understood ───────────────────────────────

    private val REPROMPT = mapOf(
        "en" to listOf(
            "Sorry, I did not catch that. Could you say it once more?",
            "I missed that. Please tell me again, slowly."
        ),
        "ta" to listOf(
            "மன்னிக்கவும், சரியாக கேட்கவில்லை. இன்னொரு முறை சொல்ல முடியுமா?",
            "எனக்கு கேட்கவில்லை. மெதுவாக மீண்டும் சொல்லுங்கள்."
        ),
        "hi" to listOf(
            "माफ कीजिए, मैं समझ नहीं पाया। एक बार फिर बताइए?",
            "सुनाई नहीं दिया। कृपया धीरे से फिर बोलिए।"
        ),
        "te" to listOf(
            "క్షమించండి, నాకు అర్థం కాలేదు. మరోసారి చెప్పగలరా?",
            "వినిపించలేదు. దయచేసి నెమ్మదిగా మళ్లీ చెప్పండి."
        ),
        "kn" to listOf(
            "ಕ್ಷಮಿಸಿ, ನನಗೆ ಅರ್ಥವಾಗಲಿಲ್ಲ. ಮತ್ತೊಮ್ಮೆ ಹೇಳಬಹುದೇ?",
            "ಕೇಳಿಸಲಿಲ್ಲ. ದಯವಿಟ್ಟು ನಿಧಾನವಾಗಿ ಮತ್ತೆ ಹೇಳಿ."
        ),
        "ml" to listOf(
            "ക്ഷമിക്കണം, എനിക്ക് മനസ്സിലായില്ല. ഒരിക്കൽ കൂടി പറയാമോ?",
            "കേട്ടില്ല. ദയവായി പതുക്കെ വീണ്ടും പറയൂ."
        )
    )

    fun reprompt(lang: String, seed: Int = 0): String = pick(REPROMPT, lang, seed)

    // ── Closing ─────────────────────────────────────────────────────────────

    private val CLOSING = mapOf(
        "en" to "Thank you. I have a clear picture now. Let me find the training options that fit you best.",
        "ta" to "நன்றி. உங்களைப் பற்றி தெளிவாக புரிந்து கொண்டேன். உங்களுக்கு ஏற்ற பயிற்சி வாய்ப்புகளை இப்போது தேடுகிறேன்.",
        "hi" to "धन्यवाद। अब मुझे पूरी तस्वीर साफ है। आपके लिए सबसे अच्छे प्रशिक्षण विकल्प ढूंढता हूँ।",
        "te" to "ధన్యవాదాలు. ఇప్పుడు నాకు స్పష్టత వచ్చింది. మీకు సరిపోయే శిక్షణ అవకాశాలు వెతుకుతాను.",
        "kn" to "ಧನ್ಯವಾದ. ಈಗ ನನಗೆ ಸ್ಪಷ್ಟ ಚಿತ್ರಣ ಸಿಕ್ಕಿದೆ. ನಿಮಗೆ ಸೂಕ್ತವಾದ ತರಬೇತಿ ಅವಕಾಶಗಳನ್ನು ಹುಡುಕುತ್ತೇನೆ.",
        "ml" to "നന്ദി. ഇപ്പോൾ എനിക്ക് വ്യക്തമായ ചിത്രം കിട്ടി. നിങ്ങൾക്ക് അനുയോജ്യമായ പരിശീലന അവസരങ്ങൾ കണ്ടെത്താം."
    )

    fun closing(lang: String): String = one(CLOSING, lang)

    // ── Results narration ───────────────────────────────────────────────────

    private val RESULT_INTRO = mapOf(
        "en" to "I found %d good options for you.",
        "ta" to "உங்களுக்காக %d நல்ல வாய்ப்புகளை கண்டறிந்தேன்.",
        "hi" to "मैंने आपके लिए %d अच्छे विकल्प खोजे हैं।",
        "te" to "మీ కోసం %d మంచి అవకాశాలు కనుగొన్నాను.",
        "kn" to "ನಿಮಗಾಗಿ %d ಉತ್ತಮ ಅವಕಾಶಗಳನ್ನು ಕಂಡುಕೊಂಡೆ.",
        "ml" to "നിങ്ങൾക്കായി %d നല്ല അവസരങ്ങൾ കണ്ടെത്തി."
    )

    fun resultIntro(lang: String, count: Int): String =
        one(RESULT_INTRO, lang).replace("%d", count.toString())

    private val RESULT_OUTRO = mapOf(
        "en" to "All the details, centre contact and funding information are on the next screen.",
        "ta" to "அனைத்து விவரங்கள், மைய தொடர்பு மற்றும் நிதி தகவல்கள் அடுத்த திரையில் உள்ளன.",
        "hi" to "सारे विवरण, केंद्र का संपर्क और फंडिंग जानकारी अगली स्क्रीन पर है।",
        "te" to "అన్ని వివరాలు, కేంద్ర సంప్రదింపు, నిధుల సమాచారం తదుపరి స్క్రీన్‌లో ఉన్నాయి.",
        "kn" to "ಎಲ್ಲಾ ವಿವರಗಳು, ಕೇಂದ್ರ ಸಂಪರ್ಕ ಮತ್ತು ಅನುದಾನ ಮಾಹಿತಿ ಮುಂದಿನ ಪರದೆಯಲ್ಲಿದೆ.",
        "ml" to "എല്ലാ വിവരങ്ങളും കേന്ദ്ര ബന്ധപ്പെടലും ഫണ്ടിംഗ് വിവരവും അടുത്ത സ്ക്രീനിലുണ്ട്."
    )

    fun resultOutro(lang: String): String = one(RESULT_OUTRO, lang)

    private val NO_RESULT = mapOf(
        "en" to "I could not find a confident match yet. You can browse all courses, or try a different interest or district.",
        "ta" to "இன்னும் உறுதியான பொருத்தம் கிடைக்கவில்லை. அனைத்து பயிற்சிகளையும் பார்க்கலாம், அல்லது வேறு ஆர்வம் அல்லது மாவட்டத்தை முயற்சிக்கலாம்.",
        "hi" to "अभी पक्का मेल नहीं मिला। आप सभी कोर्स देख सकते हैं, या दूसरी रुचि या जिला आज़मा सकते हैं।",
        "te" to "ఇంకా ఖచ్చితమైన సరిపోలిక దొరకలేదు. అన్ని కోర్సులు చూడవచ్చు, లేదా వేరే ఆసక్తి లేదా జిల్లా ప్రయత్నించవచ్చు.",
        "kn" to "ಇನ್ನೂ ಖಚಿತ ಹೊಂದಾಣಿಕೆ ಸಿಗಲಿಲ್ಲ. ಎಲ್ಲಾ ಕೋರ್ಸ್‌ಗಳನ್ನು ನೋಡಬಹುದು, ಅಥವಾ ಬೇರೆ ಆಸಕ್ತಿ ಅಥವಾ ಜಿಲ್ಲೆ ಪ್ರಯತ್ನಿಸಬಹುದು.",
        "ml" to "ഉറപ്പുള്ള പൊരുത്തം ഇതുവരെ കിട്ടിയില്ല. എല്ലാ കോഴ്‌സുകളും കാണാം, അല്ലെങ്കിൽ മറ്റൊരു താൽപ്പര്യമോ ജില്ലയോ പരീക്ഷിക്കാം."
    )

    fun noResult(lang: String): String = one(NO_RESULT, lang)

    fun isSupported(lang: String): Boolean = lang in LANGS

    /** BCP-47 tag used for both ASR and TTS. */
    fun bcp47(lang: String): String = when (lang) {
        "ta" -> "ta-IN"
        "hi" -> "hi-IN"
        "te" -> "te-IN"
        "kn" -> "kn-IN"
        "ml" -> "ml-IN"
        else -> "en-IN"
    }
}
