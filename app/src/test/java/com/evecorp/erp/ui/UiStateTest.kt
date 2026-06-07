package com.evecorp.erp.ui

import org.junit.Assert.*
import org.junit.Test

class UiStateTest {

    @Test
    fun `Loading state properties`() {
        val state = UiState.Loading

        assertTrue(state.isLoading)
        assertFalse(state.isError)
        assertNull(state.dataOrNull)
    }

    @Test
    fun `Success state properties`() {
        val state = UiState.Success("test")

        assertFalse(state.isLoading)
        assertFalse(state.isError)
        assertEquals("test", state.dataOrNull)
    }

    @Test
    fun `Error state properties`() {
        val state = UiState.Error("fail", isOffline = true)

        assertFalse(state.isLoading)
        assertTrue(state.isError)
        assertNull(state.dataOrNull)
        assertTrue(state.isOffline)
    }

    @Test
    fun `Error state default isOffline is false`() {
        val state = UiState.Error("fail")

        assertFalse(state.isOffline)
    }
}
