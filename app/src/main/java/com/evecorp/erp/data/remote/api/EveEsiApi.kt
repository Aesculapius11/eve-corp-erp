package com.evecorp.erp.data.remote.api

import com.evecorp.erp.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface EveEsiApi {

    // --- Wallet ---
    @GET("corporations/{corporation_id}/wallets/")
    suspend fun getWallets(
        @Path("corporation_id") corporationId: Long
    ): Response<List<WalletBalanceDto>>

    @GET("corporations/{corporation_id}/wallets/{division}/journal/")
    suspend fun getWalletJournal(
        @Path("corporation_id") corporationId: Long,
        @Path("division") division: Int,
        @Query("page") page: Int = 1
    ): Response<List<WalletJournalDto>>

    // --- Industry ---
    @GET("industry/systems/")
    suspend fun getIndustrySystems(): Response<List<IndustrySystemDto>>

    @GET("corporations/{corporation_id}/industry/jobs/")
    suspend fun getIndustryJobs(
        @Path("corporation_id") corporationId: Long,
        @Query("include_completed") includeCompleted: Boolean = false,
        @Query("page") page: Int = 1
    ): Response<List<IndustryJobDto>>

    // --- Market ---
    @GET("corporations/{corporation_id}/orders/")
    suspend fun getMarketOrders(
        @Path("corporation_id") corporationId: Long,
        @Query("page") page: Int = 1
    ): Response<List<MarketOrderDto>>

    @GET("characters/{character_id}/orders/")
    suspend fun getCharacterOrders(
        @Path("character_id") characterId: Long
    ): Response<List<MarketOrderDto>>

    // --- Corporation ---
    @GET("corporations/{corporation_id}/divisions/")
    suspend fun getDivisions(
        @Path("corporation_id") corporationId: Long
    ): Response<CorporationDivisionsResponse>

    @GET("corporations/{corporation_id}/assets/")
    suspend fun getAssets(
        @Path("corporation_id") corporationId: Long,
        @Query("page") page: Int = 1
    ): Response<List<HangarItemDto>>

    // --- Universe ---
    @POST("universe/names/")
    suspend fun postUniverseNames(
        @Body typeIds: List<Long>
    ): Response<List<UniverseNameDto>>

    @POST("universe/ids/")
    suspend fun searchUniverseIds(
        @Body names: List<String>
    ): Response<List<UniverseNameDto>>

    // --- Character roles ---
    @GET("characters/{character_id}/roles/")
    suspend fun getCharacterRoles(
        @Path("character_id") characterId: Long
    ): Response<CharacterRolesDto>

    // --- Corporation info ---
    @GET("corporations/{corporation_id}/")
    suspend fun getCorporation(
        @Path("corporation_id") corporationId: Long
    ): Response<CorporationInfoDto>

    // --- Alliance info ---
    @GET("alliances/{alliance_id}/")
    suspend fun getAlliance(
        @Path("alliance_id") allianceId: Long
    ): Response<AllianceInfoDto>

    // --- Character info ---
    @GET("characters/{character_id}/")
    suspend fun getCharacter(
        @Path("character_id") characterId: Long
    ): Response<CharacterInfoDto>
}


