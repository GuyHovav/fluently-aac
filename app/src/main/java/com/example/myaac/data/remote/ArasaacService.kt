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
        "mine" to 33744  // Possessive
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
