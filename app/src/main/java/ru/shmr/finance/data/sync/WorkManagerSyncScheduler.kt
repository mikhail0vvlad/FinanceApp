package ru.shmr.finance.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import ru.shmr.finance.di.ServiceLocator

internal class WorkManagerSyncScheduler(
    context: Context,
) : SyncScheduler {

    private val workManager = WorkManager.getInstance(context)
    private val connected = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun enqueueOneTime() {
        val request = OneTimeWorkRequestBuilder<FinanceSyncWorker>()
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            ONE_TIME_SYNC,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<FinanceSyncWorker>(2, TimeUnit.HOURS)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val ONE_TIME_SYNC = "finance-sync-now"
        const val PERIODIC_SYNC = "finance-sync-periodic"
    }
}

internal class FinanceSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (ServiceLocator.syncOrchestrator.syncAll()) {
        SyncOutcome.SUCCESS -> Result.success()
        SyncOutcome.PARTIAL_FAILURE -> Result.failure()
        SyncOutcome.RETRY -> Result.retry()
        SyncOutcome.FAILURE -> Result.failure()
    }
}
