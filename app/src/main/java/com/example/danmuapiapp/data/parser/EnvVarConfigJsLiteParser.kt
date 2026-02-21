package com.example.danmuapiapp.data.parser

import android.util.Log

internal class EnvVarConfigJsLiteParser(
    private val src: String,
    startIndex: Int,
    private val identifiers: Map<String, Any>,
) {
    private var i: Int = startIndex
    private val n: Int = src.length

    fun parseValue(): Any? {
        skipWsAndComments()
        if (i >= n) return null
        return when (src[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '\'', '"' -> parseString()
            '-', in '0'..'9' -> parseNumber()
            else -> parseIdentifierOrLiteral()
        }
    }

    private fun parseObject(): LinkedHashMap<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        expect('{')
        skipWsAndComments()
        if (peek('}')) { i++; return out }
        while (i < n) {
            skipWsAndComments()
            val key = parseKey()
            skipWsAndComments()
            expect(':')
            val value = parseValue()
            out[key] = value
            skipWsAndComments()
            if (peek(',')) {
                i++; skipWsAndComments()
                if (peek('}')) { i++; break }
                continue
            }
            if (peek('}')) { i++; break }
            i++
        }
        return out
    }

    private fun parseArray(): ArrayList<Any?> {
        val out = ArrayList<Any?>()
        expect('[')
        skipWsAndComments()
        if (peek(']')) { i++; return out }
        while (i < n) {
            out.add(parseValue())
            skipWsAndComments()
            if (peek(',')) {
                i++; skipWsAndComments()
                if (peek(']')) { i++; break }
                continue
            }
            if (peek(']')) { i++; break }
            i++
        }
        return out
    }

    private fun parseKey(): String {
        skipWsAndComments()
        if (i >= n) return ""
        return when (src[i]) {
            '\'', '"' -> parseString()
            else -> parseIdentifierToken()
        }
    }

    private fun parseString(): String {
        val quote = src[i]; i++
        val sb = StringBuilder()
        while (i < n) {
            val c = src[i]
            if (c == quote) { i++; break }
            if (c == '\\' && i + 1 < n) {
                when (src[i + 1]) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r')
                    't' -> sb.append('\t'); '\\' -> sb.append('\\')
                    '\'' -> sb.append('\''); '"' -> sb.append('"')
                    else -> sb.append(src[i + 1])
                }
                i += 2; continue
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    private fun parseNumber(): Any {
        val start = i
        if (i < n && src[i] == '-') i++
        while (i < n && src[i].isDigit()) i++
        var isFloat = false
        if (i < n && src[i] == '.') { isFloat = true; i++; while (i < n && src[i].isDigit()) i++ }
        if (i < n && (src[i] == 'e' || src[i] == 'E')) {
            isFloat = true; i++
            if (i < n && (src[i] == '+' || src[i] == '-')) i++
            while (i < n && src[i].isDigit()) i++
        }
        val raw = src.substring(start, i)
        return try { if (isFloat) raw.toDouble() else raw.toLong() } catch (_: Throwable) { 0 }
    }
    private fun parseIdentifierOrLiteral(): Any? {
        val ident = parseIdentifierToken()
        return when (ident) {
            "true" -> true; "false" -> false
            "null", "undefined" -> null
            else -> identifiers[ident] ?: ident
        }
    }

    private fun parseIdentifierToken(): String {
        skipWsAndComments()
        val start = i
        while (i < n) {
            val c = src[i]
            if (c.isLetterOrDigit() || c == '_' || c == '$' || c == '.') { i++; continue }
            break
        }
        return src.substring(start, i).trim()
    }

    private fun skipWsAndComments() {
        while (i < n) {
            val c = src[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') { i++; continue }
            if (c == '/' && i + 1 < n) {
                if (src[i + 1] == '/') { i += 2; while (i < n && src[i] != '\n') i++; continue }
                if (src[i + 1] == '*') { i += 2; while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) i++; if (i + 1 < n) i += 2; continue }
            }
            break
        }
    }

    private fun expect(ch: Char) {
        skipWsAndComments()
        if (i < n && src[i] == ch) { i++; return }
        Log.w(TAG, "Expected '$ch' at $i")
    }

    private fun peek(ch: Char): Boolean {
        skipWsAndComments()
        return i < n && src[i] == ch
    }

    companion object {
        private const val TAG = "EnvVarCfgJsParser"
    }
}
