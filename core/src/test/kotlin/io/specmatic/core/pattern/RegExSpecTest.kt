package io.specmatic.core.pattern

import com.mifmif.common.regex.Generex
import dk.brics.automaton.RegExp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RegExSpecTest {
    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{1,3}; 1; 1",
        "[a-zA-Z0-9]{1,3}; 2; 2",
        "[a-zA-Z0-9]{1,3}; 0; 1",
        "[a-zA-Z0-9]{1,}; 1; 1",
        "[a-zA-Z0-9]{1,}; 30; 30",
        "[a-zA-Z0-9]*; 0; 0",
        "[a-zA-Z0-9]*; 30; 30",
        "[a-zA-Z0-9]+; 1; 1",
        "[a-zA-Z0-9]+; 30; 30",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 3; 3",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 2; 3",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 5; 5",
        delimiterString = "; "
    )
    fun `should generate min length based on the regex and min length`(regex: String, minLen: Int, expectedLength: Int) {
        val shortestString = RegExSpec(regex).generateShortestStringOrRandom(minLen)
        assertThat(shortestString).hasSize(expectedLength)
    }

    @Test
    fun `should generate random min length string when regex is null`() {
        val randomString = RegExSpec(null).generateShortestStringOrRandom(10)
        assertThat(randomString).hasSize(10)
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{1,3}; 3; 3",
        "[a-zA-Z0-9]{1,3}; 2; 2",
        "[a-zA-Z0-9]{1,3}; 4; 3",
        "[a-zA-Z0-9]{1,}; 30; 30",
        "[a-zA-Z0-9]*; 30; 30",
        "[a-zA-Z0-9]+; 30; 30",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 7; 7",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 6; 6",
        "[a-zA-Z0-9]{1,3}-[a-zA-Z0-9]{1,3}; 8; 7",
        delimiterString = "; "
    )
    fun `should generate max length based on the regex and max length`(regex: String, max: Int, expectedLength: Int) {
        val longestString = RegExSpec(regex).generateLongestStringOrRandom(max)
        assertThat(longestString).hasSize(expectedLength)
    }

    @Test
    fun `should generate random max length string when regex is null`() {
        val randomString = RegExSpec(null).generateLongestStringOrRandom(10)
        assertThat(randomString).hasSize(10)
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,20}; 9",
        "[a-zA-Z0-9]{0,20}; -1",
        "[a-zA-Z0-9]{10}; 9",
        "[a-zA-Z0-9]{5}[a-zA-Z0-9]{5}; 9",
        delimiterString = "; "
    )
    fun `min is less than lower bound of a finite regex should be accept`(regex: String, min: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isTrue
        automaton.getShortestExample(true).let {
            assertThat(it.length).isGreaterThan(min)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,20}; 21",
        "[a-zA-Z0-9]{0,20}; 21",
        "[a-zA-Z0-9]{10}; 11",
        "[a-zA-Z0-9]{5}[a-zA-Z0-9]{5}; 11",
        delimiterString = "; "
    )
    fun `min is greater than upper bound of a finite regex should be rejected`(regex: String, min: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isTrue
        automaton.getShortestExample(true).let {
            assertThat(it.length).isLessThan(min)
        }
        assertThat(Generex(regex).getMatchedStrings(min).size).isNotZero()
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,20}; 9",
        "[a-zA-Z0-9]{0,20}; 0",
        "[a-zA-Z0-9]{10}; 9",
        "[a-zA-Z0-9]{5}[a-zA-Z0-9]{5}; 9",
        delimiterString = "; "
    )
    fun `max is less than lower bound of a finite regex should be rejected`(regex: String, max: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isTrue
        automaton.getShortestExample(true).let {
            assertThat(it.length).isGreaterThanOrEqualTo(max)
        }
        assertThat(Generex(regex).getMatchedStrings(max).size).isZero()
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,20}; 21",
        "[a-zA-Z0-9]{0,20}; 21",
        "[a-zA-Z0-9]{10}; 11",
        "[a-zA-Z0-9]{5}[a-zA-Z0-9]{5}; 11",
        delimiterString = "; "
    )
    fun `max is greater than upper bound of a finite regex should be accepted`(regex: String, max: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isTrue
        automaton.getShortestExample(true).let {
            assertThat(it.length).isLessThan(max)
        }
        assertThat(Generex(regex).getMatchedStrings(max).size).isNotZero()
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,}; 9",
        "[a-zA-Z0-9]{0,}; -1",
        "[a-zA-Z0-9]{5,}[a-zA-Z0-9]{5,}; 9",
        "[a-zA-Z0-9]+; 0",
        ".*; -1",
        ".+; 0",
        delimiterString = "; "
    )
    fun `min is less than lower bound of an infinite regex should be accept`(regex: String, min: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isFalse
        automaton.getShortestExample(true).let {
            assertThat(it.length).isGreaterThan(min)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "[a-zA-Z0-9]{10,}; 9",
        "[a-zA-Z0-9]{0,}; 0",
        "[a-zA-Z0-9]{5,}[a-zA-Z0-9]{5,}; 9",
        "[a-zA-Z0-9]+; 0",
        ".*; 0",
        ".+; 0",
        delimiterString = "; "
    )
    fun `max is less than lower bound of an infinite regex should be rejected`(regex: String, max: Int) {
        val automaton = RegExp(regex).toAutomaton()
        assertThat(automaton.isFinite).isFalse
        automaton.getShortestExample(true).let {
            assertThat(it.length).isGreaterThanOrEqualTo(max)
        }
        assertThat(Generex(regex).getMatchedStrings(max).size).isZero()
    }
}