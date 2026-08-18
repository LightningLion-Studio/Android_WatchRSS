package com.lightningstudio.watchrss.data.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lightningstudio.watchrss.BuildConfig
import com.lightningstudio.watchrss.WatchRssApplication
import java.util.concurrent.TimeUnit

class WatchCloudSyncWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.DEBUG) return Result.success()
        val app = applicationContext as WatchRssApplication
        app.accountStore.read() ?: return Result.success()
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
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private const val WORK_NAME = "watchrss-watch-cloud-sync-6h"
    }
}
