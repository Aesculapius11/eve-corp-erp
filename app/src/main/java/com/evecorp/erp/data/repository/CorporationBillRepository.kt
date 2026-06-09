package com.evecorp.erp.data.repository

import com.evecorp.erp.data.local.dao.CorporationBillDao
import com.evecorp.erp.data.local.entity.CorporationBillEntity
import com.evecorp.erp.data.remote.api.EveEsiApi
import com.evecorp.erp.data.remote.dto.CorporationBillDto
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorporationBillRepository @Inject constructor(
    private val corporationBillDao: CorporationBillDao,
    private val esiApi: EveEsiApi
) {
    fun getAll(corpId: Long): Flow<List<CorporationBillEntity>> =
        corporationBillDao.getAll(corpId)

    fun getUnpaid(corpId: Long): Flow<List<CorporationBillEntity>> =
        corporationBillDao.getUnpaid(corpId)

    suspend fun syncBills(corpId: Long): Result<Unit> {
        // NOTE: ESI 没有 bills 端点，此功能已废弃
        return Result.success(Unit)
    }
}

private fun CorporationBillDto.toEntity(corpId: Long) = CorporationBillEntity(
    billId = billId,
    corporationId = corpId,
    billType = billType,
    amount = amount,
    dueDate = Instant.parse(dueDate).toEpochMilli(),
    issuerId = issuerId,
    paid = paid,
    lastUpdated = System.currentTimeMillis()
)
