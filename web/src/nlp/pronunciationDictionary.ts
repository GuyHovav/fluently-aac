// Port of data/pronunciation/PronunciationDictionary.kt — near-mechanical translation.
//
// Manages pronunciation corrections for Hebrew and other languages. Supports both built-in
// corrections and user-defined custom pronunciations.
//
// The ~150-entry built-in Hebrew dictionary below is ported verbatim from the Kotlin source
// (including its category comments and its two duplicate keys — "אחות" appears twice, as
// "sister" under Family & People and again as "nurse" under Medical & Health; "משחק" appears
// twice, as "playing" under Core AAC Verbs & Actions and again as "game/toy" under Activities &
// Hobbies). It's built here as an array of [key, value] tuples fed through Object.fromEntries()
// rather than a literal object, so the last occurrence wins for a duplicate key — exactly
// matching Kotlin's `mapOf(...)` behavior for duplicate keys — without tripping a
// duplicate-object-key lint/compile diagnostic.

const BUILT_IN_HEBREW_ENTRIES: [string, string][] = [
  // Food & Eating
  ['רעב', 'רָעֵב'], // hungry (not hunger)
  ['צמא', 'צָמֵא'], // thirsty
  ['אוכל', 'אוֹכֵל'], // eating (not food)
  ['שותה', 'שׁוֹתֶה'], // drinking
  ['לחם', 'לֶחֶם'], // bread
  ['מים', 'מַיִם'], // water
  ['חלב', 'חָלָב'], // milk
  ['מיץ', 'מִיץ'], // juice
  ['פירות', 'פֵּרוֹת'], // fruits
  ['ירקות', 'יְרָקוֹת'], // vegetables
  ['בשר', 'בָּשָׂר'], // meat
  ['עוגה', 'עוּגָה'], // cake
  ['ממתק', 'מַמְתָּק'], // candy
  ['ארוחה', 'אֲרוּחָה'], // meal

  // Core AAC Verbs & Actions
  ['רוצה', 'רוֹצֶה'], // want
  ['צריך', 'צָרִיךְ'], // need
  ['הולך', 'הוֹלֵךְ'], // going
  ['בא', 'בָּא'], // coming
  ['עושה', 'עוֹשֶׂה'], // doing/making
  ['משחק', 'מְשַׂחֵק'], // playing
  ['קורא', 'קוֹרֵא'], // reading
  ['כותב', 'כּוֹתֵב'], // writing
  ['שר', 'שָׁר'], // singing
  ['רוקד', 'רוֹקֵד'], // dancing
  ['ישן', 'יָשֵׁן'], // sleeping
  ['קם', 'קָם'], // getting up
  ['יושב', 'יוֹשֵׁב'], // sitting
  ['עומד', 'עוֹמֵד'], // standing
  ['רץ', 'רָץ'], // running
  ['מדבר', 'מְדַבֵּר'], // talking
  ['שומע', 'שׁוֹמֵעַ'], // hearing/listening
  ['רואה', 'רוֹאֶה'], // seeing
  ['נותן', 'נוֹתֵן'], // giving
  ['לוקח', 'לוֹקֵחַ'], // taking
  ['עוזר', 'עוֹזֵר'], // helping
  ['פותח', 'פּוֹתֵחַ'], // opening
  ['סוגר', 'סוֹגֵר'], // closing
  ['מחכה', 'מְחַכֶּה'], // waiting

  // Feelings & Emotions
  ['שמח', 'שָׂמֵחַ'], // happy
  ['עצוב', 'עָצוּב'], // sad
  ['כועס', 'כּוֹעֵס'], // angry
  ['פוחד', 'פּוֹחֵד'], // afraid
  ['אוהב', 'אוֹהֵב'], // loving/like
  ['שונא', 'שׂוֹנֵא'], // hate
  ['עייף', 'עָיֵף'], // tired
  ['בודד', 'בּוֹדֵד'], // lonely
  ['נרגש', 'נִרְגָּשׁ'], // excited
  ['מודאג', 'מוּדְאָג'], // worried

  // Common Words & Core Vocabulary
  ['כן', 'כֵּן'], // yes
  ['לא', 'לֹא'], // no
  ['עכשיו', 'עַכְשָׁיו'], // now
  ['אחר כך', 'אַחַר כָּךְ'], // later
  ['בבקשה', 'בְּבַקָּשָׁה'], // please
  ['תודה', 'תּוֹדָה'], // thank you
  ['סליחה', 'סְלִיחָה'], // sorry/excuse me
  ['עזרה', 'עֶזְרָה'], // help
  ['עוד', 'עוֹד'], // more
  ['מספיק', 'מַסְפִּיק'], // enough
  ['גמור', 'גָּמוּר'], // finished/done
  ['טוב', 'טוֹב'], // good
  ['רע', 'רַע'], // bad
  ['גדול', 'גָּדוֹל'], // big
  ['קטן', 'קָטָן'], // small
  ['חם', 'חַם'], // hot
  ['קר', 'קַר'], // cold

  // Family & People
  ['אמא', 'אִמָּא'], // mom
  ['אבא', 'אַבָּא'], // dad
  ['אח', 'אָח'], // brother
  ['אחות', 'אָחוֹת'], // sister
  ['סבא', 'סָבָא'], // grandpa
  ['סבתא', 'סָבְתָא'], // grandma
  ['דוד', 'דּוֹד'], // uncle
  ['דודה', 'דּוֹדָה'], // aunt
  ['חבר', 'חָבֵר'], // friend (male)
  ['חברה', 'חֲבֵרָה'], // friend (female)
  ['מורה', 'מוֹרֶה'], // teacher

  // Body Parts
  ['ראש', 'רֹאשׁ'], // head
  ['פנים', 'פָּנִים'], // face
  ['עין', 'עַיִן'], // eye
  ['אוזן', 'אֹזֶן'], // ear
  ['אף', 'אַף'], // nose
  ['פה', 'פֶּה'], // mouth
  ['שן', 'שֵׁן'], // tooth
  ['יד', 'יָד'], // hand
  ['רגל', 'רֶגֶל'], // leg/foot
  ['בטן', 'בֶּטֶן'], // stomach/belly
  ['גב', 'גַּב'], // back
  ['לב', 'לֵב'], // heart

  // Places
  ['בית', 'בַּיִת'], // home/house
  ['בית ספר', 'בֵּית סֵפֶר'], // school
  ['גן', 'גַּן'], // kindergarten/park
  ['חנות', 'חֲנוּת'], // store
  ['פארק', 'פַּארְק'], // park
  ['רחוב', 'רְחוֹב'], // street
  ['עיר', 'עִיר'], // city
  ['כפר', 'כְּפָר'], // village
  ['חדר', 'חֶדֶר'], // room
  ['מטבח', 'מִטְבָּח'], // kitchen
  ['שירותים', 'שֵׁרוּתִים'], // bathroom/toilet

  // Time & Schedule
  ['בוקר', 'בֹּקֶר'], // morning
  ['צהריים', 'צָהֳרַיִם'], // noon
  ['ערב', 'עֶרֶב'], // evening
  ['לילה', 'לַיְלָה'], // night
  ['יום', 'יוֹם'], // day
  ['שבוע', 'שָׁבוּעַ'], // week
  ['חודש', 'חֹדֶשׁ'], // month
  ['שנה', 'שָׁנָה'], // year
  ['היום', 'הַיּוֹם'], // today
  ['מחר', 'מָחָר'], // tomorrow
  ['אתמול', 'אֶתְמוֹל'], // yesterday

  // Question Words
  ['מה', 'מָה'], // what
  ['מי', 'מִי'], // who
  ['איפה', 'אֵיפֹה'], // where
  ['מתי', 'מָתַי'], // when
  ['למה', 'לָמָּה'], // why
  ['איך', 'אֵיךְ'], // how
  ['כמה', 'כַּמָּה'], // how much/many

  // Medical & Health
  ['כואב', 'כּוֹאֵב'], // hurts/pain
  ['חולה', 'חוֹלֶה'], // sick
  ['רופא', 'רוֹפֵא'], // doctor
  ['אחות', 'אָחוֹת'], // nurse (same as sister)
  ['תרופה', 'תְּרוּפָה'], // medicine
  ['בריא', 'בָּרִיא'], // healthy
  ['מרגיש', 'מַרְגִּישׁ'], // feeling

  // Activities & Hobbies
  ['טלוויזיה', 'טֶלֶוִיזְיָה'], // television
  ['מוזיקה', 'מוּזִיקָה'], // music
  ['ספר', 'סֵפֶר'], // book
  ['משחק', 'מִשְׂחָק'], // game/toy
  ['כדור', 'כַּדּוּר'], // ball
  ['ציור', 'צִיּוּר'], // drawing/painting

  // Colors
  ['אדום', 'אָדֹם'], // red
  ['כחול', 'כָּחֹל'], // blue
  ['ירוק', 'יָרֹק'], // green
  ['צהוב', 'צָהֹב'], // yellow
  ['שחור', 'שָׁחֹר'], // black
  ['לבן', 'לָבָן'], // white
];

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export class PronunciationDictionary {
  /**
   * Built-in dictionary of common Hebrew words that need nikud for correct pronunciation.
   * Key: Word without nikud, Value: Word with nikud
   */
  private builtInHebrewDictionary: Map<string, string> = new Map(BUILT_IN_HEBREW_ENTRIES);

  /**
   * User-defined custom pronunciations.
   * This map can be modified by caregivers to add their own corrections.
   */
  private customDictionary: Map<string, string> = new Map();

  /**
   * Apply pronunciation corrections to the given text.
   * @param text The text to correct
   * @param languageCode The language code (e.g., "iw", "he")
   * @returns Corrected text with nikud where applicable
   */
  applyCorrections(text: string, languageCode: string): string {
    if (languageCode !== 'iw' && languageCode !== 'he') {
      return text;
    }

    let correctedText = text;

    // Apply custom dictionary first (user preferences take priority)
    for (const [original, replacement] of this.customDictionary) {
      // Use Unicode-letter-boundary regex to match whole words only (JS has no \b that works
      // correctly across Hebrew, mirroring Kotlin's `(?<!\p{L})...(?!\p{L})`).
      const regex = new RegExp(`(?<!\\p{L})${escapeRegExp(original)}(?!\\p{L})`, 'gu');
      correctedText = correctedText.replace(regex, replacement);
    }

    // Then apply built-in dictionary
    for (const [original, replacement] of this.builtInHebrewDictionary) {
      // Only apply if not already replaced by custom dictionary
      const regex = new RegExp(`(?<!\\p{L})${escapeRegExp(original)}(?!\\p{L})`, 'gu');
      correctedText = correctedText.replace(regex, replacement);
    }

    return correctedText;
  }

  /**
   * Add a custom pronunciation correction.
   * @param original The word without nikud
   * @param corrected The word with nikud or phonetic spelling
   */
  addCustomPronunciation(original: string, corrected: string): void {
    this.customDictionary.set(original, corrected);
  }

  /**
   * Remove a custom pronunciation correction.
   * @param original The word to remove from custom dictionary
   */
  removeCustomPronunciation(original: string): void {
    this.customDictionary.delete(original);
  }

  /**
   * Get all custom pronunciations.
   * @returns Map of custom pronunciations
   */
  getCustomPronunciations(): Map<string, string> {
    return new Map(this.customDictionary);
  }

  /**
   * Get all built-in pronunciations (read-only).
   * @returns Map of built-in pronunciations
   */
  getBuiltInPronunciations(): Map<string, string> {
    return this.builtInHebrewDictionary;
  }

  /**
   * Clear all custom pronunciations.
   */
  clearCustomPronunciations(): void {
    this.customDictionary.clear();
  }

  /**
   * Load custom pronunciations from a map (e.g., from persistent storage).
   */
  loadCustomPronunciations(pronunciations: Map<string, string> | Record<string, string>): void {
    this.customDictionary.clear();
    const entries = pronunciations instanceof Map ? pronunciations.entries() : Object.entries(pronunciations);
    for (const [key, value] of entries) {
      this.customDictionary.set(key, value);
    }
  }
}
