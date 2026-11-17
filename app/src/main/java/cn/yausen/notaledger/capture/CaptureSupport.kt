package cn.yausen.notaledger.capture

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.yausen.notaledger.MainActivity
import cn.yausen.notaledger.data.UserPreferences
import org.json.JSONObject
import java.util.UUID

object CaptureSupport {
    const val EXTRA_DRAFT = "capture_draft"
    const val REMINDER = "manual"
    const val CHANNEL = "ledger_capture"
    const val MONITOR_CHANNEL = "ledger_monitor"
    const val MONITOR_ID = 100
    fun listenerGranted(context: Context) = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(context, PaymentNotificationListener::class.java))
    fun usageGranted(context: Context): Boolean = context.getSystemService(AppOpsManager::class.java)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    fun notificationsGranted(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun channels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
            NotificationChannel(CHANNEL, "辅助记账提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) },
            NotificationChannel(MONITOR_CHANNEL, "前台应用识别服务", NotificationManager.IMPORTANCE_LOW),
        ))
    }
    fun pending(context: Context, id: String): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, MainActivity::class.java).setAction("cn.yausen.notaledger.DRAFT.$id")
            .putExtra(EXTRA_DRAFT, id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun notify(context: Context, source: String, draft: PaymentDraft? = null) {
        channels(context)
        if (!notificationsGranted(context)) return
        val id = if (draft == null) REMINDER else UUID.randomUUID().toString()
        if (draft != null) DraftStore.put(context, id, draft)
        val message = if (draft == null) "检测到你正在使用${source}，需要手动记一笔吗？" else
            "${source}支付 ¥${draft.amount} ${draft.merchant}，请核对后保存"
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, 200,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .setContentTitle(if (draft == null) "记账提醒" else "发现支付通知 · 待确认")
                    .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending(context, id)).setAutoCancel(true).setOnlyAlertOnce(true)
                    .setTimeoutAfter(24 * 60 * 60 * 1000L).build())
        } catch (_: SecurityException) { }
    }
    fun sync(context: Context) {
        channels(context)
        DraftStore.prune(context)
        val prefs = UserPreferences(context)
        if (prefs.foregroundReminderEnabled && usageGranted(context) && notificationsGranted(context)) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ForegroundReminderService::class.java))
        } else context.stopService(Intent(context, ForegroundReminderService::class.java))
        if (prefs.notificationCaptureEnabled && listenerGranted(context) && !PaymentNotificationListener.connected) {
            android.service.notification.NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
        }
    }
    fun clearNotifications(context: Context) {
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == 200 && it.tag != REMINDER }.forEach { context.getSystemService(NotificationManager::class.java).cancel(it.tag, it.id) }
    }
}

/** Private, expiring candidates; these are never ledger records or raw notification dumps. */
object DraftStore {
    private fun prefs(context: Context) = context.getSharedPreferences("capture-drafts", Context.MODE_PRIVATE)
    fun put(context: Context, id: String, draft: PaymentDraft) {
        prune(context)
        prefs(context).edit().putString(id, JSONObject().put("amount", draft.amount).put("merchant", draft.merchant).put("time", draft.timestamp).toString()).apply()
    }
    fun prune(context: Context) {
        val store = prefs(context)
        val edit = store.edit()
        store.all.keys.forEach { key -> if (get(context, key) == null) edit.remove(key) }
        edit.apply()
    }
    fun get(context: Context, id: String): PaymentDraft? = runCatching {
        val json = JSONObject(prefs(context).getString(id, null) ?: return null)
        val time = json.getLong("time")
        if (System.currentTimeMillis() - time !in 0..86400000L) return null
        PaymentDraft(json.getDouble("amount"), json.getString("merchant"), time)
    }.getOrNull()
    fun remove(context: Context, id: String) { prefs(context).edit().remove(id).apply() }
    fun clear(context: Context) { prefs(context).edit().clear().apply() }
}
