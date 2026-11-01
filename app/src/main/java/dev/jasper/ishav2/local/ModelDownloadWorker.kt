package dev.jasper.ishav2.local

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.jasper.ishav2.IshaApp
import dev.jasper.ishav2.util.Notifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads a model bundle as a foreground job, so a multi-gigabyte file keeps
 * coming down while the user leaves the app. Resumes from a `.part` file.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val container = (appContext as IshaApp).container
    private val models = container.models
    private val notifier = container.notifier

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val spec = LocalModelCatalog.find(id) ?: return Result.failure()

        return try {
            // Best effort: Android 12+ refuses foreground promotion when a retry
            // starts while the app is in the background; the download still runs.
            runCatching { setForeground(foregroundInfo(spec.name, 0, spec.sizeBytes)) }
            download(spec)
            models.reportFinished(id, null)
            notifier.postDownloadResult(spec.name, ok = true, message = "Tap to choose it as isha's offline brain.")
            Result.success()
        } catch (cancel: CancellationException) {
            models.reportFinished(id, "Cancelled.")
            throw cancel
        } catch (error: Exception) {
            val message = when {
                error is HttpError && (error.code == 401 || error.code == 403) ->
                    "Hugging Face refused the download. Accept the model's license on huggingface.co and check your token."
                error is HttpError -> "Server returned ${error.code}."
                else -> error.message ?: "Download failed."
            }
            val retry = error is IOException && error !is HttpError && runAttemptCount < 3
            if (retry) {
                // Network blip: keep showing progress; WorkManager resumes from the part file.
                val part = models.partFile(spec)
                models.reportProgress(id, part.length(), spec.sizeBytes)
                return Result.retry()
            }
            models.reportFinished(id, message)
            notifier.postDownloadResult(spec.name, ok = false, message = message)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val spec = LocalModelCatalog.find(inputData.getString(KEY_MODEL_ID))
        return foregroundInfo(spec?.name ?: "model", 0, spec?.sizeBytes ?: 0)
    }

    private suspend fun download(spec: LocalModelSpec) = withContext(Dispatchers.IO) {
        val part = models.partFile(spec)
        val target = models.finalFile(spec)
        var existing = if (part.exists()) part.length() else 0L

        val request = Request.Builder()
            .url(spec.url)
            .apply {
                val token = container.settings.current.huggingFaceToken
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
                if (existing > 0) header("Range", "bytes=$existing-")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 416) {
                // The part file is already complete.
                existing = part.length()
            } else if (!response.isSuccessful) {
                throw HttpError(response.code)
            }
            val append = response.code == 206
            if (!append) existing = 0
            val body = response.body ?: throw IOException("Empty response.")
            val total = if (response.code == 416) existing
            else (body.contentLength().takeIf { it > 0 }?.plus(existing) ?: spec.sizeBytes)

            if (response.code != 416) {
                FileOutputStream(part, append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(1 shl 16)
                        var done = existing
                        var lastReport = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > REPORT_EVERY_MS) {
                                lastReport = now
                                models.reportProgress(spec.id, done, total)
                                runCatching { setForeground(foregroundInfo(spec.name, done, total)) }
                            }
                        }
                    }
                }
            }
            if (part.length() < total) throw IOException("Download was cut short. It will resume.")
        }
        target.delete()
        if (!part.renameTo(target)) throw IOException("Could not finish saving the model.")
    }

    private fun foregroundInfo(name: String, done: Long, total: Long): ForegroundInfo {
        val notification = notifier.downloadNotification(name, done, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(Notifier.DOWNLOAD_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Notifier.DOWNLOAD_NOTIF_ID, notification)
        }
    }

    private class HttpError(val code: Int) : IOException("HTTP $code")

    companion object {
        const val KEY_MODEL_ID = "model_id"
        private const val REPORT_EVERY_MS = 500L

        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
