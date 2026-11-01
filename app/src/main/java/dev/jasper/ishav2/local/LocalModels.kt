package dev.jasper.ishav2.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jasper.ishav2.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An on-device model isha can run with no network. All entries are MediaPipe
 * `.task` bundles from Hugging Face's litert-community, picked for everyday
 * assistant work (drafting, planning, summarising), not for code.
 */
data class LocalModelSpec(
    val id: String,
    val name: String,
    val tag: String,
    val blurb: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Total context (prompt + reply) the bundle was compiled for. */
    val maxTokens: Int,
    val minRamGb: Int,
    /** Gated on Hugging Face: needs a token from an account that accepted the license. */
    val needsToken: Boolean = false
)

object LocalModelCatalog {
    private const val HF = "https://huggingface.co/litert-community"

    val models = listOf(
        LocalModelSpec(
            id = "qwen2.5-1.5b",
            name = "Qwen 2.5 · 1.5B",
            tag = "Recommended",
            blurb = "The best all-rounder offline: drafts messages, plans your day, summarises notes. Multilingual.",
            url = "$HF/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            fileName = "qwen2.5-1.5b-instruct-q8-ekv4096.task",
            sizeBytes = 1_598_556_720L,
            maxTokens = 4096,
            minRamGb = 6
        ),
        LocalModelSpec(
            id = "gemma3-1b",
            name = "Gemma 3 · 1B",
            tag = "Google",
            blurb = "Google's compact chat model. Warm, quick and light on battery. Needs a free Hugging Face token.",
            url = "$HF/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            fileName = "gemma3-1b-it-q4-ekv2048.task",
            sizeBytes = 554_661_246L,
            maxTokens = 2048,
            minRamGb = 4,
            needsToken = true
        ),
        LocalModelSpec(
            id = "qwen2.5-0.5b",
            name = "Qwen 2.5 · 0.5B",
            tag = "Lite",
            blurb = "Tiny and fast. Good for quick answers and short rewrites on older phones.",
            url = "$HF/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            fileName = "qwen2.5-0.5b-instruct-q8-ekv1280.task",
            sizeBytes = 546_660_344L,
            maxTokens = 1280,
            minRamGb = 3
        ),
        LocalModelSpec(
            id = "phi4-mini",
            name = "Phi-4 mini · 3.8B",
            tag = "Smartest",
            blurb = "Microsoft's strongest small model. Better reasoning for planning and decisions, but large and slower.",
            url = "$HF/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task",
            fileName = "phi-4-mini-instruct-q8-ekv4096.task",
            sizeBytes = 3_910_050_199L,
            maxTokens = 4096,
            minRamGb = 8
        )
    )

    fun find(id: String?): LocalModelSpec? = models.firstOrNull { it.id == id }
}

/** A model file present on the phone: a catalog download or a user import. */
data class InstalledModel(
    val id: String,
    val name: String,
    val file: File,
    val maxTokens: Int
)

data class DownloadState(
    val downloading: Boolean = false,
    val bytes: Long = 0,
    val total: Long = 0,
    val error: String? = null
) {
    val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class LocalModelManager(
    private val context: Context,
    private val settings: SettingsStore
) {
    val dir: File = File(context.filesDir, "models").apply { mkdirs() }

    private val _installed = MutableStateFlow(scan())
    val installed: StateFlow<List<InstalledModel>> = _installed.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads.asStateFlow()

    fun active(): InstalledModel? {
        val id = settings.current.localModelId ?: return null
        return _installed.value.firstOrNull { it.id == id }
    }

    fun select(id: String?) {
        settings.setLocalModel(id)
    }

    fun download(id: String) {
        val spec = LocalModelCatalog.find(id) ?: return
        if (spec.needsToken && settings.current.huggingFaceToken.isBlank()) {
            _downloads.update { it + (id to DownloadState(error = "Add a Hugging Face token first.")) }
            return
        }
        _downloads.update { it + (id to DownloadState(downloading = true, total = spec.sizeBytes)) }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        _downloads.update { it - id }
    }

    fun delete(id: String) {
        val model = _installed.value.firstOrNull { it.id == id }
        model?.file?.delete()
        LocalModelCatalog.find(id)?.let { partFile(it).delete() }
        if (settings.current.localModelId == id) settings.setLocalModel(null)
        refresh()
    }

    /** Copies a user-picked `.task` file into app storage and makes it available. */
    suspend fun import(uri: Uri): InstalledModel = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "imported.task"
        require(displayName.endsWith(".task", ignoreCase = true)) {
            "Pick a MediaPipe .task model file."
        }
        val safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, "$IMPORT_PREFIX$safe")
        val temp = File(dir, "${target.name}.part")
        resolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        } ?: throw IllegalStateException("Could not open that file.")
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Could not save the model.")
        }
        refresh()
        _installed.value.first { it.file == target }
    }

    fun refresh() {
        _installed.value = scan()
    }

    // -- called by ModelDownloadWorker --------------------------------------

    internal fun partFile(spec: LocalModelSpec) = File(dir, "${spec.fileName}.part")

    internal fun finalFile(spec: LocalModelSpec) = File(dir, spec.fileName)

    internal fun reportProgress(id: String, bytes: Long, total: Long) {
        _downloads.update { it + (id to DownloadState(downloading = true, bytes = bytes, total = total)) }
    }

    internal fun reportFinished(id: String, error: String?) {
        _downloads.update {
            if (error == null) it - id else it + (id to DownloadState(error = error))
        }
        refresh()
        // First model on the phone: use it without making the user pick it.
        if (error == null && settings.current.localModelId == null) select(id)
    }

    private fun scan(): List<InstalledModel> {
        val files = dir.listFiles { file -> file.isFile && file.name.endsWith(".task") }.orEmpty()
        return files.mapNotNull { file ->
            val spec = LocalModelCatalog.models.firstOrNull { it.fileName == file.name }
            when {
                spec != null -> InstalledModel(spec.id, spec.name, file, spec.maxTokens)
                file.name.startsWith(IMPORT_PREFIX) -> InstalledModel(
                    id = "file:${file.name}",
                    name = file.name.removePrefix(IMPORT_PREFIX).removeSuffix(".task"),
                    file = file,
                    maxTokens = IMPORTED_MAX_TOKENS
                )
                else -> null
            }
        }.sortedBy { it.name }
    }

    private fun workName(id: String) = "model-download-$id"

    private companion object {
        const val IMPORT_PREFIX = "import-"
        const val IMPORTED_MAX_TOKENS = 1280
    }
}
