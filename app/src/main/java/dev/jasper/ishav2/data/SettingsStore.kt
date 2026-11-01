package dev.jasper.ishav2.data

import android.content.Context
import dev.jasper.ishav2.util.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ApprovalMode { MANUAL, AUTO }

enum class ComposioKeyMode { NONE, CONNECT, PROJECT, UNKNOWN }

/**
 * Where replies come from. AUTO uses Gemini while the phone is online and
 * falls back to the downloaded on-device model when it is not.
 */
enum class BrainMode { AUTO, ONLINE, OFFLINE }

data class AppSettings(
    val geminiKey: String = "",
    val composioKey: String = "",
    val model: String = DEFAULT_MODEL,
    val approvalMode: ApprovalMode = ApprovalMode.MANUAL,
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val userId: String = "",
    val lastConversationId: String? = null,
    val composioSessionId: String? = null,
    val onboarded: Boolean = false,
    val darkTheme: Boolean = true,
    val gmailPollingEnabled: Boolean = false,
    val gmailLastCheckedAt: Long = 0L,
    val gmailLastMessageId: String? = null,
    val classroomPollingEnabled: Boolean = false,
    val classroomLastCheckedAt: Long = 0L,
    val classroomLastActivityId: String? = null,
    val brainMode: BrainMode = BrainMode.AUTO,
    val localModelId: String? = null,
    val huggingFaceToken: String = "",
    val autoApproveReads: Boolean = true,
    val briefingEnabled: Boolean = false,
    val briefingHour: Int = 7,
    val briefingMinute: Int = 30
) {
    val hasGeminiKey: Boolean get() = geminiKey.isNotBlank()
    val hasLocalModel: Boolean get() = !localModelId.isNullOrBlank()

    /** True when the app can answer at all: a Gemini key or an on-device model. */
    val canChat: Boolean get() = hasGeminiKey || hasLocalModel
    val hasComposioKey: Boolean get() = composioKey.isNotBlank()

    val composioMode: ComposioKeyMode
        get() = when {
            composioKey.isBlank() -> ComposioKeyMode.NONE
            composioKey.startsWith("ck_") -> ComposioKeyMode.CONNECT
            composioKey.startsWith("ak_") -> ComposioKeyMode.PROJECT
            else -> ComposioKeyMode.UNKNOWN
        }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_MAX_STEPS = 20
        val MODEL_SUGGESTIONS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )
    }
}

class SettingsStore(context: Context) {
    private val secure = SecurePrefs(context)
    private val prefs = context.getSharedPreferences("isha_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    fun saveGeminiKey(value: String) {
        secure.put(KEY_GEMINI, value.trim())
        _settings.value = current.copy(geminiKey = value.trim())
    }

    fun saveComposioKey(value: String) {
        secure.put(KEY_COMPOSIO, value.trim())
        _settings.value = current.copy(composioKey = value.trim())
    }

    fun setModel(value: String) {
        val model = value.trim().ifBlank { AppSettings.DEFAULT_MODEL }
        prefs.edit().putString(KEY_MODEL, model).apply()
        _settings.value = current.copy(model = model)
    }

    fun setApprovalMode(mode: ApprovalMode) {
        prefs.edit().putString(KEY_APPROVAL, mode.name).apply()
        _settings.value = current.copy(approvalMode = mode)
    }

    fun setMaxSteps(steps: Int) {
        val value = steps.coerceIn(4, 40)
        prefs.edit().putInt(KEY_MAX_STEPS, value).apply()
        _settings.value = current.copy(maxSteps = value)
    }

    fun setLastConversation(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_LAST_CHAT) else putString(KEY_LAST_CHAT, id)
        }.apply()
        _settings.value = current.copy(lastConversationId = id)
    }

    fun setComposioSession(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_SESSION) else putString(KEY_SESSION, id)
        }.apply()
        _settings.value = current.copy(composioSessionId = id)
    }

    fun setOnboarded(value: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        _settings.value = current.copy(onboarded = value)
    }

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply()
        _settings.value = current.copy(darkTheme = enabled)
    }

    fun setGmailPolling(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GMAIL_POLL, enabled).apply()
        _settings.value = current.copy(gmailPollingEnabled = enabled)
    }

    fun setGmailLastChecked(at: Long, messageId: String?) {
        prefs.edit()
            .putLong(KEY_GMAIL_LAST_AT, at)
            .apply { if (messageId == null) remove(KEY_GMAIL_LAST_ID) else putString(KEY_GMAIL_LAST_ID, messageId) }
            .apply()
        _settings.value = current.copy(gmailLastCheckedAt = at, gmailLastMessageId = messageId)
    }

    fun setClassroomPolling(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLASS_POLL, enabled).apply()
        _settings.value = current.copy(classroomPollingEnabled = enabled)
    }

    fun setClassroomLastChecked(at: Long, activityId: String?) {
        prefs.edit()
            .putLong(KEY_CLASS_LAST_AT, at)
            .apply { if (activityId == null) remove(KEY_CLASS_LAST_ID) else putString(KEY_CLASS_LAST_ID, activityId) }
            .apply()
        _settings.value = current.copy(classroomLastCheckedAt = at, classroomLastActivityId = activityId)
    }

    fun setBrainMode(mode: BrainMode) {
        prefs.edit().putString(KEY_BRAIN, mode.name).apply()
        _settings.value = current.copy(brainMode = mode)
    }

    fun setLocalModel(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY_LOCAL_MODEL) else putString(KEY_LOCAL_MODEL, id)
        }.apply()
        _settings.value = current.copy(localModelId = id)
    }

    fun saveHuggingFaceToken(value: String) {
        secure.put(KEY_HF_TOKEN, value.trim())
        _settings.value = current.copy(huggingFaceToken = value.trim())
    }

    fun setAutoApproveReads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_READS, enabled).apply()
        _settings.value = current.copy(autoApproveReads = enabled)
    }

    fun setBriefing(enabled: Boolean, hour: Int = current.briefingHour, minute: Int = current.briefingMinute) {
        prefs.edit()
            .putBoolean(KEY_BRIEFING, enabled)
            .putInt(KEY_BRIEFING_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_BRIEFING_MINUTE, minute.coerceIn(0, 59))
            .apply()
        _settings.value = current.copy(
            briefingEnabled = enabled,
            briefingHour = hour.coerceIn(0, 23),
            briefingMinute = minute.coerceIn(0, 59)
        )
    }

    fun clearChatPreferences() {
        prefs.edit().remove(KEY_LAST_CHAT).apply()
        _settings.value = current.copy(lastConversationId = null)
    }

    fun clearEverything() {
        secure.clear()
        prefs.edit().clear().apply()
        _settings.value = AppSettings(userId = newUserId(), onboarded = false)
        prefs.edit().putString(KEY_USER_ID, _settings.value.userId).apply()
    }

    private fun load(): AppSettings {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId.isNullOrBlank()) {
            userId = newUserId()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        val model = prefs.getString(KEY_MODEL, AppSettings.DEFAULT_MODEL)
            ?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_MODEL
        val approval = runCatching {
            ApprovalMode.valueOf(prefs.getString(KEY_APPROVAL, null) ?: "")
        }.getOrDefault(ApprovalMode.MANUAL)
        return AppSettings(
            geminiKey = secure.get(KEY_GEMINI).orEmpty(),
            composioKey = secure.get(KEY_COMPOSIO).orEmpty(),
            model = model,
            approvalMode = approval,
            maxSteps = prefs.getInt(KEY_MAX_STEPS, AppSettings.DEFAULT_MAX_STEPS),
            userId = userId,
            lastConversationId = prefs.getString(KEY_LAST_CHAT, null),
            composioSessionId = prefs.getString(KEY_SESSION, null),
            onboarded = prefs.getBoolean(KEY_ONBOARDED, false),
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, true),
            gmailPollingEnabled = prefs.getBoolean(KEY_GMAIL_POLL, false),
            gmailLastCheckedAt = prefs.getLong(KEY_GMAIL_LAST_AT, 0L),
            gmailLastMessageId = prefs.getString(KEY_GMAIL_LAST_ID, null),
            classroomPollingEnabled = prefs.getBoolean(KEY_CLASS_POLL, false),
            classroomLastCheckedAt = prefs.getLong(KEY_CLASS_LAST_AT, 0L),
            classroomLastActivityId = prefs.getString(KEY_CLASS_LAST_ID, null),
            brainMode = runCatching {
                BrainMode.valueOf(prefs.getString(KEY_BRAIN, null) ?: "")
            }.getOrDefault(BrainMode.AUTO),
            localModelId = prefs.getString(KEY_LOCAL_MODEL, null),
            huggingFaceToken = secure.get(KEY_HF_TOKEN).orEmpty(),
            autoApproveReads = prefs.getBoolean(KEY_AUTO_READS, true),
            briefingEnabled = prefs.getBoolean(KEY_BRIEFING, false),
            briefingHour = prefs.getInt(KEY_BRIEFING_HOUR, 7),
            briefingMinute = prefs.getInt(KEY_BRIEFING_MINUTE, 30)
        )
    }

    private fun newUserId(): String = "isha-" + UUID.randomUUID().toString().take(8)

    private companion object {
        const val KEY_GEMINI = "gemini_key"
        const val KEY_COMPOSIO = "composio_key"
        const val KEY_MODEL = "model"
        const val KEY_APPROVAL = "approval_mode"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_USER_ID = "user_id"
        const val KEY_LAST_CHAT = "last_chat"
        const val KEY_SESSION = "composio_session"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_GMAIL_POLL = "gmail_poll_enabled"
        const val KEY_GMAIL_LAST_AT = "gmail_poll_last_at"
        const val KEY_GMAIL_LAST_ID = "gmail_poll_last_id"
        const val KEY_CLASS_POLL = "classroom_poll_enabled"
        const val KEY_CLASS_LAST_AT = "classroom_poll_last_at"
        const val KEY_CLASS_LAST_ID = "classroom_poll_last_id"
        const val KEY_BRAIN = "brain_mode"
        const val KEY_LOCAL_MODEL = "local_model"
        const val KEY_HF_TOKEN = "hf_token"
        const val KEY_AUTO_READS = "auto_approve_reads"
        const val KEY_BRIEFING = "briefing_enabled"
        const val KEY_BRIEFING_HOUR = "briefing_hour"
        const val KEY_BRIEFING_MINUTE = "briefing_minute"
    }
}
