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
        assertEquals("研究", IndustryTab.RESEARCH.label)
    }

    @Test
    fun `IndustryTab enum has correct activity values`() {
        assertNull(IndustryTab.ALL.activities)
        assertEquals(listOf("manufacturing"), IndustryTab.MANUFACTURING.activities)
        assertEquals(listOf("invention"), IndustryTab.INVENTION.activities)
        assertEquals(
            listOf("researching_time_efficiency", "researching_material_efficiency"),
            IndustryTab.RESEARCH.activities
        )
    }

    @Test
    fun `IndustryUiState default values`() {
        val state = IndustryUiState()

        assertTrue(state.jobs is UiState.Loading)
        assertEquals(IndustryTab.ALL, state.selectedTab)
        assertFalse(state.isRefreshing)
    }
}
