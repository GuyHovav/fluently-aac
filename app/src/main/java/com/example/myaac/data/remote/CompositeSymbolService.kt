package com.example.myaac.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A composite symbol service that tries multiple vendors in order.
 * If the primary vendor returns no results, it falls back to the next vendor.
 * 
 * @param services List of symbol services to try, in priority order
 * @param combineResults If true, combines results from all services. If false, returns first non-empty result.
 */
class CompositeSymbolService(
    private val services: List<SymbolService>,
    private val combineResults: Boolean = false,
    private val minRequiredResults: Int = 1
) : SymbolService {
    
    override suspend fun search(query: String, language: String): List<SymbolResult> {
        return withContext(Dispatchers.IO) {
            if (combineResults) {
                // Combine results from all services
                val allResults = mutableListOf<SymbolResult>()
                for (service in services) {
                    try {
                        val results = service.search(query, language)
                        allResults.addAll(results)
                    } catch (e: Exception) {
                        // Log error but continue to next service
                        e.printStackTrace()
                    }
                }
                allResults.distinctBy { it.url }
            } else {
                // Try each service until we get enough results
                val accumulatedResults = mutableListOf<SymbolResult>()
                
                for (service in services) {
                    try {
                        val serviceName = service.javaClass.simpleName
                        android.util.Log.d("CompositeSymbolService", "Searching '$query' in $serviceName...")
                        
                        val results = service.search(query, language)
                        if (results.isNotEmpty()) {
                            android.util.Log.d("CompositeSymbolService", "Found ${results.size} results in $serviceName")
                            accumulatedResults.addAll(results)
                        } else {
                            android.util.Log.d("CompositeSymbolService", "$serviceName returned empty for '$query'")
                        }
                        
                        // If we have enough results, stop. Check unique URLs to avoid counting duplicates.
                        val uniqueCount = accumulatedResults.distinctBy { it.url }.size
                        if (uniqueCount >= minRequiredResults) {
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CompositeSymbolService", "Error in service", e)
                        e.printStackTrace()
                    }
                }
                
                if (accumulatedResults.isEmpty()) {
                    android.util.Log.w("CompositeSymbolService", "All services failed for '$query'")
                }
                
                accumulatedResults.distinctBy { it.url }
            }
        }
    }
}
