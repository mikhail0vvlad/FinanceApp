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
import ru.shmr.finance.data.security.EncryptedApiTokenRepository
import ru.shmr.finance.data.security.KeystoreCipher
import ru.shmr.finance.data.security.SecurityRepositoryImpl
import ru.shmr.finance.data.settings.DataStoreSettingsRepository
import ru.shmr.finance.data.sync.RemoteSyncGateway
import ru.shmr.finance.data.sync.SyncEngine
import ru.shmr.finance.data.sync.SyncCoordinator
import ru.shmr.finance.data.sync.SyncOutcome
import ru.shmr.finance.data.sync.SyncScheduler
import ru.shmr.finance.data.sync.WorkManagerSyncScheduler
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.ApiTokenRepository
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
            .addInterceptor(AuthInterceptor { apiTokenRepository.currentToken() })
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
    private val syncCoordinator = SyncCoordinator()

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
    lateinit var apiTokenRepository: ApiTokenRepository
        private set

    @Synchronized
    fun initialize(context: Context) {
        if (::local.isInitialized) return
        val appContext = context.applicationContext
        val database = Room.databaseBuilder(
            appContext,
            FinanceDatabase::class.java,
            "finance.db",
        )
            .addMigrations(FinanceDatabase.MIGRATION_1_2)
            .build()
        local = LocalFinanceDataSource(database)
        scheduler = WorkManagerSyncScheduler(appContext)
        apiTokenRepository = EncryptedApiTokenRepository(
            context = appContext,
            initialToken = BuildConfig.API_TOKEN,
            onTokenChanged = scheduler::enqueueOneTime,
        )
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
        if (apiTokenRepository.hasToken.value) scheduler.enqueueOneTime()
    }

    suspend fun syncAll(): SyncOutcome = syncCoordinator.run {
        if (!apiTokenRepository.hasToken.value) return@run SyncOutcome.FAILURE
        val pendingOutcome = syncEngine.sync()
        when (pendingOutcome) {
            SyncOutcome.RETRY, SyncOutcome.FAILURE -> return@run pendingOutcome
            SyncOutcome.SUCCESS, SyncOutcome.PARTIAL_FAILURE -> Unit
        }

        accountsRepository.refreshAccounts().syncFailureOrNull()?.let { return@run it }
        categoriesRepository.refreshCategories().syncFailureOrNull()?.let { return@run it }
        val accounts = local.getAccounts()
        val end = LocalDate.now().plusDays(1)
        accounts.filter { it.id > 0 }.forEach { account ->
            val start = local.transactionSyncStartDate(account.id)
            transactionsRepository.refreshTransactionsForPeriod(
                accountId = account.id,
                startDate = start,
                endDate = end,
            ).syncFailureOrNull()?.let { return@run it }
            local.markTransactionsSyncedThrough(account.id, end)
        }
        pendingOutcome
    }
}

private fun AppResult<Unit>.syncFailureOrNull(): SyncOutcome? = when (this) {
    is AppResult.Success -> null
    is AppResult.Failure -> when (error) {
        AppError.NoInternet, is AppError.Server -> SyncOutcome.RETRY
        AppError.Unauthorized,
        is AppError.Client,
        is AppError.Validation,
        AppError.Storage,
        AppError.Unknown,
        -> SyncOutcome.FAILURE
    }
}
