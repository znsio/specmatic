package io.specmatic.core.regexgenerator.js

import org.cornutum.regexpgen.RegExpGen
import java.util.*


/**
 * Provides instances of a [RegExpGen] that generates strings matching a JavaScript <CODE>RegExp</CODE>
 * (the <A href="https://www.ecma-international.org/publications-and-standards/standards/ecma-262/#sec-patterns">ECMAScript standard</A>).
 */
class Provider : org.cornutum.regexpgen.Provider {
    /**
     * Returns a [RegExpGen] that generates strings containing characters that match the given
     * regular expression.
     */
    override fun matching(regexp: String): RegExpGen {
        return Parser(regexp).parse(false)!!
    }

    /**
     * Returns a [RegExpGen] that generates strings containing only characters that match the
     * given regular expression.
     */
    override fun matchingExact(regexp: String): RegExpGen {
        return Parser(regexp).parse(true)!!
    }

    /**
     * Returns a [RegExpGen] that generates strings that do NOT match the given regular
     * expression.
     * <P></P>
     * For some regular expressions, no result is possible. For example, there is no string that
     * does not match ".*". For such expressions, this method should return [Optional.empty].
     * <P></P>
     * This is an optional service. Throws an [UnsupportedOperationException] if not implemented.
     */
    @Throws(UnsupportedOperationException::class)
    override fun notMatching(regexp: String): Optional<RegExpGen> {
        return NotMatchingFactory.makeFrom(Parser(regexp).parse(true)!!)
    }

    companion object {
        /**
         * Returns a new [Provider].
         */
        fun forEcmaScript(): org.cornutum.regexpgen.Provider {
            return Provider()
        }
    }
}