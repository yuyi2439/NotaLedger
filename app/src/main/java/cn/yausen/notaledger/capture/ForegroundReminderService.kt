package cn.yausen.notaledger.capture

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import cn.yausen.notaledger.data.UserPreferences

/** User-started monitoring, with an ongoing notification and no page/content access. */
class ForegroundReminderService : Service() {
    companion object { @Volatile var running = false; private set }
    private val handler = Handler(Looper.getMainLooper())
    private var since = System.currentTimeMillis()
    private var foreground: String? = null
    private val lastReminded = mutableMapOf<String, Long>()
    private val poll = object : Runnable {
        override fun run() {
            if (!UserPreferences(this@ForegroundReminderService).foregroundReminderEnabled ||
                !CaptureSupport.usageGranted(this@ForegroundReminderService) ||
                !CaptureSupport.notificationsGranted(this@ForegroundReminderService)) { stopSelf(); return }
            val now = System.currentTimeMillis()
            val interactive = getSystemService(PowerManager::class.java).isInteractive &&
                !getSystemService(KeyguardManager::class.java).isKeyguardLocked
            if (interactive) {
                try {
                    val events = getSystemService(UsageStatsManager::class.java).queryEvents(since, now)
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val pkg = event.packageName
                            if (pkg != foreground && pkg in PaymentParser.paymentApps && now - (lastReminded[pkg] ?: 0) >= 300000L) {
                                CaptureSupport.notify(this@ForegroundReminderService, PaymentParser.paymentApps.getValue(pkg))
                                lastReminded[pkg] = now
                            }
                            foreground = pkg
                        }
                    }
                } catch (_: SecurityException) { stopSelf(); return }
            } else foreground = null
            since = now
            handler.postDelayed(this, 2500L)
        }
    }
    override fun onCreate() {
        super.onCreate()
        CaptureSupport.channels(this)
        startForeground(CaptureSupport.MONITOR_ID, NotificationCompat.Builder(this, CaptureSupport.MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit).setContentTitle("支付应用记账提醒已开启")
            .setContentText("仅识别前台应用；点击可在设置中关闭")
            .setContentIntent(android.app.PendingIntent.getActivity(this, 1,
                Intent(this, cn.yausen.notaledger.MainActivity::class.java).putExtra("open_settings", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).setSilent(true).build())
        running = true
        handler.post(poll)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onBind(intent: Intent?) = null
    override fun onDestroy() { handler.removeCallbacks(poll); running = false; super.onDestroy() }
}
