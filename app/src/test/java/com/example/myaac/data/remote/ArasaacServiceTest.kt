package com.example.myaac.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ArasaacServiceTest {

    private val service = ArasaacService()

    @Test
    fun `getImageUrl returns correct URL format`() {
        val id = 12345
        val expectedUrl = "https://static.arasaac.org/pictograms/12345/12345_300.png"
        
        val result = service.getImageUrl(id)
        
        assertEquals(expectedUrl, result)
    }
    
    @Test
    fun `getImageUrl handles zero id`() {
        val id = 0
        val expectedUrl = "https://static.arasaac.org/pictograms/0/0_300.png"
        assertEquals(expectedUrl, service.getImageUrl(id))
    }
}
