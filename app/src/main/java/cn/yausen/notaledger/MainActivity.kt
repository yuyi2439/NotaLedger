package cn.yausen.notaledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import cn.yausen.notaledger.ui.NotaLedgerRoot
import cn.yausen.notaledger.ui.theme.NotaLedgerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
                )
            }
        }
    }
}
