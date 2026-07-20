# HW2: экран «Аналитика» + сетевой слой — дизайн

## Цель
Выполнить ДЗ2 на 100/100: новый экран «Аналитика», сетевой слой для всех экранов,
корректные UI-состояния (Loading/Content/Empty/Error), единый механизм ошибок с retry (+10).

## API (https://shmr-finance.ru/api/v1, Bearer JWT)
- `GET /accounts` → `[AccountDto]`
- `GET /categories` → `[CategoryDto]`
- `GET /transactions/account/{accountId}/period?startDate&endDate` → `[TransactionResponseDto]`

Токен и базовый URL берутся из `local.properties` (`API_TOKEN`, `API_BASE_URL`) → `BuildConfig`.
Токен не коммитится.

## Архитектура
```
ui (Compose, тупые composable)
 └─ ViewModel (вся логика, StateFlow<UiState<T>>)
     └─ domain (модели: Account, Category, Transaction, Money(BigDecimal))
         └─ data (Retrofit DTO + мапперы + репозитории, Dispatchers.IO)
```
- Retrofit + kotlinx.serialization + OkHttp.
- `AuthInterceptor` добавляет Bearer.
- `RetryInterceptor` — единая политика повторов: до 3 попыток с бэкоффом на 5xx/IOException.
- Ошибки нормализуются в `AppError` (NoInternet / Server / Unknown) в одном месте (`safeApiCall`).
- Manual DI: `ServiceLocator` + `viewModel(factory = ...)`.

## Экраны
- **Расходы / Доходы**: транзакции за сегодня по первому счёту пользователя,
  фильтр по `category.isIncome`, сумма в шапке. Состояния: Loading/Content/Empty/Error.
- **Счета**: `GET /accounts`, список с балансами.
- **Аналитика** (route `analytics`, открывается иконкой графика в топ-баре):
  - период по умолчанию: с 1-го числа текущего месяца по сегодня;
  - сортировка транзакций: от недавних к более поздним (по дате, убывание);
  - donut-диаграмма (Canvas) с суммой за период в центре + легенда;
  - фильтры (bottom sheets, как в фигме): Тип (Расходы/Доходы/Всё),
    Период (Произвольный → DateRangePicker, Неделя/Месяц/Квартал/Год),
    Статьи (multi-select категорий, «Применить»), Счёт (single-select, «Все счета»);
  - строки-фильтры с чипами текущих значений; секция «Транзакции».
  - Состояния: Loading/Content/Empty/Error.

## UiState
`Loading / Content(data) / Empty / Error(AppError)`. Отображение ошибки — общий
`ErrorContent(message, onRetry)` с кнопкой «Повторить»; ViewModel перезапускает загрузку.

## Проверка
Скриншот-сверка с Figma на эмуляторе. Для локальной проверки состояний —
мок-сервер по схеме Swagger на 10.0.2.2 (через `API_BASE_URL` в local.properties);
в коммите остаётся боевой URL.
