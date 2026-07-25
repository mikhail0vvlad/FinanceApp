package ru.shmr.finance.domain.model

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class AppLanguage(val languageTag: String) {
    RUSSIAN("ru"),
    ENGLISH("en"),
    GERMAN("de"),
    FRENCH("fr"),
    SPANISH("es"),
    ;

    companion object {
        fun fromLanguageTag(tag: String): AppLanguage =
            entries.find { it.languageTag == tag } ?: RUSSIAN
    }
}

/**
 * A local default for newly created accounts only. Existing balances and transactions must never
 * be relabelled or converted without exchange rates from the backend.
 */
enum class AppCurrency(val currency: Currency) {
    RUB(Currency.RUB),
    USD(Currency.USD),
    EUR(Currency.EUR),
    GBP(Currency.GBP),
    CNY(Currency.CNY),
    ;

    companion object {
        fun fromCode(code: String): AppCurrency =
            entries.find { it.currency.code == code } ?: RUB
    }
}

data class SecuritySettings(
    val isPinEnabled: Boolean = false,
    val isBiometricsEnabled: Boolean = false,
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RUSSIAN,
    val currency: AppCurrency = AppCurrency.RUB,
    val security: SecuritySettings = SecuritySettings(),
)
