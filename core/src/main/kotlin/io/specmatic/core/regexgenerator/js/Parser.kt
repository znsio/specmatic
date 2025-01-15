package io.specmatic.core.regexgenerator.js
import org.apache.commons.collections4.IterableUtils
import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.js.SeqGen
import org.cornutum.regexpgen.util.ToString
import java.util.*
import java.util.regex.Pattern
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.math.min


/**
 * Returns the [AbstractRegExpGen] represented by a JavaScript regular expression.
 */
class Parser
internal constructor(private val chars_: String) {
    /**
     * Returns the [AbstractRegExpGen] represented by this JavaScript regular expression.
     * If <CODE>exact</CODE> is true, the result generates strings containing only
     * characters matching this regular expression. Otherwise, the result generates strings
     * that may contain other characters surrounding the matching characters.
     */
    @Throws(IllegalArgumentException::class)
    fun parse(exact: Boolean): AbstractRegExpGen? {
        val regExpGen = next

        val c = peekc()
        if (c != EOS) {
            unexpectedChar(c)
        }

        return Optional.ofNullable(regExpGen)
            .map { r: AbstractRegExpGen ->
                if (exact) r else withStartGen(
                    withEndGen(r)
                )
            }
            .orElse(null)
    }

    private val next: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by next element of this JavaScript regular expression.
         */
        get() {
            val cursorStart = cursor()
            val alternatives: MutableList<AbstractRegExpGen?> = ArrayList()
            val alternative = alternative
            if (alternative != null) {
                alternatives.add(alternative)
                while (peekc() == '|') {
                    advance(1)
                    alternatives.add(
                        Optional.ofNullable(this.alternative)
                            .orElseThrow { error("Alternative missing") })
                }
            }

            return if (alternatives.isEmpty()) null else if (alternatives.size > 1) startingAt(
                cursorStart,
                AlternativeGen(options(), alternatives)
            ) else alternatives[0]
        }

    private val alternative: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by next alternative of this JavaScript regular expression.
         */
        get() {
            val cursorStart = cursor()
            val terms: MutableList<AbstractRegExpGen> = ArrayList()

            var termExpr: List<AbstractRegExpGen>
            while ((term.also { termExpr = it!! }) != null) {
                for (term in termExpr) {
                    if (term.isAnchoredStart && isAnchoredStart(terms)) {
                        throw error("Start-anchored expression can be matched at most once")
                    }
                    if (term.isAnchoredStart && !terms.isEmpty()) {
                        throw error("Extra expressions not allowed preceding ^ anchor")
                    }
                    if (isAnchoredEnd(terms)) {
                        throw error("Extra expressions not allowed after $ anchor")
                    }

                    terms.add(term)
                }
            }

            return if (terms.isEmpty()) null else if (terms.size > 1) startingAt(
                cursorStart,
                SeqGen(options(), terms)
            ) else terms[0]
        }

    /**
     * Returns true if the given sequence of terms is start-anchored.
     */
    private fun isAnchoredStart(terms: List<AbstractRegExpGen>): Boolean {
        return !terms.isEmpty() && terms[0].isAnchoredStart
    }

    /**
     * Returns true if the given sequence of terms is end-anchored.
     */
    private fun isAnchoredEnd(terms: List<AbstractRegExpGen>): Boolean {
        return !terms.isEmpty() && terms[terms.size - 1].isAnchoredEnd
    }

    private val term: List<AbstractRegExpGen>?
        /**
         * Returns the sequence of [RegExpGen] instances represented by next term of this JavaScript regular expression.
         */
        get() {
            var cursorStart = cursor()
            val termExpr: MutableList<AbstractRegExpGen> = ArrayList()


            // Get any start assertion
            var prefix: AbstractRegExpGen? = null
            var anchoredStart = false
            run {
                var assertionFound = true
                while (assertionFound) {
                    if ("\\b".equals(peek(2), ignoreCase = true)) {
                        throw error("Unsupported word boundary assertion")
                    }

                    if ("(?<!" == peek(4)) {
                        throw error("Unsupported negative look-behind assertion")
                    }

                    if ((("(?<=" == peek(4)).also { assertionFound = it })) {
                        advance(4)

                        prefix =
                            Optional.ofNullable(next)
                                .orElseThrow { error("Missing look-behind expression") }

                        if (peekc() != ')') {
                            throw error("Missing ')'")
                        }
                        advance(1)
                        prefix = startingAt<AbstractRegExpGen?>(cursorStart, prefix)
                    } else if (((peekc() == '^').also { assertionFound = it })) {
                        anchoredStart = true
                        advance(1)
                    }
                    cursorStart = cursor()
                }
            }
            if (prefix != null && anchoredStart) {
                throw error("Start assertion is inconsistent with look-behind assertion")
            }

            // Get atomic expression
            val cursorStartQuantified = cursor()
            var quantified = quantified
            if (quantified == null && prefix != null) {
                throw error("Missing regular expression for look-behind assertion")
            }
            quantified = startingAt<AbstractRegExpGen?>(cursorStartQuantified, quantified)

            // Get any end assertion
            cursorStart = cursor()
            var suffix: AbstractRegExpGen? = null
            var anchoredEnd = false
            var assertionFound = true
            while (assertionFound) {
                if ("\\b".equals(peek(2), ignoreCase = true)) {
                    throw error("Unsupported word boundary assertion")
                }
                if ("(?!" == peek(3)) {
                    throw error("Unsupported negative look-ahead assertion")
                }
                if ((("(?=" == peek(3)).also { assertionFound = it })) {
                    if (quantified == null) {
                        throw error("Missing regular expression for look-ahead assertion")
                    }
                    advance(3)

                    suffix =
                        Optional.ofNullable(next)
                            .orElseThrow { error("Missing look-ahead expression") }

                    if (peekc() != ')') {
                        throw error("Missing ')'")
                    }
                    advance(1)

                    suffix = startingAt<AbstractRegExpGen?>(cursorStart, suffix)
                } else if (((peekc() == '$').also { assertionFound = it })) {
                    anchoredEnd = true
                    advance(1)
                }
                cursorStart = cursor()
            }
            if (suffix != null && anchoredEnd) {
                throw error("End assertion is inconsistent with look-ahead assertion")
            }

            // Accumulate all expressions for this term
            if (prefix != null) {
                termExpr.add(prefix!!)
            }

            if (quantified != null) {
                termExpr.add(quantified)
                if (anchoredStart) {
                    quantified.isAnchoredStart = true
                }
                if (anchoredEnd) {
                    quantified.isAnchoredEnd = true
                    startingAt(cursorStartQuantified, quantified)
                }
            } else if (anchoredStart || anchoredEnd) {
                termExpr.add(
                    startingAt(
                        cursorStart,

                        AnyOfGen.builder(options())
                            .anyPrintable()
                            .anchoredStart(anchoredStart)
                            .anchoredEnd(anchoredEnd)
                            .occurs(0)
                            .build()
                    )
                )
            }

            if (suffix != null) {
                termExpr.add(suffix)
            }

            return if (termExpr.isEmpty())
                null
            else
                termExpr
        }

    private val quantified: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by next quantified atom of this JavaScript regular expression.
         */
        get() {
            val cursorStart = cursor()
            val atom = atom
            if (atom != null) {
                val quantifier = quantifier
                if (quantifier != null) {
                    if (atom.isAnchoredEnd && quantifier.maxValue > 1 && !atom.isAnchoredEndAll) {
                        throw error("End-anchored expression can be matched at most once")
                    }
                    if (atom.isAnchoredStart && quantifier.maxValue > 1 && !atom.isAnchoredStartAll) {
                        throw error("Start-anchored expression can be matched at most once")
                    }
                    atom.occurrences = quantifier
                }
            }

            return startingAt<AbstractRegExpGen?>(cursorStart, atom)
        }

    private val quantifier: Bounds?
        /**
         * Returns the [Bounds] represented by the next quantifier in this JavaScript regular expression.
         */
        get() {
            val minOccur: Int?
            var maxOccur: Int?

            when (peekc()) {
                '?' -> {
                    advance(1)
                    minOccur = 0
                    maxOccur = 1
                }

                '*' -> {
                    advance(1)
                    minOccur = 0
                    maxOccur = null
                }

                '+' -> {
                    advance(1)
                    minOccur = 1
                    maxOccur = null
                }

                '{' -> {
                    advance(1)

                    minOccur =
                        Optional.ofNullable(decimal)
                            .orElseThrow { error("Missing number") }

                    if (peekc() == ',') {
                        advance(1)
                        maxOccur = decimal
                    } else {
                        maxOccur = minOccur
                    }

                    if (peekc() != '}') {
                        throw error("Missing '}'")
                    }
                    advance(1)
                }

                else -> {
                    minOccur = null
                    maxOccur = null
                }
            }

            var quantifier: Bounds? = null
            if (minOccur != null) {
                if (peekc() == '?') {
                    advance(1)
                    maxOccur = minOccur
                }

                quantifier = Bounds(minOccur, maxOccur)
            }

            return quantifier
        }

    private val decimal: Int?
        /**
         * Returns the next decimal integer from this JavaScript regular expression.
         */
        get() {
            val decimalChars = "0123456789"

            val decimal = StringBuilder()
            var c = 0.toChar()
            while (decimalChars.indexOf((peekc().also { c = it })) >= 0
            ) {
                advance(1)
                decimal.append(c)
            }

            try {
                return if (decimal.length == 0)
                    null
                else decimal.toString().toInt()
            } catch (e: Exception) {
                throw error(String.format("Invalid decimal string=%s", decimal.toString()))
            }
        }

    private val atom: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by next atom of this JavaScript regular expression.
         */
        get() {
            val cursorStart = cursor()
            var atom: AbstractRegExpGen?

            if ((group.also { atom = it }) == null && (charClass.also {
                    atom = it
                }) == null && (atomEscape.also { atom = it }) == null && (anyOne.also { atom = it }) == null) {
                atom = patternChar
            }

            return startingAt<AbstractRegExpGen?>(cursorStart, atom)
        }

    private val group: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by a group.
         */
        get() {
            val cursorStart = cursor()
            var group: AbstractRegExpGen? = null
            if (peekc() == '(') {
                advance(1)

                if ("?:" == peek(2)) {
                    // Generation doesn't depend on capturing.
                    advance(2)
                } else if ("?<" == peek(2)) {
                    // Generation doesn't depend on capturing -- ignore group name.
                    advance(2)
                    while (peekc() != '>') {
                        advance(1)
                    }
                    advance(1)
                }

                group =
                    Optional.ofNullable(next)
                        .orElseThrow { error("Incomplete group expression") }

                if (peekc() != ')') {
                    throw error("Missing ')'")
                }
                advance(1)
            }

            return startingAt<AbstractRegExpGen?>(cursorStart, group)
        }

    private val anyOne: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by a single character.
         */
        get() {
            val cursorStart = cursor()
            var anyOne: AbstractRegExpGen? = null

            if (peekc() == '.') {
                advance(1)
                anyOne = startingAt(cursorStart, AnyPrintableGen(options(), 1))
            }

            return anyOne
        }

    private val atomEscape: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by a single character.
         */
        get() {
            var escapeClass: CharClassGen? = null
            if (peekc() == '\\') {
                advance(1)

                if ((charClassEscape.also { escapeClass = it }) == null) {
                    escapeClass = charEscape
                }
            }

            return escapeClass
        }

    private val charClass: AbstractRegExpGen?
        /**
         * Returns the [RegExpGen] represented by a character class.
         */
        get() {
            val cursorStart = cursor()
            var charClass: CharClassGen? = null
            if (peekc() == '[') {
                advance(1)

                if (peekc() == '^') {
                    advance(1)
                    charClass = NoneOfGen(options())
                } else {
                    charClass = AnyOfGen(options())
                }

                var c: Char
                var prevClass: CharClassGen?
                prevClass = null
                c = peekc()
                while (c != ']'
                    && c != EOS

                ) {
                    // Is this a non-initial/final '-' char?
                    var rangeStart: CharClassGen? = null
                    if (c == '-' && !(prevClass == null || peek(2).endsWith("]"))) {
                        // Yes, continue to look for end of character range
                        rangeStart = prevClass
                        advance(1)
                        c = peekc()
                    } else {
                        // No, look for new class member
                        charClass.addAll(prevClass)
                    }

                    if ((classEscape.also { prevClass = it }) != null) {
                        // Include escaped char(s) in this class
                    } else {
                        // Include single char in this class
                        prevClass = AnyOfGen(options(), c)
                        advance(1)
                    }

                    if (rangeStart != null) {
                        // Add char range to this class
                        val first =
                            Optional.of(rangeStart.chars!!)
                                .filter { start: Array<Char>? -> start!!.size == 1 }
                                .map { start: Array<Char>? -> start!![0] }
                                .orElseThrow { error("Character range must begin with a specific character") }

                        val last =
                            Optional.of(prevClass!!.chars!!)
                                .filter { end: Array<Char>? -> end!!.size == 1 }
                                .map { end: Array<Char>? -> end!![0] }
                                .orElseThrow { error("Character range must end with a specific character") }

                        prevClass = AnyOfGen(options(), first, last)
                    }
                    c = peekc()
                }

                charClass.addAll(prevClass)
                if (c != ']') {
                    throw error("Missing ']'")
                }
                if (charClass.isEmpty) {
                    throw error("Empty character class")
                }
                advance(1)
            }

            return startingAt<CharClassGen?>(cursorStart, charClass)
        }

    private val classEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by an escaped character class
         */
        get() {
            var escapeClass: CharClassGen? = null
            if (peekc() == '\\') {
                advance(1)

                if ((backspaceEscape.also { escapeClass = it }) == null
                    &&
                    (charClassEscape.also { escapeClass = it }) == null
                ) {
                    escapeClass = charEscape
                }
            }

            return escapeClass
        }

    private val backspaceEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by an escaped backspace character.
         */
        get() {
            val cursorStart = cursor()
            var escapeClass: CharClassGen? = null

            if (peekc() == 'b') {
                advance(1)
                escapeClass = startingAt(cursorStart, AnyOfGen(options(), '\b'))
            }

            return escapeClass
        }

    private val charClassEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by an escaped character class
         */
        get() {
            val escapeClass: CharClassGen?
            val id = peekc()

            escapeClass = when (id) {
                'd' -> {
                    charClasses().digit()
                }

                'D' -> {
                    charClasses().nonDigit()
                }

                'w' -> {
                    charClasses().word()
                }

                'W' -> {
                    charClasses().nonWord()
                }

                's' -> {
                    charClasses().space()
                }

                'S' -> {
                    charClasses().nonSpace()
                }

                else -> {
                    null
                }
            }

            if (escapeClass != null) {
                advance(1)
            }

            return escapeClass
        }

    private val charEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by an escaped character.
         */
        get() {
            var charClass: CharClassGen?

            if ((namedCharEscape.also { charClass = it }) == null && (controlEscape.also {
                    charClass = it
                }) == null && (hexCharClass.also { charClass = it }) == null && (unicodeCharClass.also {
                    charClass = it
                }) == null) {
                charClass = literalChar
            }

            return charClass
        }

    private val namedCharEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a named escaped character.
         */
        get() {
            val cursorStart = cursor()
            val escapeClass: CharClassGen?
            val id = peekc()

            when (id) {
                't' -> {
                    escapeClass = AnyOfGen(options(), '\t')
                }

                'r' -> {
                    escapeClass = AnyOfGen(options(), '\r')
                }

                'n' -> {
                    escapeClass = AnyOfGen(options(), '\n')
                }

                'f' -> {
                    escapeClass = AnyOfGen(options(), '\f')
                }

                'v' -> {
                    escapeClass = AnyOfGen(options(), 0x000b.toChar())
                }

                '0' -> {
                    escapeClass = AnyOfGen(options(), 0.toChar())
                }

                else -> {
                    if (Character.isDigit(id)) {
                        throw error("Unsupported back reference to capturing group")
                    }
                    if (id == 'k') {
                        throw error("Unsupported back reference to named group")
                    }
                    escapeClass = null
                }
            }

            if (escapeClass != null) {
                advance(1)
            }

            return startingAt<CharClassGen?>(cursorStart, escapeClass)
        }

    private val controlEscape: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a control character
         */
        get() {
            val cursorStart = cursor()
            var escapeClass: CharClassGen? = null

            if (peekc() == 'c') {
                advance(1)

                val controlChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                val controlChar = controlChars.indexOf(peekc().uppercaseChar())
                if (controlChar < 0) {
                    throw error(String.format("Invalid control escape character='%c'", peekc()))
                }
                advance(1)

                escapeClass = startingAt(cursorStart, AnyOfGen(options(), (0x0001 + controlChar).toChar()))
            }

            return escapeClass
        }

    private val hexCharClass: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a hexadecimal character
         */
        get() {
            val cursorStart = cursor()
            var escapeClass: CharClassGen? = null

            if (peekc() == 'x') {
                advance(1)

                val digits = peek(2)
                val hexCharMatcher = hexCharPattern_.matcher(digits)
                if (!hexCharMatcher.matches()) {
                    throw error(String.format("Invalid hex character='%s'", digits))
                }
                advance(2)

                escapeClass = startingAt(cursorStart, AnyOfGen(options(), digits.toInt(16) as Char))
            }

            return escapeClass
        }

    private val unicodeCharClass: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a Unicode character
         */
        get() {
            val cursorStart = cursor()
            var escapeClass: CharClassGen? = null

            if (peekc() == 'u') {
                advance(1)

                val digits = peek(4)
                val unicodeCharMatcher = unicodeCharPattern_.matcher(digits)
                if (!unicodeCharMatcher.matches()) {
                    throw error(String.format("Invalid Unicode character='%s'", digits))
                }
                advance(4)

                escapeClass = startingAt(cursorStart, AnyOfGen(options(), digits.toInt(16) as Char))
            }

            return escapeClass
        }

    private val literalChar: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a literal character.
         */
        get() {
            val cursorStart = cursor()
            return Optional.of(peekc())
                .filter { c: Char -> c != EOS }
                .map { c: Char? ->
                    val literal: CharClassGen =
                        AnyOfGen(
                            options(),
                            c!!
                        )
                    advance(1)
                    startingAt(cursorStart, literal)
                }
                .orElse(null)
        }

    private val patternChar: CharClassGen?
        /**
         * Returns the [RegExpGen] represented by a non-delimiter character.
         */
        get() {
            val cursorStart = cursor()
            val syntaxChars = "^$\\.*+?()[]{}|"

            return Optional.of(peekc())
                .filter { c: Char -> c != EOS && syntaxChars.indexOf(c) < 0 }
                .map { c: Char? ->
                    val patternChar: CharClassGen =
                        AnyOfGen(
                            options(),
                            c!!
                        )
                    advance(1)
                    startingAt(cursorStart, patternChar)
                }
                .orElse(null)
        }

    /**
     * Returns the given [AbstractRegExpGen] after prefacing any unanchored initial subexpressions
     * with an implicit ".*" expression.
     */
    private fun withStartGen(regExpGen: AbstractRegExpGen): AbstractRegExpGen {
        var alternative: AlternativeGen
        var seq: SeqGen

        var initiated = regExpGen
        if (regExpGen is AlternativeGen && (uninitiated<AlternativeGen>(
                regExpGen
            ).also {
                alternative =
                    it!!
            }) != null
        ) {
            initiated =
                AlternativeGen.builder(options())
                    .addAll(
                        IterableUtils.toList<AbstractRegExpGen>(alternative.members)
                            .stream()
                            .map<AbstractRegExpGen> { regExpGen: AbstractRegExpGen -> this.withStartGen(regExpGen) }
                            .collect(Collectors.toList<T>()))
                    .occurs(alternative.occurrences)
                    .build()
        } else if (regExpGen is SeqGen && (uninitiated(regExpGen as SeqGen).also { seq = it!! }) != null) {
            val members: List<AbstractRegExpGen> = IterableUtils.toList<Any>(seq.members)
            initiated =
                SeqGen.builder(options())
                    .addAll(
                        IntStream.range(0, members.size)
                            .mapToObj { i: Int ->
                                if (i == 0) withStartGen(
                                    members[i]
                                ) else members[i]
                            }
                            .collect(Collectors.toList<Any>()))
                    .occurs(seq.occurrences)
                    .build()
        } else if (!regExpGen.isAnchoredStart) {
            initiated = SeqGen(options(), AnyPrintableGen(options(), 0, null), regExpGen)
        }

        initiated.setSource(regExpGen.source)
        return initiated
    }

    /**
     * Returns the given [AbstractRegExpGen] after appending an implicit ".*" expression to
     * any unanchored final subexpressions.
     */
    private fun withEndGen(regExpGen: AbstractRegExpGen): AbstractRegExpGen {
        var alternative: AlternativeGen
        var seq: SeqGen

        var terminated = regExpGen
        if (regExpGen is AlternativeGen && (unterminated<AlternativeGen>(
                regExpGen
            ).also {
                alternative =
                    it!!
            }) != null
        ) {
            terminated =
                AlternativeGen.builder(options())
                    .addAll(
                        IterableUtils.toList<AbstractRegExpGen>(alternative.members)
                            .stream()
                            .map<AbstractRegExpGen> { regExpGen: AbstractRegExpGen -> this.withEndGen(regExpGen) }
                            .collect(Collectors.toList<T>()))
                    .occurs(alternative.occurrences)
                    .build()
        } else if (regExpGen is SeqGen && (unterminated(regExpGen as SeqGen).also { seq = it!! }) != null) {
            val members: List<AbstractRegExpGen> = IterableUtils.toList<Any>(seq.members)
            val last = members.size - 1
            terminated =
                SeqGen.builder(options())
                    .addAll(
                        IntStream.range(0, members.size)
                            .mapToObj { i: Int ->
                                if (i == last) withEndGen(
                                    members[i]
                                ) else members[i]
                            }
                            .collect(Collectors.toList<Any>()))
                    .occurs(seq.occurrences)
                    .build()
        } else if (!regExpGen.isAnchoredEnd) {
            terminated = SeqGen(options(), regExpGen, AnyPrintableGen(options(), 0, null))
        }

        terminated.setSource(regExpGen.source)
        return terminated
    }

    /**
     * Returns the given regular expression if it may have uninitiated subexpressions.
     * Otherwise, returns null.
     */
    private fun <T : AbstractRegExpGen?> uninitiated(regExpGen: T): T? {
        return if (regExpGen!!.isAnchoredStartAll || regExpGen.maxOccur > 1)
            null
        else
            regExpGen
    }

    /**
     * Returns the given regular expression if it may have unterminated subexpressions.
     * Otherwise, returns null.
     */
    private fun <T : AbstractRegExpGen?> unterminated(regExpGen: T): T? {
        return if (regExpGen!!.isAnchoredEndAll || regExpGen.maxOccur > 1)
            null
        else
            regExpGen
    }

    /**
     * Reports an unexpected character error.
     */
    private fun unexpectedChar(c: Char) {
        throw error(
            if (c == EOS)
                "Unexpected end of string"
            else String.format("Unexpected character=%s", c)
        )
    }

    /**
     * Returns a parse error exception.
     */
    private fun error(reason: String): RuntimeException {
        return IllegalArgumentException(String.format("%s at position=%s", reason, cursor_))
    }

    /**
     * Returns the [options][GenOptions] for [RegExpGen] instances created by this parser.
     */
    private fun options(): GenOptions {
        return options_
    }

    /**
     * Returns the [CharClasses] for this parser.
     */
    private fun charClasses(): CharClasses {
        return charClasses_
    }

    /**
     * Returns the next character.
     */
    private fun peekc(): Char {
        return if (cursor_ < chars_.length)
            chars_[cursor_]
        else
            EOS
    }

    /**
     * Returns the next <CODE>N</CODE> characters. Returns all remaining characters if
     * fewer than <CODE>N</CODE> characters remain.
     */
    private fun peek(n: Int): String {
        return chars_.substring(cursor_, min((cursor_ + n).toDouble(), chars_.length.toDouble()).toInt())
    }

    /**
     * Advances past the next <CODE>N</CODE> characters. Advances to the end if
     * fewer than <CODE>N</CODE> characters remain.
     */
    private fun advance(n: Int) {
        cursor_ = min((cursor_ + n).toDouble(), chars_.length.toDouble()).toInt()
    }

    /**
     * Returns the current cursor position.
     */
    private fun cursor(): Int {
        return cursor_
    }

    /**
     * Updates the [source][AbstractRegExpGen.getSource] for the given generator.
     */
    private fun <T : AbstractRegExpGen?> startingAt(start: Int, regExpGen: T?): CharClassGen? {
        regExpGen?.setSource(chars_.substring(start, cursor_))
        return regExpGen
    }

    override fun toString(): String {
        return ToString.getBuilder(this)
            .append(
                String.format(
                    "%s%s%s",
                    chars_.substring(0, cursor_),
                    0x00BB.toChar(),
                    chars_.substring(cursor_)
                )
            )
            .toString()
    }

    private val options_ = GenOptions()
    private val charClasses_ = CharClasses(options_)
    private var cursor_ = 0

    companion object {
        /**
         * Returns an [AbstractRegExpGen] that generates strings containing characters that match the given
         * JavaScript regular expression.
         *
         */
        @Deprecated("Replaced by {@link Provider#matching}")
        @Throws(IllegalArgumentException::class)
        fun parseRegExp(regexp: String): AbstractRegExpGen? {
            return Parser(regexp).parse(false)
        }

        /**
         * Returns an [AbstractRegExpGen] that generates strings containing only characters that match the
         * given JavaScript regular expression.
         *
         */
        @Deprecated("Replaced by {@link Provider#matchingExact}")
        @Throws(IllegalArgumentException::class)
        fun parseRegExpExact(regexp: String): AbstractRegExpGen? {
            return Parser(regexp).parse(true)
        }

        private val EOS: Char = -1.toChar()
        private val hexCharPattern_: Pattern = Pattern.compile("\\p{XDigit}{2}")
        private val unicodeCharPattern_: Pattern = Pattern.compile("\\p{XDigit}{4}")
    }
}