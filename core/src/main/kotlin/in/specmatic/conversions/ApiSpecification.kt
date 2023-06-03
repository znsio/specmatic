package `in`.specmatic.conversions

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.URLPathPattern

interface ApiSpecification {
    fun exactValuePatternsAreEqual(
        openapiURLPart: URLPathPattern,
        wrapperURLPart: URLPathPattern
    ): Boolean

    fun patternMatchesExact(
        wrapperURLPart: URLPathPattern,
        openapiURLPart: URLPathPattern,
        resolver: Resolver,
    ): Boolean
}