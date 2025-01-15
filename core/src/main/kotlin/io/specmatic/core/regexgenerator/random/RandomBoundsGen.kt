package io.specmatic.core.regexgenerator.random

import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.RandomGen
import org.cornutum.regexpgen.random.Poisson
import org.cornutum.regexpgen.util.ToString
import java.util.*


/**
 * Generates random integer values within given [Bounds].
 */
class RandomBoundsGen @JvmOverloads constructor(private val random_: Random = Random(), lambda: Int = 16) :
    RandomGen {
    /**
     * Creates a new RandomBoundsGen instance.
     */
    constructor(lambda: Int) : this(Random(), lambda)

    /**
     * Returns a random integer between <CODE>min</CODE> (inclusive) and <CODE>max</CODE> (exclusive).
     */
    override fun within(min: Int, max: Int): Int {
        return if (max - min <= 0) min else if (max < Bounds.UNBOUNDED) min + random_.nextInt(max - min) else min + extra_.next()
    }

    override fun toString(): String {
        return ToString.getBuilder(this)
            .append(extra_.lambda)
            .toString()
    }

    private val extra_: Poisson
    /**
     * Creates a new RandomBoundsGen instance. When no upper bound is defined, uses a Poisson distribution with
     * the given lambda parameter.
     */
    /**
     * Creates a new RandomBoundsGen instance.
     */
    /**
     * Creates a new RandomBoundsGen instance.
     */
    init {
        extra_ = Poisson(random_, lambda)
    }
}