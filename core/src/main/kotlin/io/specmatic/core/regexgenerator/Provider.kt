package io.specmatic.core.regexgenerator

import java.util.*


/**
 * Provides instances of a [RegExpGen] implementation.
 */
interface Provider {
    /**
     * Returns a [RegExpGen] that generates strings containing characters that match the given
     * regular expression.
     */
    fun matching(regexp: String?): RegExpGen?

    /**
     * Returns a [RegExpGen] that generates strings containing only characters that match the
     * given regular expression.
     */
    fun matchingExact(regexp: String?): RegExpGen?

    /**
     * Returns a [RegExpGen] that generates strings that do NOT match the given regular
     * expression.
     * <P></P>
     * For some regular expressions, no result is possible. For example, there is no string that
     * does not match ".*". For such expressions, this method should return [Optional.empty].
     * <P></P>
     * This is an optional service. Throws an [UnsupportedOperationException] if not implemented.
     */
    @Throws(UnsupportedOperationException::class)
    fun notMatching(regexp: String?): Optional<RegExpGen?>?
}