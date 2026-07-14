package ru.shmr.finance.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.shmr.finance.core.state.UiState

abstract class ListScreenViewModel(loader: () -> ListScreenData) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ListScreenData>>(UiState.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            delay(400) // имитация похода за данными
            _state.value = UiState.Content(loader())
        }
    }
}
