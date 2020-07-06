package run.qontract

import run.qontract.core.Resolver
import run.qontract.core.pattern.AnyPattern
import run.qontract.core.pattern.DeferredPattern
import run.qontract.core.pattern.Pattern
import run.qontract.core.resultReport
import run.qontract.core.value.Value
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun optionalPattern(pattern: Pattern): AnyPattern = AnyPattern(listOf(DeferredPattern("(empty)"), pattern))

infix fun Value.shouldMatch(pattern: Pattern) {
    val result = pattern.matches(this, Resolver())
    if(!result.isTrue()) println(resultReport(result))
    assertTrue(result.isTrue())
}

infix fun Value.shouldNotMatch(pattern: Pattern) {
    assertFalse(pattern.matches(this, Resolver()).isTrue())
}

fun emptyPattern() = DeferredPattern("(empty)")
