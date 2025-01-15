package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.js.RegExpGenVisitor


/**
 * Generates a sequence containing any <EM>except</EM> a given set of characters.
 */
class NoneOfGen : CharClassGen {
    /**
     * Creates a new NoneOfGen instance.
     */
    constructor(options: GenOptions?) : super(options)

    /**
     * Creates a new NoneOfGen instance.
     */
    protected constructor(options: GenOptions?, c: Char) : super(options, c)

    /**
     * Creates a new NoneOfGen instance.
     */
    protected constructor(options: GenOptions?, first: Char, last: Char) : super(options, first, last)

    /**
     * Creates a new NoneOfGen instance.
     */
    protected constructor(options: GenOptions?, chars: Set<Char?>?) : super(options, chars)

    /**
     * Creates a new NoneOfGen instance.
     */
    constructor(charClass: CharClassGen) : super(charClass.options) {
        addAll(charClass)
    }

    override val chars: Array<Char>
        /**
         * Returns the characters in this class.
         */
        get() = makeChars()

    /**
     * Creates an array containing the characters in this class
     */
    override fun makeChars(): Array<Char> {
        return options.anyPrintableChars.stream()
            .filter { c: Char? -> !charSet.contains(c) }
            .toArray<Char> { _Dummy_.__Array__() }
    }

    /**
     * Implements the Visitor pattern for [org.cornutum.regexpgen.RegExpGen] implementations.
     */
    override fun accept(visitor: RegExpGenVisitor?) {
        visitor.visit(this)
    }

    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as NoneOfGen
            else
                null

        return other != null
                && super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    /**
     * Builds a [NoneOfGen] instance.
     */
    class Builder @JvmOverloads constructor(options: GenOptions? = BUILDER_OPTIONS) :
        CharClassGenBuilder<Builder?>() {
        override val charClassGen: CharClassGen
            /**
             * Returns the [CharClassGen] instance for this builder.
             */
            get() = noneOf_

        fun build(): NoneOfGen {
            return noneOf_
        }

        private val noneOf_ = NoneOfGen(options)
    }

    companion object {
        /**
         * Returns an [NoneOfGen] builder.
         */
        fun builder(): Builder {
            return Builder()
        }

        /**
         * Returns an [NoneOfGen] builder.
         */
        fun builder(options: GenOptions?): Builder {
            return Builder(options)
        }
    }
}