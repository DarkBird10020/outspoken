package com.outspoken.conversation

/**
 * A language the app can work in: the cards, the phrase bank, the topic buttons, the hand-sign
 * phrases and the phone's voice, and what the visitor is heard in. The person picks theirs in
 * Settings (owner request: the app is for people everywhere). The replies themselves come from the
 * on-device model, asked to write in this language; nothing is translated off the phone.
 *
 * The built-in phrases were written for this app and should be checked by a native speaker; they
 * avoid forms that depend on the person's gender where the language allows.
 */
enum class AppLanguage(
    /** ISO 639-1 code, as the speech recogniser's language guesses name it ("hi"). */
    val code: String,
    /** The language's name in English, for the model's instructions. */
    val englishName: String,
    /** The language's own name, as shown in Settings. */
    val label: String,
    val script: Script,
    /** BCP 47 tags for the speech recogniser, best first; null leaves the English choice to the app. */
    val speechTags: List<String>?,
    /** ISO 639 code of the offline voice to speak with. */
    val voice: String,
    val phrases: List<String>,
    val yesNo: List<String>,
    /** One-tap questions for the visitor: button label to question. */
    val topics: List<Pair<String, String>>,
    /** The three fixed cards on the main page: more options, yes / no, say anything. */
    val fixedCards: List<String>,
    /** Built-in first words for "Say anything" before the model answers. */
    val firstWords: List<String>,
    /** Built-in words that fit after most words, for "Say anything" after the first. */
    val anywhere: List<String>,
    /** What a hand sign says, by its English phrase; a phrase not listed is said as it is. */
    val signPhrases: Map<String, String> = emptyMap(),
    /** Other codes the recogniser may use for it ("cmn" for Chinese). */
    val aliases: List<String> = emptyList(),
) {
    English(
        code = "en",
        englishName = "English",
        label = "English",
        script = Script.Latin,
        speechTags = null,
        voice = "en",
        phrases = listOf("I need water", "I am in pain", "Please call the nurse", "I need the toilet", "I am too hot", "I am too cold", "Thank you"),
        yesNo = listOf("Yes", "No"),
        topics = listOf(
            "Pain" to "Are you in pain?",
            "Comfort" to "Are you comfortable?",
            "Food and drink" to "Are you hungry or thirsty?",
            "Feelings" to "How are you feeling?",
            "Family" to "Do you want to see your family?",
        ),
        fixedCards = listOf("More options", "Yes / No", "Say anything"),
        firstWords = listOf("I", "Please", "My", "Can you", "I need", "I want", "I feel", "Thank you", "Yes", "No", "Where is", "When"),
        anywhere = listOf("please", "now", "a little", "more", "not", "very", "help", "water", "pain", "home", "and", "the"),
    ),
    Hindi(
        code = "hi",
        englishName = "Hindi",
        label = "हिन्दी",
        script = Script.Devanagari,
        speechTags = listOf("hi-IN"),
        voice = "hi",
        phrases = listOf("मुझे पानी चाहिए", "मुझे दर्द हो रहा है", "कृपया नर्स को बुलाइए", "मुझे शौचालय जाना है", "मुझे बहुत गर्मी लग रही है", "मुझे बहुत ठंड लग रही है", "धन्यवाद"),
        yesNo = listOf("हाँ", "नहीं"),
        topics = listOf(
            "दर्द" to "क्या आपको दर्द हो रहा है?",
            "आराम" to "क्या आप आराम से हैं?",
            "खाना-पानी" to "क्या आपको भूख या प्यास लगी है?",
            "हालचाल" to "आप कैसा महसूस कर रहे हैं?",
            "परिवार" to "क्या आप अपने परिवार से मिलना चाहते हैं?",
        ),
        fixedCards = listOf("और विकल्प", "हाँ / नहीं", "कुछ भी कहें"),
        firstWords = listOf("मुझे", "कृपया", "मैं", "मेरा", "क्या", "हाँ", "नहीं", "धन्यवाद", "अभी", "थोड़ा", "पानी", "दर्द"),
        anywhere = listOf("कृपया", "अभी", "थोड़ा", "पानी", "दर्द", "आराम", "चाहिए", "है", "नहीं", "और", "बहुत", "मदद"),
        // "मुझे आपसे प्यार है" reads the same whoever says it; "करता / करती हूँ" would need a gender.
        signPhrases = mapOf(
            "Yes" to "हाँ", "No" to "नहीं", "Thank you" to "धन्यवाद", "I love you" to "मुझे आपसे प्यार है",
            "Please call the nurse" to "कृपया नर्स को बुलाइए", "Please wait" to "कृपया रुकिए",
        ),
    ),
    Bengali(
        code = "bn",
        englishName = "Bengali",
        label = "বাংলা",
        script = Script.Bengali,
        speechTags = listOf("bn-IN", "bn-BD"),
        voice = "bn",
        phrases = listOf("আমার জল চাই", "আমার ব্যথা করছে", "দয়া করে নার্সকে ডাকুন", "আমি বাথরুমে যেতে চাই", "আমার খুব গরম লাগছে", "আমার খুব ঠান্ডা লাগছে", "ধন্যবাদ"),
        yesNo = listOf("হ্যাঁ", "না"),
        topics = listOf(
            "ব্যথা" to "আপনার কি ব্যথা করছে?",
            "আরাম" to "আপনি কি আরামে আছেন?",
            "খাবার-জল" to "আপনার কি খিদে বা তেষ্টা পেয়েছে?",
            "অনুভূতি" to "আপনি কেমন বোধ করছেন?",
            "পরিবার" to "আপনি কি পরিবারের সাথে দেখা করতে চান?",
        ),
        fixedCards = listOf("আরও বিকল্প", "হ্যাঁ / না", "যা খুশি বলুন"),
        firstWords = listOf("আমার", "আমি", "দয়া করে", "একটু", "হ্যাঁ", "না", "ধন্যবাদ", "এখন"),
        anywhere = listOf("জল", "ব্যথা", "চাই", "না", "আরও", "একটু", "এখন", "সাহায্য"),
        signPhrases = mapOf(
            "Yes" to "হ্যাঁ", "No" to "না", "Thank you" to "ধন্যবাদ", "I love you" to "আমি তোমাকে ভালোবাসি",
            "Please call the nurse" to "দয়া করে নার্সকে ডাকুন", "Please wait" to "দয়া করে অপেক্ষা করুন",
        ),
    ),
    Marathi(
        code = "mr",
        englishName = "Marathi",
        label = "मराठी",
        script = Script.Devanagari,
        speechTags = listOf("mr-IN"),
        voice = "mr",
        phrases = listOf("मला पाणी हवे आहे", "मला दुखत आहे", "कृपया नर्सला बोलवा", "मला शौचालयात जायचे आहे", "मला खूप गरम होत आहे", "मला खूप थंडी वाजत आहे", "धन्यवाद"),
        yesNo = listOf("हो", "नाही"),
        topics = listOf(
            "वेदना" to "तुम्हाला दुखत आहे का?",
            "आराम" to "तुम्ही आरामात आहात का?",
            "खाणे-पिणे" to "तुम्हाला भूक किंवा तहान लागली आहे का?",
            "भावना" to "तुम्हाला कसे वाटत आहे?",
            "कुटुंब" to "तुम्हाला कुटुंबाला भेटायचे आहे का?",
        ),
        fixedCards = listOf("आणखी पर्याय", "हो / नाही", "काहीही बोला"),
        firstWords = listOf("मला", "मी", "कृपया", "माझे", "थोडे", "हो", "नाही", "धन्यवाद"),
        anywhere = listOf("पाणी", "दुखत", "हवे", "आहे", "नाही", "आणखी", "थोडे", "मदत"),
        signPhrases = mapOf(
            "Yes" to "हो", "No" to "नाही", "Thank you" to "धन्यवाद", "I love you" to "माझे तुमच्यावर प्रेम आहे",
            "Please call the nurse" to "कृपया नर्सला बोलवा", "Please wait" to "कृपया थांबा",
        ),
    ),
    Tamil(
        code = "ta",
        englishName = "Tamil",
        label = "தமிழ்",
        script = Script.Tamil,
        speechTags = listOf("ta-IN", "ta-LK"),
        voice = "ta",
        phrases = listOf("எனக்கு தண்ணீர் வேண்டும்", "எனக்கு வலிக்கிறது", "தயவுசெய்து நர்ஸை அழையுங்கள்", "நான் கழிப்பறைக்கு போக வேண்டும்", "எனக்கு மிகவும் சூடாக இருக்கிறது", "எனக்கு மிகவும் குளிராக இருக்கிறது", "நன்றி"),
        yesNo = listOf("ஆம்", "இல்லை"),
        topics = listOf(
            "வலி" to "உங்களுக்கு வலிக்கிறதா?",
            "வசதி" to "நீங்கள் வசதியாக இருக்கிறீர்களா?",
            "உணவு" to "உங்களுக்கு பசி அல்லது தாகமாக இருக்கிறதா?",
            "உணர்வு" to "நீங்கள் எப்படி உணர்கிறீர்கள்?",
            "குடும்பம்" to "உங்கள் குடும்பத்தைப் பார்க்க வேண்டுமா?",
        ),
        fixedCards = listOf("மேலும் விருப்பங்கள்", "ஆம் / இல்லை", "எதையும் சொல்லுங்கள்"),
        firstWords = listOf("எனக்கு", "நான்", "தயவுசெய்து", "என்", "கொஞ்சம்", "ஆம்", "இல்லை", "நன்றி"),
        anywhere = listOf("தண்ணீர்", "வலி", "வேண்டும்", "இல்லை", "இன்னும்", "கொஞ்சம்", "இப்போது", "உதவி"),
        signPhrases = mapOf(
            "Yes" to "ஆம்", "No" to "இல்லை", "Thank you" to "நன்றி", "I love you" to "நான் உங்களை நேசிக்கிறேன்",
            "Please call the nurse" to "தயவுசெய்து நர்ஸை அழையுங்கள்", "Please wait" to "தயவுசெய்து காத்திருங்கள்",
        ),
    ),
    Telugu(
        code = "te",
        englishName = "Telugu",
        label = "తెలుగు",
        script = Script.Telugu,
        speechTags = listOf("te-IN"),
        voice = "te",
        phrases = listOf("నాకు నీళ్ళు కావాలి", "నాకు నొప్పిగా ఉంది", "దయచేసి నర్సును పిలవండి", "నేను బాత్రూమ్‌కి వెళ్ళాలి", "నాకు చాలా వేడిగా ఉంది", "నాకు చాలా చలిగా ఉంది", "ధన్యవాదాలు"),
        yesNo = listOf("అవును", "కాదు"),
        topics = listOf(
            "నొప్పి" to "మీకు నొప్పిగా ఉందా?",
            "సౌకర్యం" to "మీరు సౌకర్యంగా ఉన్నారా?",
            "ఆహారం" to "మీకు ఆకలిగా లేదా దాహంగా ఉందా?",
            "భావాలు" to "మీకు ఎలా అనిపిస్తోంది?",
            "కుటుంబం" to "మీరు మీ కుటుంబాన్ని చూడాలనుకుంటున్నారా?",
        ),
        fixedCards = listOf("మరిన్ని ఎంపికలు", "అవును / కాదు", "ఏదైనా చెప్పండి"),
        firstWords = listOf("నాకు", "నేను", "దయచేసి", "నా", "కొంచెం", "అవును", "కాదు", "ధన్యవాదాలు"),
        anywhere = listOf("నీళ్ళు", "నొప్పి", "కావాలి", "లేదు", "ఇంకా", "కొంచెం", "ఇప్పుడు", "సహాయం"),
        signPhrases = mapOf(
            "Yes" to "అవును", "No" to "కాదు", "Thank you" to "ధన్యవాదాలు", "I love you" to "నేను నిన్ను ప్రేమిస్తున్నాను",
            "Please call the nurse" to "దయచేసి నర్సును పిలవండి", "Please wait" to "దయచేసి ఆగండి",
        ),
    ),
    Kannada(
        code = "kn",
        englishName = "Kannada",
        label = "ಕನ್ನಡ",
        script = Script.Kannada,
        speechTags = listOf("kn-IN"),
        voice = "kn",
        phrases = listOf("ನನಗೆ ನೀರು ಬೇಕು", "ನನಗೆ ನೋವಾಗುತ್ತಿದೆ", "ದಯವಿಟ್ಟು ನರ್ಸ್ ಅನ್ನು ಕರೆಯಿರಿ", "ನಾನು ಶೌಚಾಲಯಕ್ಕೆ ಹೋಗಬೇಕು", "ನನಗೆ ತುಂಬಾ ಸೆಕೆಯಾಗುತ್ತಿದೆ", "ನನಗೆ ತುಂಬಾ ಚಳಿಯಾಗುತ್ತಿದೆ", "ಧನ್ಯವಾದಗಳು"),
        yesNo = listOf("ಹೌದು", "ಇಲ್ಲ"),
        topics = listOf(
            "ನೋವು" to "ನಿಮಗೆ ನೋವಾಗುತ್ತಿದೆಯೇ?",
            "ಆರಾಮ" to "ನೀವು ಆರಾಮವಾಗಿದ್ದೀರಾ?",
            "ಊಟ" to "ನಿಮಗೆ ಹಸಿವು ಅಥವಾ ಬಾಯಾರಿಕೆ ಆಗಿದೆಯೇ?",
            "ಭಾವನೆ" to "ನಿಮಗೆ ಹೇಗನಿಸುತ್ತಿದೆ?",
            "ಕುಟುಂಬ" to "ನೀವು ನಿಮ್ಮ ಕುಟುಂಬವನ್ನು ನೋಡಲು ಬಯಸುತ್ತೀರಾ?",
        ),
        fixedCards = listOf("ಇನ್ನಷ್ಟು ಆಯ್ಕೆಗಳು", "ಹೌದು / ಇಲ್ಲ", "ಏನಾದರೂ ಹೇಳಿ"),
        firstWords = listOf("ನನಗೆ", "ನಾನು", "ದಯವಿಟ್ಟು", "ನನ್ನ", "ಸ್ವಲ್ಪ", "ಹೌದು", "ಇಲ್ಲ", "ಧನ್ಯವಾದಗಳು"),
        anywhere = listOf("ನೀರು", "ನೋವು", "ಬೇಕು", "ಇಲ್ಲ", "ಇನ್ನೂ", "ಸ್ವಲ್ಪ", "ಈಗ", "ಸಹಾಯ"),
        signPhrases = mapOf(
            "Yes" to "ಹೌದು", "No" to "ಇಲ್ಲ", "Thank you" to "ಧನ್ಯವಾದಗಳು", "I love you" to "ನಾನು ನಿನ್ನನ್ನು ಪ್ರೀತಿಸುತ್ತೇನೆ",
            "Please call the nurse" to "ದಯವಿಟ್ಟು ನರ್ಸ್ ಅನ್ನು ಕರೆಯಿರಿ", "Please wait" to "ದಯವಿಟ್ಟು ಕಾಯಿರಿ",
        ),
    ),
    Gujarati(
        code = "gu",
        englishName = "Gujarati",
        label = "ગુજરાતી",
        script = Script.Gujarati,
        speechTags = listOf("gu-IN"),
        voice = "gu",
        phrases = listOf("મારે પાણી જોઈએ છે", "મને દુખાવો થાય છે", "કૃપા કરીને નર્સને બોલાવો", "મારે શૌચાલય જવું છે", "મને ખૂબ ગરમી લાગે છે", "મને ખૂબ ઠંડી લાગે છે", "આભાર"),
        yesNo = listOf("હા", "ના"),
        topics = listOf(
            "દુખાવો" to "શું તમને દુખાવો થાય છે?",
            "આરામ" to "શું તમે આરામથી છો?",
            "ખાવું-પીવું" to "શું તમને ભૂખ કે તરસ લાગી છે?",
            "લાગણી" to "તમને કેવું લાગે છે?",
            "પરિવાર" to "શું તમે તમારા પરિવારને મળવા માંગો છો?",
        ),
        fixedCards = listOf("વધુ વિકલ્પો", "હા / ના", "કંઈ પણ કહો"),
        firstWords = listOf("મને", "મારે", "હું", "કૃપા કરીને", "થોડું", "હા", "ના", "આભાર"),
        anywhere = listOf("પાણી", "દુખાવો", "જોઈએ", "નથી", "વધુ", "થોડું", "હમણાં", "મદદ"),
        signPhrases = mapOf(
            "Yes" to "હા", "No" to "ના", "Thank you" to "આભાર", "I love you" to "હું તમને પ્રેમ કરું છું",
            "Please call the nurse" to "કૃપા કરીને નર્સને બોલાવો", "Please wait" to "કૃપા કરીને રાહ જુઓ",
        ),
    ),
    Malayalam(
        code = "ml",
        englishName = "Malayalam",
        label = "മലയാളം",
        script = Script.Malayalam,
        speechTags = listOf("ml-IN"),
        voice = "ml",
        phrases = listOf("എനിക്ക് വെള്ളം വേണം", "എനിക്ക് വേദനിക്കുന്നു", "ദയവായി നഴ്സിനെ വിളിക്കൂ", "എനിക്ക് ശുചിമുറിയിൽ പോകണം", "എനിക്ക് വളരെ ചൂടെടുക്കുന്നു", "എനിക്ക് വളരെ തണുക്കുന്നു", "നന്ദി"),
        yesNo = listOf("അതെ", "ഇല്ല"),
        topics = listOf(
            "വേദന" to "നിങ്ങൾക്ക് വേദനയുണ്ടോ?",
            "സുഖം" to "നിങ്ങൾ സുഖമായിരിക്കുന്നോ?",
            "ഭക്ഷണം" to "നിങ്ങൾക്ക് വിശപ്പോ ദാഹമോ ഉണ്ടോ?",
            "മനസ്സ്" to "നിങ്ങൾക്ക് എങ്ങനെ തോന്നുന്നു?",
            "കുടുംബം" to "നിങ്ങൾക്ക് കുടുംബത്തെ കാണണോ?",
        ),
        fixedCards = listOf("കൂടുതൽ ഓപ്ഷനുകൾ", "അതെ / ഇല്ല", "എന്തും പറയൂ"),
        firstWords = listOf("എനിക്ക്", "ഞാൻ", "ദയവായി", "എന്റെ", "കുറച്ച്", "അതെ", "ഇല്ല", "നന്ദി"),
        anywhere = listOf("വെള്ളം", "വേദന", "വേണം", "ഇല്ല", "കൂടുതൽ", "കുറച്ച്", "ഇപ്പോൾ", "സഹായം"),
        signPhrases = mapOf(
            "Yes" to "അതെ", "No" to "ഇല്ല", "Thank you" to "നന്ദി", "I love you" to "ഞാൻ നിന്നെ സ്നേഹിക്കുന്നു",
            "Please call the nurse" to "ദയവായി നഴ്സിനെ വിളിക്കൂ", "Please wait" to "ദയവായി കാത്തിരിക്കൂ",
        ),
    ),
    Urdu(
        code = "ur",
        englishName = "Urdu",
        label = "اردو",
        script = Script.Arabic,
        speechTags = listOf("ur-IN", "ur-PK"),
        voice = "ur",
        phrases = listOf("مجھے پانی چاہیے", "مجھے درد ہو رہا ہے", "براہ کرم نرس کو بلائیں", "مجھے بیت الخلا جانا ہے", "مجھے بہت گرمی لگ رہی ہے", "مجھے بہت سردی لگ رہی ہے", "شکریہ"),
        yesNo = listOf("ہاں", "نہیں"),
        topics = listOf(
            "درد" to "کیا آپ کو درد ہو رہا ہے؟",
            "آرام" to "کیا آپ آرام سے ہیں؟",
            "کھانا پینا" to "کیا آپ کو بھوک یا پیاس لگی ہے؟",
            "حال" to "آپ کیسا محسوس کر رہے ہیں؟",
            "خاندان" to "کیا آپ اپنے خاندان سے ملنا چاہتے ہیں؟",
        ),
        fixedCards = listOf("مزید اختیارات", "ہاں / نہیں", "کچھ بھی کہیں"),
        firstWords = listOf("مجھے", "میں", "براہ کرم", "میرا", "تھوڑا", "ہاں", "نہیں", "شکریہ"),
        anywhere = listOf("پانی", "درد", "چاہیے", "نہیں", "اور", "تھوڑا", "ابھی", "مدد"),
        signPhrases = mapOf(
            "Yes" to "ہاں", "No" to "نہیں", "Thank you" to "شکریہ", "I love you" to "مجھے آپ سے محبت ہے",
            "Please call the nurse" to "براہ کرم نرس کو بلائیں", "Please wait" to "براہ کرم رکیے",
        ),
    ),
    Spanish(
        code = "es",
        englishName = "Spanish",
        label = "Español",
        script = Script.Latin,
        speechTags = listOf("es-ES", "es-US", "es-MX"),
        voice = "es",
        phrases = listOf("Necesito agua", "Me duele", "Por favor, llame a la enfermera", "Necesito ir al baño", "Tengo mucho calor", "Tengo mucho frío", "Gracias"),
        yesNo = listOf("Sí", "No"),
        topics = listOf(
            "Dolor" to "¿Le duele algo?",
            "Comodidad" to "¿Se siente bien así?",
            "Comida y bebida" to "¿Tiene hambre o sed?",
            "Ánimo" to "¿Cómo se siente?",
            "Familia" to "¿Quiere ver a su familia?",
        ),
        fixedCards = listOf("Más opciones", "Sí / No", "Decir algo"),
        firstWords = listOf("Yo", "Por favor", "Mi", "Necesito", "Quiero", "Me siento", "Gracias", "Sí", "No"),
        anywhere = listOf("agua", "dolor", "ahora", "un poco", "más", "no", "ayuda", "por favor"),
        signPhrases = mapOf(
            "Yes" to "Sí", "No" to "No", "Thank you" to "Gracias", "I love you" to "Te quiero",
            "Please call the nurse" to "Por favor, llame a la enfermera", "Please wait" to "Por favor, espere",
        ),
    ),
    French(
        code = "fr",
        englishName = "French",
        label = "Français",
        script = Script.Latin,
        speechTags = listOf("fr-FR", "fr-CA"),
        voice = "fr",
        phrases = listOf("J'ai besoin d'eau", "J'ai mal", "Appelez l'infirmière, s'il vous plaît", "Je dois aller aux toilettes", "J'ai trop chaud", "J'ai trop froid", "Merci"),
        yesNo = listOf("Oui", "Non"),
        topics = listOf(
            "Douleur" to "Avez-vous mal ?",
            "Confort" to "Est-ce que ça va comme ça ?",
            "Manger et boire" to "Avez-vous faim ou soif ?",
            "Ressenti" to "Comment vous sentez-vous ?",
            "Famille" to "Voulez-vous voir votre famille ?",
        ),
        fixedCards = listOf("Plus de choix", "Oui / Non", "Dire autre chose"),
        firstWords = listOf("Je", "S'il vous plaît", "Mon", "J'ai besoin", "Je veux", "Je me sens", "Merci", "Oui", "Non"),
        anywhere = listOf("eau", "mal", "maintenant", "un peu", "plus", "pas", "aide", "s'il vous plaît"),
        signPhrases = mapOf(
            "Yes" to "Oui", "No" to "Non", "Thank you" to "Merci", "I love you" to "Je t'aime",
            "Please call the nurse" to "Appelez l'infirmière, s'il vous plaît", "Please wait" to "Attendez, s'il vous plaît",
        ),
    ),
    German(
        code = "de",
        englishName = "German",
        label = "Deutsch",
        script = Script.Latin,
        speechTags = listOf("de-DE", "de-AT", "de-CH"),
        voice = "de",
        phrases = listOf("Ich brauche Wasser", "Ich habe Schmerzen", "Bitte rufen Sie die Pflegekraft", "Ich muss auf die Toilette", "Mir ist zu warm", "Mir ist zu kalt", "Danke"),
        yesNo = listOf("Ja", "Nein"),
        topics = listOf(
            "Schmerzen" to "Haben Sie Schmerzen?",
            "Wohlbefinden" to "Liegen Sie bequem?",
            "Essen und Trinken" to "Haben Sie Hunger oder Durst?",
            "Gefühle" to "Wie fühlen Sie sich?",
            "Familie" to "Möchten Sie Ihre Familie sehen?",
        ),
        fixedCards = listOf("Mehr Auswahl", "Ja / Nein", "Etwas sagen"),
        firstWords = listOf("Ich", "Bitte", "Mein", "Ich brauche", "Ich möchte", "Mir ist", "Danke", "Ja", "Nein"),
        anywhere = listOf("Wasser", "Schmerzen", "jetzt", "etwas", "mehr", "nicht", "Hilfe", "bitte"),
        signPhrases = mapOf(
            "Yes" to "Ja", "No" to "Nein", "Thank you" to "Danke", "I love you" to "Ich liebe dich",
            "Please call the nurse" to "Bitte rufen Sie die Pflegekraft", "Please wait" to "Bitte warten Sie",
        ),
    ),
    Portuguese(
        code = "pt",
        englishName = "Portuguese",
        label = "Português",
        script = Script.Latin,
        speechTags = listOf("pt-BR", "pt-PT"),
        voice = "pt",
        // "Agradeço": "Obrigado / Obrigada" changes with the speaker's gender.
        phrases = listOf("Preciso de água", "Estou com dor", "Por favor, chame a enfermeira", "Preciso ir ao banheiro", "Estou com muito calor", "Estou com muito frio", "Agradeço"),
        yesNo = listOf("Sim", "Não"),
        topics = listOf(
            "Dor" to "Está com dor?",
            "Conforto" to "Está confortável?",
            "Comida e bebida" to "Está com fome ou sede?",
            "Sentimentos" to "Como está se sentindo?",
            "Família" to "Quer ver sua família?",
        ),
        fixedCards = listOf("Mais opções", "Sim / Não", "Dizer algo"),
        firstWords = listOf("Eu", "Por favor", "Meu", "Preciso", "Quero", "Estou", "Agradeço", "Sim", "Não"),
        anywhere = listOf("água", "dor", "agora", "um pouco", "mais", "não", "ajuda", "por favor"),
        signPhrases = mapOf(
            "Yes" to "Sim", "No" to "Não", "Thank you" to "Agradeço", "I love you" to "Eu te amo",
            "Please call the nurse" to "Por favor, chame a enfermeira", "Please wait" to "Por favor, espere",
        ),
    ),
    Arabic(
        code = "ar",
        englishName = "Arabic",
        label = "العربية",
        script = Script.Arabic,
        speechTags = listOf("ar-SA", "ar-EG", "ar-AE"),
        voice = "ar",
        phrases = listOf("أحتاج إلى ماء", "أشعر بألم", "من فضلك نادِ الممرضة", "أحتاج إلى دخول الحمام", "أشعر بحرّ شديد", "أشعر ببرد شديد", "شكرًا"),
        yesNo = listOf("نعم", "لا"),
        topics = listOf(
            "الألم" to "هل تشعر بألم؟",
            "الراحة" to "هل أنت مرتاح؟",
            "الطعام والشراب" to "هل أنت جائع أو عطشان؟",
            "المشاعر" to "كيف تشعر الآن؟",
            "العائلة" to "هل تريد رؤية عائلتك؟",
        ),
        fixedCards = listOf("خيارات أخرى", "نعم / لا", "قل أي شيء"),
        firstWords = listOf("أنا", "من فضلك", "أحتاج", "أريد", "أشعر", "شكرًا", "نعم", "لا"),
        anywhere = listOf("ماء", "ألم", "الآن", "قليلًا", "أكثر", "لا", "مساعدة", "من فضلك"),
        signPhrases = mapOf(
            "Yes" to "نعم", "No" to "لا", "Thank you" to "شكرًا", "I love you" to "أحبك",
            "Please call the nurse" to "من فضلك نادِ الممرضة", "Please wait" to "من فضلك انتظر",
        ),
    ),
    Chinese(
        code = "zh",
        englishName = "Chinese (Mandarin, simplified characters)",
        label = "中文",
        script = Script.Han,
        speechTags = listOf("cmn-Hans-CN", "zh-CN"),
        voice = "zh",
        phrases = listOf("我需要喝水", "我很疼", "请叫护士", "我要上厕所", "我太热了", "我太冷了", "谢谢"),
        yesNo = listOf("是", "不"),
        topics = listOf(
            "疼痛" to "你疼吗？",
            "舒适" to "你舒服吗？",
            "饮食" to "你饿了还是渴了？",
            "感受" to "你感觉怎么样？",
            "家人" to "你想见家人吗？",
        ),
        fixedCards = listOf("更多选项", "是 / 不", "说点什么"),
        firstWords = listOf("我", "请", "我的", "我需要", "我想", "我觉得", "谢谢", "是", "不"),
        anywhere = listOf("水", "疼", "现在", "一点", "更多", "不", "帮助", "请"),
        signPhrases = mapOf(
            "Yes" to "是", "No" to "不", "Thank you" to "谢谢", "I love you" to "我爱你",
            "Please call the nurse" to "请叫护士", "Please wait" to "请稍等",
        ),
        aliases = listOf("cmn"),
    ),
    Japanese(
        code = "ja",
        englishName = "Japanese",
        label = "日本語",
        script = Script.Japanese,
        speechTags = listOf("ja-JP"),
        voice = "ja",
        phrases = listOf("水が欲しいです", "痛いです", "看護師さんを呼んでください", "トイレに行きたいです", "とても暑いです", "とても寒いです", "ありがとう"),
        yesNo = listOf("はい", "いいえ"),
        topics = listOf(
            "痛み" to "痛いところはありますか？",
            "楽さ" to "今の姿勢は楽ですか？",
            "食事" to "お腹は空いていますか？喉は渇いていますか？",
            "気分" to "気分はどうですか？",
            "家族" to "家族に会いたいですか？",
        ),
        fixedCards = listOf("他の選択肢", "はい / いいえ", "何でも言う"),
        firstWords = listOf("私は", "お願い", "私の", "欲しい", "したい", "ありがとう", "はい", "いいえ"),
        anywhere = listOf("水", "痛い", "今", "少し", "もっと", "ない", "助けて", "ください"),
        signPhrases = mapOf(
            "Yes" to "はい", "No" to "いいえ", "Thank you" to "ありがとう", "I love you" to "愛しています",
            "Please call the nurse" to "看護師さんを呼んでください", "Please wait" to "待ってください",
        ),
    ),
    ;

    companion object {
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: English

        /** The language a recogniser tag or guess names ("en-IN", "hi", "cmn-Hans-CN"); null when none. */
        fun fromCode(tag: String?): AppLanguage? {
            val primary = tag?.replace('_', '-')?.substringBefore('-')?.lowercase() ?: return null
            return entries.firstOrNull { it.code == primary || primary in it.aliases }
        }

        /** Names saved in Settings ("English,Hindi"), in order, unknown ones skipped. */
        fun listFromNames(names: String?): List<AppLanguage> =
            names.orEmpty().split(',').mapNotNull { name -> entries.firstOrNull { it.name == name.trim() } }.distinct()
    }
}

/**
 * Hindi when most letters in [text] are Devanagari, or when it is Hindi written in English letters;
 * English when most letters are Latin otherwise; null when it has neither. The Hindi recogniser
 * writes Devanagari; the English one writes Hindi in English letters ("kya Tumhen Meri Awaaz
 * sunai de rahi hai", 16:38:27), so half or more of the words being everyday Hindi counts too.
 */
fun spokenLanguage(text: String): AppLanguage? = languageOfText(text, listOf(AppLanguage.English, AppLanguage.Hindi))

/**
 * Which of [choices] (the person's languages, in their order) [text] is in, told by its letters;
 * null when none fits. Languages sharing a script are told apart by order, except Hindi written in
 * English letters, which is found by its everyday words.
 */
fun languageOfText(text: String, choices: List<AppLanguage>): AppLanguage? {
    val script = dominantScript(text) ?: return null
    if (script == Script.Latin && AppLanguage.Hindi in choices && looksLikeRomanHindi(text)) return AppLanguage.Hindi
    return choices.firstOrNull { it.script == script }
        // Kanji alone reads as Chinese characters; with no Chinese chosen it is Japanese.
        ?: if (script == Script.Han) choices.firstOrNull { it.script == Script.Japanese } else null
}

private fun looksLikeRomanHindi(text: String): Boolean {
    val words = text.lowercase().split(NOT_LATIN).filter { it.isNotEmpty() }
    val hindi = words.count { it in ROMAN_HINDI }
    return hindi >= 2 && hindi * 2 >= words.size
}

private val NOT_LATIN = Regex("[^a-z]+")

/** Everyday Hindi words in English letters; none is also a common English word. */
private val ROMAN_HINDI = setOf(
    "kya", "hai", "hain", "hoon", "ho", "tum", "tumhe", "tumhen", "tumko", "aap", "aapko", "apko",
    "mujhe", "mujhko", "mera", "meri", "mere", "tera", "teri", "tere", "nahi", "nahin", "haan",
    "kaise", "kaisa", "kaisi", "kuch", "kuchh", "abhi", "theek", "thik", "raha", "rahi", "rahe",
    "karo", "karna", "karte", "chahiye", "pani", "dard", "bhi", "aur", "sunai", "awaaz", "awaz",
    "ko", "se", "ka", "ki", "ke", "mein", "yeh", "kab", "kahan", "kyun", "kyon", "accha", "acha",
    "achha", "bahut", "thoda", "thodi", "khana", "neend", "jaldi", "batao", "bolo", "sakte",
    "sakta", "sakti", "gaye", "gaya", "gayi", "lagi", "laga", "hua", "hui",
)
