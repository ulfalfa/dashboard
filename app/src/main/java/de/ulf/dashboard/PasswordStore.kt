package de.ulf.dashboard

import java.io.File
import java.io.IOException
import java.security.SecureRandom

/** Portal password: generated randomly on first use, persisted in [file] and replaceable via [regenerate]. */
class PasswordStore(private val file: File) {
    private var cached: String? = null

    /** @throws IOException if the password cannot be read or persisted */
    @Synchronized
    fun load(): String {
        cached?.let { return it }
        val stored = if (file.exists()) file.readText(Charsets.UTF_8).trim() else ""
        return stored.ifEmpty { generateAndStore() }.also { cached = it }
    }

    /** @throws IOException if the new password cannot be persisted; the old one stays valid then */
    @Synchronized
    fun regenerate(): String = generateAndStore().also { cached = it }

    private fun generateAndStore(): String {
        val random = SecureRandom()
        val password = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })
        // Write to a temp file first so a crash mid-write never leaves a truncated password
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(password, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            throw IOException("Could not replace ${file.path} - check free storage space and retry")
        }
        return password
    }

    private companion object {
        const val LENGTH = 12
        const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no look-alike characters
    }
}
