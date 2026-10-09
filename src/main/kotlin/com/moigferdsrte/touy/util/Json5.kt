package com.moigferdsrte.touy.util

/** Dependency-free JSON5 reader. Used off the tick hot path; rejects incomplete input. */
object Json5 {
    fun parse(text: String): Any? {
        require(text.length <= 65_536) { "Configuration exceeds 64 KiB" }
        return Reader(text).parse()
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun parse(): Any? {
            val result = value(0)
            skip()
            require(index == text.length) { "Unexpected content at $index" }
            return result
        }

        private fun value(depth: Int): Any? {
            require(depth <= 32) { "JSON5 nesting exceeds 32 levels" }
            skip()
            require(index < text.length) { "Unexpected end of JSON5" }
            return when (text[index]) {
                '{' -> objectValue(depth + 1)
                '[' -> arrayValue(depth + 1)
                '\'', '"' -> string()
                else -> literal()
            }
        }

        private fun objectValue(depth: Int): Map<String, Any?> {
            index++
            val result = LinkedHashMap<String, Any?>()
            skip()
            if (take('}')) return result
            while (true) {
                skip()
                val key = if (peek() == '\'' || peek() == '"') string() else identifier()
                require(!result.containsKey(key)) { "Duplicate property: $key" }
                skip()
                require(take(':')) { "Expected ':' at $index" }
                result[key] = value(depth)
                skip()
                if (take('}')) return result
                require(take(',')) { "Expected ',' at $index" }
                skip()
                if (take('}')) return result
            }
        }

        private fun arrayValue(depth: Int): List<Any?> {
            index++
            val result = ArrayList<Any?>()
            skip()
            if (take(']')) return result
            while (true) {
                result.add(value(depth))
                skip()
                if (take(']')) return result
                require(take(',')) { "Expected ',' at $index" }
                skip()
                if (take(']')) return result
            }
        }

        private fun identifier(): String {
            val result = StringBuilder()
            while (index < text.length) {
                var ch = text[index]
                if (ch == '\\') {
                    index++
                    require(take('u')) { "Expected Unicode identifier escape at $index" }
                    ch = hex(4)
                } else {
                    if (!(ch == '$' || ch == '_' || Character.isUnicodeIdentifierPart(ch))) break
                    index++
                }
                require(result.isNotEmpty() || ch == '$' || ch == '_' || Character.isUnicodeIdentifierStart(ch)) { "Invalid property name" }
                result.append(ch)
            }
            require(result.isNotEmpty()) { "Expected property name at $index" }
            return result.toString()
        }

        private fun string(): String {
            val quote = text[index++]
            val result = StringBuilder()
            while (index < text.length) {
                val ch = text[index++]
                if (ch == quote) return result.toString()
                require(ch != '\n' && ch != '\r') { "Unescaped newline in string" }
                if (ch != '\\') { result.append(ch); continue }
                require(index < text.length) { "Incomplete string escape" }
                when (val escape = text[index++]) {
                    '\n', '\u2028', '\u2029' -> Unit
                    '\r' -> take('\n')
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    'v' -> result.append('\u000B')
                    '0' -> { require(!peek().isDigit()) { "Octal escapes are not JSON5" }; result.append('\u0000') }
                    'u' -> result.append(hex(4))
                    'x' -> result.append(hex(2))
                    else -> { require(!escape.isDigit()) { "Octal escapes are not JSON5" }; result.append(escape) }
                }
            }
            throw IllegalArgumentException("Unterminated string")
        }

        private fun hex(length: Int): Char {
            require(index + length <= text.length) { "Incomplete hexadecimal escape" }
            val digits = text.substring(index, index + length)
            index += length
            return (digits.toIntOrNull(16) ?: throw IllegalArgumentException("Invalid hexadecimal escape")).toChar()
        }

        private fun literal(): Any? {
            val start = index
            while (index < text.length && !text[index].isWhitespace() && text[index] !in ",]}:/") index++
            val token = text.substring(start, index)
            return when (token) {
                "true" -> true
                "false" -> false
                "null" -> null
                "NaN", "+NaN", "-NaN" -> Double.NaN
                "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY
                "-Infinity" -> Double.NEGATIVE_INFINITY
                else -> {
                    val unsigned = token.removePrefix("+").removePrefix("-")
                    if (unsigned.startsWith("0x", true)) {
                        val number = unsigned.substring(2).toLongOrNull(16) ?: throw IllegalArgumentException("Invalid hexadecimal number: $token")
                        if (token.startsWith('-')) -number else number
                    } else {
                        require(NUMBER.matches(token)) { "Invalid JSON5 value: $token" }
                        token.toLongOrNull() ?: token.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number: $token")
                    }
                }
            }
        }

        private fun skip() {
            while (index < text.length) {
                val ch = text[index]
                if (ch.isWhitespace() || ch == '\uFEFF') { index++; continue }
                if (text.startsWith("//", index)) {
                    index += 2
                    while (index < text.length && text[index] !in "\r\n\u2028\u2029") index++
                } else if (text.startsWith("/*", index)) {
                    val end = text.indexOf("*/", index + 2)
                    require(end >= 0) { "Unterminated comment" }
                    index = end + 2
                } else return
            }
        }

        private fun peek(): Char = text.getOrNull(index) ?: '\u0000'
        private fun take(ch: Char): Boolean {
            if (peek() != ch) return false
            index++
            return true
        }
    }

    private val NUMBER = Regex("[+-]?(?:0|[1-9][0-9]*)(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?|[+-]?\\.[0-9]+(?:[eE][+-]?[0-9]+)?")
}
