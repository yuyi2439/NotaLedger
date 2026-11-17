package cn.yausen.notaledger

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import cn.yausen.notaledger.capture.CaptureSupport
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import cn.yausen.notaledger.ui.NotaLedgerRoot
import cn.yausen.notaledger.ui.theme.NotaLedgerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val captureRequest = mutableStateOf<String?>(null)
    private val settingsRequest = mutableStateOf(false)
    private fun receiveIntent(value: Intent?) {
        captureRequest.value = value?.getStringExtra(CaptureSupport.EXTRA_DRAFT)
        settingsRequest.value = value?.getBooleanExtra("open_settings", false) == true
        value?.removeExtra(CaptureSupport.EXTRA_DRAFT)
        value?.removeExtra("open_settings")
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveIntent(intent)
    }
    override fun onResume() {
        super.onResume()
        runCatching { CaptureSupport.sync(this) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        receiveIntent(intent)
        val container = application as NotaLedgerApp
        // Make sure a brand new install has usable categories before the first frame.
        lifecycleScope.launch {
            container.repository.seedDefaultsIfNeeded()
        }

        setContent {
            NotaLedgerTheme {
                NotaLedgerRoot(
                    repository = container.repository,
                    preferences = container.preferences,
                    captureRequest = captureRequest.value,
                    onCaptureConsumed = { captureRequest.value = null },
                    openSettings = settingsRequest.value,
                    onSettingsConsumed = { settingsRequest.value = false },
                )
            }
        }
    }
}
