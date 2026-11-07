package de.ulf.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PasswordStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(file: File) = PasswordStore(file)

    @Test
    fun generatesOnFirstUseAndPersists() {
        val file = File(tmp.root, "pw.txt")
        val first = store(file).load()
        assertEquals(12, first.length)
        assertEquals(first, store(file).load()) // a new instance reads it from disk
    }

    @Test
    fun regenerateReplacesPasswordAndPersists() {
        val file = File(tmp.root, "pw.txt")
        val s = store(file)
        val old = s.load()
        val new = s.regenerate()
        assertNotEquals(old, new)
        assertEquals(new, s.load())
        assertEquals(new, store(file).load())
    }

    @Test
    fun blankFileGetsNewPassword() {
        val file = File(tmp.root, "pw.txt").apply { writeText("  \n") }
        assertTrue(store(file).load().isNotBlank())
    }
}
