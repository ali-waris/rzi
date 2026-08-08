package com.hc.rzi.domain.text

import java.security.MessageDigest

object DedupeKey {

    private val WHITESPACE = Regex("\\s+")
    private const val SEPARATOR = " "

    fun normalize(value: String): String =
        value.trim().replace(WHITESPACE, " ").lowercase()

    fun of(text: String, bookName: String, pageNumber: Int?): String {
        val canonical = buildString {
            append(normalize(text))
            append(SEPARATOR)
            append(normalize(bookName))
            append(SEPARATOR)
            append(pageNumber?.toString().orEmpty())
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
