package ru.shmr.finance.di

import android.content.Context
import androidx.room.Room
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import ru.shmr.finance.BuildConfig
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
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
import ru.shmr.finance.data.sync.SyncOrchestrator
import ru.shmr.finance.data.sync.SyncScheduler
import ru.shmr.finance.data.sync.WorkManagerSyncScheduler
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.ApiTokenRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.repository.SecurityRepository
import ru.shmr.finance.domain.repository.SettingsRepository

/**
 * Single composition root for the app: builds and hands out every dependency (network, Room,
 * repositories, settings, security, sync). Owns no business logic itself — that lives in the
 * classes it constructs, such as [SyncOrchestrator].
 */
object ServiceLocator {

    val dispatchers: DispatcherProvider = DefaultDispatcherProvider

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

    internal lateinit var networkMonitor: NetworkMonitor
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
    internal lateinit var syncOrchestrator: SyncOrchestrator
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
        local = LocalFinanceDataSource(database, dispatchers)
        scheduler = WorkManagerSyncScheduler(appContext)
        apiTokenRepository = EncryptedApiTokenRepository(
            context = appContext,
            initialToken = BuildConfig.API_TOKEN,
            onTokenChanged = scheduler::enqueueOneTime,
            dispatchers = dispatchers,
        )
        val syncEngine = SyncEngine(local, RemoteSyncGateway(api, dispatchers))
        accountsRepository = AccountsRepositoryImpl(api, local, scheduler, dispatchers)
        categoriesRepository = CategoriesRepositoryImpl(api, local, dispatchers)
        transactionsRepository = TransactionsRepositoryImpl(api, local, scheduler, dispatchers)
        settingsRepository = DataStoreSettingsRepository(appContext)
        securityRepository = SecurityRepositoryImpl(
            credentialStorage = DataStorePinCredentialStorage(appContext),
            cipher = KeystoreCipher(),
            settingsRepository = settingsRepository,
            dispatchers = dispatchers,
        )
        syncOrchestrator = SyncOrchestrator(
            local = local,
            syncEngine = syncEngine,
            apiTokenRepository = apiTokenRepository,
            accountsRepository = accountsRepository,
            categoriesRepository = categoriesRepository,
            transactionsRepository = transactionsRepository,
        )
        networkMonitor = NetworkMonitor(appContext, scheduler).also { it.start() }
        scheduler.ensurePeriodic()
        if (apiTokenRepository.hasToken.value) scheduler.enqueueOneTime()
    }
}
