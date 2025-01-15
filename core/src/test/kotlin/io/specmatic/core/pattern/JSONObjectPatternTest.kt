package io.specmatic.core.pattern

import io.specmatic.GENERATION
import io.specmatic.conversions.OpenApiSpecification
import io.specmatic.core.*
import io.specmatic.core.utilities.Flags.Companion.ALL_PATTERNS_MANDATORY
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_QUERY_PARAMS
import io.specmatic.core.utilities.Flags.Companion.EXTENSIBLE_SCHEMA
import io.specmatic.core.utilities.Flags.Companion.IGNORE_INLINE_EXAMPLES
import io.specmatic.core.utilities.Flags.Companion.IGNORE_INLINE_EXAMPLE_WARNINGS
import io.specmatic.core.utilities.Flags.Companion.MAX_TEST_REQUEST_COMBINATIONS
import io.specmatic.core.utilities.exceptionCauseMessage
import io.specmatic.core.value.*
import io.specmatic.shouldNotMatch
import io.specmatic.stub.captureStandardOutput
import io.specmatic.trimmedLinesString
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.function.Consumer

internal class JSONObjectPatternTest {
    @Test
    fun `should filter out optional and extended keys from object and sub-objects`() {
        val pattern = parsedPattern("""{
            "topLevelMandatoryKey": "(number)",
            "topLevelOptionalKey?": "(string)",
            "subMandatoryObject": {
                "subMandatoryKey": "(string)",
                "subOptionalKey?": "(number)"
            },
            "subOptionalObject?": {
                "subMandatoryKey": "(string)",
                "subOptionalKey": "(number)"
            }
        }
        """.trimIndent())
        val matchingValue = parsedValue("""{
            "topLevelMandatoryKey": 10,
            "topLevelOptionalKey": "value",
            "topLevelExtendedKey": "value",
            "subMandatoryObject": {
                "subObjectExtendedKey": "value",
                "subMandatoryKey": "value",
                "subOptionalKey": 10
            },
            "subOptionalObject": {
                "subObjectExtendedKey": "value",
                "subMandatoryKey": "value",
                "subOptionalKey": 10
            }
        }
        """.trimIndent())

        val valueWithoutOptionals = pattern.eliminateOptionalKey(matchingValue, Resolver())
        val expectedValue = parsedValue("""{
            "topLevelMandatoryKey": 10,
            "subMandatoryObject": {
                "subMandatoryKey": "value"
            }
        }        
        """.trimIndent())

        assertThat(valueWithoutOptionals).isEqualTo(expectedValue)
    }

    @Test
    fun `Given an optional key, the generated object should contain the key without the question mark`() {
        when (val result = parsedPattern("""{"id?": "(number)"}""", null).generate(Resolver())) {
            is JSONObjectValue -> assertTrue("id" in result.jsonObject)
            else -> throw Exception("Wrong type, got ${result.javaClass}, expected JSONObjectValue")
        }
    }

    @Test
    fun `Given an optional key, the unsuffixed key should be looked up in state when generating a value`() {
        val facts = HashMap<String, Value>().apply {
            put("id", NumberValue(12345))
        }

        val resolver = Resolver(facts)

        when (val value = parsedPattern("""{"id?": "(number)"}""", null).generate(resolver)) {
            is JSONObjectValue -> {
                val id = value.jsonObject["id"] as NumberValue
                assertEquals(12345, id.number)
            }
            else -> Exception("Expected JSONObjectValue, got ${value.javaClass}")
        }
    }

    @Test
    fun `should not ignore extra keys by default`() {
        val value = parsedValue("""{"expected": 10, "unexpected": 20}""")
        val pattern = parsedPattern("""{"expected": "(number)"}""")

        value shouldNotMatch pattern
    }

    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch toJSONObjectPattern(mapOf("name" to StringPattern()))
    }

    @Test
    fun `it should encompass itself`() {
        val type = parsedPattern("""{"name": "(string)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `json type should encompass tabular`() {
        val json = parsedPattern("""{"name": "(string)"}""") as JSONObjectPattern
        val tabular = TabularPattern(json.pattern)

        assertThat(json.encompasses(tabular, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass itself with a nullable value`() {
        val type = parsedPattern("""{"number": "(number?)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `having a non-null value it should NOT encompass another with a nullable value of the same type`() {
        val bigger = parsedPattern("""{"number": "(number?)"}""")
        val smallerWithNumber = parsedPattern("""{"number": "(number)"}""")
        val smallerWithNull = parsedPattern("""{"number": "(null)"}""")

        assertThat(smallerWithNumber.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
        assertThat(smallerWithNull.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `having a nullable value it should encompass another with a non null value of the same type`() {
        val bigger = parsedPattern("""{"number": "(number?)"}""")
        val smallerWithNumber = parsedPattern("""{"number": "(number)"}""")
        val smallerWithNull = parsedPattern("""{"number": "(null)"}""")

        assertThat(bigger.encompasses(smallerWithNumber, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(bigger.encompasses(smallerWithNull, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass with an optional key`() {
        val type = parsedPattern("""{"number?": "(number)"}""")
        assertThat(type.encompasses(type, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with the optional key missing`() {
        val bigger = parsedPattern("""{"required": "(number)", "optional?": "(number)"}""")
        val smaller = parsedPattern("""{"required": "(number)"}""")
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `it should encompass another with an unheard of key`() {
        val bigger = parsedPattern("""{"required": "(number)"}""")
        val smaller = parsedPattern("""{"required": "(number)", "extra": "(number)"}""")
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }


    @Test
    fun `it should encompass itself when ellipsis is present`() {
        val bigger = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern(), "..." to StringPattern()))
        assertThat(bigger.encompasses(bigger, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `type with ellipsis is equivalent to a type with the same keys except the ellipsis`() {
        val theOne = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern()))
        val theOther = toJSONObjectPattern(mapOf<String, Pattern>("data" to NumberPattern(), "..." to StringPattern()))

        assertThat(theOne.encompasses(theOther, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(theOther.encompasses(theOne, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `returns as many errors as contract-invalid values in a JSON object`() {
        val type = JSONObjectPattern(mapOf("id" to NumberPattern(), "height" to NumberPattern()))
        val json = parsedJSON("""{"id": "abc123", "height": "5 feet 7"}""")

        assertThat(type.matches(json, Resolver())).satisfies(Consumer {
            it as Result.Failure
            assertThat(it.causes).hasSize(2)

            println(it)
        })
    }

    @Test
    fun `backward compatibility errors should all be returned together`() {
        val older = JSONObjectPattern(mapOf("id" to NumberPattern()))
        val newer = JSONObjectPattern(mapOf("id" to StringPattern(), "address" to JSONObjectPattern(mapOf("flat" to NumberPattern()))))

        val resultText = newer.encompasses(older, Resolver(), Resolver()).reportString()

        assertThat(resultText).contains("id")
        assertThat(resultText).contains("address")
    }

    @Test
    fun `an error for failure n levels deep should have a path with all the levels`() {
        val type = JSONObjectPattern(mapOf("id" to NumberPattern(), "address" to JSONObjectPattern(mapOf("flat" to NumberPattern()))))
        val json = parsedJSON("""{"id": "abc123", "address": {"flat": "10"}}""")

        assertThat(type.matches(json, Resolver())).satisfies(Consumer {
            it as Result.Failure
            assertThat(it.causes).hasSize(2)

            assertThat(it.toFailureReport().toString().trimmedLinesString()).isEqualTo("""
                >> id

                   Expected number, actual was "abc123"

                >> address.flat

                   Expected number, actual was "10"
            """.trimIndent().trim().trimmedLinesString())
        })
    }

    @Nested
    inner class MatchReturnsAllKeyErrors {
        val type = JSONObjectPattern(mapOf("id" to NumberPattern(), "address" to StringPattern()))
        val json = parsedJSON("""{"id": "10", "person_address": "abc123"}""")
        val error: Result.Failure = type.matches(json, Resolver()) as Result.Failure
        private val reportText = error.toFailureReport().toText()

        @Test
        fun `return as many errors as the number of key errors`() {
            error

            assertThat(error.toMatchFailureDetailList()).hasSize(3)
        }

        @Test
        fun `errors should refer to the missing keys`() {
            println(reportText)

            assertThat(reportText).contains(">> id")
            assertThat(reportText).contains(">> address")
            assertThat(reportText).contains(">> person_address")
        }

        @Test
        fun `key errors appear before value errors`() {
            assertThat(reportText.indexOf(">> person_address")).isLessThan(reportText.indexOf(">> id"))
            assertThat(reportText.indexOf(">> address")).isLessThan(reportText.indexOf(">> id"))
        }
    }

    @Nested
    inner class MinAndMaxProperties {
        @Nested
        inner class MatchingValuesToPatterns {
            private val basePattern = JSONObjectPattern(
                mapOf(
                    "id" to NumberPattern(),
                    "name?" to StringPattern(),
                    "address?" to StringPattern(),
                    "department?" to StringPattern(),
                )
            )

            @ParameterizedTest
            @CsvSource(
                value = [
                    """result | min | max | obj """,
                    """fail   | 2   |     | {"id": 10}""",
                    """pass   | 2   |     | {"id": 10, "name": "Jill"}""",
                    """pass   | 2   |     | {"id": 10, "name": "Jill", "address": "Baker street"}""",
                    """pass   |     | 2   | {"id": 10, "name": "Jill"}""",
                    """fail   |     | 2   | {"id": 10, "name": "Jill", "address": "Baker street"}""",
                    """pass   | 2   | 3   | {"id": 10, "name": "Jill", "address": "Baker street"}""",
                    """fail   | 2   | 3   | {"id": 10, "name": "Jill", "address": "Baker street", "department": "HR"}""",
                    """fail   | 2   | 3   | {"id": 10}""",
                ],
                delimiter = '|',
                useHeadersInDisplayName = true,
                ignoreLeadingAndTrailingWhitespace = true
            )
            fun cases(result: String, minProperties: String?, maxProperties: String?, obj: String) {
                val json = parsedJSONObject(obj)

                val pattern = (
                        minProperties?.let { basePattern.copy(minProperties = it.toInt()) } ?: basePattern
                        ).let { withMin ->
                        maxProperties?.let { withMin.copy(maxProperties = it.toInt()) } ?: withMin
                    }

                when (result) {
                    "fail" -> assertThat(pattern.matches(json, Resolver())).isInstanceOf(Result.Failure::class.java)
                    "pass" -> assertThat(pattern.matches(json, Resolver())).isInstanceOf(Result.Success::class.java)
                    else -> throw Exception("Unknown result $result")
                }
            }
        }

        @Nested
        inner class GeneratingValues {
            @Test
            fun `should generate a value with at least the minimum number of properties`() {
                val pattern = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    minProperties = 2
                )

                val value: JSONObjectValue = pattern.generate(Resolver())

                assertThat(value.jsonObject.keys).hasSizeGreaterThanOrEqualTo(2)
            }

            @Test
            fun `generation should fail when available properties are less than minimum`() {
                val pattern = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern()
                    ),
                    minProperties = 3
                )

                assertThatThrownBy { pattern.generate(Resolver()) }.isInstanceOf(ContractException::class.java)
            }

            @Test
            fun `should generate a value with at most the maximum number of properties`() {
                val pattern = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    maxProperties = 2
                )

                val value: JSONObjectValue = pattern.generate(Resolver())

                assertThat(value.jsonObject.keys).hasSizeLessThanOrEqualTo(2)
            }

            @Test
            fun `generation should fail when the count of mandatory properties is greater than maxProperties`() {
                val pattern = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name" to StringPattern(),
                        "address" to StringPattern(),
                    ),
                    maxProperties = 2
                )

                assertThatThrownBy { pattern.generate(Resolver()) }.isInstanceOf(ContractException::class.java)
            }
        }
    }

    @Nested
    @Tag(GENERATION)
    inner class TestGeneration {
        @Test
        fun `Given an optional key, the unsuffixed key should be looked up in the row when generating a pattern`() {
            val row = Row(listOf("id"), listOf("12345"))
            val patterns = parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(row, Resolver()).map { it.value }

            val value = patterns.map { it.generate(Resolver()) }.map {
                if (it !is JSONObjectValue)
                    throw Exception("Expected JSONObjectValue2, got ${it.javaClass}")

                it.jsonObject.getOrDefault("id", NumberValue(0))
            }.find {
                it is NumberValue && it.number == 12345
            }

            assertEquals(12345, (value as NumberValue).number)
        }

        @Test
        fun `Given a column name in the examples, a json key must be replaced by the example`() {
            val row = Row(listOf("id"), listOf("10"))
            val pattern =
                parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).map { it.value }.first()

            if (pattern !is JSONObjectPattern)
                throw Exception("Expected JSONObjectPattern, got ${pattern.javaClass}")

            val id = pattern.generate(Resolver()).jsonObject["id"] as NumberValue
            assertEquals(10, id.number)
        }

        @Test
        fun `Given a column name in the examples, a json key in a lazily looked up pattern must be replaced by the example`() {
            val row = Row(listOf("id"), listOf("10"))
            val actualPattern =
                parsedPattern("""{"id": "(number)"}""", null).newBasedOn(row, Resolver()).map { it.value }.first()

            val resolver = Resolver(newPatterns = mapOf("(Id)" to actualPattern))

            val lazyPattern = parsedPattern("(Id)")
            val value = lazyPattern.generate(resolver)

            if (value !is JSONObjectValue)
                throw Exception("Expected JSONObjectValue, got ${value.javaClass}")

            val id = value.jsonObject["id"] as NumberValue
            assertEquals(10, id.number)
        }

        @Test
        fun `When generating a new pattern based on a row, a json value multiple 1+ lazy levels down must be replaced by the example value`() {
            val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"city": "(string)"}""")))

            val personPattern = parsedPattern("""{"name": "(string)", "address": "(Address)"}""")

            val row = Row(listOf("city"), listOf("Mumbai"))

            val newPattern = personPattern.newBasedOn(row, resolver).map { it.value }.first()
            if (newPattern !is JSONObjectPattern)
                throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

            assertTrue(newPattern.pattern["name"] is StringPattern)

            val addressPattern = newPattern.pattern["address"]
            if (addressPattern !is JSONObjectPattern)
                throw AssertionError("Expected JSONObjectPattern, got ${addressPattern?.javaClass?.name}")

            val cityPattern = addressPattern.pattern["city"]
            if (cityPattern !is ExactValuePattern)
                throw AssertionError("Expected ExactMatchPattern, got ${cityPattern?.javaClass?.name}")

            val cityValue = cityPattern.pattern
            if (cityValue !is StringValue)
                throw AssertionError("Expected StringValue, got ${cityValue.javaClass.name}")

            assertEquals("Mumbai", cityValue.string)
        }

        @Test
        fun `Should avoid combinatorial explosion when many request properties with many possible values`() {
            System.setProperty(MAX_TEST_REQUEST_COMBINATIONS, "64")

            val resolver = Resolver(
                newPatterns = (1..6).associate { paramIndex ->
                    "(enum${paramIndex})" to AnyPattern((0..9).map { possibleValueIndex ->
                        ExactValuePattern(StringValue("${paramIndex}${possibleValueIndex}"))
                    }.toList())
                }
            )

            val objPattern = parsedPattern("""{"p1": "(enum1)", "p2": "(enum2)", "p3": "(enum3)", "p4": "(enum4)", "p5": "(enum5)", "p6": "(enum6)"}""")

            val newPatterns = try {
                objPattern.newBasedOn(Row(), resolver).map { it.value }.toList()
            } finally {
                System.clearProperty("MAX_TEST_REQUEST_COMBINATIONS")
            }
            assertThat(newPatterns).hasSize(64)

            System.clearProperty(MAX_TEST_REQUEST_COMBINATIONS)
        }

        @Test
        fun `When generating a new pattern based on a row, a concrete pattern value in the object should not become a concrete value`() {
            val resolver = Resolver()

            val personPattern = parsedPattern("""{"name": "(string)"}""")

            val newPattern = personPattern.newBasedOn(Row(), resolver).map { it.value }.first()

            if (newPattern !is JSONObjectPattern)
                throw AssertionError("Expected JSONObjectPattern, got ${newPattern.javaClass.name}")

            assertTrue(newPattern.pattern["name"] is StringPattern)
        }

        @Test
        fun `should return errors with id field`() {
            val patterns =
                parsedPattern("""{"id?": "(number)"}""", null).newBasedOn(Row(), Resolver()).map { it.value }

            assertNotNull(patterns.find { pattern ->
                val result = pattern.matches(JSONObjectValue(mapOf("id" to StringValue("abc"))), Resolver())
                result is Result.Failure && result.toMatchFailureDetails() == MatchFailureDetails(
                    listOf("id"),
                    listOf("""Expected number, actual was "abc"""")
                )
            })
        }

        @Test
        fun `creates three combinations per optional field with optional children`() {
            val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string)"}""")))

            val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address)"}""")

            val combinations = personPattern.newBasedOn(resolver).toList()

            assertThat(combinations.size).isEqualTo(3)

            val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
            val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
            val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
            val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
            val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

            assertThat(combinations).contains(personWithAddressWithStreet)
            assertThat(combinations).contains(personWithAddressWithoutStreet)
            assertThat(combinations).contains(personWithoutAddress)
        }

        @Test
        fun `creates four combinations per optional field with optional children with optional value`() {
            val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string?)"}""")))

            val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address)"}""")

            val combinations = personPattern.newBasedOn(resolver).toList()

            assertThat(combinations.size).isEqualTo(4)

            val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
            val addressPatternWithStreetSetToNull = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to NullPattern))
            val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
            val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
            val personWithAddressWithStreetSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreetSetToNull))
            val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
            val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

            assertThat(combinations).contains(personWithAddressWithStreet)
            assertThat(combinations).contains(personWithAddressWithStreetSetToNull)
            assertThat(combinations).contains(personWithAddressWithoutStreet)
            assertThat(combinations).contains(personWithoutAddress)
        }

        @Test
        fun `creates five combinations per optional field with optional value with optional children`() {
            val resolver = Resolver(newPatterns = mapOf("(Address)" to parsedPattern("""{"number": "(string)", "street?": "(string?)"}""")))

            val personPattern = parsedPattern("""{"name": "(string)", "address?": "(Address?)"}""")

            val combinations = personPattern.newBasedOn(resolver).toList()

            assertThat(combinations.size).isEqualTo(5)

            val addressPatternWithStreet = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to StringPattern()))
            val addressPatternWithStreetSetToNull = toJSONObjectPattern(mapOf("number" to StringPattern(), "street?" to NullPattern))
            val addressPatternWithoutStreet = toJSONObjectPattern(mapOf("number" to StringPattern()))
            val personWithAddressWithStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreet))
            val personWithAddressWithStreetSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithStreetSetToNull))
            val personWithAddressWithoutStreet = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to addressPatternWithoutStreet))
            val personWithAddressSetToNull = toJSONObjectPattern(mapOf("name" to StringPattern(), "address?" to NullPattern))
            val personWithoutAddress = toJSONObjectPattern(mapOf("name" to StringPattern()))

            assertThat(combinations).contains(personWithAddressWithStreet)
            assertThat(combinations).contains(personWithAddressWithStreetSetToNull)
            assertThat(combinations).contains(personWithAddressWithoutStreet)
            assertThat(combinations).contains(personWithAddressSetToNull)
            assertThat(combinations).contains(personWithoutAddress)
        }

        @Test
        fun `objects generated for tests should have a min of minProperties keys and a max of maxProperties keys`() {
            val pattern = JSONObjectPattern(
                mapOf(
                    "id" to NumberPattern(),
                    "name?" to StringPattern(),
                    "address?" to StringPattern(),
                    "department?" to StringPattern(),
                ),
                minProperties = 2,
                maxProperties = 3
            )

            val newPatterns: List<JSONObjectPattern> = pattern.newBasedOn(Row(), Resolver()).toList().map { it.value as JSONObjectPattern }

            assertThat(newPatterns).allSatisfy {
                assertThat(it.pattern.keys).hasSizeGreaterThanOrEqualTo(2)
                assertThat(it.pattern.keys).hasSizeLessThanOrEqualTo(3)
            }
        }

        @Nested
        inner class BackwardCompatibility {
            @ParameterizedTest
            @CsvSource(
                value = [
                    """old | new | compatible""",
                    """3   | 2   | false""",
                    """3   | 4   | true""",
                ],
                delimiter = '|',
                useHeadersInDisplayName = true,
                ignoreLeadingAndTrailingWhitespace = true
            )
            fun `min cases`(old: Int, new: Int, compatible: Boolean) {
                val older = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    minProperties = old
                )
                val newer = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    minProperties = new
                )

                val result = newer.encompasses(older, Resolver(), Resolver())

                when(compatible) {
                    true -> assertThat(result).isInstanceOf(Result.Success::class.java)
                    false -> assertThat(result).isInstanceOf(Result.Failure::class.java)
                }
            }

            @ParameterizedTest
            @CsvSource(
                value = [
                    """old | new | compatible""",
                    """3   | 2   | true""",
                    """3   | 4   | false""",
                ],
                delimiter = '|',
                useHeadersInDisplayName = true,
                ignoreLeadingAndTrailingWhitespace = true
            )
            fun `max cases`(old: Int, new: Int, compatible: Boolean) {
                val older = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    maxProperties = old
                )
                val newer = JSONObjectPattern(
                    mapOf(
                        "id" to NumberPattern(),
                        "name?" to StringPattern(),
                        "address?" to StringPattern(),
                        "department?" to StringPattern(),
                    ),
                    maxProperties = new
                )

                val result = newer.encompasses(older, Resolver(), Resolver())

                when(compatible) {
                    true -> assertThat(result).isInstanceOf(Result.Success::class.java)
                    false -> assertThat(result).isInstanceOf(Result.Failure::class.java)
                }
            }
        }

        @Test
        fun `should generative negative patterns based on negative patterns of the values`() {
            val pattern = JSONObjectPattern(
                mapOf(
                    "address?" to StringPattern(),
                )
            )

            val negativePatterns: List<Pattern> = pattern.negativeBasedOn(Row(), Resolver()).map { it.value }.toList()

            val jsonInternalPatterns = negativePatterns.filterIsInstance<JSONObjectPattern>().map { it.pattern }

            assertThat(jsonInternalPatterns).containsExactlyInAnyOrder(
                emptyMap(),
                mapOf("address?" to NullPattern),
                mapOf("address?" to NumberPattern()),
                mapOf("address?" to BooleanPattern())
            )
        }
    }

    @Test
    fun `generate should return value from dictionary if present`() {
        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "address" to StringPattern()
            ),
            typeAlias = addressTypeAlias
        )

        val address = StringValue("22B Baker Street")

        val dictionary = mapOf("Address.address" to address)

        val resolver = Resolver(
            newPatterns = mapOf(addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(addressPattern)
        assertThat(value).isEqualTo(JSONObjectValue(mapOf("address" to address)))
    }

    @Test
    fun `generate should return value for array from dictionary if present`() {
        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "addresses" to ListPattern(StringPattern())
            ),
            typeAlias = addressTypeAlias
        )

        val expectedAddress = StringValue("22B Baker Street")

        val dictionary = mapOf("Address.addresses[*]" to expectedAddress)

        val resolver = Resolver(
            newPatterns = mapOf(addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(addressPattern) as JSONObjectValue

        val addresses = value.findFirstChildByPath("addresses")!! as JSONArrayValue
        assertThat(addresses.list).allSatisfy {
            assertThat(it).isEqualTo(expectedAddress)
        }
    }

    @Test
    fun `generate should return value from dictionary for key when typeAlias is missing`() {
        val addressPattern = JSONObjectPattern(
            mapOf(
                "address" to StringPattern()
            )
        )

        val expectedAddress = StringValue("22B Baker Street")

        val dictionary = mapOf(".address" to expectedAddress)

        val resolver = Resolver(
            dictionary = dictionary
        )

        val value = resolver.generate(addressPattern) as JSONObjectValue

        val address = value.findFirstChildByPath("address") as StringValue
        assertThat(address).isEqualTo(expectedAddress)
    }

    @Test
    fun `should generate objects in an array with the dictionary-provided values`() {
        val personTypeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "addresses" to ListPattern(DeferredPattern("(Address)"))
            ),
            typeAlias = personTypeAlias
        )

        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "street" to StringPattern()
            ),
            typeAlias = addressTypeAlias
        )

        val expectedStreet = StringValue("Baker Street")

        val dictionary = mapOf("Address.street" to expectedStreet)

        val resolver = Resolver(
            newPatterns = mapOf(personTypeAlias to personPattern, addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(DeferredPattern("(Person)")) as JSONObjectValue

        val addresses = value.findFirstChildByPath("addresses") as JSONArrayValue

        assertThat(addresses.list).allSatisfy {
            val address = it as JSONObjectValue
            assertThat(address.findFirstChildByPath("street")).isEqualTo(expectedStreet)
        }
    }

    @Test
    fun `should log errors when dictionary values in an array do not match`() {
        val personTypeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "addresses" to ListPattern(StringPattern())
            ),
            typeAlias = personTypeAlias
        )

        val streetAsNumber = NumberValue(10)

        val dictionary = mapOf("Person.addresses[*]" to streetAsNumber)

        val resolver = Resolver(
            newPatterns = mapOf(personTypeAlias to personPattern),
            dictionary = dictionary
        )

        val (output, _) = captureStandardOutput {
            val value = resolver.generate(DeferredPattern("(Person)")) as JSONObjectValue
            println(value.toStringLiteral())
        }

        println(output)
        assertThat(output)
            .contains("string")
            .contains("number")
            .contains("Person.addresses[*]")
    }

    @Test
    fun `generate should return value for optional keys from dictionary if present`() {
        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "address?" to StringPattern()
            ),
            typeAlias = addressTypeAlias
        )

        val expectedAddress = StringValue("22B Baker Street")

        val dictionary = mapOf("Address.address" to expectedAddress)

        val resolver = Resolver(
            newPatterns = mapOf(addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        var value: JSONObjectValue

        while(true) {
            value = resolver.generate(addressPattern) as JSONObjectValue
            if("address" in value.jsonObject)
                break
        }

        assertThat(value).isEqualTo(JSONObjectValue(mapOf("address" to expectedAddress)))
    }

    @Test
    fun `generate should return value from dictionary two levels down if present`() {
        val typeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "name" to JSONObjectPattern(
                    mapOf(
                        "last_name" to StringPattern()
                    )
                )
            ),
            typeAlias = typeAlias
        )

        val name = StringValue("Stark")

        val dictionary = mapOf("Person.name.last_name" to name)

        val resolver = Resolver(
            newPatterns = mapOf(typeAlias to personPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(personPattern)

        val expectedPersonDetails = JSONObjectValue(
            mapOf(
                "name" to JSONObjectValue(
                    mapOf("last_name" to name)
                )
            )
        )

        assertThat(value).isEqualTo(expectedPersonDetails)
    }

    @Test
    fun `reffed pattern alias should restart the path when calculating the lookup`() {
        val personTypeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "name" to StringPattern(),
                "address" to DeferredPattern("(Address)")
            ),
            typeAlias = personTypeAlias
        )

        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "street" to StringPattern()
            ),
            typeAlias = addressTypeAlias
        )

        val name = StringValue("Stark")
        val street = StringValue("Baker Street")

        val dictionary = mapOf("Person.name" to name, "Address.street" to street)

        val resolver = Resolver(
            newPatterns = mapOf(personTypeAlias to personPattern, addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(DeferredPattern("(Person)"))

        val expectedPersonDetails = JSONObjectValue(
            mapOf(
                "name" to StringValue("Stark"),
                "address" to JSONObjectValue(
                    mapOf("street" to street)
                )
            )
        )

        assertThat(value).isEqualTo(expectedPersonDetails)
    }

    @Test
    fun `multi-level example in dictionary`() {
        val personTypeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "address" to DeferredPattern("(Address)")
            ),
            typeAlias = personTypeAlias
        )

        val addressTypeAlias = "(Address)"

        val addressPattern = JSONObjectPattern(
            mapOf(
                "street" to StringPattern()
            ),
            typeAlias = addressTypeAlias
        )

        val street = StringValue("Baker Street")

        val dictionary = mapOf("Person.address" to JSONObjectValue(
            mapOf("street" to street)
        ))

        val resolver = Resolver(
            newPatterns = mapOf(personTypeAlias to personPattern, addressTypeAlias to addressPattern),
            dictionary = dictionary
        )

        val value = resolver.generate(DeferredPattern("(Person)"))

        val expectedPersonDetails = JSONObjectValue(
            mapOf(
                "address" to JSONObjectValue(
                    mapOf("street" to street)
                )
            )
        )

        assertThat(value).isEqualTo(expectedPersonDetails)
    }

    @Test
    fun `throw exception when example is found but invalid `() {
        val personTypeAlias = "(Person)"

        val personPattern = JSONObjectPattern(
            mapOf(
                "id" to NumberPattern(),
            ),
            typeAlias = personTypeAlias
        )

        val id = StringValue("abc123")

        val dictionary = mapOf("Person.id" to id)

        val resolver = Resolver(
            newPatterns = mapOf(personTypeAlias to personPattern),
            dictionary = dictionary
        )

        assertThatThrownBy {
            resolver.generate(DeferredPattern("(Person)"))
        }.satisfies(Consumer {
            assertThat(exceptionCauseMessage(it))
                .withFailMessage(exceptionCauseMessage(it))
                .contains("number")
                .contains("abc123")
        })
    }

    @Nested
    inner class CyclicalGeneration {
        @BeforeEach
        fun setup() {
            System.setProperty(ALL_PATTERNS_MANDATORY, "true")
            System.setProperty(IGNORE_INLINE_EXAMPLES, "true")
            System.setProperty(IGNORE_INLINE_EXAMPLE_WARNINGS, "true")
            System.setProperty(ATTRIBUTE_SELECTION_QUERY_PARAM_KEY, "fields")
            System.setProperty(EXTENSIBLE_QUERY_PARAMS, "fields")
            System.setProperty(EXTENSIBLE_SCHEMA, "fields")
        }

        @AfterEach
        fun teardown() {
            System.clearProperty(ALL_PATTERNS_MANDATORY)
            System.clearProperty(IGNORE_INLINE_EXAMPLES)
            System.clearProperty(IGNORE_INLINE_EXAMPLE_WARNINGS)
            System.clearProperty(ATTRIBUTE_SELECTION_QUERY_PARAM_KEY)
            System.clearProperty(EXTENSIBLE_QUERY_PARAMS)
            System.clearProperty(EXTENSIBLE_SCHEMA)
        }

        @Test
        fun `should result in failure when optional keys are missing and resolver is set to allPatternsAsMandatory`() {
            val pattern = parsedPattern("""{
            "topLevelMandatoryKey": "(number)",
            "topLevelOptionalKey?": "(string)",
            "subMandatoryObject": {
                "subMandatoryKey": "(string)",
                "subOptionalKey?": "(number)"
            },
            "subOptionalObject?": {
                "subMandatoryKey": "(string)",
                "subOptionalKey": "(number)"
            }
        }
        """.trimIndent())
            val matchingValue = parsedValue("""{
            "topLevelMandatoryKey": 10,
            "subMandatoryObject": {
                "subMandatoryKey": "value"
            },
            "subOptionalObject": {
                "subMandatoryKey": "value"
            }
        }
        """.trimIndent())
            val result = pattern.matches(matchingValue, Resolver().withAllPatternsAsMandatory())
            println(result.reportString())

            assertThat(result).isInstanceOf(Result.Failure::class.java)
            assertThat(result.reportString()).isEqualToNormalizingWhitespace("""
            >> subOptionalObject.subOptionalKey
            Expected key named "subOptionalKey" was missing
            >> topLevelOptionalKey
            Expected optional key named "topLevelOptionalKey" was missing
            >> subMandatoryObject.subOptionalKey
            Expected optional key named "subOptionalKey" was missing
            """.trimIndent())
        }

        @Test
        fun `should not result in failure for missing keys when pattern is cycling with resolver set to allPatternsAsMandatory`() {
            val basePattern = parsedPattern("""{
            "mandatoryKey": "(number)",
            "optionalKey?": "(string)"
        }""".trimIndent()) as JSONObjectPattern

            val thirdPattern = JSONObjectPattern(
                basePattern.pattern + mapOf("firstObject?" to DeferredPattern("(firstPattern)")),
                typeAlias = "(thirdPattern)"
            )
            val secondPattern = JSONObjectPattern(
                basePattern.pattern + mapOf("thirdObject?" to thirdPattern),
                typeAlias = "(secondPattern)"
            )
            val firstPattern = JSONObjectPattern(
                basePattern.pattern + mapOf("secondObject?" to secondPattern),
                typeAlias = "(firstPattern)"
            )
            val newPatterns = mapOf("(firstPattern)" to firstPattern, "(secondPattern)" to secondPattern, "(thirdPattern)" to thirdPattern)

            val matchingValue = parsedValue("""{
            "mandatoryKey": 10,
            "optionalKey": "abc",
            "secondObject": {
                "mandatoryKey": 10,
                "optionalKey": "abc",
                "thirdObject": {
                    "mandatoryKey": 10,
                    "optionalKey": "abc",
                    "firstObject": {
                        "mandatoryKey": 10
                    }
                }
            }
        }""".trimIndent())
            val matchingResult = firstPattern.matches(matchingValue, Resolver(newPatterns = newPatterns).withAllPatternsAsMandatory())
            println(matchingResult.reportString())
            assertThat(matchingResult).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `should not result in failure for missing keys when pattern is cycling with an allOf schema`() {
            val spec = """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample API
          version: 0.1.9
        paths:
          /hello:
            get:
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        ${"$"}ref: '#/components/schemas/MainMessage'
        components:
          schemas:
            MainMessage:
              allOf:
                - ${"$"}ref: '#/components/schemas/Message'
            Message:
              type: object
              properties:
                message:
                  type: string
                details:
                  ${"$"}ref: '#/components/schemas/Details'
            Details:
              oneOf:
                - ${"$"}ref: '#/components/schemas/Message'
        """.trimIndent()
            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver)
            println(value.toStringLiteral())

            val matchResult = responsePattern.matches(value, resolver)
            println(matchResult.reportString())

            assertThat(matchResult).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `should not result in failure for missing keys with array ref when pattern is cycling with an allOf schema`() {
            val spec = """
        openapi: 3.0.0
        info:
          title: Sample API
          description: Sample API
          version: 0.1.9
        paths:
          /hello:
            get:
              responses:
                '200':
                  description: Says hello
                  content:
                    application/json:
                      schema:
                        ${"$"}ref: '#/components/schemas/MainMessage'
        components:
          schemas:
            MainMessage:
              allOf:
                - ${"$"}ref: '#/components/schemas/MessageType'
                - ${"$"}ref: '#/components/schemas/Message'
              discriminator:
                propertyName: type
                mapping:
                  Message: '#/components/schemas/Message'
            Message:
              allOf:
                - ${"$"}ref: '#/components/schemas/MessageType'
                - type: object
                  properties:
                    msgRefOrValue:
                      type: array
                      items:
                        ${"$"}ref: '#/components/schemas/MessageRefOrValue'
              discriminator:
                propertyName: type
                mapping:
                  Message: '#/components/schemas/Message'
            MessageType:
              type: object
              properties:
                type:
                  type: string
            MessageRefOrValue:
              oneOf:
                - ${"$"}ref: '#/components/schemas/MessageRef'
                - ${"$"}ref: '#/components/schemas/Message'
              discriminator:
                propertyName: type
                mapping:
                  Message: '#/components/schemas/Message'
                  MessageRef: '#/components/schemas/MessageRef'
            MessageRef:
              type: object
              properties:
                id:
                  type: string
                type:
                  type: string
        """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver)
            println(value.toStringLiteral())

            val matchResult = responsePattern.matches(value, resolver)
            println(matchResult.reportString())

            assertThat(matchResult).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `recursion with cross property conflict handled correctly when generating values`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          ${"$"}ref: '#/components/schemas/DataRef'
        indirectSelfRef:
          ${"$"}ref: '#/components/schemas/DataRefToRef'
    DataRefToRef:
      type: object
      required:
        - messageRefToRef
      properties:
        messageRefToRef:
          ${"$"}ref: '#/components/schemas/DataRef'
    DataRef:
      type: object
      required:
        - messageRef
      properties:
        messageRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver)
            println(value.toStringLiteral())

            val matchResult = responsePattern.matches(value, resolver)
            println(matchResult.reportString())

            assertThat(matchResult).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `recursion with cross property conflict across arrays handled correctly when generating values`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/DataRef'
        indirectSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/DataRefToRef'
    DataRefToRef:
      type: object
      required:
        - messageRefToRef
      properties:
        messageRefToRef:
          ${"$"}ref: '#/components/schemas/DataRef'
    DataRef:
      type: object
      required:
        - messageRef
      properties:
        messageRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver)
            println(value.toStringLiteral())

            val matchResult = responsePattern.matches(value, resolver)
            println(matchResult.reportString())

            assertThat(matchResult).isInstanceOf(Result.Success::class.java)
        }

        @Test
        fun `simple unavoidable recursion when all properties should appear`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      required:
      - directSelfRef
      properties:
        directSelfRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            assertThatThrownBy {
                responsePattern.generate(resolver)
            }.satisfies(Consumer {
                assertThat(exceptionCauseMessage(it)).contains("Invalid pattern cycle: Data.directSelfRef")
            })

        }

        @Test
        fun `simple avoidable recursion when all properties should appear`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver)

            assertThat(value).isEqualTo(JSONObjectValue(mapOf("directSelfRef" to JSONObjectValue())))
        }

        @Test
        fun `simple unavoidable recursion across arrays`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      required:
      - directSelfRef
      properties:
        directSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            assertThatThrownBy {
                responsePattern.generate(resolver)
            }.satisfies(Consumer {
                assertThat(exceptionCauseMessage(it)).contains("Invalid pattern cycle: Data.directSelfRef")
            })

        }

        @Test
        fun `simple avoidable recursion across arrays`() {
            val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

            val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

            val scenario = feature.scenarios.first()
            val resolver = scenario.resolver.copy(allPatternsAreMandatory = true)

            val responsePattern = scenario.httpResponsePattern.body
            val value = responsePattern.generate(resolver) as JSONObjectValue

            assertThat(value.findFirstChildByPath("directSelfRef")).isNotNull()

            val listPropertyValue = value.findFirstChildByPath("directSelfRef") as JSONArrayValue

            assertThat(listPropertyValue.list).allSatisfy {
                assertThat(it).isEqualTo(JSONObjectValue())
            }

        }

    }

    @Test
    fun `simple unavoidable recursion`() {
        val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      required:
      - directSelfRef
      properties:
        directSelfRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        val scenario = feature.scenarios.first()
        val resolver = scenario.resolver

        val responsePattern = scenario.httpResponsePattern.body
        assertThatThrownBy {
            responsePattern.generate(resolver)
        }.satisfies(Consumer {
            assertThat(exceptionCauseMessage(it)).contains("Invalid pattern cycle: Data")
        })

    }

    @Test
    fun `simple avoidable recursion`() {
        val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        val scenario = feature.scenarios.first()
        val resolver = scenario.resolver

        val responsePattern = scenario.httpResponsePattern.body
        val value = responsePattern.generate(resolver)

        assertThat(value).isEqualTo(JSONObjectValue())
    }

    @Test
    fun `simple unavoidable recursion across arrays`() {
        val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      required:
      - directSelfRef
      properties:
        directSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        val scenario = feature.scenarios.first()
        val resolver = scenario.resolver

        val responsePattern = scenario.httpResponsePattern.body
        assertThatThrownBy {
            responsePattern.generate(resolver)
        }.satisfies(Consumer {
            assertThat(exceptionCauseMessage(it)).contains("Invalid pattern cycle: Data")
        })

    }

    @Test
    fun `simple avoidable recursion across arrays`() {
        val spec = """
openapi: 3.0.0
info:
  title: Sample API
  description: Sample API
  version: 0.1.9
paths:
  /hello:
    get:
      responses:
        '200':
          description: Has data
          content:
            application/json:
              schema:
                ${"$"}ref: '#/components/schemas/Data'
components:
  schemas:
    Data:
      type: object
      properties:
        directSelfRef:
          type: array
          items:
            ${"$"}ref: '#/components/schemas/Data'
    """.trimIndent()

        val feature = OpenApiSpecification.fromYAML(spec, "").toFeature()

        val scenario = feature.scenarios.first()
        val resolver = scenario.resolver

        val responsePattern = scenario.httpResponsePattern.body
        val value = responsePattern.generate(resolver) as JSONObjectValue

        assertThat(value).isEqualTo(JSONObjectValue())
    }
}
