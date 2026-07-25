package ru.shmr.finance.di

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.shmr.finance.BuildConfig
import ru.shmr.finance.data.network.AuthInterceptor
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.RetryInterceptor
import ru.shmr.finance.data.repository.AccountsRepositoryImpl
import ru.shmr.finance.data.repository.CategoriesRepositoryImpl
import ru.shmr.finance.data.repository.TransactionsRepositoryImpl
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

object ServiceLocator {

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { BuildConfig.API_TOKEN })
            .addInterceptor(RetryInterceptor())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC),
                    )
                }
            }
            .build()
    }

    private val api: FinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FinanceApi::class.java)
    }

    val accountsRepository: AccountsRepository by lazy { AccountsRepositoryImpl(api) }
    val categoriesRepository: CategoriesRepository by lazy { CategoriesRepositoryImpl(api) }
    val transactionsRepository: TransactionsRepository by lazy { TransactionsRepositoryImpl(api) }
}
