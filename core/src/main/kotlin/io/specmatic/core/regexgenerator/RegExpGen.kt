package io.specmatic.core.regexgenerator

import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.RandomGen


/**
 * Generates strings that match a regular expression.
 */
interface RegExpGen : Comparable<RegExpGen> {
    /**
     * Returns the minimum length for any matching string.
     */
    val minLength: Int

    /**
     * Returns the maximum length for any matching string.
     */
    val maxLength: Int

    val length: Bounds
        /**
         * Returns the length bounds for any matching string.
         */
        get() = Bounds(minLength, maxLength)

    /**
     * Returns the [options][GenOptions] for this generator.
     */
    val options: GenOptions?

    /**
     * Returns the regular expression string from which this generator was derived.
     */
    val source: String?

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    /**
     * Returns a random string that matches this regular expression.
     */
    fun generate(random: RandomGen?, length: Bounds? = Bounds()): String

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    fun generate(random: RandomGen?, minLength: Int?, maxLength: Int?): String {
        return generate(random, Bounds(minLength, maxLength))
    }

    /**
     * Returns false if no string matching this regular expression can satisfy the given bounds.
     */
    fun isFeasibleLength(bounds: Bounds): Boolean {
        var feasible: Boolean
        try {
            effectiveLength(bounds)
            feasible = true
        } catch (e: IllegalArgumentException) {
            feasible = false
        }

        return feasible
    }

    /**
     * Returns the effective limits of the given length bounds for this regular expression.
     * Throws an exception if no string matching this regular expression can satisfy the given bounds.
     */
    @Throws(IllegalArgumentException::class)
    fun effectiveLength(bounds: Bounds): Bounds {
        return bounds.clippedTo("Length", minLength, maxLength)
    }

    /**
     * Compares [RegExpGen] instances in order of increasing range of [matching lengths][.getLength].
     */
    override fun compareTo(other: RegExpGen): Int {
        return length.compareTo(other.length)
    }
}