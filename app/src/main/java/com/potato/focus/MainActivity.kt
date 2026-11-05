package com.potato.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.potato.focus.ui.PotatoRoot
import com.potato.focus.ui.theme.PotatoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val requestedTab = MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = tabFromIntent(intent)
        setContent {
            val requested by requestedTab.collectAsState()
            PotatoTheme {
                PotatoRoot(
                    requestedTab = requested,
                    onTabRequestHandled = { requestedTab.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab.value = tabFromIntent(intent)
    }

    private fun tabFromIntent(intent: Intent?): Int? {
        val tab = intent?.getIntExtra(EXTRA_TAB, -1) ?: -1
        return tab.takeIf { it in 0..3 }
    }

    companion object {
        const val EXTRA_TAB = "extra_tab"
    }
}
