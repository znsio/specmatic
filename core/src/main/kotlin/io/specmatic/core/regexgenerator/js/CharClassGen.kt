package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.RandomGen
import org.cornutum.regexpgen.js.CharClasses
import java.util.*
import java.util.function.Consumer
import java.util.stream.IntStream
import kotlin.math.max
import kotlin.math.min


/**
 * Generates a sequence based on a set of characters.
 */
abstract class CharClassGen : AbstractRegExpGen {
    /**
     * Creates a new CharClassGen instance.
     */
    protected constructor(options: GenOptions?) : super(options!!)

    /**
     * Creates a new CharClassGen instance.
     */
    protected constructor(options: GenOptions?, c: Char) : super(options!!) {
        add(c)
    }

    /**
     * Creates a new CharClassGen instance.
     */
    protected constructor(options: GenOptions?, first: Char, last: Char) : super(
        options!!
    ) {
        addAll(first, last)
    }

    /**
     * Creates a new CharClassGen instance.
     */
    protected constructor(options: GenOptions?, chars: Set<Char>?) : super(
        options!!
    ) {
        addAll(chars)
    }

    /**
     * Adds a character to this class
     */
    fun add(c: Char) {
        charSet.add(c)
        charArray_ = null
    }

    /**
     * Adds all characters in the given range to this class
     */
    fun addAll(first: Char, last: Char) {
        val min = Character.codePointAt(charArrayOf(min(first.code.toDouble(), last.code.toDouble()) as Char), 0)
        val max = Character.codePointAt(charArrayOf(max(first.code.toDouble(), last.code.toDouble()) as Char), 0)

        val range = StringBuilder()
        for (c in min..max) {
            range.appendCodePoint(c)
        }

        addAll(range.toString())
    }

    /**
     * Adds all of the given characters to this class.
     */
    fun addAll(chars: String) {
        for (i in 0 until chars.length) {
            add(chars[i])
        }
    }

    /**
     * Adds all of the given characters to this class.
     */
    fun addAll(charClass: CharClassGen?) {
        if (charClass != null) {
            for (c in charClass.chars!!) {
                add(c)
            }
        }
    }

    /**
     * Adds all of the given characters to this class.
     */
    fun addAll(chars: Set<Char>?) {
        chars?.forEach(Consumer { c: Char -> add(c) })
    }

    open val chars: Array<Char>?
        /**
         * Returns the characters in this class.
         */
        get() {
            if (charArray_ == null) {
                charArray_ = makeChars()
            }

            return charArray_
        }

    /**
     * Returns true if the given character belongs to this class.
     */
    fun contains(c: Char): Boolean {
        return Arrays.stream(chars).anyMatch { classChar: Char -> c == classChar }
    }

    /**
     * Creates an array containing the characters in this class
     */
    protected open fun makeChars(): Array<Char> {
        return charSet.stream().toArray<Char> { _Dummy_.__Array__() }
    }

    val isEmpty: Boolean
        /**
         * Returns true if and only if this class contains no characters.
         */
        get() = charSet.isEmpty()

    /**
     * Returns the minimum length for any matching string.
     */
    override fun getMinLength(): Int {
        return minOccur
    }

    /**
     * Returns the maximum length for any matching string.
     */
    override fun getMaxLength(): Int {
        return maxOccur
    }

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    override fun generateLength(random: RandomGen?, length: Bounds?): String {
        val matching = StringBuilder()

        val chars = chars
        val generated = random!!.within(length)
        check(!(generated > 0 && chars!!.size == 0)) {
            String.format(
                "%s: Can't generate string of valid length=%s -- no matching characters available",
                this,
                generated
            )
        }

        IntStream.range(0, generated)
            .forEach { i: Int -> matching.append(chars!![random.below(chars.size)]) }

        return matching.toString()
    }

    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` is CharClassGen)
                `object`
            else
                null

        return other != null && super.equals(other)
                && other.charSet == charSet
    }

    override fun hashCode(): Int {
        return (super.hashCode()
                xor charSet.hashCode())
    }

    /**
     * Returns the set of characters that define this class.
     */
    val charSet: MutableSet<Char> = HashSet()
    private var charArray_: Array<Char>? = null

    /**
     * Builds a [CharClassGen] instance.
     */
    abstract class CharClassGenBuilder<T : CharClassGenBuilder<T>?> :
        BaseBuilder<T>() {
        /**
         * Returns the [CharClassGen] instance for this builder.
         */
        protected abstract val charClassGen: CharClassGen

        override val abstractRegExpGen: AbstractRegExpGen
            /**
             * Returns the [AbstractRegExpGen] instance for this builder.
             */
            get() = charClassGen

        fun add(c: Char): T {
            charClassGen.add(c)
            return this as T
        }

        fun addAll(first: Char, last: Char): T {
            charClassGen.addAll(first, last)
            return this as T
        }

        fun addAll(chars: String): T {
            charClassGen.addAll(chars)
            return this as T
        }

        fun addAll(charClass: CharClassGen?): T {
            charClassGen.addAll(charClass)
            return this as T
        }

        fun addAll(chars: Set<Char>?): T {
            charClassGen.addAll(chars)
            return this as T
        }

        fun digit(): T {
            charClassGen.addAll(charClasses_.digit())
            return this as T
        }

        fun nonDigit(): T {
            charClassGen.addAll(charClasses_.nonDigit())
            return this as T
        }

        fun word(): T {
            charClassGen.addAll(charClasses_.word())
            return this as T
        }

        fun nonWord(): T {
            charClassGen.addAll(charClasses_.nonWord())
            return this as T
        }

        fun space(): T {
            charClassGen.addAll(charClasses_.space())
            return this as T
        }

        fun nonSpace(): T {
            charClassGen.addAll(charClasses_.nonSpace())
            return this as T
        }

        private val charClasses_ = CharClasses(BUILDER_OPTIONS)
    }
}