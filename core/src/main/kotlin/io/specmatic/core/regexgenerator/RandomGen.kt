package io.specmatic.core.regexgenerator
import org.cornutum.regexpgen.Bounds
import java.util.*


/**
 * Generates random integer values.
 */
interface RandomGen {
    /**
     * Returns a random integer between <CODE>min</CODE> (inclusive) and <CODE>max</CODE> (exclusive).
     */
    fun within(min: Int, max: Int): Int

    /**
     * Returns a random integer between <CODE>0</CODE> (inclusive) and <CODE>max</CODE> (exclusive).
     */
    fun below(max: Int): Int {
        return within(0, max)
    }

    /**
     * Returns a random integer within the given bounds.
     */
    fun within(bounds: Bounds): Int {
        return within(bounds.minValue, Bounds.sumOf(bounds.maxValue, 1))
    }

    /**
     * Returns the given elements shuffled into a random sequence.
     */
    fun <T> shuffle(elements: List<T?>): List<T?> {
        val shuffled: List<T?> = ArrayList(elements)
        for (to in elements.size downTo 2) {
            Collections.swap(shuffled, below(to), to - 1)
        }

        return shuffled
    }
}