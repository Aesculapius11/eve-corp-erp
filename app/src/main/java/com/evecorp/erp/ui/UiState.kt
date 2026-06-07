package com.evecorp.erp.ui

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val isOffline: Boolean = false) : UiState<Nothing>()
}

val <T> UiState<T>.isLoading: Boolean get() = this is UiState.Loading
val <T> UiState<T>.isError: Boolean get() = this is UiState.Error
val <T> UiState<T>.dataOrNull: T? get() = (this as? UiState.Success)?.data
