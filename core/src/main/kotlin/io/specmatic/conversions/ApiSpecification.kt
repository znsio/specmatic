package io.specmatic.conversions

import io.specmatic.core.Resolver
import io.specmatic.core.URLPathSegmentPattern

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