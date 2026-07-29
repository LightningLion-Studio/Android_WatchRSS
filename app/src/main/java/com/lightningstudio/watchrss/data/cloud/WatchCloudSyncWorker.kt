package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lightningstudio.watchrss.WatchRssApplication
import java.util.concurrent.TimeUnit

class WatchCloudSyncWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as WatchRssApplication
        val account = app.accountStore.read() ?: return Result.success()
        if (account.entitlement.plan != "member" || !account.entitlement.active) {
            return Result.success()
        }
        return if (app.cloudSyncService.syncNow()) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<WatchCloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "watchrss-watch-cloud-sync-6h",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }
    }
}
