package ru.shmr.finance.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import ru.shmr.finance.data.network.dto.AccountDto
import ru.shmr.finance.data.network.dto.AccountCreateRequestDto
import ru.shmr.finance.data.network.dto.AccountUpdateRequestDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionDto
import ru.shmr.finance.data.network.dto.TransactionRequestDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

interface FinanceApi {

    @GET("accounts")
    suspend fun getAccounts(): List<AccountDto>

    @POST("accounts")
    suspend fun createAccount(@Body request: AccountCreateRequestDto): AccountDto

    @PUT("accounts/{id}")
    suspend fun updateAccount(
        @Path("id") id: Int,
        @Body request: AccountUpdateRequestDto,
    ): AccountDto

    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("transactions/account/{accountId}/period")
    suspend fun getTransactionsForPeriod(
        @Path("accountId") accountId: Int,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String,
    ): List<TransactionResponseDto>

    @POST("transactions")
    suspend fun createTransaction(@Body request: TransactionRequestDto): TransactionDto

    @PUT("transactions/{id}")
    suspend fun updateTransaction(
        @Path("id") id: Int,
        @Body request: TransactionRequestDto,
    ): TransactionResponseDto
}
