package com.baloch.ai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.baloch.ai.ui.BalochViewModel
import com.baloch.ai.ui.MainScreen
import com.baloch.ai.ui.theme.BalochTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BalochViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FORTRESS PRIVACY (Oct 14 2026): block screenshots and screen-recording
        // of Baloch by other apps, and blank the system recents thumbnail.
        // Every Baloch screen (chat, memory, settings, privacy dashboard) is
        // composed inside this one activity window, so this single flag
        // protects all of them.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            BalochTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        viewModel.shutdown()
        super.onDestroy()
    }
}
