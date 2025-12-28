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

open class ArasaacService {
    private val BASE_URL = "https://api.arasaac.org/v1/"

    private val api: ArasaacApi by lazy {
        retrofit2.Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(ArasaacApi::class.java)
    }

    open suspend fun searchPictograms(query: String, locale: String = "en"): List<ArasaacPictogram> {
        return withContext(Dispatchers.IO) {
            try {
                api.searchPictograms(locale, query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    open fun getImageUrl(id: Int): String {
        return "https://static.arasaac.org/pictograms/$id/${id}_300.png"
    }
}
