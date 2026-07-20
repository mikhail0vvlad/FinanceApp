# HW2 Refactor & Network Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Отполировать уже рабочее приложение под HW2 — применить MVI из лекции к Аналитике (State/Action/Effect + Screen/Content), добавить единый Snackbar-механизм ошибок с «Повторить», убрать «нейрослоп»-следы, укрепить сетевой слой, и проверить всё вживую.

**Architecture:** Слоистая архитектура (domain / dto / mapper / repository / network / ui) уже на месте. Аналитика переводится на однонаправленный поток данных: ViewModel держит `StateFlow<AnalyticsState>` + `SharedFlow<AnalyticsEffect>`, экран разделяется на stateful `AnalyticsScreen` и stateless `AnalyticsContent`. Остальные экраны остаются как есть, но чистятся.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.09.03), Retrofit 2.11 + kotlinx.serialization, OkHttp 4.12, Coroutines 1.9, JUnit4 (для юнит-тестов чистых функций).

## Global Constraints

- Package root: `ru.shmr.finance`. minSdk 24, targetSdk/compileSdk 34, JVM 17.
- Никакой бизнес-логики в @Composable — только в VM/holders.
- Сеть только вне главного потока (`safeApiCall` на `Dispatchers.IO`, тяжёлые вычисления на `Dispatchers.Default`).
- UI не использует сетевые (DTO) модели — только доменные.
- Комментарии: только краткие «почему», без туториальных doc-блоков, дублирующих код.
- Каждая задача заканчивается компилируемым состоянием (`./gradlew compileDebugKotlin`), юнит-тесты — `./gradlew testDebugUnitTest`.

---

### Task 1: Money.formatted() → DecimalFormat (+ юнит-тесты)

**Files:**
- Modify: `app/src/main/java/ru/shmr/finance/domain/model/Money.kt`
- Modify: `gradle/libs.versions.toml` (добавить junit)
- Modify: `app/build.gradle.kts` (добавить `testImplementation(libs.junit)`)
- Test: `app/src/test/java/ru/shmr/finance/domain/model/MoneyTest.kt`

**Interfaces:**
- Produces: `Money.formatted(): String` — группировка разрядов пробелом, дробная часть через запятую, символ валюты в конце. Поведение эквивалентно текущему.

- [ ] **Step 1: Добавить JUnit в каталог версий**

В `gradle/libs.versions.toml` в `[versions]` добавить:
```toml
junit = "4.13.2"
```
В `[libraries]` добавить:
```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
```

- [ ] **Step 2: Подключить тестовую зависимость**

В `app/build.gradle.kts` в блок `dependencies { ... }` добавить строку:
```kotlin
    testImplementation(libs.junit)
```

- [ ] **Step 3: Написать падающий тест**

Создать `app/src/test/java/ru/shmr/finance/domain/model/MoneyTest.kt`:
```kotlin
package ru.shmr.finance.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `integer amount groups thousands with spaces`() {
        assertEquals("1 000 ₽", Money(BigDecimal("1000.00")).formatted())
    }

    @Test
    fun `fractional amount uses comma and drops trailing zeros`() {
        assertEquals("1 234 567,89 $", Money(BigDecimal("1234567.89"), Currency.USD).formatted())
        assertEquals("1 500,5 ₽", Money(BigDecimal("-1500.50")).formatted().let { it }.replace("-", ""))
    }

    @Test
    fun `zero is formatted without fraction`() {
        assertEquals("0 ₽", Money(BigDecimal.ZERO).formatted())
    }

    @Test
    fun `negative keeps sign before grouped digits`() {
        assertEquals("-1 500,5 ₽", Money(BigDecimal("-1500.50")).formatted())
    }
}
```

- [ ] **Step 4: Запустить тест — убедиться, что падает/не компилируется как ожидается**

Run: `./gradlew testDebugUnitTest --tests "ru.shmr.finance.domain.model.MoneyTest"`
Expected: FAIL (старый форматтер даёт тот же результат для большинства кейсов, но задача — заменить реализацию; если все проходят сразу, всё равно продолжаем заменой реализации ради чистоты).

- [ ] **Step 5: Заменить реализацию formatted()**

В `Money.kt` заменить тело функции `formatted()` и импорты. Итоговый файл:
```kotlin
package ru.shmr.finance.domain.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class Currency(val code: String, val symbol: String) {
    RUB("RUB", "₽"),
    USD("USD", "$"),
    EUR("EUR", "€"),
    ;

    companion object {
        fun fromCode(code: String): Currency = entries.find { it.code == code } ?: RUB
    }
}

data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.RUB,
) {
    operator fun plus(other: Money): Money = copy(amount = amount + other.amount)

    fun formatted(): String = "${amountFormat.format(amount)} ${currency.symbol}"

    companion object {
        val ZERO = Money(BigDecimal.ZERO)

        private val amountFormat = DecimalFormat(
            "#,##0.##",
            DecimalFormatSymbols(Locale.ROOT).apply {
                groupingSeparator = ' '
                decimalSeparator = ','
            },
        )

        fun parse(raw: String, currencyCode: String = "RUB"): Money =
            Money(BigDecimal(raw), Currency.fromCode(currencyCode))
    }
}
```

- [ ] **Step 6: Запустить тесты — убедиться, что проходят**

Run: `./gradlew testDebugUnitTest --tests "ru.shmr.finance.domain.model.MoneyTest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/ru/shmr/finance/domain/model/Money.kt app/src/test/java/ru/shmr/finance/domain/model/MoneyTest.kt
git commit -m "refactor(money): format via DecimalFormat + add unit tests"
```

---

### Task 2: Устойчивый разбор transactionDate (+ юнит-тест)

**Files:**
- Modify: `app/src/main/java/ru/shmr/finance/data/mapper/Mappers.kt`
- Test: `app/src/test/java/ru/shmr/finance/data/mapper/MapperDateTest.kt`

**Interfaces:**
- Consumes: `Money.parse` (Task 1 не меняет сигнатуру).
- Produces: приватная `parseDateTime(raw: String): LocalDateTime` внутри Mappers.kt, устойчивая к формату с `Z`, со смещением и без смещения. `TransactionResponseDto.toDomain()` использует её.

- [ ] **Step 1: Написать падающий тест**

Создать `app/src/test/java/ru/shmr/finance/data/mapper/MapperDateTest.kt`:
```kotlin
package ru.shmr.finance.data.mapper

import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shmr.finance.data.network.dto.AccountBriefDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

class MapperDateTest {

    private fun dto(date: String) = TransactionResponseDto(
        id = 1,
        account = AccountBriefDto(1, "Acc", "0", "RUB"),
        category = CategoryDto(1, "Cat", "💰", true),
        amount = "100.00",
        transactionDate = date,
        comment = null,
        createdAt = date,
        updatedAt = date,
    )

    @Test
    fun `parses RFC3339 with Z`() {
        val tx = dto("2024-03-15T10:30:00.000Z").toDomain()
        assertEquals(Month.MARCH, tx.dateTime.month)
        assertEquals(15, tx.dateTime.dayOfMonth)
    }

    @Test
    fun `parses value with explicit offset`() {
        val tx = dto("2024-03-15T13:30:00+03:00").toDomain()
        assertEquals(15, tx.dateTime.dayOfMonth)
    }

    @Test
    fun `parses local date-time without offset`() {
        val tx = dto("2024-03-15T10:30:00").toDomain()
        assertEquals(15, tx.dateTime.dayOfMonth)
        assertEquals(10, tx.dateTime.hour)
    }
}
```

- [ ] **Step 2: Запустить — убедиться, что падает**

Run: `./gradlew testDebugUnitTest --tests "ru.shmr.finance.data.mapper.MapperDateTest"`
Expected: FAIL на кейсе с offset / без offset (текущий `Instant.parse` бросает исключение).

- [ ] **Step 3: Заменить парсинг даты в Mappers.kt**

Заменить импорты и функцию так, чтобы файл стал:
```kotlin
package ru.shmr.finance.data.mapper

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import ru.shmr.finance.data.network.dto.AccountDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction

fun AccountDto.toDomain() = Account(
    id = id,
    name = name,
    balance = Money.parse(balance, currency),
)

fun CategoryDto.toDomain() = Category(
    id = id,
    name = name,
    emoji = emoji,
    isIncome = isIncome,
)

fun TransactionResponseDto.toDomain() = Transaction(
    id = id,
    accountId = account.id,
    accountName = account.name,
    category = category.toDomain(),
    comment = comment?.takeIf { it.isNotBlank() },
    amount = Money.parse(amount, account.currency),
    dateTime = parseDateTime(transactionDate),
)

// Сервер отдаёт date-time по-разному: с 'Z', со смещением и иногда без него.
private fun parseDateTime(raw: String): LocalDateTime =
    runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
        .recoverCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        .getOrElse { LocalDateTime.parse(raw) }
```

- [ ] **Step 4: Запустить тесты — убедиться, что проходят**

Run: `./gradlew testDebugUnitTest --tests "ru.shmr.finance.data.mapper.MapperDateTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/shmr/finance/data/mapper/Mappers.kt app/src/test/java/ru/shmr/finance/data/mapper/MapperDateTest.kt
git commit -m "fix(mapper): tolerate offset and offset-less transaction dates"
```

---

### Task 3: Чистка «нейрослопа» + стабильность (не-Аналитика)

**Files:**
- Modify: `app/src/main/java/ru/shmr/finance/data/network/SafeApiCall.kt`
- Modify: `app/src/main/java/ru/shmr/finance/data/network/Interceptors.kt`
- Modify: `app/src/main/java/ru/shmr/finance/ui/screens/TransactionListViewModel.kt`
- Modify: `app/src/main/java/ru/shmr/finance/ui/components/StateViews.kt`
- Modify: `app/src/main/java/ru/shmr/finance/ui/components/ListItem.kt`
- Modify: `app/src/main/java/ru/shmr/finance/ui/FinanceApp.kt`

**Interfaces:**
- Produces: `@Immutable data class ListItemModel`, `@Immutable sealed interface LeadContent`. Без изменений сигнатур.

- [ ] **Step 1: SafeApiCall.kt — убрать doc-блок**

Удалить KDoc над `safeApiCall` (строки `/** Выполняет сетевой вызов... */`). Функцию и логику оставить как есть.

- [ ] **Step 2: Interceptors.kt — сократить комментарий**

Заменить doc-блок над `RetryInterceptor` на одну строку внутри/над классом:
```kotlin
// Сервер периодически отвечает 5xx — повторяем запрос с растущей паузой.
class RetryInterceptor(
```
Удалить KDoc-блок `/** Единая политика повторов... */`.

- [ ] **Step 3: TransactionListViewModel.kt — убрать doc-блок**

Удалить KDoc `/** Общая вью-модель... */` над `abstract class TransactionListViewModel`. Код без изменений.

- [ ] **Step 4: StateViews.kt — убрать doc-блок**

Удалить KDoc `/** Единый механизм отображения ошибок... */` над `ErrorState`. Код без изменений.

- [ ] **Step 5: ListItem.kt — @Immutable на модели**

Добавить импорт `import androidx.compose.runtime.Immutable` и аннотации:
```kotlin
@Immutable
sealed interface LeadContent {
    data class Emoji(val emoji: String) : LeadContent
    data object None : LeadContent
}

@Immutable
data class ListItemModel(
```

- [ ] **Step 6: FinanceApp.kt — человеческий TODO на FAB**

Заменить `onClick = { /* TODO: добавление операции */ }` на:
```kotlin
                FloatingActionButton(onClick = { /* добавление операции — следующее ДЗ */ }) {
```

- [ ] **Step 7: Компиляция**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/ru/shmr/finance/data/network/SafeApiCall.kt app/src/main/java/ru/shmr/finance/data/network/Interceptors.kt app/src/main/java/ru/shmr/finance/ui/screens/TransactionListViewModel.kt app/src/main/java/ru/shmr/finance/ui/components/StateViews.kt app/src/main/java/ru/shmr/finance/ui/components/ListItem.kt app/src/main/java/ru/shmr/finance/ui/FinanceApp.kt
git commit -m "refactor: trim boilerplate comments, mark UI models stable"
```

---

### Task 4: MVI-контракт и ViewModel Аналитики

**Files:**
- Create: `app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsContract.kt`
- Modify: `app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsModels.kt` (аннотации @Immutable, убрать слоп-комменты)
- Modify: `app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsViewModel.kt` (переписать под onAction + effects)

**Interfaces:**
- Consumes: `AnalyticsFilters`, `AnalyticsData`, `TypeFilter`, `PeriodPreset`, `CategoryShare` (AnalyticsModels.kt); `UiState`, `AppError`, репозитории из `ServiceLocator`.
- Produces:
  - `@Immutable data class AnalyticsState(ui: UiState<AnalyticsData>, filters: AnalyticsFilters, accounts: List<Account>, categories: List<Category>)`
  - `sealed interface AnalyticsAction { SelectType(type), SelectPreset(preset), SelectCustomPeriod(start,end), SelectCategories(ids), SelectAccount(accountId), Retry }`
  - `sealed interface AnalyticsEffect { data class ShowError(error: AppError) }`
  - `AnalyticsViewModel(startWithIncome): state: StateFlow<AnalyticsState>, effects: SharedFlow<AnalyticsEffect>, fun onAction(action)`

- [ ] **Step 1: Создать AnalyticsContract.kt**

```kotlin
package ru.shmr.finance.ui.screens.analytics

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Category

@Immutable
data class AnalyticsState(
    val ui: UiState<AnalyticsData> = UiState.Loading,
    val filters: AnalyticsFilters,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

sealed interface AnalyticsAction {
    data class SelectType(val type: TypeFilter) : AnalyticsAction
    data class SelectPreset(val preset: PeriodPreset) : AnalyticsAction
    data class SelectCustomPeriod(val start: LocalDate, val end: LocalDate) : AnalyticsAction
    data class SelectCategories(val ids: Set<Int>?) : AnalyticsAction
    data class SelectAccount(val accountId: Int?) : AnalyticsAction
    data object Retry : AnalyticsAction
}

sealed interface AnalyticsEffect {
    data class ShowError(val error: AppError) : AnalyticsEffect
}
```

- [ ] **Step 2: AnalyticsModels.kt — @Immutable и чистка комментариев**

Добавить `import androidx.compose.runtime.Immutable`. Пометить `AnalyticsFilters`, `CategoryShare`, `AnalyticsData` как `@Immutable`. Заменить комментарий `/** Период по умолчанию: с начала текущего месяца по текущую дату. */` над `default` на короткий `// по умолчанию: с начала месяца по сегодня`. Комментарии `/** null — все статьи */` и `/** null — все счета */` заменить на inline `// null = все`. Комментарий `/** Доля от общей суммы, 0..1. */` оставить как есть (полезный «почему»). Логику не менять.

- [ ] **Step 3: Переписать AnalyticsViewModel.kt под MVI**

Полное содержимое:
```kotlin
package ru.shmr.finance.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

class AnalyticsViewModel(
    startWithIncome: Boolean,
    private val accountsRepository: AccountsRepository = ServiceLocator.accountsRepository,
    private val categoriesRepository: CategoriesRepository = ServiceLocator.categoriesRepository,
    private val transactionsRepository: TransactionsRepository = ServiceLocator.transactionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AnalyticsState(
            filters = AnalyticsFilters.default(
                if (startWithIncome) TypeFilter.INCOME else TypeFilter.EXPENSES,
            ),
        ),
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AnalyticsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: AnalyticsAction) {
        when (action) {
            is AnalyticsAction.SelectType ->
                applyFilters { copy(type = action.type, selectedCategoryIds = null) }
            is AnalyticsAction.SelectPreset -> applyFilters {
                val (start, end) = AnalyticsFilters.periodFor(action.preset)
                copy(preset = action.preset, startDate = start, endDate = end)
            }
            is AnalyticsAction.SelectCustomPeriod -> applyFilters {
                copy(preset = PeriodPreset.CUSTOM, startDate = action.start, endDate = action.end)
            }
            is AnalyticsAction.SelectCategories ->
                applyFilters { copy(selectedCategoryIds = action.ids?.takeIf { it.isNotEmpty() }) }
            is AnalyticsAction.SelectAccount ->
                applyFilters { copy(selectedAccountId = action.accountId) }
            AnalyticsAction.Retry -> load()
        }
    }

    private fun applyFilters(transform: AnalyticsFilters.() -> AnalyticsFilters) {
        val updated = _state.value.filters.transform()
        if (updated != _state.value.filters) {
            _state.update { it.copy(filters = updated) }
            load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hadContent = _state.value.ui is UiState.Content
            if (!hadContent) _state.update { it.copy(ui = UiState.Loading) }
            val filters = _state.value.filters

            if (_state.value.accounts.isEmpty()) {
                when (val result = accountsRepository.getAccounts()) {
                    is AppResult.Failure -> return@launch fail(result.error, hadContent)
                    is AppResult.Success -> _state.update { it.copy(accounts = result.data) }
                }
            }
            if (_state.value.categories.isEmpty()) {
                when (val result = categoriesRepository.getCategories()) {
                    is AppResult.Failure -> return@launch fail(result.error, hadContent)
                    is AppResult.Success -> _state.update { it.copy(categories = result.data) }
                }
            }

            val accountsToQuery = _state.value.accounts
                .filter { filters.selectedAccountId == null || it.id == filters.selectedAccountId }
            if (accountsToQuery.isEmpty()) {
                _state.update { it.copy(ui = UiState.Empty) }
                return@launch
            }

            val transactions = when (val result = loadTransactions(accountsToQuery, filters)) {
                is AppResult.Failure -> return@launch fail(result.error, hadContent)
                is AppResult.Success -> result.data
            }

            val ui = withContext(Dispatchers.Default) {
                buildContent(transactions, filters, accountsToQuery.first())
            }
            _state.update { it.copy(ui = ui) }
        }
    }

    // Ошибка при перезагрузке (контент уже есть) — Snackbar, не сбрасываем экран.
    private suspend fun fail(error: AppError, hadContent: Boolean) {
        if (hadContent) {
            _effects.emit(AnalyticsEffect.ShowError(error))
        } else {
            _state.update { it.copy(ui = UiState.Error(error)) }
        }
    }

    private suspend fun loadTransactions(
        accounts: List<Account>,
        filters: AnalyticsFilters,
    ): AppResult<List<Transaction>> = coroutineScope {
        val results = accounts
            .map { account ->
                async {
                    transactionsRepository.getTransactionsForPeriod(
                        accountId = account.id,
                        startDate = filters.startDate,
                        endDate = filters.endDate,
                    )
                }
            }
            .map { it.await() }
        results.filterIsInstance<AppResult.Failure>().firstOrNull()
            ?: AppResult.Success(
                results.filterIsInstance<AppResult.Success<List<Transaction>>>().flatMap { it.data },
            )
    }

    private fun buildContent(
        transactions: List<Transaction>,
        filters: AnalyticsFilters,
        anyAccount: Account,
    ): UiState<AnalyticsData> {
        val filtered = transactions
            .filter {
                when (filters.type) {
                    TypeFilter.EXPENSES -> !it.category.isIncome
                    TypeFilter.INCOME -> it.category.isIncome
                    TypeFilter.ALL -> true
                }
            }
            .filter { filters.selectedCategoryIds == null || it.category.id in filters.selectedCategoryIds }
            .sortedByDescending { it.dateTime }

        if (filtered.isEmpty()) return UiState.Empty

        val currency = anyAccount.balance.currency
        val total = filtered.fold(Money.ZERO.copy(currency = currency)) { acc, tx -> acc + tx.amount }

        val shares = filtered
            .groupBy { it.category }
            .map { (category, txs) ->
                val amount = txs.fold(Money.ZERO.copy(currency = currency)) { acc, tx -> acc + tx.amount }
                val fraction = if (total.amount.signum() == 0) {
                    0f
                } else {
                    amount.amount.divide(total.amount, 4, RoundingMode.HALF_UP).toFloat()
                }
                CategoryShare(
                    category = category,
                    amount = amount,
                    fraction = fraction,
                    percent = BigDecimal((fraction * 100).toDouble())
                        .setScale(0, RoundingMode.HALF_UP).toInt(),
                )
            }
            .sortedByDescending { it.amount.amount }

        return UiState.Content(AnalyticsData(total = total, shares = shares, transactions = filtered))
    }
}
```

- [ ] **Step 4: Компиляция (ожидаемо ломается AnalyticsScreen)**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `AnalyticsScreen.kt` ещё ссылается на старые методы VM (`viewModel.filters`, `viewModel::onTypeSelected` и т.п.). Это чинит Task 5.

- [ ] **Step 5: Commit (WIP допустим — компиляция чинится следующей задачей)**

```bash
git add app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsContract.kt app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsModels.kt app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsViewModel.kt
git commit -m "feat(analytics): introduce MVI state/action/effect contract"
```

---

### Task 5: Split Screen/Content + Snackbar-эффект ошибок

**Files:**
- Modify: `app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml` (строка про ошибку обновления, если нужна)

**Interfaces:**
- Consumes: `AnalyticsState`, `AnalyticsAction`, `AnalyticsEffect`, `AnalyticsViewModel.onAction`, `state`, `effects` (Task 4); листы `TypeFilterSheet/PeriodFilterSheet/ArticlesFilterSheet/AccountFilterSheet/DetailSheet/CustomPeriodDialog` (AnalyticsSheets.kt); `DonutChart`, `chartColor`, `LegendDot` (DonutChart/AnalyticsSheets); `error.message()` (StateViews).
- Produces: `AnalyticsScreen(startWithIncome, onBack, modifier, viewModel)` (публичная сигнатура без изменений), приватная `AnalyticsContent(state, onAction, modifier)`.

- [ ] **Step 1: Добавить строку ресурса для Snackbar (если отсутствует)**

В `app/src/main/res/values/strings.xml` добавить (проверить, что нет дубля):
```xml
    <string name="error_reload_failed">Не удалось обновить данные</string>
```

- [ ] **Step 2: Переписать AnalyticsScreen.kt**

Полное содержимое:
```kotlin
package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.shmr.finance.R
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.ui.components.EmptyState
import ru.shmr.finance.ui.components.ErrorState
import ru.shmr.finance.ui.components.ListItemRow
import ru.shmr.finance.ui.components.LoadingState
import ru.shmr.finance.ui.components.message
import ru.shmr.finance.ui.screens.toListItem
import ru.shmr.finance.ui.theme.LeadBadgeOutline

private enum class AnalyticsSheet { TYPE, PERIOD, ARTICLES, ACCOUNT, DETAIL }

@Composable
fun AnalyticsScreen(
    startWithIncome: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AnalyticsViewModel(startWithIncome) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val retryLabel = stringResource(R.string.action_retry)
    LaunchedEffectCollectErrors(viewModel, snackbarHostState, retryLabel) {
        viewModel.onAction(AnalyticsAction.Retry)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        AnalyticsContent(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun LaunchedEffectCollectErrors(
    viewModel: AnalyticsViewModel,
    snackbarHostState: SnackbarHostState,
    retryLabel: String,
    onRetry: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AnalyticsEffect.ShowError -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.error.plainMessage(context),
                        actionLabel = retryLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) onRetry()
                }
            }
        }
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsState,
    onAction: (AnalyticsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<AnalyticsSheet?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        AnalyticsTopBar(onBack)

        when (val ui = state.ui) {
            UiState.Loading -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                LoadingState(Modifier.weight(1f))
            }

            UiState.Empty -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                EmptyState(Modifier.weight(1f))
            }

            is UiState.Error -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                ErrorState(ui.error, { onAction(AnalyticsAction.Retry) }, Modifier.weight(1f))
            }

            is UiState.Content -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "chart") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DonutChart(
                            shares = ui.data.shares,
                            caption = stringResource(R.string.analytics_total_for_period),
                            amount = ui.data.total.formatted(),
                            modifier = Modifier.clickable { openSheet = AnalyticsSheet.DETAIL },
                        )
                        ChartLegend(ui.data.shares)
                    }
                }
                item(key = "filters") { FilterRows(state) { openSheet = it } }
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.analytics_transactions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(ui.data.transactions, key = { it.id }) { transaction ->
                    ListItemRow(
                        transaction.toListItem().copy(
                            subtitle = transaction.comment ?: transaction.accountName,
                        ),
                    )
                }
            }
        }
    }

    AnalyticsSheets(
        state = state,
        openSheet = openSheet,
        onAction = onAction,
        onCloseSheet = { openSheet = null },
        onCustomRequested = {
            openSheet = null
            showDatePicker = true
        },
    )

    if (showDatePicker) {
        CustomPeriodDialog(
            initialStart = state.filters.startDate,
            initialEnd = state.filters.endDate,
            onConfirm = { start, end -> onAction(AnalyticsAction.SelectCustomPeriod(start, end)) },
            onDismiss = { showDatePicker = false },
        )
    }
}

@Composable
private fun AnalyticsSheets(
    state: AnalyticsState,
    openSheet: AnalyticsSheet?,
    onAction: (AnalyticsAction) -> Unit,
    onCloseSheet: () -> Unit,
    onCustomRequested: () -> Unit,
) {
    when (openSheet) {
        AnalyticsSheet.TYPE -> TypeFilterSheet(
            selected = state.filters.type,
            onSelected = { onAction(AnalyticsAction.SelectType(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.PERIOD -> PeriodFilterSheet(
            filters = state.filters,
            onPresetSelected = { onAction(AnalyticsAction.SelectPreset(it)) },
            onCustomRequested = onCustomRequested,
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.ARTICLES -> ArticlesFilterSheet(
            categories = state.categories.filter {
                when (state.filters.type) {
                    TypeFilter.EXPENSES -> !it.isIncome
                    TypeFilter.INCOME -> it.isIncome
                    TypeFilter.ALL -> true
                }
            },
            selectedIds = state.filters.selectedCategoryIds,
            onApply = { onAction(AnalyticsAction.SelectCategories(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.ACCOUNT -> AccountFilterSheet(
            accounts = state.accounts,
            selectedAccountId = state.filters.selectedAccountId,
            onSelected = { onAction(AnalyticsAction.SelectAccount(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.DETAIL -> (state.ui as? UiState.Content)?.let { content ->
            DetailSheet(data = content.data, onDismiss = onCloseSheet)
        }

        null -> Unit
    }
}

@Composable
private fun AnalyticsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Text(
            text = stringResource(R.string.analytics_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun ChartLegend(shares: List<CategoryShare>) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shares.take(3).forEachIndexed { index, share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(chartColor(index))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = share.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterRows(
    state: AnalyticsState,
    onOpenSheet: (AnalyticsSheet) -> Unit,
) {
    val filters = state.filters
    val articlesChip = when (val ids = filters.selectedCategoryIds) {
        null -> stringResource(R.string.all_articles)
        else -> state.categories.filter { it.id in ids }.joinToString { it.name }
            .ifEmpty { stringResource(R.string.all_articles) }
    }
    val accountChip = state.accounts.find { it.id == filters.selectedAccountId }?.name
        ?: stringResource(R.string.all_accounts)

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilterRow(
            icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.List),
            label = stringResource(R.string.filter_type),
            chipText = filters.type.label(),
            onClick = { onOpenSheet(AnalyticsSheet.TYPE) },
        )
        FilterRow(
            icon = painterResource(R.drawable.ic_calendar_month),
            label = stringResource(R.string.filter_period),
            chipText = formatPeriod(filters.startDate, filters.endDate),
            onClick = { onOpenSheet(AnalyticsSheet.PERIOD) },
        )
        FilterRow(
            icon = rememberVectorPainter(Icons.Outlined.Sell),
            label = stringResource(R.string.filter_articles),
            chipText = articlesChip,
            onClick = { onOpenSheet(AnalyticsSheet.ARTICLES) },
        )
        FilterRow(
            icon = rememberVectorPainter(Icons.Outlined.CreditCard),
            label = stringResource(R.string.filter_account),
            chipText = accountChip,
            onClick = { onOpenSheet(AnalyticsSheet.ACCOUNT) },
        )
    }
}

@Composable
private fun FilterRow(
    icon: Painter,
    label: String,
    chipText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, LeadBadgeOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = chipText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
```

- [ ] **Step 3: Добавить не-Composable сообщение об ошибке для Snackbar**

Snackbar внутри `LaunchedEffect` не может звать `@Composable fun message()`. Добавить в `StateViews.kt` не-composable вариант:
```kotlin
fun AppError.plainMessage(context: android.content.Context): String = when (this) {
    AppError.NoInternet -> context.getString(R.string.error_no_internet)
    AppError.Unauthorized -> context.getString(R.string.error_unauthorized)
    is AppError.Server -> context.getString(R.string.error_server, code)
    AppError.Unknown -> context.getString(R.string.error_unknown)
}
```
(добавить `import android.content.Context` не требуется — используется полное имя в сигнатуре; либо добавить импорт по вкусу.)

- [ ] **Step 4: Компиляция**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Полный debug-сборочный прогон + юнит-тесты**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/shmr/finance/ui/screens/analytics/AnalyticsScreen.kt app/src/main/java/ru/shmr/finance/ui/components/StateViews.kt app/src/main/res/values/strings.xml
git commit -m "feat(analytics): split Screen/Content, add Snackbar error effect with retry"
```

---

### Task 6: Живая проверка — сид-данные + скриншот-тестирование

**Files:** нет изменений кода (только проверка). При обнаружении дефектов — отдельные фиксы.

**Interfaces:** использует запущенный эмулятор (`adb`), токен из `local.properties`, API `https://shmr-finance.ru/api/v1/`.

- [ ] **Step 1: Убедиться, что эмулятор запущен**

Run: `adb devices`
Expected: минимум одно устройство в состоянии `device`. Если нет — запустить AVD.

- [ ] **Step 2: Создать демо-данные через API (согласовано с пользователем)**

Один счёт и несколько транзакций (расход/доход) через POST. Точные схемы взять из `scratchpad/api.yaml` (эндпоинты `POST /accounts`, `POST /transactions`, `GET /categories` для id категорий). Проверить `GET /accounts` — должен вернуть непустой список.

- [ ] **Step 3: Установить и запустить приложение**

Run: `./gradlew installDebug` затем `adb shell am start -n ru.shmr.finance/.MainActivity`
Expected: приложение открылось на экране «Расходы».

- [ ] **Step 4: Скриншоты состояний**

Пройти и снять `adb exec-out screencap -p > <file>.png`:
- Расходы (Content), Доходы (Content), Счета (Content)
- Аналитика (Content) + открыть лист «Период»/«Статьи»/«Счёт»
- Empty (например, период без транзакций), Loading (при запуске), Error/Snackbar (временно сломать сеть — airplane mode/неверный baseUrl — и нажать «Повторить»).

- [ ] **Step 5: Проверить отсутствие крашей при переключении вкладок**

Быстро переключать вкладки и открывать/закрывать Аналитику; смотреть `adb logcat` на предмет `FATAL EXCEPTION`.
Expected: без крашей/ANR.

- [ ] **Step 6: Финальная фиксация (если были фиксы дефектов)**

```bash
git add -A
git commit -m "chore: fixes found during live verification"
```

---

## Self-Review

- **Spec coverage:** Чистка слопа (Task 3 + части Task 1,2,4), Money-форматтер (Task 1), маппер (Task 2), MVI Аналитики (Task 4+5), Snackbar-эффект/бонус (Task 5), стабильность @Immutable (Task 3 ListItem, Task 4 Analytics models), сид-данные + скриншоты (Task 6). Все разделы спеки покрыты.
- **Placeholders:** нет TBD/«обработать ошибки» без кода — каждый шаг с кодом содержит код.
- **Type consistency:** `AnalyticsState.ui/filters/accounts/categories`, `AnalyticsAction.*`, `AnalyticsEffect.ShowError`, `onAction`, `state`, `effects`, `plainMessage(context)` — используются одинаково в Task 4 и Task 5. `parseDateTime`, `formatted`, `chartColor`, `LegendDot`, `toListItem` — согласованы с существующим кодом.
- **Замечание по стабильности:** `UiState`/`AppResult` — sealed interface с data-классами, Compose считает их стабильными; `List<...>` в `AnalyticsState` формально нестабилен, но `@Immutable` на классе — осознанная договорённость (мы не мутируем списки).
