package com.boardbanker.core.event

import com.boardbanker.core.model.EventActionDefinition
import com.boardbanker.core.model.EventDefinition
import com.boardbanker.core.model.GameDefinitions
import com.boardbanker.core.money.MoneyFormatter
import kotlinx.serialization.json.jsonPrimitive

object EventInstructionFormatter {
    private val PLACEHOLDER_PATTERN = Regex("""\{([a-zA-Z]+)\}""")

    fun formatDescription(
        event: EventDefinition,
        definitions: GameDefinitions,
        actionIndex: Int = 0,
    ): String = formatTemplate(event.eventDescription, event, definitions, actionIndex)

    fun formatDisplayText(
        event: EventDefinition,
        definitions: GameDefinitions,
        actionIndex: Int = 0,
    ): String = buildString {
        val description = formatDescription(event, definitions, actionIndex)
        if (event.eventSubtitle.isNotBlank()) {
            append(event.eventSubtitle)
            if (description.isNotBlank()) {
                append("\n\n")
            }
        }
        append(description)
    }

    fun formatTemplate(
        template: String,
        event: EventDefinition,
        definitions: GameDefinitions,
        actionIndex: Int = 0,
    ): String {
        if (!template.contains('{')) {
            return template
        }
        val action = event.actions.getOrNull(actionIndex) ?: event.actions.firstOrNull()
        val values = placeholderValues(action, definitions)
        return PLACEHOLDER_PATTERN.replace(template) { match ->
            val key = match.groupValues[1]
            values[key] ?: match.value
        }
    }

    internal fun placeholderValues(
        action: EventActionDefinition?,
        definitions: GameDefinitions,
    ): Map<String, String> {
        val banking = definitions.bankingValues
        val values = mutableMapOf<String, String>()

        fun money(amount: Int): String = MoneyFormatter.format(amount, definitions)
        fun intParam(key: String): Int? =
            action?.parameters?.get(key)?.jsonPrimitive?.content?.toIntOrNull()

        val amount = action?.amount ?: intParam("amount")
        amount?.let { values["amount"] = money(it) }

        values["goSalary"] = money(banking.goSalary)
        values["jailReleaseFee"] = money(banking.jailReleaseFee)

        val amountPerOtherPlayer = intParam("amountPerOtherPlayer") ?: action?.amount
        amountPerOtherPlayer?.let { values["amountPerOtherPlayer"] = money(it) }

        intParam("amountPerProperty")?.let { values["amountPerProperty"] = money(it) }
        intParam("jackpotAmount")?.let { values["jackpotAmount"] = money(it) }
        intParam("penaltyAmount")?.let { values["penaltyAmount"] = money(it) }
        intParam("thresholdAmount")?.let { values["thresholdAmount"] = money(it) }
        intParam("rewardAmount")?.let { values["rewardAmount"] = money(it) }
        intParam("baseRebateAmount")?.let { values["baseRebateAmount"] = money(it) }
        intParam("maximumCreditAmount")?.let { values["maximumCreditAmount"] = money(it) }

        return values
    }
}
