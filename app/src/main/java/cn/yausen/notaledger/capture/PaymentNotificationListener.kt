package cn.yausen.notaledger.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cn.yausen.notaledger.data.UserPreferences

class PaymentNotificationListener : NotificationListenerService() {
    companion object { @Volatile var connected = false; private set }
    private val seen = linkedMapOf<String, Long>()
    override fun onListenerConnected() { connected = true }
    override fun onListenerDisconnected() { connected = false }
    override fun onDestroy() { connected = false; super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!UserPreferences(this).notificationCaptureEnabled || sbn.packageName !in PaymentParser.paymentApps) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        // Chat/conversation notifications must never become payment candidates.
        if (sbn.notification.category == Notification.CATEGORY_MESSAGE || extras.containsKey(Notification.EXTRA_MESSAGES)) return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val draft = PaymentParser.parse(sbn.packageName, "$title\n$body", sbn.postTime) ?: return
        val now = System.currentTimeMillis()
        if (now - sbn.postTime !in 0..120000L) return
        seen.entries.removeAll { now - it.value > 120000L }
        val key = "${sbn.key}|${draft.amount}|${draft.merchant}"
        if (seen.containsKey(key)) return
        seen[key] = now
        CaptureSupport.notify(this, PaymentParser.paymentApps.getValue(sbn.packageName), draft)
    }
}
