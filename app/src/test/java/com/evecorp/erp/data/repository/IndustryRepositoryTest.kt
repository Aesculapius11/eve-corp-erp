package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.IndustryJobDao
import com.evecorp.erp.data.local.dao.SystemCostIndexDao
import com.evecorp.erp.data.local.entity.SystemCostIndexEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.CostIndexDto
import com.evecorp.erp.data.remote.dto.IndustrySystemDto
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class IndustryRepositoryTest {

    private lateinit var systemCostIndexDao: SystemCostIndexDao
    private lateinit var industryJobDao: IndustryJobDao
    private lateinit var esiApi: EveEsiApi
    private lateinit var repository: IndustryRepository

    @Before
    fun setup() {
        systemCostIndexDao = mockk(relaxed = true)
        industryJobDao = mockk(relaxed = true)
        esiApi = mockk(relaxed = true)
        repository = IndustryRepository(systemCostIndexDao, industryJobDao, esiApi)
    }

    @Test
    fun `syncCostIndices filters by interesting system ids`() = runTest {
        val systems = listOf(
            IndustrySystemDto(
                solarSystemId = 30001424,
                costIndices = listOf(
                    CostIndexDto("manufacturing", 0.032),
                    CostIndexDto("invention", 0.021)
                )
            ),
            IndustrySystemDto(
                solarSystemId = 30000142,
                costIndices = listOf(
                    CostIndexDto("manufacturing", 0.05)
                )
            )
        )
        coEvery { esiApi.getIndustrySystems() } returns Response.success(systems)

        val result = repository.syncCostIndices(listOf(30001424L))

        assertTrue(result.isSuccess)
        coVerify {
            systemCostIndexDao.deleteAll()
            systemCostIndexDao.insertAll(match { it.size == 1 && it[0].systemId == 30001424L })
        }
    }

    @Test
    fun `syncCostIndices stores all when no filter`() = runTest {
        val systems = listOf(
            IndustrySystemDto(30001424, listOf(CostIndexDto("manufacturing", 0.03))),
            IndustrySystemDto(30000142, listOf(CostIndexDto("manufacturing", 0.05)))
        )
        coEvery { esiApi.getIndustrySystems() } returns Response.success(systems)

        val result = repository.syncCostIndices()

        assertTrue(result.isSuccess)
        coVerify {
            systemCostIndexDao.insertAll(match { it.size == 2 })
        }
    }

    @Test
    fun `getActiveJobs returns flow from dao`() {
        every { industryJobDao.getActiveJobs(123L) } returns flowOf(emptyList())

        val flow = repository.getActiveJobs(123L)

        assertNotNull(flow)
    }
}
