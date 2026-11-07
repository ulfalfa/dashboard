package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RotationTest {
    private val a = Page("https://a", 5, 100, 0)
    private val b = Page("https://b", 5, 100, 0)

    @Test
    fun startsAtFirstPage() {
        assertEquals(a, Rotation(listOf(a, b)).current())
    }

    @Test
    fun wrapsAroundAfterLastPage() {
        val rotation = Rotation(listOf(a, b))
        assertEquals(b, rotation.advance())
        assertEquals(a, rotation.advance())
    }

    @Test
    fun singlePageStaysOnSamePage() {
        assertEquals(a, Rotation(listOf(a)).advance())
    }

    @Test
    fun rejectsEmptyList() {
        assertThrows(IllegalArgumentException::class.java) { Rotation(emptyList()) }
    }
}
