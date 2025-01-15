package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.js.AnyPrintableGen
import org.cornutum.regexpgen.js.CharClassGen
import org.cornutum.regexpgen.js.RegExpGenVisitor


/**
 * Generates a sequence containing any of a set of characters.
 */
open class AnyOfGen : CharClassGen {
    /**
     * Creates a new AnyOfGen instance.
     */
    constructor(options: GenOptions?) : super(options)

    /**
     * Creates a new AnyOfGen instance.
     */
    constructor(options: GenOptions?, c: Char) : super(options, c)

    /**
     * Creates a new AnyOfGen instance.
     */
    constructor(options: GenOptions?, first: Char, last: Char) : super(options, first, last)

    /**
     * Creates a new AnyOfGen instance.
     */
    constructor(options: GenOptions?, chars: Set<Char?>?) : super(options, chars)

    /**
     * Implements the Visitor pattern for [org.cornutum.regexpgen.RegExpGen] implementations.
     */
    override fun accept(visitor: RegExpGenVisitor) {
        visitor.visit(this)
    }

    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as AnyOfGen
            else
                null

        return other != null
                && super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    /**
     * Builds an [AnyOfGen] instance.
     */
    class Builder @JvmOverloads constructor(options: GenOptions? = BUILDER_OPTIONS) :
        CharClassGenBuilder<Builder?>() {
        /**
         * Returns the [CharClassGen] instance for this builder.
         */
        override fun getCharClassGen(): CharClassGen {
            return anyOf_
        }

        fun anyPrintable(): Builder {
            anyOf_ = AnyPrintableGen(anyOf_.options)
            return this
        }

        fun build(): AnyOfGen {
            return anyOf_
        }

        private var anyOf_: AnyOfGen

        init {
            anyOf_ = AnyOfGen(options)
        }
    }

    companion object {
        /**
         * Returns an [AnyOfGen] builder.
         */
        fun builder(): Builder {
            return Builder()
        }

        /**
         * Returns an [AnyOfGen] builder.
         */
        fun builder(options: GenOptions?): Builder {
            return Builder(options)
        }
    }
}