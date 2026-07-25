package ru.shmr.finance.di

import android.content.Context
import androidx.room.Room
import java.time.LocalDate
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.shmr.finance.BuildConfig
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.data.local.FinanceDatabase
import ru.shmr.finance.data.local.LocalFinanceDataSource
import ru.shmr.finance.data.network.AuthInterceptor
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.NetworkMonitor
import ru.shmr.finance.data.network.RetryInterceptor
import ru.shmr.finance.data.repository.AccountsRepositoryImpl
import ru.shmr.finance.data.repository.CategoriesRepositoryImpl
import ru.shmr.finance.data.repository.TransactionsRepositoryImpl
import ru.shmr.finance.data.security.DataStorePinCredentialStorage
import ru.shmr.finance.data.security.KeystoreCipher
import ru.shmr.finance.data.security.SecurityRepositoryImpl
import ru.shmr.finance.data.settings.DataStoreSettingsRepository
import ru.shmr.finance.data.sync.RemoteSyncGateway
import ru.shmr.finance.data.sync.SyncEngine
import ru.shmr.finance.data.sync.SyncOutcome
import ru.shmr.finance.data.sync.SyncScheduler
import ru.shmr.finance.data.sync.WorkManagerSyncScheduler
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.repository.SecurityRepository
import ru.shmr.finance.domain.repository.SettingsRepository
import ru.shmr.finance.domain.model.AppError

object ServiceLocator {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }

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

    private lateinit var local: LocalFinanceDataSource
    private lateinit var scheduler: SyncScheduler
    private lateinit var syncEngine: SyncEngine

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var accountsRepository: AccountsRepository
        private set
    lateinit var categoriesRepository: CategoriesRepository
        private set
    lateinit var transactionsRepository: TransactionsRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var securityRepository: SecurityRepository
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (::local.isInitialized) return
        val appContext = context.applicationContext
        val database = Room.databaseBuilder(
            appContext,
            FinanceDatabase::class.java,
            "finance.db",
        ).build()
        local = LocalFinanceDataSource(database)
        scheduler = WorkManagerSyncScheduler(appContext)
        syncEngine = SyncEngine(local, RemoteSyncGateway(api))
        accountsRepository = AccountsRepositoryImpl(api, local, scheduler)
        categoriesRepository = CategoriesRepositoryImpl(api, local)
        transactionsRepository = TransactionsRepositoryImpl(api, local, scheduler)
        settingsRepository = DataStoreSettingsRepository(appContext)
        securityRepository = SecurityRepositoryImpl(
            credentialStorage = DataStorePinCredentialStorage(appContext),
            cipher = KeystoreCipher(),
            settingsRepository = settingsRepository,
        )
        networkMonitor = NetworkMonitor(appContext, scheduler).also { it.start() }
        scheduler.ensurePeriodic()
        scheduler.enqueueOneTime()
    }

    suspend fun syncAll(): SyncOutcome {
        val pendingOutcome = syncEngine.sync()
        if (pendingOutcome != SyncOutcome.SUCCESS) return pendingOutcome

        accountsRepository.refreshAccounts().syncFailureOrNull()?.let { return it }
        categoriesRepository.refreshCategories().syncFailureOrNull()?.let { return it }
        val accounts = local.getAccounts()
        val start = LocalDate.of(1970, 1, 1)
        val end = LocalDate.now().plusDays(1)
        accounts.filter { it.id > 0 }.forEach { account ->
            transactionsRepository.refreshTransactionsForPeriod(
                accountId = account.id,
                startDate = start,
                endDate = end,
            ).syncFailureOrNull()?.let { return it }
        }
        return SyncOutcome.SUCCESS
    }
}

private fun AppResult<Unit>.syncFailureOrNull(): SyncOutcome? = when (this) {
    is AppResult.Success -> null
    is AppResult.Failure -> when (error) {
        AppError.NoInternet, is AppError.Server -> SyncOutcome.RETRY
        AppError.Unauthorized, AppError.Unknown -> SyncOutcome.FAILURE
    }
}
