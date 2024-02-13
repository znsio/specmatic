package `in`.specmatic.core.pattern

import `in`.specmatic.core.Resolver

interface ScalarPattern : Pattern {
    override fun complexity(resolver: Resolver): ULong = 1.toULong()
}
