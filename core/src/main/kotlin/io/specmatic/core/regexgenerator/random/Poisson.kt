//////////////////////////////////////////////////////////////////////////////
//
//                    Copyright 2020, Cornutum Project
//                             www.cornutum.org
//
//////////////////////////////////////////////////////////////////////////////
package org.cornutum.regexpgen.random

import org.cornutum.regexpgen.util.ToString
import java.util.*
import kotlin.math.exp

/**
 * Generates random numbers following a Poisson distribution
 */
class Poisson
    (random: Random, lambda: Int) {
    /**
     * Returns the next random number from this Poisson distribution.
     */
    fun next(): Int {
        var k = 0
        var p = 1.0
        while ((random.nextDouble().let { p *= it; p }) > L) {
            k++
        }
        return k
    }

    override fun toString(): String {
        return ToString.getBuilder(this)
            .append(lambda)
            .toString()
    }

    private val random: Random

    /**
     * Returns the lambda parameter for this Poisson distribution.
     */
    val lambda: Int
    private val L: Double

    /**
     * Creates a new Poisson distribution with the given lambda parameter.
     */
    init {
        require(lambda > 0) { "Lambda must be > 0" }

        L = exp(-lambda.toDouble())
        this.random = random
        this.lambda = lambda
    }
}