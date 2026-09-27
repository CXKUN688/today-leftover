package com.jinyuyuliang.budget
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jinyuyuliang.budget.ui.BudgetApp
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BudgetApp() }
    }
    override fun onResume() {
        super.onResume()
        ListenerMonitor.reconnect(this)
    }
}
