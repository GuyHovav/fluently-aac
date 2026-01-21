package com.example.myaac.data.nlp

/**
 * Curated vocabulary for AAC (Augmentative and Alternative Communication) users
 * Optimized for common communication needs of speech-impaired individuals
 */
object AacVocabulary {
    
    /**
     * Get AAC-optimized starter words for beginning sentences
     * Prioritizes high-frequency communication needs
     */
    fun getStarterWords(languageCode: String = "en"): List<String> {
        return when (languageCode) {
            "en" -> listOf(
                // First person (most common in AAC)
                "I", "me", "my",
                
                // Core requests
                "want", "need", "like", "help",
                
                // Yes/No
                "yes", "no", "maybe",
                
                // Questions
                "what", "where", "when", "who", "why", "how",
                
                // Politeness
                "please", "thank", "sorry",
                
                // Common verbs
                "go", "come", "see", "feel", "have", "get"
            )
            
            "iw" -> listOf(
                // First person
                "אני", "שלי", "אותי",
                
                // Core requests
                "רוצה", "צריך", "אוהב", "עזרה",
                
                // Yes/No
                "כן", "לא", "אולי",
                
                // Questions
                "מה", "איפה", "מתי", "מי", "למה", "איך",
                
                // Politeness
                "בבקשה", "תודה", "סליחה",
                
                // Common verbs
                "ללכת", "לבוא", "לראות", "מרגיש", "יש", "לקבל"
            )
            
            else -> getStarterWords("en")
        }
    }
    
    /**
     * Comprehensive AAC vocabulary (200+ essential words)
     * Categorized by communication function
     */
    fun getCoreVocabulary(languageCode: String = "en"): List<String> {
        return when (languageCode) {
            "en" -> listOf(
                // === PRONOUNS & BASIC ===
                "i", "you", "he", "she", "we", "they", "it", "this", "that", 
                "me", "my", "your", "mine", "yours",
                
                // === CORE REQUESTS (High Priority) ===
                "want", "need", "like", "love", "help", "stop", "go", "come",
                "more", "again", "done", "finished", "ready",
                
                // === VERBS (Actions) ===
                "eat", "drink", "sleep", "wake", "play", "watch", "listen",
                "read", "write", "draw", "make", "do", "have", "get", "give",
                "take", "put", "open", "close", "turn", "move", "sit", "stand",
                "walk", "run", "jump", "throw", "catch", "push", "pull",
                "look", "see", "hear", "feel", "think", "know", "remember",
                "understand", "learn", "teach", "show", "tell", "say", "ask",
                "call", "talk", "speak",
                
                // === SOCIAL & COURTESY ===
                "yes", "no", "ok", "okay", "please", "thanks", "thank", "sorry",
                "excuse", "welcome", "hello", "hi", "bye", "goodbye", "goodnight",
                "good", "morning", "afternoon", "evening",
                
                // === QUESTIONS ===
                "what", "where", "when", "who", "why", "how", "which", "can",
                
                // === FEELINGS & STATES ===
                "happy", "sad", "mad", "angry", "scared", "worried", "excited",
                "tired", "sick", "hurt", "pain", "hungry", "thirsty", "full",
                "hot", "cold", "comfortable", "uncomfortable", "good", "bad",
                "better", "worse", "fine", "well",
                
                // === MEDICAL & CARE ===
                "bathroom", "toilet", "medicine", "doctor", "nurse", "hospital",
                "wheelchair", "bed", "shower", "bath", "clean", "dirty",
                "change", "diaper", "position", "comfortable",
                
                // === ADJECTIVES (Descriptors) ===
                "big", "small", "little", "large", "long", "short", "tall",
                "wide", "narrow", "thick", "thin", "heavy", "light",
                "fast", "slow", "loud", "quiet", "soft", "hard",
                "new", "old", "young", "same", "different",
                "easy", "hard", "difficult", "simple",
                "nice", "mean", "kind", "rude", "fun", "boring",
                
                // === TIME ===
                "now", "later", "soon", "today", "tomorrow", "yesterday",
                "morning", "afternoon", "evening", "night", "day",
                "always", "never", "sometimes", "often",
                "before", "after", "next", "last", "first",
                
                // === PLACES ===
                "home", "house", "room", "kitchen", "bedroom", "bathroom",
                "school", "class", "work", "office", "hospital", "clinic",
                "store", "shop", "restaurant", "park", "outside", "inside",
                "here", "there", "near", "far",
                
                // === PREPOSITIONS ===
                "in", "on", "at", "to", "from", "with", "without",
                "for", "about", "by", "up", "down", "out", "off",
                "over", "under", "above", "below", "beside", "between",
                "behind", "front", "next", "near",
                
                // === COMMON OBJECTS ===
                "food", "water", "drink", "cup", "plate", "spoon", "fork",
                "phone", "tv", "television", "computer", "tablet", "book",
                "music", "movie", "game", "toy", "chair", "table", "door",
                "window", "light", "blanket", "pillow", "clothes",
                
                // === FAMILY & PEOPLE ===
                "mom", "mother", "dad", "father", "parent", "brother", "sister",
                "family", "friend", "person", "people", "man", "woman", "child",
                "baby", "boy", "girl", "caregiver", "aide", "therapist"
            )
            
            "iw" -> listOf(
                // === כינויים ובסיס ===
                "אני", "אתה", "את", "הוא", "היא", "אנחנו", "הם", "הן",
                "זה", "זאת", "שלי", "שלך", "שלו", "שלה",
                
                // === בקשות ליבה ===
                "רוצה", "צריך", "אוהב", "עזרה", "עצור", "לך", "בוא",
                "עוד", "שוב", "גמור", "סיים", "מוכן",
                
                // === פעלים ===
                "לאכול", "לשתות", "לישון", "להתעורר", "לשחק", "לראות", "לשמוע",
                "לקרוא", "לכתוב", "לעשות", "יש", "לקבל", "לתת", "לשים",
                "לפתוח", "לסגור", "לזוז", "לשבת", "לעמוד", "ללכת",
                "מרגיש", "חושב", "יודע", "זוכר", "מבין",
                
                // === נימוס ===
                "כן", "לא", "בסדר", "בבקשה", "תודה", "סליחה",
                "שלום", "להתראות", "בוקר", "ערב", "לילה", "טוב",
                
                // === שאלות ===
                "מה", "איפה", "מתי", "מי", "למה", "איך", "יכול",
                
                // === רגשות ===
                "שמח", "עצוב", "כועס", "פוחד", "עייף", "חולה",
                "כואב", "רעב", "צמא", "חם", "קר", "טוב", "רע",
                
                // === רפואי ===
                "שירותים", "תרופה", "רופא", "אחות", "בית חולים",
                "מיטה", "מקלחת", "נקי", "מלוכלך",
                
                // === זמן ===
                "עכשיו", "מאוחר", "היום", "מחר", "אתמול",
                "בוקר", "צהריים", "ערב", "לילה", "תמיד", "לעולם לא",
                
                // === מקומות ===
                "בית", "חדר", "מטבח", "חדר שינה", "שירותים",
                "בית ספר", "עבודה", "חנות", "פארק", "כאן", "שם",
                
                // === מילות יחס ===
                "ב", "על", "ל", "עם", "בלי", "למעלה", "למטה",
                "מעל", "מתחת", "ליד", "בין", "אחרי", "לפני",
                
                // === משפחה ===
                "אמא", "אבא", "אח", "אחות", "משפחה", "חבר", "איש", "אישה"
            )
            
            else -> getCoreVocabulary("en")
        }
    }
    
    /**
     * Get context-specific predictions based on the last word
     */
    fun getContextPredictions(lastWord: String, languageCode: String = "en"): List<String> {
        val word = lastWord.lowercase().trim()
        
        return when (languageCode) {
            "en" -> when (word) {
                "i" -> listOf("want", "need", "like", "am", "feel", "have", "can", "will")
                "want" -> listOf("to", "water", "food", "help", "more", "go", "eat", "drink")
                "need" -> listOf("help", "water", "food", "to", "bathroom", "medicine", "rest")
                "feel" -> listOf("good", "bad", "tired", "happy", "sad", "sick", "hungry")
                "am" -> listOf("tired", "hungry", "thirsty", "happy", "sad", "sick", "ready")
                "can" -> listOf("i", "you", "help", "go", "we", "please")
                "go" -> listOf("to", "home", "bathroom", "outside", "now", "later")
                "help" -> listOf("me", "please", "now")
                else -> emptyList()
            }
            
            "iw" -> when (word) {
                "אני" -> listOf("רוצה", "צריך", "אוהב", "מרגיש", "יכול", "הולך")
                "רוצה" -> listOf("ל", "מים", "אוכל", "עזרה", "עוד", "ללכת")
                "צריך" -> listOf("עזרה", "מים", "אוכל", "שירותים", "תרופה")
                "מרגיש" -> listOf("טוב", "רע", "עייף", "שמח", "עצוב", "חולה")
                else -> emptyList()
            }
            
            else -> getContextPredictions(lastWord, "en")
        }
    }
}
