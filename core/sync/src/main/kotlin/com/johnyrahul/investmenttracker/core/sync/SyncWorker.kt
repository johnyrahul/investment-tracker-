package com.johnyrahul.investmenttracker.core.sync

import android.content.Context
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

/** Implemented by the Application so the worker can reach the app's singletons. */
interface SyncGraph {
    val gmailSync: GmailSync
    val priceRefresher: PriceRefresher
}

/** Background job: fetch new CAS emails, then refresh prices. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val graph = applicationContext as SyncGraph
        graph.gmailSync.sync()
        val prices = graph.priceRefresher.refresh()
        return if (prices is PriceRefresher.Result.Failed && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "daily-sync"
        private const val ONE_OFF = "sync-now"

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Twice a day, so the NAVs AMFI publishes late in the evening show up by the morning. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
