package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.RandomGen
import org.cornutum.regexpgen.RegExpGen
import org.cornutum.regexpgen.js.RegExpGenVisitor
import org.cornutum.regexpgen.util.ToString
import java.util.*
import java.util.stream.Stream


/**
 * Base class for [RegExpGen] implementations.
 */
abstract class AbstractRegExpGen protected constructor(
    private val options_: GenOptions,
    minOccur: Int?,
    maxOccur: Int?
) :
    RegExpGen {
    /**
     * Creates a new AbstractRegExpGen instance.
     */
    /**
     * Creates a new AbstractRegExpGen instance.
     */
    protected constructor(options: GenOptions, length: Int = 1) : this(options, length, length)

    /**
     * Changes the number of occurrences allowed for this regular expression.
     */
    fun setOccurrences(minOccur: Int?, maxOccur: Int?) {
        occurrences = Bounds(minOccur, maxOccur)
    }

    val minOccur: Int
        /**
         * Returns the minimum number occurrences allowed for this regular expression.
         */
        get() = occurrences!!.minValue

    val maxOccur: Int
        /**
         * Returns the maximum number occurrences allowed for this regular expression.
         */
        get() = occurrences!!.maxValue

    /**
     * Returns the [options][GenOptions] for this generator.
     */
    override fun getOptions(): GenOptions {
        return options_
    }

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    override fun generate(random: RandomGen, bounds: Bounds): String {
        return generateLength(random, effectiveLength(bounds))
    }

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    protected abstract fun generateLength(random: RandomGen, length: Bounds): String

    open var isAnchoredStart: Boolean
        /**
         * Returns if any part of this regular expression must match the start of a string.
         */
        get() = isAnchoredStartAll
        /**
         * Changes if this regular expression must match the start of a string.
         */
        set(anchored) {
            isAnchoredStartAll = anchored
        }

    open var isAnchoredEnd: Boolean
        /**
         * Returns any part of if this regular expression must match the end of a string.
         */
        get() = isAnchoredEndAll
        /**
         * Changes if this regular expression must match the end of a string.
         */
        set(anchored) {
            isAnchoredEndAll = anchored
        }

    open val startAlternatives: Stream<AbstractRegExpGen>
        /**
         * Returns the possible starting subexpressions for this regular expression.
         */
        get() = Stream.of(this)

    open val endAlternatives: Stream<AbstractRegExpGen>
        /**
         * Returns the possible ending subexpressions for this regular expression.
         */
        get() = Stream.of(this)

    /**
     * Changes the regular expression string from which this generator was derived.
     */
    fun setSource(source: String?) {
        source_ = source
    }

    /**
     * Returns the regular expression string from which this generator was derived.
     */
    override fun getSource(): String {
        return source_!!
    }

    /**
     * Implements the Visitor pattern for [RegExpGen] implementations.
     */
    abstract fun accept(visitor: RegExpGenVisitor)

    override fun toString(): String {
        return ToString.getBuilder(this)
            .append(Objects.toString(source, ""))
            .toString()
    }

    override fun equals(`object`: Any?): Boolean {
        val other =
            if (`object` is AbstractRegExpGen)
                `object`
            else
                null

        return other != null && other.occurrences == occurrences
                && other.isAnchoredStart == isAnchoredStart && other.isAnchoredEnd == isAnchoredEnd
    }

    override fun hashCode(): Int {
        return (javaClass.hashCode()
                xor occurrences.hashCode()
                xor java.lang.Boolean.hashCode(isAnchoredStart)
                xor java.lang.Boolean.hashCode(isAnchoredEnd))
    }

    private var source_: String? = null
    /**
     * Returns the number of occurrences allowed for this regular expression.
     */
    /**
     * Changes the number of occurrences allowed for this regular expression.
     */
    var occurrences: Bounds? = null

    /**
     * Returns if this regular expression must match the start of a string.
     */
    var isAnchoredStartAll: Boolean = false
        private set

    /**
     * Returns if this regular expression must match the end of a string.
     */
    var isAnchoredEndAll: Boolean = false
        private set

    /**
     * Creates a new AbstractRegExpGen instance.
     */
    init {
        setOccurrences(minOccur, maxOccur)
    }

    /**
     * Builds an [AbstractRegExpGen] instance.
     */
    abstract class BaseBuilder<T : BaseBuilder<T>?> {
        /**
         * Returns the [AbstractRegExpGen] instance for this builder.
         */
        protected abstract val abstractRegExpGen: AbstractRegExpGen

        fun occurs(minOccur: Int?, maxOccur: Int?): T {
            abstractRegExpGen.setOccurrences(minOccur, maxOccur)
            return this as T
        }

        fun occurs(occurs: Int): T {
            abstractRegExpGen.setOccurrences(occurs, occurs)
            return this as T
        }

        fun occurs(bounds: Bounds?): T {
            abstractRegExpGen.occurrences = bounds
            return this as T
        }

        @JvmOverloads
        fun anchoredStart(anchored: Boolean = true): T {
            abstractRegExpGen.isAnchoredStart = anchored
            return this as T
        }

        @JvmOverloads
        fun anchoredEnd(anchored: Boolean = true): T {
            abstractRegExpGen.isAnchoredEnd = anchored
            return this as T
        }
    }

    companion object {
        val BUILDER_OPTIONS: GenOptions = GenOptions()
    }
}