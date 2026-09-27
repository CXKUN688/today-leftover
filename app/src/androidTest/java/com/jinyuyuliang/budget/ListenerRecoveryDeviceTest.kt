package com.jinyuyuliang.budget

import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.os.ParcelFileDescriptor
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Only exercises listener lifecycle. Never seeds, clears, or mutates the user's ledger. */
@RunWith(AndroidJUnit4::class)
class ListenerRecoveryDeviceTest {
    private fun bound(): Boolean {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys notification")
        val text = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return text.substringAfter("Live notification listeners", "").substringBefore("Snoozed notification listeners").contains("com.jinyuyuliang.budget.PaymentListenerService")
    }
    private fun awaitBound(expected: Boolean): Boolean {
        repeat(30) { if (bound() == expected) return true; Thread.sleep(300) }
        return false
    }
    @Test fun resumeReconnectsAuthorizedButDisconnectedListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = ComponentName(context, PaymentListenerService::class.java)
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        assumeTrue(context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component))
        try {
            instrumentation.runOnMainSync { NotificationListenerService.requestUnbind(component) }
            assertTrue("Listener should be disconnected for this regression test", awaitBound(false))
            // Launch as the shell, avoiding background-activity launch restrictions in the test process.
            val launch = instrumentation.uiAutomation.executeShellCommand("am start -n com.jinyuyuliang.budget/.MainActivity")
            ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { it.readText() }
            assertTrue("Opening the app must reconnect an already-authorized listener", awaitBound(true))
        } finally {
            instrumentation.runOnMainSync { NotificationListenerService.requestRebind(component) }
        }
    }
}
