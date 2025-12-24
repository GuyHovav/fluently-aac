package com.example.myaac.data.remote

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ArasaacPictogram(
    val _id: Int,
    val keywords: List<ArasaacKeyword> = emptyList()
)

data class ArasaacKeyword(
    val keyword: String
)

open class ArasaacService {
    private val gson = Gson()
    private val BASE_URL_TEMPLATE = "https://api.arasaac.org/v1/pictograms/%s/search/"

    open suspend fun searchPictograms(query: String, locale: String = "en"): List<ArasaacPictogram> {
        return withContext(Dispatchers.IO) {
            try {
                // Encode spaces for URL
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val urlString = String.format(BASE_URL_TEMPLATE, locale) + encodedQuery
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val listType = object : TypeToken<List<ArasaacPictogram>>() {}.type
                    gson.fromJson(json, listType)
                } else {
                    emptyList()
                }
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
