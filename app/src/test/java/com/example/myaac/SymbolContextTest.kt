package com.example.myaac

import com.example.myaac.data.remote.ArasaacService
import com.example.myaac.data.remote.GeminiService
import com.example.myaac.data.remote.GlobalSymbolsService
import com.example.myaac.data.remote.GoogleImageService
import com.example.myaac.data.remote.SymbolService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.util.Properties

class SymbolContextTest {

    private fun getProperty(key: String): String {
        val props = Properties()
        val propFile = File("../local.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { props.load(it) }
            return props.getProperty(key) ?: ""
        }
        return System.getenv(key) ?: ""
    }

    @Test
    fun testSymbolContext() = runBlocking {
        val geminiKey = getProperty("GEMINI_API_KEY")
        val googleKey = getProperty("GOOGLE_SEARCH_API_KEY")
        val googleCx = getProperty("GOOGLE_SEARCH_ENGINE_ID")

        if (geminiKey.isEmpty()) {
            println("SKIP: No Gemini API Key found")
            return@runBlocking
        }

        val geminiService = GeminiService(geminiKey)
        
        // Initialize all vendors
        val vendors = mapOf(
            "Arasaac" to ArasaacService(),
            "Mulberry" to GlobalSymbolsService("mulberry"),
            "Google" to GoogleImageService(googleKey, googleCx)
        )

        val topics = listOf(
            "Golf",
            "Nightclub",
            "Kitchen",
            "Hospital",
            "Zoo",
            "Beach"
        )

        val report = StringBuilder()
        report.append("# Symbol Context Test Report (Multi-Vendor)\n\n")

        var totalChecked = 0
        var totalCorrect = 0

        try {
            for (topic in topics) {
                println("Processing topic: $topic...")
                report.append("## Topic: $topic\n\n")
                
                try {
                    val words = geminiService.generateBoard(topic, count = 5) // Reduced count for speed
                    report.append("| Word | Arasaac | Mulberry | Google Images |\n")
                    report.append("|---|---|---|---|\n")

                    for (word in words) {
                        val rowReport = StringBuilder("| **$word** |")
                        
                        for ((vendorName, service) in vendors) {
                            try {
                                val symbols = service.search(word, "en")
                                val symbolUrl = symbols.firstOrNull()?.url

                                if (symbolUrl != null) {
                                    totalChecked++
                                    val prompt = """
                                        I am building an AAC (Augmentative and Alternative Communication) board for the topic '$topic'.
                                        The item label is '$word'.
                                        The system selected this symbol: $symbolUrl
                                        
                                        Does this symbol correctly represent '$word' in the context of '$topic'?
                                        (e.g., if the topic is 'Golf', the word 'Club' should be a golf club, not a dancing club).
                                        
                                        Search context for this symbol: $vendorName Symbol/Image
                                        
                                        Respond with:
                                        MATCH: [YES/NO]
                                        REASON: [Brief explanation]
                                    """.trimIndent()
                                    
                                    val evaluation = geminiService.answerQuestion(prompt)
                                    val isMatch = evaluation.contains("MATCH: YES", ignoreCase = true)
                                    
                                    if (isMatch) totalCorrect++

                                    val statusIcon = if (isMatch) "✅" else "❌"
                                    rowReport.append(" [Link]($symbolUrl) $statusIcon<br><span style='font-size:0.8em'>${evaluation.lines().firstOrNull() ?: ""}</span> |")
                                } else {
                                    rowReport.append(" No symbol ⚠️ |")
                                }
                            } catch (e: Exception) {
                                println("Error processing $vendorName for '$word': ${e.message}")
                                rowReport.append(" Error ⚠️ |")
                            }
                        }
                        rowReport.append("\n")
                        report.append(rowReport)
                    }
                } catch (e: Exception) {
                     println("Error processing topic '$topic': ${e.message}")
                     report.append("Error processing topic: ${e.message}\n")
                }
                report.append("\n---\n")
            }
        } finally {
            val accuracy = if (totalChecked > 0) (totalCorrect.toDouble() / totalChecked * 100).toInt() else 0
            report.append("\n# Final Conclusion\n")
            report.append("- Total Symbols Checked: $totalChecked\n")
            report.append("- Correct Context: $totalCorrect\n")
            report.append("- Accuracy: $accuracy%\n")

            println("\nTest Completed. Accuracy: $accuracy%")
            
            File("symbol_context_report.md").writeText(report.toString())
            println("Report written to symbol_context_report.md")
        }
    }
}
