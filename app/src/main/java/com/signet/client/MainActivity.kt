package com.signet.client

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.signet.client.ui.SignetScreen

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == RESULT_OK) model.start() else model.repository.notify("Разрешение на VPN не предоставлено")
    }
    private val notification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if(savedInstanceState==null)consume(intent)
        setContent { SignetScreen(model, ::connect) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); consume(intent) }
    private fun consume(intent: Intent?) {
        val raw = if(intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else intent?.data?.let {
            if(it.scheme == "signet" && it.host == "import") it.getQueryParameter("url") else it.toString()
        }
        // Imports always require review in the import dialog; external links never start VPN.
        if(!raw.isNullOrBlank() && raw.length<=4*1024*1024) pendingImport.value = raw
        intent?.data=null;intent?.removeExtra(Intent.EXTRA_TEXT)
    }
    private fun connect() {
        if(Build.VERSION.SDK_INT >= 33) notification.launch(Manifest.permission.POST_NOTIFICATIONS)
        val prepare = VpnService.prepare(this)
        if(prepare != null) permission.launch(prepare) else model.start()
    }
    companion object { val pendingImport = mutableStateOf<String?>(null) }
}
