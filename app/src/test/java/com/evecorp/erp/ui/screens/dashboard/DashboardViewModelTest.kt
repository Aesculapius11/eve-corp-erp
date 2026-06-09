package com.evecorp.erp.ui.screens.dashboard

import com.evecorp.erp.ui.UiState
import org.junit.Assert.*
import org.junit.Test

class DashboardViewModelTest {

    @Test
    fun `DashboardUiState default values`() {
        val state = DashboardUiState()

        assertTrue(state.balance is UiState.Loading)
        assertTrue(state.journal is UiState.Loading)
        assertTrue(state.costIndex is UiState.Loading)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `DashboardUiState copy with refreshing`() {
        val state = DashboardUiState(isRefreshing = true)

        assertTrue(state.isRefreshing)
    }
}
