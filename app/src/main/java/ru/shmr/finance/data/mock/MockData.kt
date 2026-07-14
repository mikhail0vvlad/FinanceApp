package ru.shmr.finance.data.mock

import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction

object MockData {

    val expenses = listOf(
        tx("e1", "Покупка канцтоваров", "✏️", 1_200),
        tx("e2", "Обед в кафе", "☕", 750),
        tx("e3", "Топливо для машины", "⛽", 2_300),
        tx("e4", "Подписка на сервис", "📱", 450),
        tx("e5", "Ремонт техники", "🔧", 5_800),
        tx("e6", "Покупка билетов", "🎫", 3_200),
        tx("e7", "Оплата интернета", "🌐", 800),
        tx("e8", "Магазин продуктов", "🛒", 2_450),
    )
    val expensesTotal = Money(323_524)

    val income = listOf(
        tx("i1", "Продажа старой мебели", "🛏️", 8_500, income = true),
        tx("i2", "Возврат налога", "📄", 15_000, income = true),
        tx("i3", "Премия за проект", "💼", 25_000, income = true),
        tx("i4", "Подработка фриланс", "💻", 13_450, income = true),
        tx("i5", "Сдача квартиры", "🏠", 38_000, income = true),
        tx("i6", "Кэшбек на карту", "💳", 1_200, income = true),
        tx("i7", "Подарок от родителей", "🎁", 5_000, income = true),
    )
    val incomeTotal = Money(323_524)

    val accounts = listOf(
        Account("a1", "Яндекс Pay", Money(123_322), "💳"),
        Account("a2", "Газпромбанк", Money(122_322), "🏦"),
        Account("a3", "Сбербанк", Money(122_322), "🏦"),
    )
    val accountsTotal = Money(1_322_444)

    private fun tx(id: String, name: String, emoji: String, amount: Long, income: Boolean = false) =
        Transaction(
            id = id,
            category = Category("c_$id", name, emoji, income),
            comment = null,
            amount = Money(amount),
        )
}
