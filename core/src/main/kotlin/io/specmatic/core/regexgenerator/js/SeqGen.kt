package io.specmatic.core.regexgenerator.js

import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.RandomGen
import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream
import kotlin.math.max
import kotlin.math.min


/**
 * Generates strings matching a sequence of regular expressions.
 */
abstract class SeqGen
/**
 * Creates a new SeqGen instance.
 */
    (options: GenOptions?) : AbstractRegExpGen(options!!) {
    /**
     * Creates a new SeqGen instance.
     */
    constructor(options: GenOptions?, vararg members: AbstractRegExpGen?) : this(options) {
        for (member: AbstractRegExpGen? in members) {
            add(member)
        }
    }

    /**
     * Creates a new SeqGen instance.
     */
    constructor(options: GenOptions?, members: Iterable<T?>) : this(options) {
        for (member: AbstractRegExpGen? in members) {
            add(member)
        }
    }

    /**
     * Adds a regular expression to this sequence.
     */
    fun add(member: AbstractRegExpGen?) {
        if (member != null) {
            members_.add(member)
        }
    }

    /**
     * Add a sequence of characters to this sequence.
     */
    fun add(chars: String?) {
        Stream.of(Optional.ofNullable(chars).orElse(""))
            .flatMap { s: String -> IntStream.range(0, s.length).mapToObj({ i: Int -> s.get(i) }) }
            .forEach { c: Char? -> add(AnyOfGen(options, c!!)) }
    }


    val members: Iterable<AbstractRegExpGen>
        /**
         * Returns the members of this sequence.
         */
        get() = members_

    /**
     * Returns the minimum length for any matching string.
     */
    override fun getMinLength(): Int {
        return Bounds.productOf(minOccur, membersMinLength)
    }

    /**
     * Returns the maximum length for any matching string.
     */
    override fun getMaxLength(): Int {
        return Bounds.productOf(maxOccur, membersMaxLength)
    }

    protected val membersMinLength: Int
        /**
         * Returns the minimum length for any matching sequence.
         */
        get() = getRemainingMinLength(0)

    /**
     * Returns the minimum length for any matching subsequence starting with the i-th member
     */
    private fun getRemainingMinLength(start: Int): Int {
        return IntStream.range(start, members_.size)
            .map({ i: Int -> members_.get(i).minLength })
            .reduce({ a: Int, b: Int -> Bounds.sumOf(a, b) })
            .orElse(0)
    }

    /**
     * Returns the maximum length for any matching subsequence starting with the i-th member
     */
    private fun getRemainingMaxLength(start: Int): Int {
        return IntStream.range(start, members_.size)
            .map({ i: Int -> members_.get(i).maxLength })
            .reduce({ a: Int, b: Int -> Bounds.sumOf(a, b) })
            .orElse(0)
    }

    protected val membersMaxLength: Int
        /**
         * Returns the maximum length for any matching sequence.
         */
        get() = IntStream.range(0, members_.size)
            .map({ i: Int -> members_.get(i).maxLength })
            .reduce({ a: Int, b: Int -> Bounds.sumOf(a, b) })
            .orElse(0)

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    override fun generateLength(random: RandomGen?, length: Bounds?): String {
        val matching: StringBuilder = StringBuilder()

        if (maxLength > 0) {
            // Given a range of lengths...
            val lengthMin: Int = length!!.minValue
            val lengthMax: Int = length.maxValue


            // ...allowing for a range of occurrences...
            val memberMin: Int = membersMinLength
            val memberMax: Int = membersMaxLength
            val mayOccurMin: Int = lengthMin / memberMax
            val mayOccurMax: Int = Bounds.bounded(lengthMax).map({ max: Int? ->
                Bounds.dividedBy(
                    max!!, memberMin
                )
            }).orElse(Bounds.UNBOUNDED)
            val mayOccur: Bounds =
                Bounds(mayOccurMin, mayOccurMax)
                    .clippedTo("Occurrences", minOccur, maxOccur)

            // ...for a random number of occurrences...
            var targetOccur: Int = random!!.within(mayOccur)
            val targetLength: Int =
                Bounds.bounded(lengthMax).orElse(targetOccur * random.within(Bounds(memberMin, memberMax)))

            // ...generate a random match for each occurrence
            var remaining: Int
            var needed: Int
            remaining = targetLength
            needed = lengthMin
            while (targetOccur > 0
                && remaining > 0

            ) {
                // Next occurrence match complete?
                val nextMin: Int = needed / targetOccur
                val nextMax: Int = remaining / targetOccur
                val seqMatch: String? = completeSeq(random, 0, nextMin, nextMax)
                if (seqMatch != null) {
                    // Yes, append the next occurrence
                    matching.append(seqMatch)
                } else {
                    // No, no more occurrences are possible now
                    targetOccur = 0
                }
                targetOccur--
                remaining = targetLength - matching.length
                needed = lengthMin - matching.length
            }
        }

        return matching.toString()
    }

    /**
     * Completes a random string with the given range that matches this sequence starting with the i'th member.
     */
    private fun completeSeq(random: RandomGen?, i: Int, needed: Int, remaining: Int): String? {
        val member: AbstractRegExpGen = members_.get(i)
        val memberMin: Int = member.minLength
        val memberMax: Int = member.maxLength

        var matching: String?
        var memberMatchMin: Int
        val memberMatchMax: Int
        matching = null
        memberMatchMax = remaining - getRemainingMinLength(i + 1)
        memberMatchMin = min(
            memberMax.toDouble(),
            max(memberMin.toDouble(), Bounds.reduceBy(needed, getRemainingMaxLength(i + 1)).toDouble())
        )
            .toInt()
        while (matching == null && memberMatchMax >= memberMatchMin && memberMatchMin >= memberMin

        ) {
            val memberMatch: String? = members_.get(i).generate(random!!, Bounds(memberMatchMin, memberMatchMax))

            matching =
                if (memberMatch != null && i + 1 < members_.size) Optional.ofNullable(
                    completeSeq(
                        random,
                        i + 1,
                        needed - memberMatch.length,
                        remaining - memberMatch.length
                    )
                )
                    .map({ remainingMatch: String -> memberMatch + remainingMatch })
                    .orElse(null) else memberMatch
            memberMatchMin--
        }

        return matching
    }

    override var isAnchoredStart: Boolean
        /**
         * Returns if any part of this regular expression must match the start of a string.
         */
        get() {
            return isAnchoredStartAll
                    || startAlternatives.anyMatch { r: AbstractRegExpGen -> r.isAnchoredStart }
        }
        set(isAnchoredStart) {
            super.isAnchoredStart = isAnchoredStart
        }

    override var isAnchoredEnd: Boolean
        /**
         * Returns if any part of this regular expression must match the end of a string.
         */
        get() {
            return isAnchoredEndAll
                    || endAlternatives.anyMatch { r: AbstractRegExpGen -> r.isAnchoredEnd }
        }
        set(isAnchoredEnd) {
            super.isAnchoredEnd = isAnchoredEnd
        }

    override val startAlternatives: Stream<AbstractRegExpGen>
        /**
         * Returns the possible starting subexpressions for this regular expression.
         */
        get() {
            return if (members_.isEmpty()) Stream.empty() else members_.get(0).startAlternatives
        }

    override val endAlternatives: Stream<AbstractRegExpGen>
        /**
         * Returns the possible ending subexpressions for this regular expression.
         */
        get() {
            return if (members_.isEmpty()) Stream.empty() else members_.get(members_.size - 1).endAlternatives
        }

    /**
     * Implements the Visitor pattern for [AbstractRegExpGen] implementations.
     */
    fun accept(visitor: RegExpGenVisitor) {
        visitor.visit(this)
    }

    override fun equals(`object`: Any?): Boolean {
        val other: SeqGen? =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as SeqGen
            else
                null

        return other != null && super.equals(other)
                && other.members_ == members_
    }

    override fun hashCode(): Int {
        return (super.hashCode()
                xor members_.hashCode())
    }

    private val members_: MutableList<AbstractRegExpGen> = ArrayList()

    /**
     * Builds a [SeqGen] instance.
     */
    class Builder @JvmOverloads constructor(options: GenOptions? = BUILDER_OPTIONS) :
        BaseBuilder<Builder?>() {
        override val abstractRegExpGen: AbstractRegExpGen
            /**
             * Returns the [AbstractRegExpGen] instance for this builder.
             */
            get() {
                return seq_
            }

        fun add(vararg members: AbstractRegExpGen?): Builder {
            for (member: AbstractRegExpGen? in members) {
                seq_.add(member)
            }
            return this
        }

        fun addAll(members: Iterable<AbstractRegExpGen?>): Builder {
            for (member: AbstractRegExpGen? in members) {
                seq_.add(member)
            }
            return this
        }

        fun add(chars: String?): Builder {
            seq_.add(chars)
            return this
        }

        fun build(): SeqGen {
            return seq_
        }

        private val seq_: SeqGen

        init {
            seq_ = SeqGen(options)
        }
    }

    companion object {
        /**
         * Returns an [SeqGen] builder.
         */
        fun builder(): Builder {
            return Builder()
        }

        /**
         * Returns an [SeqGen] builder.
         */
        fun builder(options: GenOptions?): Builder {
            return Builder(options)
        }
    }
}