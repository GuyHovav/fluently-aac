package com.example.myaac.util

import java.util.Locale

object GrammarEngine {

    private val pronouns = setOf("i", "you", "he", "she", "it", "we", "they", "me", "him", "her", "us", "them")
    private val verbs = setOf(
        "want", "like", "need", "love", "go", "see", "hear", "feel", "eat", "drink", 
        "sleep", "play", "work", "help", "stop", "come", "get", "take", "make", "do", "say"
    )
    private val adjectives = setOf(
        "happy", "sad", "mad", "hungry", "tired", "sick", "scared", "excited", 
        "good", "bad", "big", "small", "hot", "cold", "thirsty", "angry"
    )
    private val nouns = setOf(
        "apple", "banana", "cookie", "sandwich", "water", "milk", "juice", 
        "book", "crayon", "tablet", "bathroom", "home", "school", "mom", "dad",
        "toy", "ball", "car", "dog", "cat", "friend", "teacher"
    )

    private val articles = setOf("a", "an", "the")
    private val prepositions = setOf("to", "for", "with", "at", "by", "from", "in", "on")

    /**
     * Attempts to fix a list of words to be more grammatically correct.
     * Example: ["i", "want", "play"] -> "I want to play"
     */
    fun fixSentence(words: List<String>, languageCode: String = "en"): String {
        if (words.isEmpty()) return ""
        if (languageCode != "en" && languageCode != "he" && languageCode != "iw") {
            return words.joinToString(" ").replaceFirstChar { it.uppercase() }
        }

        // Basic Hebrew handling: join with spaces and capitalize (not really capitalization in Hebrew, but first char if English)
        if (languageCode == "he" || languageCode == "iw") {
             return words.joinToString(" ")
        }

        val processedWords = mutableListOf<String>()
        val input = words.flatMap { it.lowercase().split(" ") }.filter { it.isNotBlank() }

        if (input.isEmpty()) return ""

        for (i in input.indices) {
            val current = input[i]
            val next = input.getOrNull(i + 1)

            processedWords.add(current)

            if (next != null) {
                // Rule 1: Verb + Verb -> Insert "to"
                if (isVerb(current) && isVerb(next)) {
                    if (current != "stop" && current != "do" && current != "can" && next != "to") {
                        processedWords.add("to")
                    }
                }

                // Rule 2: Pronoun + Adjective -> Insert am/is/are
                if (isPronoun(current) && isAdjective(next)) {
                    val copula = when (current) {
                        "i" -> "am"
                        "you", "we", "they" -> "are"
                        "he", "she", "it" -> "is"
                        else -> null
                    }
                    if (copula != null && next != copula) {
                        processedWords.add(copula)
                    }
                }

                // Rule 3: Verb + Noun -> Insert article
                if (isVerb(current) && isNoun(next)) {
                   if (!isNonCountable(next) && !articles.contains(current) && !prepositions.contains(current)) {
                       val article = if ("aeiou".contains(next[0])) "an" else "a"
                       processedWords.add(article)
                   }
                }
            }
        }

        // Post-processing: remove duplicates if they happened (e.g. "want to to play")
        val finalWords = mutableListOf<String>()
        for (word in processedWords) {
            if (finalWords.isEmpty() || finalWords.last() != word) {
                finalWords.add(word)
            }
        }

        return finalWords.joinToString(" ") { word ->
            if (word == "i") "I" else word
        }.replaceFirstChar { it.uppercase() }
    }

    private fun isVerb(word: String) = verbs.contains(word)
    private fun isPronoun(word: String) = pronouns.contains(word)
    private fun isAdjective(word: String) = adjectives.contains(word)
    private fun isNoun(word: String) = nouns.contains(word)
    
    private fun isNonCountable(word: String) = setOf("water", "milk", "juice", "bread", "help", "work", "sleep").contains(word)
}
