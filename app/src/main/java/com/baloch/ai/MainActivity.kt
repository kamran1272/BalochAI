package com.baloch.ai

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.baloch.ai.settings.ProtectionMode
import com.baloch.ai.ui.BalochViewModel
import com.baloch.ai.ui.MainScreen
import com.baloch.ai.ui.theme.BalochTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BalochViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FORTRESS PRIVACY (Oct 14 2026): Protection mode (Settings → Privacy
        // & Permissions) blocks screenshots and screen-recording of Baloch by
        // other apps, and blanks the system recents thumbnail. Default ON;
        // the user can switch it OFF to capture the screen (e.g. to report
        // an issue). Every Baloch screen (chat, memory, settings, privacy
        // dashboard) is composed inside this one activity window, so this
        // single flag protects all of them.
        applyProtectionMode()
        setContent {
            BalochTheme {
                MainScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply on return: toggling Protection mode in Settings takes
        // effect immediately, without an app restart.
        applyProtectionMode()
    }

    private fun applyProtectionMode() {
        if (ProtectionMode.isOn(this)) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onDestroy() {
        viewModel.shutdown()
        super.onDestroy()
    }
}
