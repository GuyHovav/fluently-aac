package com.example.myaac.data.nlp

import simplenlg.features.Feature
import simplenlg.features.Tense
import simplenlg.framework.LexicalCategory
import simplenlg.framework.NLGFactory
import simplenlg.lexicon.Lexicon
import simplenlg.phrasespec.SPhraseSpec
import simplenlg.realiser.english.Realiser

class MorphologyService {

    private val lexicon: Lexicon = Lexicon.getDefaultLexicon()
    private val nlgFactory: NLGFactory = NLGFactory(lexicon)
    private val realiser: Realiser = Realiser(lexicon)

    /**
     * generating variations for a given word and part of speech.
     * For example, "eat" (VERB) -> ["ate", "eating", "will eat", "eats"]
     */
    fun getVariations(word: String, category: String): List<String> {
        val variations = mutableListOf<String>()
        val lexicalCategory = when (category.uppercase()) {
            "VERB" -> LexicalCategory.VERB
            "NOUN" -> LexicalCategory.NOUN
            "ADJECTIVE" -> LexicalCategory.ADJECTIVE
            else -> return emptyList()
        }

        try {
            when (lexicalCategory) {
                LexicalCategory.VERB -> {
                    // Base form
                    variations.add(word) 
                    
                    // Past
                    val pastClause: SPhraseSpec = nlgFactory.createClause()
                    pastClause.setVerb(word)
                    pastClause.setFeature(Feature.TENSE, Tense.PAST)
                    variations.add(realiser.realiseSentence(pastClause).trim().trimEnd('.'))

                    // Present Progressive (ing)
                    val progressiveClause: SPhraseSpec = nlgFactory.createClause()
                    progressiveClause.setVerb(word)
                    progressiveClause.setFeature(Feature.PROGRESSIVE, true)
                    progressiveClause.setFeature(Feature.TENSE, Tense.PRESENT)
                     // SimpleNLG makes full sentences, we might get "is eating". 
                     // Ideally we want just the morphological form "eating".
                     // SimpleNLG is a bit sentence-heavy. 
                     // Let's try to just get the word form if possible, 
                     // or clean up the "is" if it adds auxiliary.
                     // Actually, for AAC "is eating" is often what we want for "present progressive".
                    variations.add(realiser.realiseSentence(progressiveClause).trim().trimEnd('.'))

                    // Future
                    val futureClause: SPhraseSpec = nlgFactory.createClause()
                    futureClause.setVerb(word)
                    futureClause.setFeature(Feature.TENSE, Tense.FUTURE)
                    variations.add(realiser.realiseSentence(futureClause).trim().trimEnd('.'))
                }
                LexicalCategory.NOUN -> {
                     // Singular
                     variations.add(word)

                     // Plural
                     val nounPhrase = nlgFactory.createNounPhrase(word)
                     nounPhrase.setPlural(true)
                     variations.add(realiser.realise(nounPhrase).realisation.trim())
                }
                else -> {
                    // Add other categories if needed
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(word) // Fallback
        }

        return variations.distinct()
    }
}
