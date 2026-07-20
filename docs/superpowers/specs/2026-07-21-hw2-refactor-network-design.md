# HW2: рефакторинг, сетевой слой и «человеческий» код — дизайн

Дата: 2026-07-21
Ветка: `dz_2_rabota_s_setju`

## Контекст

Приложение SHMR Finance (Jetpack Compose) к моменту начала работы уже реализует
почти все требования HW2: чистая слоистая архитектура (domain / dto / mapper /
repository / network / ui), сетевые вызовы вне главного потока, `UiState`
(Loading / Content / Empty / Error), дефолтный период аналитики и сортировку,
а также бонусный `RetryInterceptor` + кнопку «Повторить».

Задача — **не спасать код, а отполировать его**: применить знания из лекции по
Compose (слайды 60–63 — MVI: State / Action / Effect + разделение
Screen/Content; тема стабильности и эффектов), убрать «нейрослоп»-следы
(машинно-ровные комментарии, over-engineering), укрепить сетевой слой и
проверить всё вживую.

Живая проверка API уже показала: сетевой слой рабочий (категории грузятся,
авторизация валидна), но у токена **0 счетов** → без сид-данных все экраны
показывают только Empty. Демо-данные создаём через API.

## Требования HW2 — статус

| Требование | Статус |
|---|---|
| Нет бизнес-логики в @Composable | ✅ вынесено в VM |
| Экран «Аналитика», дефолтный период (начало месяца → сегодня) | ✅ |
| Сортировка истории от недавних к старым | ✅ `sortedByDescending` |
| Фильтры аналитики (тип / период / статьи / счёт) | ✅ |
| Сеть вне главного потока | ✅ `safeApiCall` на IO, тяжёлое — на Default |
| Сетевые + доменные модели, маппинг | ✅ dto → mapper → domain |
| Нет сетевых моделей в UI | ✅ |
| Состояния Loading/Content/Empty/Error на всех экранах | ✅ |
| Бонус: единый механизм ошибок с «Повторить» (+10) | ⚠️ есть inline retry; добавляем Snackbar-эффект |
| Лучшие практики из лекции (MVI, стабильность) | ⚠️ применяем к Аналитике |

## Объём работ

### 1. Чистка «нейрослопа» (все затронутые файлы)

- Убрать туториальные KDoc, дублирующие код: `safeApiCall`, `RetryInterceptor`,
  `TransactionListViewModel`, `ErrorState`, `AnalyticsFilters.default` и т.п.
  Оставить только краткие «почему»-комментарии (например, одна строка про
  повтор на 5xx — без doc-блока).
- `AnalyticsScreen.FilterRows`: заменить полностью квалифицированный
  `List<ru.shmr.finance.domain.model.Account>` на импортированный `List<Account>`
  (и то же для `Category`).
- `Money.formatted()`: заменить рукописный цикл группировки разрядов на
  `DecimalFormat` + `DecimalFormatSymbols` (пробел — разделитель групп, запятая —
  дробная часть). Короче и идиоматичнее.
- Переформулировать `// TODO: добавление операции` на FAB в человеческую заметку.

### 2. Аналитика → MVI (лекция, слайды 60–63)

Новые типы (в пакете `ui.screens.analytics`):

```kotlin
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

- VM: `state: StateFlow<AnalyticsState>`, `effects: SharedFlow<AnalyticsEffect>`,
  один вход `fun onAction(action: AnalyticsAction)`. Внутренняя логика загрузки
  и `buildContent` сохраняется (она корректна), меняется только «оболочка».
- Разделение composable:
  - **`AnalyticsScreen`** (stateful) — собирает `state`, держит локальный
    UI-стейт листов/диалога (`openSheet`, `showDatePicker`), собирает `effects`
    через `LaunchedEffect` и показывает Snackbar.
  - **`AnalyticsContent(state, onAction, modifier)`** (stateless) — рисует
    график/фильтры/историю, дёргает `onAction`.

### 3. Единый механизм ошибок (бонус +10)

- `Scaffold` с `SnackbarHost` в `AnalyticsScreen`.
- **Ошибка при перезагрузке** (контент уже был, поменяли фильтр) → VM эмитит
  `AnalyticsEffect.ShowError`; экран показывает **Snackbar с действием
  «Повторить»** → `onAction(AnalyticsAction.Retry)`. Контент не сбрасывается.
- **Ошибка при первичной загрузке** (контента ещё нет) → полноэкранный
  `ErrorState` (в нём тоже есть «Повторить»).
- Это ровно демонстрирует лекционный паттерн `Effect` + `LaunchedEffect`.

### 4. Укрепление сетевого слоя

- Маппер `TransactionResponseDto.toDomain()`: разбор `transactionDate` делаем
  устойчивым — сначала `Instant.parse` (RFC3339 с `Z`/offset), при неудаче
  `LocalDateTime.parse` (значение без смещения), чтобы формат-квирк не
  превращался молча в `AppError.Unknown`. Вынести в маленькую приватную
  функцию `parseDateTime(raw: String): LocalDateTime`.

### 5. Стабильность (тема лекции)

- `@Immutable` на стабильных холдерах: `ListItemModel`, `LeadContent`,
  `CategoryShare`, `AnalyticsData`, `AnalyticsState`, `AnalyticsFilters` — чтобы
  composable оставались skippable.

### 6. Проверка

- `./gradlew assembleDebug` — чисто.
- Установить на эмулятор.
- Засеять через API один счёт + несколько транзакций (расход/доход).
- Скриншоты: каждая вкладка + Аналитика в состояниях Loading / Content / Empty /
  Error и Snackbar-повтор.

## Вне объёма

- Перевод экранов Расходы/Доходы/Счета на MVI (остаются как есть + чистка).
- Пиксель-в-пиксель по Figma.
- Новые фичи (FAB остаётся TODO-заглушкой).

## Риски

- Live-формат `transactionDate` неизвестен точно до первой транзакции —
  устойчивый парсер (п.4) закрывает риск.
- Создание демо-данных пишет в тестовый бэкенд курса (POST /accounts,
  /transactions) — согласовано с пользователем.
