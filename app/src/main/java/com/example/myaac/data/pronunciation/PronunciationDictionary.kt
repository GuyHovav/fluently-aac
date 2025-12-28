package com.example.myaac.data.pronunciation

/**
 * Manages pronunciation corrections for Hebrew and other languages.
 * Supports both built-in corrections and user-defined custom pronunciations.
 */
class PronunciationDictionary {
    
    /**
     * Built-in dictionary of common Hebrew words that need nikud for correct pronunciation.
     * Key: Word without nikud, Value: Word with nikud
     */
    private val builtInHebrewDictionary = mapOf(
        // Food & Eating
        "רעב" to "רָעֵב",      // hungry (not hunger)
        "צמא" to "צָמֵא",      // thirsty
        "אוכל" to "אוֹכֵל",    // eating (not food)
        "שותה" to "שׁוֹתֶה",   // drinking
        "לחם" to "לֶחֶם",      // bread
        "מים" to "מַיִם",      // water
        "חלב" to "חָלָב",      // milk
        "מיץ" to "מִיץ",       // juice
        "פירות" to "פֵּרוֹת", // fruits
        "ירקות" to "יְרָקוֹת", // vegetables
        "בשר" to "בָּשָׂר",    // meat
        "עוגה" to "עוּגָה",    // cake
        "ממתק" to "מַמְתָּק",  // candy
        "ארוחה" to "אֲרוּחָה", // meal
        
        // Core AAC Verbs & Actions
        "רוצה" to "רוֹצֶה",    // want
        "צריך" to "צָרִיךְ",   // need
        "הולך" to "הוֹלֵךְ",   // going
        "בא" to "בָּא",        // coming
        "עושה" to "עוֹשֶׂה",   // doing/making
        "משחק" to "מְשַׂחֵק",  // playing
        "קורא" to "קוֹרֵא",    // reading
        "כותב" to "כּוֹתֵב",   // writing
        "שר" to "שָׁר",        // singing
        "רוקד" to "רוֹקֵד",    // dancing
        "ישן" to "יָשֵׁן",     // sleeping
        "קם" to "קָם",         // getting up
        "יושב" to "יוֹשֵׁב",   // sitting
        "עומד" to "עוֹמֵד",    // standing
        "רץ" to "רָץ",         // running
        "מדבר" to "מְדַבֵּר",  // talking
        "שומע" to "שׁוֹמֵעַ",   // hearing/listening
        "רואה" to "רוֹאֶה",    // seeing
        "נותן" to "נוֹתֵן",    // giving
        "לוקח" to "לוֹקֵחַ",   // taking
        "עוזר" to "עוֹזֵר",    // helping
        "פותח" to "פּוֹתֵחַ",  // opening
        "סוגר" to "סוֹגֵר",    // closing
        "מחכה" to "מְחַכֶּה",  // waiting
        
        // Feelings & Emotions
        "שמח" to "שָׂמֵחַ",     // happy
        "עצוב" to "עָצוּב",    // sad
        "כועס" to "כּוֹעֵס",   // angry
        "פוחד" to "פּוֹחֵד",   // afraid
        "אוהב" to "אוֹהֵב",    // loving/like
        "שונא" to "שׂוֹנֵא",   // hate
        "עייף" to "עָיֵף",     // tired
        "בודד" to "בּוֹדֵד",   // lonely
        "נרגש" to "נִרְגָּשׁ", // excited
        "מודאג" to "מוּדְאָג", // worried
        
        // Common Words & Core Vocabulary
        "כן" to "כֵּן",        // yes
        "לא" to "לֹא",         // no
        "עכשיו" to "עַכְשָׁיו", // now
        "אחר כך" to "אַחַר כָּךְ", // later
        "בבקשה" to "בְּבַקָּשָׁה", // please
        "תודה" to "תּוֹדָה",    // thank you
        "סליחה" to "סְלִיחָה", // sorry/excuse me
        "עזרה" to "עֶזְרָה",    // help
        "עוד" to "עוֹד",       // more
        "מספיק" to "מַסְפִּיק", // enough
        "גמור" to "גָּמוּר",   // finished/done
        "טוב" to "טוֹב",       // good
        "רע" to "רַע",         // bad
        "גדול" to "גָּדוֹל",   // big
        "קטן" to "קָטָן",      // small
        "חם" to "חַם",         // hot
        "קר" to "קַר",         // cold
        
        // Family & People
        "אמא" to "אִמָּא",      // mom
        "אבא" to "אַבָּא",      // dad
        "אח" to "אָח",          // brother
        "אחות" to "אָחוֹת",     // sister
        "סבא" to "סָבָא",       // grandpa
        "סבתא" to "סָבְתָא",   // grandma
        "דוד" to "דּוֹד",       // uncle
        "דודה" to "דּוֹדָה",    // aunt
        "חבר" to "חָבֵר",       // friend (male)
        "חברה" to "חֲבֵרָה",    // friend (female)
        "מורה" to "מוֹרֶה",     // teacher
        
        // Body Parts
        "ראש" to "רֹאשׁ",       // head
        "פנים" to "פָּנִים",    // face
        "עין" to "עַיִן",       // eye
        "אוזן" to "אֹזֶן",      // ear
        "אף" to "אַף",          // nose
        "פה" to "פֶּה",         // mouth
        "שן" to "שֵׁן",         // tooth
        "יד" to "יָד",          // hand
        "רגל" to "רֶגֶל",       // leg/foot
        "בטן" to "בֶּטֶן",      // stomach/belly
        "גב" to "גַּב",         // back
        "לב" to "לֵב",          // heart
        
        // Places
        "בית" to "בַּיִת",      // home/house
        "בית ספר" to "בֵּית סֵפֶר", // school
        "גן" to "גַּן",          // kindergarten/park
        "חנות" to "חֲנוּת",      // store
        "פארק" to "פַּארְק",     // park
        "רחוב" to "רְחוֹב",      // street
        "עיר" to "עִיר",         // city
        "כפר" to "כְּפָר",       // village
        "חדר" to "חֶדֶר",        // room
        "מטבח" to "מִטְבָּח",    // kitchen
        "שירותים" to "שֵׁרוּתִים", // bathroom/toilet
        
        // Time & Schedule
        "בוקר" to "בֹּקֶר",     // morning
        "צהריים" to "צָהֳרַיִם", // noon
        "ערב" to "עֶרֶב",       // evening
        "לילה" to "לַיְלָה",   // night
        "יום" to "יוֹם",        // day
        "שבוע" to "שָׁבוּעַ",   // week
        "חודש" to "חֹדֶשׁ",     // month
        "שנה" to "שָׁנָה",      // year
        "היום" to "הַיּוֹם",   // today
        "מחר" to "מָחָר",       // tomorrow
        "אתמול" to "אֶתְמוֹל",  // yesterday
        
        // Question Words
        "מה" to "מָה",          // what
        "מי" to "מִי",          // who
        "איפה" to "אֵיפֹה",    // where
        "מתי" to "מָתַי",       // when
        "למה" to "לָמָּה",      // why
        "איך" to "אֵיךְ",       // how
        "כמה" to "כַּמָּה",     // how much/many
        
        // Medical & Health
        "כואב" to "כּוֹאֵב",    // hurts/pain
        "חולה" to "חוֹלֶה",     // sick
        "רופא" to "רוֹפֵא",     // doctor
        "אחות" to "אָחוֹת",     // nurse (same as sister)
        "תרופה" to "תְּרוּפָה", // medicine
        "בריא" to "בָּרִיא",   // healthy
        "מרגיש" to "מַרְגִּישׁ", // feeling
        
        // Activities & Hobbies
        "טלוויזיה" to "טֶלֶוִיזְיָה", // television
        "מוזיקה" to "מוּזִיקָה", // music
        "ספר" to "סֵפֶר",        // book
        "משחק" to "מִשְׂחָק",    // game/toy
        "כדור" to "כַּדּוּר",    // ball
        "ציור" to "צִיּוּר",     // drawing/painting
        
        // Colors
        "אדום" to "אָדֹם",       // red
        "כחול" to "כָּחֹל",      // blue
        "ירוק" to "יָרֹק",       // green
        "צהוב" to "צָהֹב",       // yellow
        "שחור" to "שָׁחֹר",      // black
        "לבן" to "לָבָן"         // white
    )
    
    /**
     * User-defined custom pronunciations.
     * This map can be modified by caregivers to add their own corrections.
     */
    private val customDictionary = mutableMapOf<String, String>()
    
    /**
     * Apply pronunciation corrections to the given text.
     * @param text The text to correct
     * @param languageCode The language code (e.g., "iw", "he")
     * @return Corrected text with nikud where applicable
     */
    fun applyCorrections(text: String, languageCode: String): String {
        if (languageCode != "iw" && languageCode != "he") {
            return text
        }
        
        var correctedText = text
        
        // Apply custom dictionary first (user preferences take priority)
        customDictionary.forEach { (original, replacement) ->
            // Use word boundary regex to match whole words only
            val regex = Regex("(?<!\\p{L})${Regex.escape(original)}(?!\\p{L})")
            correctedText = correctedText.replace(regex, replacement)
        }
        
        // Then apply built-in dictionary
        builtInHebrewDictionary.forEach { (original, replacement) ->
            // Only apply if not already replaced by custom dictionary
            val regex = Regex("(?<!\\p{L})${Regex.escape(original)}(?!\\p{L})")
            correctedText = correctedText.replace(regex, replacement)
        }
        
        return correctedText
    }
    
    /**
     * Add a custom pronunciation correction.
     * @param original The word without nikud
     * @param corrected The word with nikud or phonetic spelling
     */
    fun addCustomPronunciation(original: String, corrected: String) {
        customDictionary[original] = corrected
    }
    
    /**
     * Remove a custom pronunciation correction.
     * @param original The word to remove from custom dictionary
     */
    fun removeCustomPronunciation(original: String) {
        customDictionary.remove(original)
    }
    
    /**
     * Get all custom pronunciations.
     * @return Map of custom pronunciations
     */
    fun getCustomPronunciations(): Map<String, String> {
        return customDictionary.toMap()
    }
    
    /**
     * Get all built-in pronunciations (read-only).
     * @return Map of built-in pronunciations
     */
    fun getBuiltInPronunciations(): Map<String, String> {
        return builtInHebrewDictionary
    }
    
    /**
     * Clear all custom pronunciations.
     */
    fun clearCustomPronunciations() {
        customDictionary.clear()
    }
    
    /**
     * Load custom pronunciations from a map (e.g., from persistent storage).
     */
    fun loadCustomPronunciations(pronunciations: Map<String, String>) {
        customDictionary.clear()
        customDictionary.putAll(pronunciations)
    }
}
