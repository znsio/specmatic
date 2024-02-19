package `in`.specmatic.conversions

import `in`.specmatic.core.Resolver
import `in`.specmatic.core.URLPathSegmentPattern

interface ApiSpecification {
    fun exactValuePatternsAreEqual(
        openapiURLPart: URLPathSegmentPattern,
        wrapperURLPart: URLPathSegmentPattern
    ): Boolean

    fun patternMatchesExact(
        wrapperURLPart: URLPathSegmentPattern,
        openapiURLPart: URLPathSegmentPattern,
        resolver: Resolver,
    ): Boolean
}