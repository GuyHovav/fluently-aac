package com.example.myaac.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query

// Global Symbols API Models
data class GlobalSymbolResponse(
    val id: String,
    val name: String,
    val image_url: String
)

interface GlobalSymbolsApi {
    @GET("concepts/suggest")
    suspend fun search(
        @Query("query") query: String,
        @Query("slug") slug: String = "mulberry", // Default to Mulberry
        @Query("language") language: String
    ): List<GlobalSymbolResponse>
}

class GlobalSymbolsService : SymbolService {
    private val BASE_URL = "https://api.globalsymbols.com/v1/"

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
                // Map language codes if necessary (e.g. 'he' might need check if supported)
                // For now pass as is, often APIs use ISO 639-1
                val results = api.search(query = query, language = language)
                results.map { 
                    SymbolResult(
                        url = it.image_url,
                        label = it.name
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
