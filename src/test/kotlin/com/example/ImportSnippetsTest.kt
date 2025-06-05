package com.example

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ImportSnippetsTest {
    private val sampleJson = """[
        {"id":"1","title":"Test","code":"println(\"Hello\")"}
    ]"""

    @Test
    fun `importSnippetsFromFile should create new snippet with new ID and return feedback`() {
        val tempFile = Files.createTempFile("snippets", ".json")
        Files.writeString(tempFile, sampleJson)
        val initialList = emptyList<Snippet>()
        var result: List<Snippet>? = null
        var feedback: String? = null

        importSnippetsFromFile(tempFile, initialList) { newSnippets, fb ->
            result = newSnippets
            feedback = fb
        }

        assertNotNull(result)
        assertEquals(1, result!!.size)
        val snippet = result!![0]
        assertNotEquals("1", snippet.id)
        assertEquals("Test", snippet.title)
        assertEquals("println(\"Hello\")", snippet.code)
        assertTrue(feedback!!.contains("1件"), "Feedback should mention number of imported snippets")
    }

    @Test
    fun `importSnippetsFromFile should handle non-existent file`() {
        val nonExist = Paths.get("does_not_exist_${System.currentTimeMillis()}.json")
        val initial = listOf(Snippet("orig","Orig","code"))
        var result: List<Snippet>? = listOf()
        var feedback: String? = null

        importSnippetsFromFile(nonExist, initial) { newSnippets, fb ->
            result = newSnippets
            feedback = fb
        }

        assertNull(result)
        assertTrue(feedback!!.contains("見つかりません"))
    }
} 