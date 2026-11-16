package com.mikotrack.core

import kotlin.math.abs

/**
 * Parses the coordinates typed by the user.
 *
 * Accepted forms (latitude first unless the hemisphere letters say otherwise):
 * - decimal degrees separated by a comma, a semicolon or spaces: `41.61, 2.29`,
 *   `41.61 2.29`, `41,61 2,29`, `-33.9; 18.4`;
 * - degrees, minutes and seconds with symbols: `41°36'44"N 2°17'25"E`;
 * - degrees and decimal minutes: `N 41° 36.733' E 2° 17.417'`;
 * - hemisphere letters with plain numbers: `41.61N 2.29E`, `41 36 44 N 2 17 25 E`
 *   (`O` is accepted for west, as in Catalan and Spanish).
 */
object CoordinateParser {

    /** Why a text could not be read as coordinates. */
    enum class Error { EMPTY, FORMAT, LATITUDE_RANGE, LONGITUDE_RANGE, MINUTES_SECONDS_RANGE }

    sealed interface Result {
        data class Success(val latitude: Double, val longitude: Double) : Result
        data class Failure(val error: Error) : Result
    }

    private sealed interface Token {
        /** [slot] is 0 for degrees, 1 for minutes, 2 for seconds, or -1 when no symbol follows. */
        data class Number(val value: Double, val negative: Boolean, val slot: Int) : Token
        data class Hemisphere(val letter: Char) : Token
    }

    private class Component {
        var hemisphere: Char? = null
        var leadingHemisphere = false
        val values = DoubleArray(3)
        var negative = false
        var lastSlot = -1
        var count = 0
        var hasFraction = false
    }

    fun parse(input: String): Result {
        val text = normalise(input)
        if (text.isBlank()) return Result.Failure(Error.EMPTY)
        val tokens = tokenize(text) ?: return Result.Failure(Error.FORMAT)
        val numbers = tokens.filterIsInstance<Token.Number>()
        val plain = tokens.all { it is Token.Number && it.slot == -1 }

        if (plain) return parsePlain(text, numbers)

        val components = groupComponents(tokens) ?: return Result.Failure(Error.FORMAT)
        if (components.size != 2) return Result.Failure(Error.FORMAT)
        return assemble(components[0], components[1])
    }

    /** Numbers without any symbol or hemisphere: decimal, or split evenly in two halves. */
    private fun parsePlain(text: String, numbers: List<Token.Number>): Result {
        val values = when (numbers.size) {
            2, 4, 6 -> numbers
            1 -> {
                // "41,2" is read as a single decimal number: split it at the comma instead.
                val parts = text.split(',').map { it.trim() }
                if (parts.size != 2) return Result.Failure(Error.FORMAT)
                val split = parts.mapNotNull { part -> tokenize(part)?.singleOrNull() as? Token.Number }
                if (split.size != 2) return Result.Failure(Error.FORMAT)
                split
            }
            else -> return Result.Failure(Error.FORMAT)
        }
        val half = values.size / 2
        val components = listOf(values.subList(0, half), values.subList(half, values.size)).map { group ->
            Component().apply {
                group.forEachIndexed { index, number ->
                    if (index == 0) negative = number.negative
                    else if (number.negative) return Result.Failure(Error.FORMAT)
                    this.values[index] = number.value
                    if (number.value % 1.0 != 0.0 && index < group.size - 1) hasFraction = true
                    count = index + 1
                    lastSlot = index
                }
            }
        }
        return assemble(components[0], components[1])
    }

    private fun groupComponents(tokens: List<Token>): List<Component>? {
        val components = mutableListOf<Component>()
        var current = Component()
        fun finish() {
            components += current
            current = Component()
        }
        for (token in tokens) {
            when (token) {
                is Token.Hemisphere -> {
                    if (current.count == 0) {
                        if (current.hemisphere != null) return null
                        current.hemisphere = token.letter
                        current.leadingHemisphere = true
                    } else if (current.hemisphere == null) {
                        current.hemisphere = token.letter
                        finish()
                    } else {
                        finish()
                        current.hemisphere = token.letter
                        current.leadingHemisphere = true
                    }
                }
                is Token.Number -> {
                    val slot = if (token.slot >= 0) token.slot else current.lastSlot + 1
                    if (current.count > 0 && (slot <= current.lastSlot || slot > 2)) {
                        if (current.hemisphere != null && !current.leadingHemisphere) return null
                        finish()
                    }
                    val effectiveSlot = if (current.count == 0 && token.slot < 0) 0 else slot
                    if (effectiveSlot > 2) return null
                    if (current.count > 0 && token.negative) return null
                    if (current.count == 0) {
                        // Minutes or seconds cannot start a coordinate.
                        if (effectiveSlot != 0) return null
                        current.negative = token.negative
                    } else if (current.values[current.lastSlot] % 1.0 != 0.0) {
                        current.hasFraction = true
                    }
                    current.values[effectiveSlot] = token.value
                    current.lastSlot = effectiveSlot
                    current.count++
                }
            }
        }
        if (current.count > 0) {
            components += current
        } else if (current.hemisphere != null) {
            return null
        }
        return components
    }

    private fun assemble(first: Component, second: Component): Result {
        if (first.hasFraction || second.hasFraction) return Result.Failure(Error.FORMAT)
        for (component in listOf(first, second)) {
            if (component.values[1] >= 60.0 || component.values[2] >= 60.0) {
                return Result.Failure(Error.MINUTES_SECONDS_RANGE)
            }
        }
        val firstIsLatitude = when {
            first.hemisphere != null && second.hemisphere != null -> {
                val a = isLatitudeLetter(first.hemisphere!!)
                val b = isLatitudeLetter(second.hemisphere!!)
                if (a == b) return Result.Failure(Error.FORMAT)
                a
            }
            first.hemisphere != null -> isLatitudeLetter(first.hemisphere!!)
            second.hemisphere != null -> !isLatitudeLetter(second.hemisphere!!)
            else -> true
        }
        val latitudeComponent = if (firstIsLatitude) first else second
        val longitudeComponent = if (firstIsLatitude) second else first
        val latitude = value(latitudeComponent)
        val longitude = value(longitudeComponent)
        if (latitude.isNaN() || abs(latitude) > 90.0) return Result.Failure(Error.LATITUDE_RANGE)
        if (longitude.isNaN() || abs(longitude) > 180.0) return Result.Failure(Error.LONGITUDE_RANGE)
        return Result.Success(latitude, longitude)
    }

    private fun value(component: Component): Double {
        val magnitude = component.values[0] + component.values[1] / 60.0 + component.values[2] / 3600.0
        val southOrWest = component.hemisphere == 'S' || component.hemisphere == 'W' || component.hemisphere == 'O'
        if (southOrWest && component.negative) return Double.NaN
        return if (southOrWest || component.negative) -magnitude else magnitude
    }

    private fun isLatitudeLetter(letter: Char) = letter == 'N' || letter == 'S'

    private fun normalise(input: String): String = input.trim().uppercase()
        .replace('º', '°')
        .replace('˚', '°')
        .replace("''", "\"")
        .replace('′', '\'')
        .replace('’', '\'')
        .replace('‘', '\'')
        .replace('´', '\'')
        .replace('`', '\'')
        .replace('″', '"')
        .replace('“', '"')
        .replace('”', '"')
        .replace('−', '-')

    private fun tokenize(text: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() || c == ',' || c == ';' -> i++
                c.isDigit() || ((c == '-' || c == '+') && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    val negative = c == '-'
                    if (c == '-' || c == '+') i++
                    val start = i
                    while (i < text.length && text[i].isDigit()) i++
                    if (i + 1 < text.length && (text[i] == '.' || text[i] == ',') && text[i + 1].isDigit()) {
                        i++
                        while (i < text.length && text[i].isDigit()) i++
                    }
                    val value = text.substring(start, i).replace(',', '.').toDoubleOrNull() ?: return null
                    var j = i
                    while (j < text.length && text[j] == ' ') j++
                    val slot = when (text.getOrNull(j)) {
                        '°' -> 0
                        '\'' -> 1
                        '"' -> 2
                        else -> -1
                    }
                    if (slot >= 0) i = j + 1
                    tokens += Token.Number(value, negative, slot)
                }
                c in "NSEWO" -> {
                    tokens += Token.Hemisphere(c)
                    i++
                }
                else -> return null
            }
        }
        return tokens
    }
}
