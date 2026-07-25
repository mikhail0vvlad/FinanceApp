# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## What this is

SHMR Finance (`ru.shmr.finance`) — an Android/Jetpack Compose coursework app ("SHMR mobile school"). It tracks income/expenses across three bottom-nav tabs (Расходы/Доходы/Счета) plus an Аналитика screen with bar/donut charts. Work is done per homework assignment; specs and plans for each are written up in `docs/superpowers/specs/` and `docs/superpowers/plans/` before implementation.

## Build & test

Run Gradle via `gradlew.bat` in **PowerShell**, not `./gradlew` through the Bash tool — the Bash-tool invocation misparses task args and fails with "Could not find or load main class".

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
```

- Single test class: `.\gradlew.bat testDebugUnitTest --tests "ru.shmr.finance.domain.model.MoneyTest" --console=plain`
- Single test method: `.\gradlew.bat testDebugUnitTest --tests "ru.shmr.finance.domain.model.MoneyTest.zero is formatted without fraction" --console=plain`
- Unit tests live under `app/src/test/java/ru/shmr/finance/`; framework is plain JUnit4 (no MockK/Mockito/Robolectric wired up yet).
- No lint/formatter config (no detekt/ktlint) is set up in this repo — `kotlin.code.style=official` in `gradle.properties` is the only style setting.

### Running/installing on device

Requires a real or emulated device via `adb`. There's no scripted install task beyond the standard `installDebug`:

```powershell
.\gradlew.bat installDebug --console=plain
```

### API token

The app talks to a live test backend and needs a personal token: add `API_TOKEN=...` to `local.properties` (gitignored, not present in the repo) and rebuild — it's exposed as `BuildConfig.API_TOKEN`. `API_BASE_URL` defaults to `https://shmr-finance.ru/api/v1/` (OpenAPI spec at `.../swagger/documentation.yaml`) and can also be overridden in `local.properties`. A token with no accounts yet will show every data screen as Empty until an account and some transactions are created through the API.

## Architecture

Single-module app (`:app`), no DI framework — dependencies are wired through a plain lazy-initialized `di/ServiceLocator.kt` object (Retrofit client, repositories). Layering, outer to inner:

```
ui (Compose screens/ViewModels)
  -> domain (repository interfaces, models: Account/Category/Transaction/Money/AppError)
  -> data (Impl repositories, Retrofit FinanceApi, DTOs, mappers)
```

- **Networking**: `data/network/FinanceApi.kt` (Retrofit + kotlinx.serialization). `di/ServiceLocator` builds the `OkHttpClient` with two interceptors from `data/network/Interceptors.kt`: `AuthInterceptor` (bearer token) and `RetryInterceptor` (retries with backoff on 5xx — the server intermittently returns 500s). All calls go through `safeApiCall` (`data/network/SafeApiCall.kt`), which maps exceptions/HTTP codes to `AppResult<T>` (`core/result/AppResult.kt`) — `HttpException` 401 → `AppError.Unauthorized`, 5xx → `AppError.Server`, `IOException` → `AppError.NoInternet`, everything else → `AppError.Unknown`.
- **Repositories**: `domain/repository/Repositories.kt` declares the interfaces (`AccountsRepository`, `CategoriesRepository`, `TransactionsRepository`); `data/repository/RepositoriesImpl.kt` implements them against `FinanceApi`, converting DTOs via `data/mapper/Mappers.kt`.
- **UI state**: screens render a shared `core/state/UiState<T>` sealed interface (`Loading` / `Content` / `Empty` / `Error`), not raw loading/error booleans. `ui/screens/ListScreen.kt` is the generic list renderer (header total + `LazyColumn`) reused by Expenses/Income (`ui/screens/TransactionListViewModel.kt` backs both) and Accounts.
- **Analytics screen** (`ui/screens/analytics/`) is the one screen built with explicit MVI: `AnalyticsContract.kt` defines `AnalyticsState` / `AnalyticsAction` / `AnalyticsEffect`, `AnalyticsViewModel` reduces actions to state, `AnalyticsScreen`/`AnalyticsSheets`/`DonutChart` are stateless composables driven by that state, and errors surface as a Snackbar effect with retry rather than replacing the content.
- **Navigation** (`ui/FinanceApp.kt`): the `NavHost` has two levels *on purpose*. The outer graph has the tab route and `analytics/{income}`; the Scaffold (top bar, bottom nav, FAB) lives inside the tabs destination rather than wrapping the whole graph, so navigating into Аналитика doesn't strip the bars out from under the outgoing screen mid-transition. Аналитика brings its own Scaffold/top bar and needs nothing from the outer level.

## Working notes

- `transactionDate` from the API is RFC3339 with microseconds and a trailing `Z` (e.g. `2026-07-20T22:44:29.180526Z`) — handle parsing accordingly (see `data/mapper/MapperDateTest.kt`).
- When posting transactions, always send `"comment":null` rather than omitting the `comment` key — omitting it can 500.
