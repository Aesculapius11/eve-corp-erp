package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.WalletBalanceDao
import com.evecorp.erp.data.local.dao.WalletJournalDao
import com.evecorp.erp.data.local.entity.WalletBalanceEntity
import com.evecorp.erp.data.local.entity.WalletJournalEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.WalletBalanceDto
import com.evecorp.erp.data.remote.dto.WalletJournalDto
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class WalletRepositoryTest {

    private lateinit var walletBalanceDao: WalletBalanceDao
    private lateinit var walletJournalDao: WalletJournalDao
    private lateinit var esiApi: EveEsiApi
    private lateinit var repository: WalletRepository

    @Before
    fun setup() {
        walletBalanceDao = mockk(relaxed = true)
        walletJournalDao = mockk(relaxed = true)
        esiApi = mockk(relaxed = true)
        repository = WalletRepository(walletBalanceDao, walletJournalDao, esiApi)
    }

    @Test
    fun `syncBalance stores total balance from all divisions`() = runTest {
        val wallets = listOf(
            WalletBalanceDto(balance = 1000.0, division = 1),
            WalletBalanceDto(balance = 2000.0, division = 2)
        )
        coEvery { esiApi.getWallets(123L) } returns Response.success(wallets)

        val result = repository.syncBalance(123L)

        assertTrue(result.isSuccess)
        coVerify {
            walletBalanceDao.upsert(match {
                it.corporationId == 123L && it.balance == 3000.0
            })
        }
    }

    @Test
    fun `syncBalance returns failure on API error`() = runTest {
        coEvery { esiApi.getWallets(123L) } returns Response.error(403, okhttp3.ResponseBody.create(null, ""))

        val result = repository.syncBalance(123L)

        assertTrue(result.isFailure)
    }

    @Test
    fun `syncJournal inserts only new entries`() = runTest {
        coEvery { walletJournalDao.getMaxId(123L) } returns 100L
        // Division 1 有两条记录，id=99 应被过滤
        val entries = listOf(
            WalletJournalDto(id = 99, date = "2026-01-01T00:00:00Z", refType = "tax", amount = -10.0),
            WalletJournalDto(id = 101, date = "2026-01-02T00:00:00Z", refType = "bounty", amount = 100.0)
        )
        // 只 mock division 1，其余 division 返回 404 跳过
        coEvery { esiApi.getWalletJournal(123L, 1, any()) } returns Response.success(entries)
        for (div in 2..7) {
            coEvery { esiApi.getWalletJournal(123L, div, any()) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))
        }

        val result = repository.syncJournal(123L)

        assertTrue(result.isSuccess)
        coVerify {
            walletJournalDao.insertAll(match { it.size == 1 && it[0].id == 101L })
        }
    }

    @Test
    fun `syncJournal skips divisions on 403`() = runTest {
        coEvery { walletJournalDao.getMaxId(123L) } returns null
        coEvery { esiApi.getWalletJournal(123L, any(), any()) } returns Response.error(403, okhttp3.ResponseBody.create(null, ""))

        val result = repository.syncJournal(123L)

        // 403 = 无权限，跳过该 division，整体仍成功
        assertTrue(result.isSuccess)
    }

    @Test
    fun `getBalance returns flow from dao`() {
        val entity = WalletBalanceEntity(123L, 5000.0, System.currentTimeMillis())
        every { walletBalanceDao.getBalance(123L) } returns flowOf(entity)

        val flow = repository.getBalance(123L)

        assertNotNull(flow)
    }
}
