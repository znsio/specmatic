package io.specmatic.core.regexgenerator

import java.util.*
import kotlin.math.max
import kotlin.math.min


/**
 * Defines length constraints for strings matching a regular expression.
 */
class Bounds @JvmOverloads constructor(minValue: Int = 0, maxValue: Int = UNBOUNDED) :
    Comparable<Bounds> {
    /**
     * Creates a new Bounds with a minimum of 0 (inclusive) and a
     * maximum of <CODE>maxValue</CODE> (inclusive).
     */
    constructor(maxValue: Int) : this(0, maxValue)

    /**
     * Returns a new [Bounds] instance for the intersection of this bounds with the given range.
     * Throws an exception if this bounds lies outside the range.
     */
    @Throws(IllegalArgumentException::class)
    fun clippedTo(rangeName: String?, rangeMin: Int, rangeMax: Int): Bounds? {
        require(minValue <= rangeMax) { String.format("%s cannot be greater than %s", rangeName, rangeMax) }

        require(maxValue >= rangeMin) { String.format("%s cannot be less than %s", rangeName, rangeMin) }

        return Bounds(max(minValue, rangeMin), min(maxValue, rangeMax))
    }

    /**
     * Returns true if this bounds intersects with the given range.
     */
    fun intersects(rangeMin: Int, rangeMax: Int): Boolean {
        var intersects = try {
            clippedTo("", rangeMin, rangeMax) != null
        } catch (e: IllegalArgumentException) {
            false
        }
        return intersects
    }

    /**
     * Compares [Bounds] instances in order of increasing range of values.
     */
    override fun compareTo(other: Bounds): Int {
        val min1 = minValue
        val max1 = maxValue
        val range1 = bounded(max1).map { max: Int -> max - min1 }.orElse(null)

        val min2 = other.minValue
        val max2 = other.maxValue
        val range2 = bounded(max2).map { max: Int -> max - min2 }.orElse(null)

        return if (range1 != null && range2 != null) range1 - range2 else if (range1 != null) -1 else if (range2 != null) 1 else min2 - min1
    }

    override fun toString(): String {
        return StringBuilder()
            .append('[')
            .append(minValue)
            .append(',')
            .append(Optional.of(maxValue).filter { max: Int? -> max!! < UNBOUNDED }.orElse(null))
            .append(']')
            .toString()
    }


    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as Bounds
            else
                null

        return other != null && other.minValue == minValue && other.maxValue == maxValue
    }

    override fun hashCode(): Int {
        return (javaClass.hashCode()
                xor minValue
                xor maxValue)
    }

    /**
     * Returns the minimum value (inclusive) for any matching string.
     */
    val minValue: Int

    /**
     * Returns the maximum value (inclusive) for any matching string.
     */
    val maxValue: Int
    /**
     * Creates a new Bounds with a minimum of <CODE>minValue</CODE> (inclusive) and a
     * maximum of <CODE>maxValue</CODE> (inclusive). If <CODE>minValue</CODE> is null,
     * the default is 0. If <CODE>maxValue</CODE> is null,
     * the default is [.UNBOUNDED].
     */
    /**
     * Creates a new Bounds with a minimum of 0 (inclusive) and a
     * maximum of [.UNBOUNDED].
     */
    init {
        val min = Optional.ofNullable(minValue).orElse(0)
        val max = Optional.ofNullable(maxValue).orElse(UNBOUNDED)

        require(min >= 0) { "Minimum value must be non-negative" }
        require(min <= max) { String.format("Minimum value=%s is greater than maximum value=%s", min, max) }

        this.minValue = min
        this.maxValue = max
    }

    companion object {
        /**
         * If the given value is [.UNBOUNDED], returns <CODE>Optional.empty()</CODE>;
         * otherwise, returns <CODE>Optional.of( value)</CODE>.
         */
        fun bounded(value: Int): Optional<Int> {
            return if (value == UNBOUNDED) Optional.empty() else Optional.of(value)
        }

        /**
         * Returns the sum of the given values, avoiding overflow.
         */
        fun sumOf(a: Int, b: Int): Int {
            return try {
                Math.addExact(a, b)
            } catch (e: ArithmeticException) {
                UNBOUNDED
            }
        }

        /**
         * If <CODE>b</CODE> is greater than <CODE>a</CODE>, returns 0;
         * otherwise, returns the result of subtracting <CODE>b</CODE> from <CODE>a</CODE>.
         */
        fun reduceBy(a: Int, b: Int): Int {
            return if (b > a) 0 else a - b
        }

        /**
         * Returns the product of the given values, avoiding overflow.
         */
        fun productOf(a: Int, b: Int): Int {
            return try {
                Math.multiplyExact(a, b)
            } catch (e: ArithmeticException) {
                UNBOUNDED
            }
        }

        /**
         * If <CODE>b</CODE> is 0, returns [.UNBOUNDED];
         * otherwise, returns <CODE>a / b</CODE>
         */
        fun dividedBy(a: Int, b: Int): Int {
            return if (b == 0) UNBOUNDED else a / b
        }

        /**
         * Designates an unbounded maximum value.
         */
        const val UNBOUNDED: Int = Int.MAX_VALUE
    }
}