package com.example.myaac.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query

// Global Symbols API Models
data class GlobalSymbolResponse(
    val id: Int,
    val subject: String, // "name" is actually "subject" in JSON
    val pictos: List<Picto>
)

data class Picto(
    val id: Int,
    val image_url: String
)

interface GlobalSymbolsApi {
    @GET("concepts/suggest")
    suspend fun search(
        @Query("query") query: String,
        @Query("symbolset") symbolset: String = "mulberry", // Default to Mulberry
        @Query("language") language: String,
        @Query("language_iso_format") languageIsoFormat: String = "639-3" // Support 3-letter codes
    ): List<GlobalSymbolResponse>
}

class GlobalSymbolsService(private val symbolSet: String = "mulberry") : SymbolService {
    private val BASE_URL = "https://globalsymbols.com/api/v1/"

    private val api: GlobalSymbolsApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(GlobalSymbolsApi::class.java)
    }

    override suspend fun search(query: String, language: String): List<SymbolResult> {
        return withContext(Dispatchers.IO) {
            try {
                // Map language codes to ISO 639-3 (3-letter)
                // GlobalSymbols API requires 'eng' for English
                val iso3Language = when (language) {
                    "en" -> "eng"
                    "he", "iw" -> "heb"
                    "es" -> "spa"
                    "fr" -> "fra"
                    else -> language // Try as is or default
                }
                
                val results = api.search(
                    query = query, 
                    symbolset = symbolSet,
                    language = iso3Language, 
                    languageIsoFormat = "639-3"
                )
                
                results.flatMap { concept ->
                    concept.pictos.map { picto ->
                        SymbolResult(
                            url = picto.image_url,
                            label = concept.subject
                        )
                    }
                }
            } catch (e: Exception) {
                println("GlobalSymbolsService ERROR: ${e.message}")
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
