package ru.shmr.finance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.domain.repository.ApiTokenRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.SecurityRepository
import ru.shmr.finance.domain.repository.SettingsRepository

/**
 * Backs the tabs Scaffold: online status, persisted settings, app-lock state, whether an API
 * token is configured, the category cache the settings sheet's article search filters, and the
 * single day Expenses/Income currently filter by.
 */
internal class TabsViewModel(
    val isOnline: StateFlow<Boolean>,
    private val settingsRepository: SettingsRepository,
    securityRepository: SecurityRepository,
    categoriesRepository: CategoriesRepository,
    apiTokenRepository: ApiTokenRepository,
    dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val security: StateFlow<SecurityState> = securityRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecurityState())

    val tokenConfigured: StateFlow<Boolean> = apiTokenRepository.hasToken

    val categoriesState: StateFlow<UiState<List<Category>>> = categoriesRepository
        .observeCategories()
        .map<List<Category>, UiState<List<Category>>> { categories ->
            if (categories.isEmpty()) UiState.Empty else UiState.Content(categories)
        }
        .catch { emit(UiState.Error(AppError.Unknown)) }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _selectedDate = MutableStateFlow(LocalDate.now(clock))

    /** The single day Expenses/Income show; defaults to today and is never in the future. */
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    /** Selects a single day for Expenses/Income to display; dates after today are ignored. */
    fun selectDate(date: LocalDate) {
        if (date.isAfter(LocalDate.now(clock))) return
        _selectedDate.value = date
    }

    fun setCurrency(currency: AppCurrency) {
        viewModelScope.launch { settingsRepository.setCurrency(currency) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }
}
