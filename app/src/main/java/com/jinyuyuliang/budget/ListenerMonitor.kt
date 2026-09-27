package com.jinyuyuliang.budget

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Connection state is process-local: an old persisted "connected" flag would be misleading after a kill. */
data class ListenerHealth(val authorized: Boolean = false, val connected: Boolean = false, val message: String = "尚未收到微信或支付宝系统通知")
object ListenerMonitor {
    private val mutable = MutableStateFlow(ListenerHealth())
    val state = mutable.asStateFlow()
    private var lastAttempt = -30_000L
    fun refresh(context: Context) {
        val granted = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(context, PaymentListenerService::class.java))
        val previous = context.getSharedPreferences("listener_health", Context.MODE_PRIVATE).getString("message", null)
        mutable.update { it.copy(authorized = granted, connected = granted && it.connected, message = previous ?: it.message) }
    }
    fun reconnect(context: Context) {
        refresh(context)
        val now = SystemClock.elapsedRealtime()
        if (!mutable.value.authorized || mutable.value.connected || now - lastAttempt < 5_000L) return
        lastAttempt = now
        try {
            NotificationListenerService.requestRebind(ComponentName(context, PaymentListenerService::class.java))
        } catch (_: SecurityException) {
            mutable.update { it.copy(connected = false) }
        }
    }
    fun connected(context: Context) { refresh(context); mutable.update { it.copy(connected = true) } }
    fun disconnected() { mutable.update { it.copy(connected = false) } }
    fun event(context: Context, source: String, outcome: String) {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
        val message = "$time · $source · $outcome"
        context.getSharedPreferences("listener_health", Context.MODE_PRIVATE).edit().putString("message", message).apply()
        mutable.update { it.copy(message = message) }
    }
}
