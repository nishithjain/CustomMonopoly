package com.boardbanker.core.model

object PropertyDisplayNames {
    private val PROPERTY_ID_PATTERN = Regex("""^PRP_(\d+)$""", RegexOption.IGNORE_CASE)

    fun propertyNumber(propertyId: String): Int? =
        PROPERTY_ID_PATTERN.matchEntire(propertyId)?.groupValues?.get(1)?.toIntOrNull()

    fun displayNameWithNumber(property: PropertyDefinition): String {
        val number = propertyNumber(property.propertyId)
        return if (number != null) "[$number] ${property.name}" else property.name
    }

    fun displayNameWithNumber(propertyId: String, definitions: GameDefinitions): String {
        val property = definitions.properties[propertyId]
        return when {
            property != null -> displayNameWithNumber(property)
            else -> propertyId
        }
    }
}

fun PropertyDefinition.displayNameWithNumber(): String =
    PropertyDisplayNames.displayNameWithNumber(this)
