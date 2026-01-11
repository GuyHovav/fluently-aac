package com.example.myaac.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query

data class GoogleImageSearchResponse(
    val items: List<GoogleImageItem>? = null
)

data class GoogleImageItem(
    val link: String,
    val mime: String? = null,
    val title: String? = null
)

interface GoogleImageSearchApi {
    @GET("v1")
    suspend fun search(
        @Query("key") apiKey: String,
        @Query("cx") searchEngineId: String,
        @Query("q") query: String,
        @Query("searchType") searchType: String = "image",
        @Query("num") num: Int = 3,
        @Query("safe") safe: String = "active"
    ): GoogleImageSearchResponse
}

class GoogleImageService(
    private val explicitApiKey: String? = null,
    private val explicitSearchEngineId: String? = null
) : SymbolService {
    private val BASE_URL = "https://www.googleapis.com/customsearch/"
    
    private val api: GoogleImageSearchApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(GoogleImageSearchApi::class.java)
    }
    
    override suspend fun search(query: String, language: String): List<SymbolResult> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = explicitApiKey ?: com.example.myaac.BuildConfig.GOOGLE_SEARCH_API_KEY
                val searchEngineId = explicitSearchEngineId ?: com.example.myaac.BuildConfig.GOOGLE_SEARCH_ENGINE_ID
                
                if (apiKey.isEmpty() || searchEngineId.isEmpty()) {
                    android.util.Log.w("GoogleImageService", "API key or Search Engine ID not configured")
                    return@withContext emptyList()
                }
                
                // Search with quality modifiers to get cleaner images
                val searchQuery = "$query icon clipart"
                
                val response = api.search(
                    apiKey = apiKey,
                    searchEngineId = searchEngineId,
                    query = searchQuery
                )
                
                // Return all results
                response.items?.map { item ->
                    SymbolResult(
                        url = item.link,
                        label = item.title ?: query
                    )
                } ?: emptyList()
                
            } catch (e: Exception) {
                android.util.Log.e("GoogleImageService", "Error searching for image: $query", e)
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
