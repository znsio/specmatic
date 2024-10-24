package io.specmatic.core.discriminator

data class DiscriminatorMetadata(
    val discriminatorProperty: String,
    val discriminatorValue: String,
) {
    fun isEmpty() = discriminatorProperty.isEmpty() || discriminatorValue.isEmpty()
}