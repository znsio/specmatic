package io.specmatic.core.regexgenerator

import org.cornutum.regexpgen.util.CharUtils
import org.cornutum.regexpgen.util.ToString
import java.util.*
import java.util.stream.Collectors
import java.util.stream.IntStream


/**
 * Defines options for generating regular expression matches.
 */
class GenOptions {
    /**
     * Changes the set of characters used to generate matches for the "." expression.
     */
    fun setAnyPrintableChars(chars: String) {
        anyPrintableChars = IntStream.range(0, chars.length)
            .mapToObj { i: Int -> chars[i] }
            .collect(Collectors.toSet())
    }

    var anyPrintableChars: Set<Char>?
        /**
         * Returns the set of characters used to generate matches for the "." expression.
         */
        get() = anyPrintable_
        /**
         * Changes the set of characters used to generate matches for the "." expression.
         */
        set(chars) {
            val anyPrintable = Optional.ofNullable(chars).orElse(ANY_LATIN_1)
            require(!anyPrintable.isEmpty()) { "Printable character set is empty" }
            anyPrintable.stream()
                .filter { character: Char? -> CharUtils.isLineTerminator(character) }
                .findFirst()
                .ifPresent { lt: Char ->
                    throw IllegalArgumentException(
                        String.format(
                            "Printable character set cannot include line terminator=\\u%s",
                            Integer.toHexString(lt.code)
                        )
                    )
                }

            anyPrintable_ = anyPrintable
        }

    override fun toString(): String {
        return ToString.getBuilder(this)
            .toString()
    }

    private var anyPrintable_: Set<Char>? = null

    /**
     * Creates a new GenOptions instance for generating matches for the given
     * regular expression.
     */
    init {
        anyPrintableChars = ANY_LATIN_1
    }

    companion object {
        /**
         * All printable characters in the basic and supplemental Latin-1 code blocks
         */
        val ANY_LATIN_1: Set<Char> = Collections.unmodifiableSet(
            CharUtils.printableLatin1()
                .collect(Collectors.toSet())
        )

        /**
         * All printable characters in the ASCII code block
         */
        val ANY_ASCII: Set<Char> = Collections.unmodifiableSet(
            CharUtils.printableAscii()
                .collect(Collectors.toSet())
        )
    }
}