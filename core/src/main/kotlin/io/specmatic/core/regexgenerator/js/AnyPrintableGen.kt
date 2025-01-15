package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.js.RegExpGenVisitor


/**
 * Generates a sequence of any printable characters.
 */
class AnyPrintableGen
/**
 * Creates a new AnyPrintableGen instance.
 */
    (options: GenOptions?) : AnyOfGen(options) {
    /**
     * Creates a new AnyPrintableGen instance.
     */
    constructor(options: GenOptions?, length: Int) : this(options) {
        setOccurrences(length, length)
    }

    /**
     * Creates a new AnyPrintableGen instance.
     */
    constructor(options: GenOptions?, minOccur: Int?, maxOccur: Int?) : this(options) {
        setOccurrences(minOccur, maxOccur)
    }

    /**
     * Returns the set of characters that define this class.
     */
    override fun getCharSet(): Set<Char> {
        return options.anyPrintableChars
    }

    /**
     * Returns the characters in this class.
     */
    override fun getChars(): Array<Char> {
        return makeChars()
    }

    /**
     * Implements the Visitor pattern for [org.cornutum.regexpgen.RegExpGen] implementations.
     */
    override fun accept(visitor: RegExpGenVisitor) {
        visitor.visit(this)
    }

    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as AnyPrintableGen
            else
                null

        return other != null
                && other.occurrences == occurrences
    }

    override fun hashCode(): Int {
        return (javaClass.hashCode()
                xor occurrences.hashCode())
    }
}