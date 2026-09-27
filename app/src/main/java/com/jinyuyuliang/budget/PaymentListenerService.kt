package com.jinyuyuliang.budget

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jinyuyuliang.budget.data.BudgetStore
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class PaymentListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerMonitor.connected(this)
        // Only recover still-visible notifications; this is not access to historical payment records.
        runCatching { activeNotifications }.getOrNull()?.sortedBy { it.postTime }?.forEach(::capture)
    }
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerMonitor.disconnected()
        handler.postDelayed({ ListenerMonitor.reconnect(this) }, 5_000L)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ListenerMonitor.disconnected()
        super.onDestroy()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) = capture(sbn)
    private fun capture(sbn: StatusBarNotification) {
        if (sbn.packageName !in listOf("com.tencent.mm", "com.eg.android.AlipayGphone")) return
        val source = if (sbn.packageName == "com.tencent.mm") "微信" else "支付宝"
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val date = Instant.ofEpochMilli(sbn.postTime).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val candidate = PaymentParser.parse(sbn.packageName, sbn.key, title, text, date)
        if (candidate == null) {
            ListenerMonitor.event(this, source, "收到系统通知，未匹配付款格式")
            return
        }
        val store = BudgetStore.get(this)
        store.scope.launch {
            runCatching { store.apply(Mutation.AddCandidates(listOf(candidate))) }
                .onSuccess { ListenerMonitor.event(this@PaymentListenerService, source, "已识别交易候选，请到待处理列表核对") }
                .onFailure { ListenerMonitor.event(this@PaymentListenerService, source, "收到交易通知，但候选保存失败") }
        }
    }
}
