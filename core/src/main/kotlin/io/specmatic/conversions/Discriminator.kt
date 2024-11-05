package io.specmatic.conversions

import io.specmatic.core.pattern.ContractException
import io.specmatic.core.pattern.ExactValuePattern
import io.specmatic.core.pattern.Pattern
import io.specmatic.core.value.StringValue
import io.swagger.v3.oas.models.media.Schema

private typealias PropertyName = String
private typealias SchemaName = String
private typealias DiscriminatorDetails = Map<PropertyName, Map<SchemaName, Pair<SchemaName, List<Schema<Any>>>>>

data class Discriminator(private val discriminatorDetails: DiscriminatorDetails = emptyMap()) {
    fun isNotEmpty(): Boolean {
        return discriminatorDetails.isNotEmpty()
    }

    val values: List<String>
        get() {
            return discriminatorDetails.entries.firstOrNull()?.value?.keys?.toList() ?: emptyList()
        }

    val key: String?
        get() {
            return discriminatorDetails.entries.firstOrNull()?.key
        }

    val schemas: List<Schema<Any>>
        get() {
            return discriminatorDetails.entries.flatMap {
                it.value.values.flatMap {
                    it.second
                }
            }
        }

    fun plus(newDiscriminatorDetails: Triple<PropertyName, Map<SchemaName, Pair<SchemaName, List<Schema<Any>>>>, Discriminator>?): Discriminator {
        if(newDiscriminatorDetails == null)
            return this

        val (propertyName, valuesAndSchemas: Map<SchemaName, Pair<SchemaName, List<Schema<Any>>>>, newDiscriminator) = newDiscriminatorDetails

        val updatedDiscriminatorDetails: DiscriminatorDetails =
            discriminatorDetails.plus(propertyName to valuesAndSchemas)

        return this.copy(discriminatorDetails = updatedDiscriminatorDetails).plus(newDiscriminator)
    }

    fun plus(newDiscriminator: Discriminator): Discriminator {
        return this.copy(discriminatorDetails = mergeMapOfMaps(discriminatorDetails, newDiscriminator.discriminatorDetails))
    }

    private fun <T> mergeMapOfMaps(
        discriminatorDetails1: Map<String, Map<String, T>>,
        discriminatorDetails2: Map<String, Map<String, T>>
    ): Map<String, Map<String, T>> {
        val keys = discriminatorDetails1.keys + discriminatorDetails2.keys

        return keys.associateWith { key ->
            val detail1 = discriminatorDetails1[key] ?: emptyMap()
            val detail2 = discriminatorDetails2[key] ?: emptyMap()

            (detail1 + detail2)
        }
    }

    fun hasValueForKey(propertyName: String?): Boolean {
        if(propertyName == null)
            return false

        return propertyName in discriminatorDetails
    }

    fun valueFor(propertyName: String): Pattern {
        if(propertyName !in discriminatorDetails)
            throw ContractException("$propertyName not found in discriminator details")

        return discriminatorDetails.getValue(propertyName).firstNotNullOf { ExactValuePattern(StringValue(it.key), discriminator = true) }
    }

    fun explode(): List<Discriminator> {
        return explode(discriminatorDetails)
    }

    private fun explode(discriminatorDetails: DiscriminatorDetails): List<Discriminator> {
        val propertyName = discriminatorDetails.keys.firstOrNull() ?: return listOf(Discriminator())

        val discriminatorDetailsWithOneKeyLess = discriminatorDetails - propertyName

        val valueOptionsWithSchemasForProperty = discriminatorDetails.getValue(propertyName)

        return valueOptionsWithSchemasForProperty.flatMap { valueOption: Map.Entry<String, Pair<String, List<Schema<Any>>>> ->
            explode(discriminatorDetailsWithOneKeyLess).map { discriminator ->
                discriminator.plus(Triple(propertyName, mapOf(valueOption.toPair()), Discriminator()))
            }
        }
    }
}
