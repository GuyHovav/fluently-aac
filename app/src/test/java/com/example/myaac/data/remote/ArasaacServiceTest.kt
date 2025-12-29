package com.example.myaac.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ArasaacServiceTest {

    @Test
    fun testSearchDog() = runBlocking {
        val service = ArasaacService()
        println("Searching for 'dog'...")
        val results = service.search("dog", "en")
        println("Results for 'dog': ${results.size}")
        results.forEach { println(" - ${it.label} (${it.url})") }
        assertTrue("Should return results for 'dog'", results.isNotEmpty())
    }

    @Test
    fun testSearchHotDog() = runBlocking {
        val service = ArasaacService()
        println("Searching for 'hot dog'...")
        val results = service.search("hot dog", "en")
        println("Results for 'hot dog': ${results.size}")
        results.forEach { println(" - ${it.label} (${it.url})") }
        assertTrue("Should return results for 'hot dog'", results.isNotEmpty())
    }
}
