"""Edition-aware money formatting aligned with game-core MoneyFormatter."""

from __future__ import annotations

from typing import Any

from monopoly_edition_generator.paths import GeneratorError

MONETARY_PLACEHOLDER_KEYS = frozenset(
    {
        "amount",
        "amountPerOtherPlayer",
        "amountPerProperty",
        "jackpotAmount",
        "penaltyAmount",
        "thresholdAmount",
        "baseRebateAmount",
        "maximumCreditAmount",
        "rewardAmount",
        "goSalary",
        "jailReleaseFee",
    }
)

BANKING_PLACEHOLDER_KEYS = {
    "goSalary": "goSalary",
    "jailReleaseFee": "jailReleaseFee",
}


def format_money(amount: int | float, banking: dict[str, Any]) -> str:
    if not isinstance(amount, (int, float)) or isinstance(amount, bool):
        raise GeneratorError(f"Money amount must be numeric, got {amount!r}.")

    currency = banking.get("currency")
    if not isinstance(currency, dict):
        raise GeneratorError("banking_values.json must contain currency.")

    code = str(currency.get("code") or "")
    symbol = str(currency.get("symbol") or "")
    if not symbol:
        raise GeneratorError("currency.symbol cannot be empty.")

    value = int(round(amount))
    if value < 0:
        return f"-{format_money(abs(value), banking)}"

    if code == "M":
        number = str(value)
    else:
        number = f"{value:,}"

    return f"{symbol}{number}"
