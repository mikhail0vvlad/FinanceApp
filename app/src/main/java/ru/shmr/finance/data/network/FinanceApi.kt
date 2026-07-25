package ru.shmr.finance.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import ru.shmr.finance.data.network.dto.AccountDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

interface FinanceApi {

    @GET("accounts")
    suspend fun getAccounts(): List<AccountDto>

    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("transactions/account/{accountId}/period")
    suspend fun getTransactionsForPeriod(
        @Path("accountId") accountId: Int,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): List<TransactionResponseDto>
}
