package com.thiago.listamercado

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import com.thiago.listamercado.ui.AppRoot
import com.thiago.listamercado.ui.components.LocalRepository
import com.thiago.listamercado.ui.theme.ListaMercadoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val repository = (application as ListaMercadoApp).repository
        setContent {
            ListaMercadoTheme {
                CompositionLocalProvider(LocalRepository provides repository) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            val type = intent.type ?: contentResolver.getType(uri)
            SharedFiles.offer(SharedFile(uri, type))
        }
    }
}
