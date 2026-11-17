package cn.yausen.notaledger.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.content.ComponentName
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.yausen.notaledger.capture.*
import cn.yausen.notaledger.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(preferences: UserPreferences) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var disclosure by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                kotlinx.coroutines.delay(2000)
                revision++
            }
        }
    }
    fun refresh() {
        runCatching { CaptureSupport.sync(context) }.onFailure { error = "服务未能启动，请重新开启功能：${it.localizedMessage}" }
        revision++
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure { error = "无法打开系统设置，请在系统设置中手动授权。" }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    // Read system authorization again whenever the activity returns from settings.
    val capture = remember(revision) { preferences.notificationCaptureEnabled }
    val reminder = remember(revision) { preferences.foregroundReminderEnabled }
    val listener = remember(revision) { CaptureSupport.listenerGranted(context) }
    val usage = remember(revision) { CaptureSupport.usageGranted(context) }
    val notifications = remember(revision) { CaptureSupport.notificationsGranted(context) }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("辅助记账", style = MaterialTheme.typography.titleLarge)
            Text("两种功能独立可选，默认关闭。所有账单都需要你核对、选择分类并确认保存。")
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("通知捕获快捷记账", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(capture, { enabled ->
                            if (enabled) disclosure = "capture" else {
                                preferences.notificationCaptureEnabled = false
                                DraftStore.clear(context)
                                CaptureSupport.clearNotifications(context)
                                refresh()
                            }
                        })
                    }
                    Text("仅处理微信、支付宝支付通知，识别金额和商户并预填账单，点击提醒后核对保存。不会读取聊天或页面内容。")
                    Text(if (!capture) "已关闭" else if (!listener) "待授予通知使用权" else if (!notifications) "无法发送提醒：请开启本应用通知" else if (!PaymentNotificationListener.connected) "通知监听尚未连接，请重新连接或重新开启权限" else "通知监听已连接")
                    if (capture) {
                        TextButton(onClick = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("管理通知使用权") }
                        if (listener && !PaymentNotificationListener.connected) TextButton(onClick = {
                            NotificationListenerService.requestRebind(ComponentName(context, PaymentNotificationListener::class.java)); revision++
                        }) { Text("重新连接") }
                    }
                }
            }
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text("前台应用识别提醒", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(reminder, { enabled ->
                            if (enabled) disclosure = "reminder" else {
                                preferences.foregroundReminderEnabled = false
                                context.getSystemService(android.app.NotificationManager::class.java).cancel(CaptureSupport.REMINDER, 200)
                                refresh()
                            }
                        })
                    }
                    Text("检测打开微信、支付宝时发送静默记账提醒。仅识别前台应用包名，不读取页面，不获取金额；点击后打开空白记账页。每个支付应用最多每 5 分钟提醒一次。")
                    Text(if (!reminder) "已关闭" else if (!usage) "待授予使用情况访问权限" else if (!notifications) "无法发送提醒：请开启本应用通知" else if (ForegroundReminderService.running) "正在检测（常驻通知）" else "检测已停止，请点击重试")
                    if (reminder) {
                        TextButton(onClick = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("管理使用情况访问权限") }
                        TextButton(onClick = { refresh() }) { Text("刷新状态 / 重启检测") }
                    }
                }
            }
            if (capture || reminder) {
                if (!notifications) Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text("开启记账提醒通知") }
                Text("通知捕获依赖支付软件实际发出的系统通知，关闭支付通知将无法捕获；识别可能有误。草稿 24 小时后失效，取消或关闭捕获后不会保存到账本。")
                Text("后台检测会增加耗电。若被系统停止，请重新打开本应用；重启手机后也需打开本应用恢复。可在系统电池设置中允许后台运行。")
                TextButton(onClick = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text("打开电池优化设置") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    disclosure?.let { feature ->
        AlertDialog(onDismissRequest = { disclosure = null }, title = { Text("开启辅助记账？") },
            text = { Text(if (feature == "capture") "需要通知使用权。系统授权范围较广，本应用仅处理微信、支付宝的支付通知，生成待确认草稿；不自动保存账单。请在下一页仅为本应用授权。" else "需要使用情况访问权限，仅用于识别前台应用包名。开启后通过常驻通知维持后台检测，不读取屏幕内容，也不会提取金额。") },
            confirmButton = { TextButton(onClick = {
                disclosure = null
                if (feature == "capture") {
                    preferences.notificationCaptureEnabled = true
                    if (!listener) open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } else {
                    preferences.foregroundReminderEnabled = true
                    if (!usage) open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }
                refresh()
            }) { Text("开启并设置权限") } },
            dismissButton = { TextButton(onClick = { disclosure = null }) { Text("取消") } })
    }
}
