package com.example.myaac.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class CompositeSymbolServiceTest {

    // Mock service that returns empty results
    class EmptySymbolService : SymbolService {
        override suspend fun search(query: String, language: String): List<SymbolResult> {
            return emptyList()
        }
    }

    // Mock service that returns results
    class SuccessSymbolService(private val results: List<SymbolResult>) : SymbolService {
        override suspend fun search(query: String, language: String): List<SymbolResult> {
            return results
        }
    }

    // Mock service that throws exception
    class FailingSymbolService : SymbolService {
        override suspend fun search(query: String, language: String): List<SymbolResult> {
            throw Exception("Service failed")
        }
    }

    @Test
    fun testFallbackToSecondService() = runBlocking {
        val expectedResults = listOf(
            SymbolResult("http://example.com/image.png", "test")
        )
        
        val composite = CompositeSymbolService(
            services = listOf(
                EmptySymbolService(),
                SuccessSymbolService(expectedResults)
            ),
            combineResults = false
        )

        val results = composite.search("test", "en")
        assertEquals(expectedResults, results)
    }

    @Test
    fun testPrimaryServiceReturnsResults() = runBlocking {
        val primaryResults = listOf(
            SymbolResult("http://primary.com/image.png", "primary")
        )
        val secondaryResults = listOf(
            SymbolResult("http://secondary.com/image.png", "secondary")
        )
        
        val composite = CompositeSymbolService(
            services = listOf(
                SuccessSymbolService(primaryResults),
                SuccessSymbolService(secondaryResults)
            ),
            combineResults = false
        )

        val results = composite.search("test", "en")
        assertEquals(primaryResults, results)
    }

    @Test
    fun testCombineResults() = runBlocking {
        val primaryResults = listOf(
            SymbolResult("http://primary.com/image.png", "primary")
        )
        val secondaryResults = listOf(
            SymbolResult("http://secondary.com/image.png", "secondary")
        )
        
        val composite = CompositeSymbolService(
            services = listOf(
                SuccessSymbolService(primaryResults),
                SuccessSymbolService(secondaryResults)
            ),
            combineResults = true
        )

        val results = composite.search("test", "en")
        assertEquals(2, results.size)
        assertTrue(results.containsAll(primaryResults))
        assertTrue(results.containsAll(secondaryResults))
    }

    @Test
    fun testHandlesServiceFailure() = runBlocking {
        val expectedResults = listOf(
            SymbolResult("http://example.com/image.png", "test")
        )
        
        val composite = CompositeSymbolService(
            services = listOf(
                FailingSymbolService(),
                SuccessSymbolService(expectedResults)
            ),
            combineResults = false
        )

        val results = composite.search("test", "en")
        assertEquals(expectedResults, results)
    }

    @Test
    fun testAllServicesFail() = runBlocking {
        val composite = CompositeSymbolService(
            services = listOf(
                FailingSymbolService(),
                EmptySymbolService()
            ),
            combineResults = false
        )

        val results = composite.search("test", "en")
        assertTrue(results.isEmpty())
    }
}
