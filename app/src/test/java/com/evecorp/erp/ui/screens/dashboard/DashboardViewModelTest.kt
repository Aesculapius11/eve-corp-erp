package com.evecorp.erp.ui.screens.dashboard

import com.evecorp.erp.data.local.entity.BalanceSnapshotEntity
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
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
        assertTrue(state.balanceHistory is UiState.Loading)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `DashboardUiState copy with refreshing`() {
        val state = DashboardUiState(isRefreshing = true)

        assertTrue(state.isRefreshing)
    }

    @Test
    fun `resolveDashboardBalance prefers latest snapshot to keep hero and chart in sync`() {
        val rawBalance = UiState.Success(
            WalletBalanceEntity(
                corporationId = 42L,
                balance = 100.0,
                lastUpdated = 1_000L
            )
        )
        val history = UiState.Success(
            listOf(
                BalanceSnapshotEntity(
                    corporationId = 42L,
                    balance = 100.0,
                    date = "2026-06-14",
                    timestamp = 1_000L
                ),
                BalanceSnapshotEntity(
                    corporationId = 42L,
                    balance = 120.0,
                    date = "2026-06-15",
                    timestamp = 2_000L
                )
            )
        )

        val resolved = resolveDashboardBalance(42L, rawBalance, history)

        assertTrue(resolved is UiState.Success)
        val balance = (resolved as UiState.Success).data
        assertEquals(120.0, balance.balance, 0.0)
        assertEquals(2_000L, balance.lastUpdated)
    }

    @Test
    fun `resolveDashboardBalance falls back to raw balance when history is unavailable`() {
        val rawBalance = UiState.Success(
            WalletBalanceEntity(
                corporationId = 42L,
                balance = 100.0,
                lastUpdated = 1_000L
            )
        )

        val resolved = resolveDashboardBalance(42L, rawBalance, UiState.Loading)

        assertEquals(rawBalance, resolved)
    }
}
