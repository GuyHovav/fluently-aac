package com.example.myaac.data.nlp

import com.example.myaac.data.local.WordFrequencyDao
import com.example.myaac.data.local.WordFrequencyEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.mockito.kotlin.any

class LocalPredictionEngineTest {

    @Mock
    private lateinit var mockDao: WordFrequencyDao

    private lateinit var engine: LocalPredictionEngine

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        engine = LocalPredictionEngine(mockDao)
    }

    @Test
    fun `predict returns default common words when no history exists`() = runTest {
        // Given no learned data
        whenever(mockDao.getTopWords(5)).thenReturn(emptyList())

        // When predicting with empty context
        val result = engine.predict(emptyList(), 5)

        // Then returns defaults
        assertTrue(result.isNotEmpty())
        assertTrue(result.contains("I"))
        assertTrue(result.contains("want"))
    }

    @Test
    fun `predict uses bigrams for context`() = runTest {
        // Given a known bigram
        val context = listOf("I")
        whenever(mockDao.getBigramsStartingWith("i", 5)).thenReturn(
            listOf(com.example.myaac.data.local.WordBigramEntity(word1 = "i", word2 = "want", frequency = 10))
        )

        // When predicting
        val result = engine.predict(context, 5)

        // Then "want" is suggested
        assertEquals("want", result.first())
    }

    @Test
    fun `recordUsage delegates to dao`() = runTest {
        engine.recordUsage("hello")
        verify(mockDao).recordWordUsage("hello")
    }
}
