package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.Bounds
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.RandomGen
import org.cornutum.regexpgen.js.RegExpGenVisitor
import java.util.*
import java.util.stream.IntStream
import java.util.stream.Stream


/**
 * Generates strings matching one of a set of alternative regular expressions.
 */
class AlternativeGen
/**
 * Creates a new AlternativeGen instance.
 */
    (options: GenOptions?) : AbstractRegExpGen(options!!) {
    /**
     * Creates a new AlternativeGen instance.
     */
    constructor(options: GenOptions?, vararg members: AbstractRegExpGen) : this(options) {
        for (member: AbstractRegExpGen in members) {
            add(member)
        }
    }

    /**
     * Creates a new AlternativeGen instance.
     */

    /**
     * Adds an alternative regular expression.
     */
    fun add(member: AbstractRegExpGen) {
        members_.add(member)
    }

    val members: Iterable<AbstractRegExpGen>
        /**
         * Returns the alternative regular expressions.
         */
        get() = members_

    /**
     * Returns the minimum length for any matching string.
     */
    override fun getMinLength(): Int {
        return Bounds.productOf(minOccur, memberMinLength)
    }

    /**
     * Returns the maximum length for any matching string.
     */
    override fun getMaxLength(): Int {
        return Bounds.productOf(maxOccur, memberMaxLength)
    }

    protected val memberMinLength: Int
        /**
         * Returns the minimum length of a string that can match some member.
         */
        get() = IntStream.range(0, members_.size)
            .map({ i: Int -> members_.get(i).minLength })
            .min()
            .orElse(0)

    protected val memberMaxLength: Int
        /**
         * Returns the maximum length of a string that can match some member.
         */
        get() {
            return IntStream.range(0, members_.size)
                .map({ i: Int -> members_.get(i).maxLength })
                .max()
                .orElse(0)
        }

    /**
     * Returns a random string within the given bounds that matches this regular expression.
     */
    override fun generateLength(random: RandomGen, length: Bounds): String {
        val matching: StringBuilder = StringBuilder()

        if (maxLength > 0) {
            // Given a range of lengths...
            val lengthMin: Int = length.minValue
            val lengthMax: Int = length.maxValue


            // ...allowing for a range of occurrences...
            val memberMin: Int = memberMinLength
            val memberMax: Int = memberMaxLength
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
            var targetOccur: Int = random.within(mayOccur)
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
                // Can some random member generate the next occurrence?
                val nextMin: Int = needed / targetOccur
                val nextMax: Int = remaining / targetOccur
                val alternativeMatch: String? = completeAlternative(random, nextMin, nextMax)
                if (alternativeMatch != null) {
                    // Yes, append the next occurrence
                    matching.append(alternativeMatch)
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
     * Completes a random string with the given range that matches some alternative.
     */
    private fun completeAlternative(random: RandomGen?, needed: Int, remaining: Int): String? {
        var memberMatch: String?
        var nextMin: Int
        memberMatch = null
        nextMin = needed
        while (memberMatch == null
            && nextMin >= 0

        ) {
            val nextBounds: Bounds = Bounds(nextMin, remaining)

            memberMatch =
                memberFeasibleFor(random, nextBounds)
                    .map({ member: AbstractRegExpGen ->
                        member.generate(
                            random!!, nextBounds
                        )
                    })
                    .orElse(null)
            nextMin--
        }

        return memberMatch
    }

    /**
     * Returns a member that can generate a string within the given bounds.
     */
    private fun memberFeasibleFor(random: RandomGen?, length: Bounds): Optional<AbstractRegExpGen> {
        return random!!.shuffle(members_)
            .stream()
            .filter { member: AbstractRegExpGen -> member.isFeasibleLength(length) }
            .findFirst()
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
            return members_.stream().flatMap { r: AbstractRegExpGen -> r.startAlternatives }
        }

    override val endAlternatives: Stream<AbstractRegExpGen>
        /**
         * Returns the possible ending subexpressions for this regular expression.
         */
        get() {
            return members_.stream().flatMap { r: AbstractRegExpGen -> r.endAlternatives }
        }

    /**
     * Implements the Visitor pattern for [AbstractRegExpGen] implementations.
     */
    override fun accept(visitor: RegExpGenVisitor) {
        visitor.visit(this)
    }

    override fun equals(`object`: Any?): Boolean {
        val other: AlternativeGen? =
            if (`object` != null && `object`.javaClass == javaClass)
                `object` as AlternativeGen
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
     * Builds an [AlternativeGen] instance.
     */
    class Builder @JvmOverloads constructor(options: GenOptions? = BUILDER_OPTIONS) :
        BaseBuilder<Builder?>() {
        override val abstractRegExpGen: AbstractRegExpGen
            /**
             * Returns the [AbstractRegExpGen] instance for this builder.
             */
            get() {
                return alternative_
            }

        fun add(vararg members: AbstractRegExpGen): Builder {
            for (member: AbstractRegExpGen in members) {
                alternative_.add(member)
            }
            return this
        }

        fun addAll(members: Iterable<AbstractRegExpGen>): Builder {
            for (member: AbstractRegExpGen in members) {
                alternative_.add(member)
            }
            return this
        }

        fun build(): AlternativeGen {
            return alternative_
        }

        private val alternative_: AlternativeGen

        init {
            alternative_ = AlternativeGen(options)
        }
    }

    companion object {
        /**
         * Returns an [AlternativeGen] builder.
         */
        fun builder(): Builder {
            return Builder()
        }

        /**
         * Returns an [AlternativeGen] builder.
         */
        fun builder(options: GenOptions?): Builder {
            return Builder(options)
        }
    }
}