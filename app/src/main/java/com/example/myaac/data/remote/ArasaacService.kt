package com.example.myaac.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArasaacPictogram(
    val _id: Int,
    val keywords: List<ArasaacKeyword> = emptyList()
)

data class ArasaacKeyword(
    val keyword: String
)

open class ArasaacService : SymbolService {
    private val BASE_URL = "https://api.arasaac.org/v1/"

    private val api: ArasaacApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ArasaacApi::class.java)
    }
    
    // Manual overrides for common ambiguous words
    private val SYMBOL_OVERRIDES = mapOf(
        "can" to 26521, // Verb "can" / "able to"
        "will" to 26497, // Future tense arrow
        "may" to 22350,  // "Maybe" / possibility
        "like" to 2267,  // Thumbs up / like
        "kind" to 34166, // Kind/nice person
        "saw" to 5858,   // Verb "see" (past) instead of tool
        "well" to 34166, // Good/Well
        "mine" to 33744, // Possessive
        // Pronouns
        "i" to 6632,
        "you" to 6625,
        "we" to 7185,
        "they" to 7032,
        "he" to 6480,
        "she" to 7028,
        // Core Verbs
        "want" to 5441,
        "need" to 37160,
        "feel" to 30197,
        "have" to 32761,
        "go" to 8142,
        // Questions
        "what" to 22620,
        "where" to 7764,
        "when" to 32874,
        "why" to 36719,
        // Be verbs if needed
        "am" to 26521, // Reuse 'can/ability' or find better
        "is" to 26521,
        "are" to 26521
    )

    open suspend fun searchPictograms(query: String, locale: String = "en"): List<ArasaacPictogram> {
        // Check overrides first (only for English single words for now)
        if (locale == "en") {
            val lowerQuery = query.lowercase().trim()
            val overrideId = SYMBOL_OVERRIDES[lowerQuery]
            if (overrideId != null) {
                return listOf(ArasaacPictogram(overrideId, listOf(ArasaacKeyword(query))))
            }
        }
    
        return withContext(Dispatchers.IO) {
            api.searchPictograms(locale, query)
        }
    }
    
    open fun getImageUrl(id: Int): String {
        return "https://static.arasaac.org/pictograms/$id/${id}_300.png"
    }

    override suspend fun search(query: String, language: String): List<SymbolResult> {
        val pictograms = searchPictograms(query, language)
        return pictograms.map { 
            val label = it.keywords.firstOrNull()?.keyword ?: query
            SymbolResult(
                url = getImageUrl(it._id),
                label = label
            )
        }
    }
}
