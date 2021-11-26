package `in`.specmatic.core.pattern

import `in`.specmatic.core.KeyError
import `in`.specmatic.core.KeyErrorCheck

object IgnoreUnexpectedKeys: KeyErrorCheck {
    override fun validate(pattern: Map<String, Any>, actual: Map<String, Any>): KeyError? = null
}