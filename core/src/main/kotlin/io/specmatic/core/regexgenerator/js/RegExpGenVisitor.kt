package io.specmatic.core.regexgenerator.js

import org.cornutum.regexpgen.js.SeqGen
/**
 * Implements the Visitor pattern for [org.cornutum.regexpgen.RegExpGen] implementations.
 */
interface RegExpGenVisitor {
    fun visit(regExpGen: AlternativeGen)
    fun visit(regExpGen: SeqGen)
    fun visit(regExpGen: AnyOfGen)
    fun visit(regExpGen: NoneOfGen)
    fun visit(regExpGen: AnyPrintableGen)
}
