package com.hc.rzi.domain.text

object FtsQuery {

    private val DISALLOWED = Regex("[^\\p{L}\\p{Nd}]+")

    fun sanitize(raw: String): String? {
        val tokens = raw
            .replace(DISALLOWED, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }
}
