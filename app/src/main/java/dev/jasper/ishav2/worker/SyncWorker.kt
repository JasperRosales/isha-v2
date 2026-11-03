package dev.jasper.ishav2.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.jasper.ishav2.IshaApp
import java.util.concurrent.TimeUnit

/**
 * Every couple of hours while online: refresh Classroom deadlines and the
 * Gmail digest, so both are on the phone for when it goes offline.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? IshaApp)?.container ?: return Result.success()
        if (!container.settings.current.hasComposioKey) return Result.success()
        // Failures are saved on the stores and shown on their screens.
        container.sync.syncDeadlines()
        container.sync.syncMail()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "isha_school_mail_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
