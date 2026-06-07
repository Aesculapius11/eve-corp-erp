package com.evecorp.erp.ui.screens.industry

import com.evecorp.erp.ui.UiState
import org.junit.Assert.*
import org.junit.Test

class IndustryViewModelTest {

    @Test
    fun `IndustryTab enum has correct labels`() {
        assertEquals("全部", IndustryTab.ALL.label)
        assertEquals("制造", IndustryTab.MANUFACTURING.label)
        assertEquals("发明", IndustryTab.INVENTION.label)
        assertEquals("拷贝", IndustryTab.COPYING.label)
    }

    @Test
    fun `IndustryTab enum has correct activity values`() {
        assertNull(IndustryTab.ALL.activity)
        assertEquals("manufacturing", IndustryTab.MANUFACTURING.activity)
        assertEquals("invention", IndustryTab.INVENTION.activity)
        assertEquals("copying", IndustryTab.COPYING.activity)
    }

    @Test
    fun `IndustryUiState default values`() {
        val state = IndustryUiState()

        assertTrue(state.jobs is UiState.Loading)
        assertEquals(IndustryTab.ALL, state.selectedTab)
        assertFalse(state.isRefreshing)
    }
}
