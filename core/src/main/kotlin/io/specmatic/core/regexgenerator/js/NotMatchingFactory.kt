package io.specmatic.core.regexgenerator.js
import org.apache.commons.collections4.IterableUtils
import org.apache.commons.collections4.SetUtils
import org.cornutum.regexpgen.RegExpGen
import org.cornutum.regexpgen.js.RegExpGenVisitor
import org.cornutum.regexpgen.js.SeqGen
import org.cornutum.regexpgen.util.CharUtils
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream


/**
 * Creates a RegExpGen that produces strings that do NOT match a specified RegExpGen.
 */
class NotMatchingFactory private constructor() : RegExpGenVisitor {
    fun visit(regExpGen: AlternativeGen) {
        sequences =
            IterableUtils.toList(regExpGen.members)
                .stream()
                .flatMap { gen: AbstractRegExpGen -> sequencesFor(gen).stream() }
                .collect(Collectors.toList())
    }

    override fun visit(regExpGen: SeqGen) {
        sequences =
            allSequences(
                IterableUtils.toList<org.cornutum.regexpgen.js.AbstractRegExpGen>(regExpGen.members)
                    .stream()
                    .map { regExpGen: org.cornutum.regexpgen.js.AbstractRegExpGen -> sequencesFor(regExpGen) }
                    .collect(Collectors.toList<Any>()))
    }

    fun visit(regExpGen: AnyOfGen) {
        visitCharClass(regExpGen)
    }

    fun visit(regExpGen: NoneOfGen) {
        visitCharClass(regExpGen)
    }

    fun visit(regExpGen: AnyPrintableGen) {
        visitCharClass(regExpGen)
    }

    fun visitCharClass(regExpGen: CharClassGen) {
        sequences =
            Optional.of(regExpGen)
                .filter { chars: CharClassGen -> chars.maxOccur > 0 }
                .map { chars: CharClassGen ->
                    listOf(
                        listOf(chars)
                    )
                }
                .orElse(emptyList())
    }

    /**
     * Returns the cross product of all matching generator sequences for each member of a SeqGen.
     */
    private fun allSequences(memberSequences: List<List<List<CharClassGen>>>): List<List<CharClassGen>> {
        val all: MutableList<List<CharClassGen>> = ArrayList()
        val members = memberSequences.size
        if (members == 1) {
            all.addAll(memberSequences[0])
        } else {
            allSequences(memberSequences.subList(0, members - 1))
                .forEach(Consumer { prevSeq: List<CharClassGen> ->
                    memberSequences[members - 1]
                        .forEach(Consumer { nextSeq: List<CharClassGen> ->
                            all.add(
                                Stream.concat(
                                    prevSeq.stream(),
                                    nextSeq.stream()
                                ).collect(Collectors.toList())
                            )
                        })
                })
        }

        return all
    }

    private var sequences: List<List<CharClassGen>>? = null

    companion object {
        /**
         * Returns RegExpGen that produces strings that do NOT match the specified RegExpGen.
         * Returns [Optional.empty] if unable to generate not-matching strings.
         */
        fun makeFrom(regExpGen: AbstractRegExpGen): Optional<RegExpGen> {
            val notMatching: AbstractRegExpGen?

            // Get all matching sequences, ignoring those that match only an empty string.
            val anchored = regExpGen.isAnchoredStart && regExpGen.isAnchoredEnd
            val sequences =
                sequencesFor(regExpGen)
                    .stream()
                    .filter { chars: List<CharClassGen> -> !(anchored && chars.isEmpty()) }
                    .collect(Collectors.toList())


            // Empty string is the only possible match?
            if (sequences.isEmpty()) {
                // Yes, then any non-empty string is "not matching"
                notMatching = withSource(AnyPrintableGen(regExpGen.options, 1, null), ".+")
            } else if (sequences.stream().anyMatch { obj: List<CharClassGen> -> obj.isEmpty() }) {
                // Yes, then any string could be a match.
                notMatching = null
            } else {
                // Given all possible matching initial character class sequences...
                val notAlternatives: MutableList<AbstractRegExpGen?> = ArrayList()

                val initial = getInitialRequired(sequences)
                val initialPrefix = getInitialPrefix(sequences)
                val initialAny = getAnyOf(initial)
                val initialNone = getNoneOf(initial)

                val mismatching: Set<Char?>? =  // Are initial chars defined and consistent?
                    if (initialAny == initialNone)  // No, can't determine a mismatching generator
                        null else  // Yes, find a set of mismatching chars that are...
                        Stream.concat( // ... required to be excluded
                            SetUtils.difference(initialNone, initialAny)
                                .stream(),  // ... or not among those required to be allowed

                            CharUtils.printableChars()
                                .filter { c: Char -> !initialAny.contains(c) }) // ... and are excluded from any optional prefix

                            .filter { c: Char -> isExcluded(initialPrefix, c) }
                            .limit(32)
                            .collect(Collectors.toSet())

                // Mismatching chars found?
                if (Optional.ofNullable<Set<Char?>?>(mismatching).map<Boolean> { m: Set<Char?>? -> !m!!.isEmpty() }
                        .orElse(false)) {
                    // Yes, include a mismatching generator.
                    notAlternatives.add(
                        withLength(
                            withSource(AnyOfGen(regExpGen.options, mismatching), mismatching),
                            1,
                            null
                        )
                    )
                }

                // Minimum length required?
                if (regExpGen.minLength > 0) {
                    // Yes, include a too-short generator
                    val allowed =
                        initial.stream()
                            .flatMap { chars: CharClassGen -> Arrays.stream(chars.chars) }
                            .limit(32)
                            .collect(Collectors.toSet())

                    notAlternatives.add(
                        withLength(
                            withSource(AnyOfGen(regExpGen.options, allowed), allowed),
                            0,
                            regExpGen.minLength - 1
                        )
                    )
                }

                notMatching =
                    if (notAlternatives.isEmpty()) null else if (notAlternatives.size == 1) notAlternatives[0] else withSource(
                        AlternativeGen(regExpGen.options, notAlternatives), notAlternatives
                    )
            }

            return Optional.ofNullable(notMatching).map { regExpGen: AbstractRegExpGen ->
                withAnchors(
                    regExpGen
                )
            }
        }

        /**
         * Returns the generators for the initial required char of any matching sequence.
         */
        private fun getInitialRequired(sequences: List<List<CharClassGen>>): Set<CharClassGen> {
            return initialChars(sequences).filter { chars: CharClassGen -> chars.minOccur > 0 }
                .collect(Collectors.toSet())
        }

        /**
         * Returns the generators for any optional prefix of any matching sequence.
         */
        private fun getInitialPrefix(sequences: List<List<CharClassGen>>): Set<CharClassGen> {
            return initialChars(sequences).filter { chars: CharClassGen -> chars.minOccur == 0 }
                .collect(Collectors.toSet())
        }

        /**
         * Returns true if the given character does not match any of the given the generators.
         */
        private fun isExcluded(prefix: Set<CharClassGen>, c: Char): Boolean {
            return prefix.stream().allMatch { chars: CharClassGen -> isExcluded(chars, c) }
        }

        /**
         * Returns true if the given character does not match the given generator.
         */
        private fun isExcluded(chars: CharClassGen, c: Char): Boolean {
            return if (chars is NoneOfGen) chars.charSet.contains(c) else if (chars is AnyPrintableGen) CharUtils.isLineTerminator(
                c
            ) else !chars.charSet.contains(c)
        }

        /**
         * Returns the generators of the given type for the initial char of any matching sequence.
         */
        private fun getAnyOf(candidates: Set<CharClassGen>): Set<Char> {
            return candidates.stream()
                .filter { chars: CharClassGen? -> chars is AnyOfGen }
                .flatMap { chars: CharClassGen -> chars.charSet.stream() }
                .collect(Collectors.toSet())
        }

        /**
         * Returns the generators of the given type for the initial char of any matching sequence.
         */
        private fun getNoneOf(candidates: Set<CharClassGen>): Set<Char> {
            return candidates.stream()
                .filter { chars: CharClassGen? -> chars is NoneOfGen }
                .flatMap { chars: CharClassGen -> chars.charSet.stream() }
                .collect(Collectors.toSet())
        }

        /**
         * Returns the generators for the initial char of any matching sequence.
         */
        private fun initialChars(sequences: List<List<CharClassGen>>): Stream<CharClassGen> {
            return sequences.stream().filter { seq: List<CharClassGen> -> seq.size > 0 }
                .map { seq: List<CharClassGen> -> seq[0] }
        }

        private fun <T : AbstractRegExpGen?> withSource(regExpGen: T, source: Set<Char>): T {
            return withSource(
                regExpGen,
                source.stream().map { c: Char? -> CharUtils.charClassLiteral(c) }
                    .collect(Collectors.joining("", "[", "]")))
        }

        private fun withSource(alternativeGen: AlternativeGen, members: List<AbstractRegExpGen?>?): AlternativeGen {
            return withSource(
                alternativeGen,
                members!!.stream().map { obj: AbstractRegExpGen? -> obj!!.source }
                    .collect(Collectors.joining("|", "(", ")")))
        }

        private fun <T : AbstractRegExpGen?> withSource(regExpGen: T, source: String): T {
            regExpGen!!.setSource(source)
            return regExpGen
        }

        private fun <T : AbstractRegExpGen?> withLength(regExpGen: T, minLength: Int, maxLength: Int?): T {
            regExpGen!!.setOccurrences(minLength, maxLength)
            return withSource<T>(
                regExpGen,
                String.format("%s{%s,%s}", regExpGen.source, minLength, Objects.toString(maxLength, ""))
            )
        }

        private fun <T : AbstractRegExpGen?> withAnchors(regExpGen: T): T? {
            return Optional.ofNullable(regExpGen)
                .map { r: T ->
                    r!!.isAnchoredStart = true
                    r.isAnchoredEnd = true
                    withSource<T>(r, String.format("^%s$", r.source))
                }
                .orElse(null)
        }

        /**
         * Returns all matching generator sequences.
         */
        protected fun sequencesFor(regExpGen: AbstractRegExpGen): List<List<CharClassGen>> {
            val factory = NotMatchingFactory()
            regExpGen.accept(factory)

            val emptySeq = emptyList<CharClassGen>()
            return Stream.concat(
                factory.sequences!!.stream(),

                Optional.of(emptySeq)
                    .filter { empty: List<CharClassGen>? -> regExpGen.minOccur == 0 }
                    .map<Stream<List<CharClassGen>>> { t: List<CharClassGen>? ->
                        Stream.of(
                            t
                        )
                    }
                    .orElse(Stream.empty()))

                .collect(Collectors.toList())
        }
    }
}