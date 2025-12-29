package com.example.myaac.data.remote

data class SymbolResult(
    val url: String,
    val label: String
)

interface SymbolService {
    suspend fun search(query: String, language: String): List<SymbolResult>
}
