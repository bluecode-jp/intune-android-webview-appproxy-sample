package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    companion object {
        private val scopesProxy = ScopesItem("PROXY", listOf("https://TestWebSite-blucodeinc.msappproxy.net/user_impersonation"))
        private val scopesGraph = ScopesItem("GRAPH", listOf("User.Read"))
        private val scopesSelectItems = listOf(
            scopesProxy,
            scopesGraph
        )
    }

    private lateinit var scopesAdapter: ArrayAdapter<ScopesItem>
    private var currentScopesIndex: Int = -1

    private lateinit var btnInitMsal: Button
    private lateinit var btnSso: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnAccount: Button
    private lateinit var btnTokenAsync: Button
    private lateinit var btnTokenSync: Button
    private lateinit var btnMamInit: Button
    private lateinit var btnMamStatus: Button
    private lateinit var btnMamRegister: Button
    private lateinit var btnWeb: Button
    private lateinit var btnLog: Button
    private lateinit var btnLogClear: Button
    private lateinit var webView: WebView
    private lateinit var btnProdInit: Button
    private lateinit var btnProdLogin: Button
    private lateinit var btnProdWait: Button

    private val logHistory = StringBuilder()
    private val logger = createLogger("Main")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        AppProxyAuthManager.setLogger(createLogger("APAuth"))
        WebView.setWebContentsDebuggingEnabled(true)
        initViews()
    }

    private suspend fun loadWebSite() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        webView.webViewClient = WebViewClient()

        val accessToken = AppProxyAuthManager.acquireToken(scopesProxy.scopes)
        if (accessToken == null) {
            webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net")
        } else {
            val headers = mapOf("Authorization" to "Bearer $accessToken")
            webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net", headers)
        }
    }

    private fun initViews() {
        scopesAdapter = ArrayAdapter(
            this,
            android.R.layout.select_dialog_singlechoice,
            scopesSelectItems
        )

        btnInitMsal = findViewById(R.id.btnInitMsal)
        btnInitMsal.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AppProxyAuthManager.initMsal(applicationContext)
                    showMsg("btnInitMsal: ${isSuccessToString(result)}")
                } catch (exception: Exception) {
                    logger.error("btnInitMsal", exception)
                }
            }
        }

        btnSso = findViewById(R.id.btnSso)
        btnSso.setOnClickListener {
            val activity = this

            selectScopes { scopes ->
                lifecycleScope.launch {
                    try {
                        val account = AppProxyAuthManager.sso(scopes, activity)
                        showMsg("btnSso: ${isSuccessToString(account != null)}")
                    } catch (exception: Exception) {
                        logger.error("btnSso", exception)
                    }
                }
            }
        }

        btnSignOut = findViewById(R.id.btnSignOut)
        btnSignOut.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AppProxyAuthManager.cleanupAccount()
                    showMsg("btnSignOut: ${isSuccessToString(result)}")
                } catch (exception: Exception) {
                    logger.error("btnSignOut", exception)
                }
            }
        }
        
        btnAccount = findViewById(R.id.btnAccount)
        btnAccount.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val account = AppProxyAuthManager.getAccount()
                    showMsg("btnAccount: ${account?.username}")
                } catch (exception: Exception) {
                    logger.error("btnAccount", exception)
                }
            }
        }
        
        btnTokenAsync = findViewById(R.id.btnTokenAsync)
        btnTokenAsync.setOnClickListener {
            selectScopes { scopes ->
                lifecycleScope.launch {
                    try {
                        val accessToken = AppProxyAuthManager.acquireToken(scopes)
                        showMsg("btnTokenAsync: ${shortenToken(accessToken)}")
                    } catch (exception: Exception) {
                        logger.error("btnTokenAsync", exception)
                    }
                }
            }
        }

        btnTokenSync = findViewById(R.id.btnTokenSync)
        btnTokenSync.setOnClickListener {
            selectScopes { scopes ->
                lifecycleScope.launch {
                    try {
                        val accessToken = withContext(Dispatchers.IO) {
                            AppProxyAuthManager.acquireTokenBackground(scopes)
                        }
                        showMsg("btnTokenSync: ${shortenToken(accessToken)}")
                    } catch (exception: Exception) {
                        logger.error("btnTokenSync", exception)
                    }
                }
            }
        }

        btnMamInit = findViewById(R.id.btnMamInit)
        btnMamInit.setOnClickListener {
            try {
                val result = AppProxyAuthManager.initMam()
                showMsg("btnMamInit: ${isSuccessToString(result)}")
            } catch (exception: Exception) {
                logger.error("btnMamInit", exception)
            }
        }

        btnMamRegister = findViewById(R.id.btnMamRegister)
        btnMamRegister.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AppProxyAuthManager.registerMam()
                    showMsg("btnMamRegister: ${isSuccessToString(result)}")
                } catch (exception: Exception) {
                    logger.error("btnMamRegister", exception)
                }
            }
        }

        btnMamStatus = findViewById(R.id.btnMamStatus)
        btnMamStatus.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = AppProxyAuthManager.getMamStatus()
                    showMsg("btnMamStatus: ${status?.name}")
                } catch (exception: Exception) {
                    logger.error("btnMamStatus", exception)
                }
            }
        }

        btnWeb = findViewById(R.id.btnWeb)
        btnWeb.setOnClickListener {
            lifecycleScope.launch {
                try {
                    loadWebSite()
                } catch (exception: Exception) {
                    logger.error("btnWeb", exception)
                }
            }
        }

        btnLog = findViewById(R.id.btnLog)
        btnLog.setOnClickListener {
            try {
                showLog()
            } catch (exception: Exception) {
                logger.error("btnLog", exception)
            }
        }

        btnLogClear = findViewById(R.id.btnLogClear)
        btnLogClear.setOnClickListener {
            try {
                logHistory.clear()
                showMsg("btnLogClear: Done")
            } catch (exception: Exception) {
                logger.error("btnLogClear", exception)
            }
        }

        btnProdInit = findViewById(R.id.btnProdInit)
        btnProdInit.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AppProxyAuthManager.initialize(applicationContext)
                    showMsg("btnProdInit: ${isSuccessToString(result)}")
                } catch (exception: Exception) {
                    logger.error("btnProdInit", exception)
                }
            }
        }

        btnProdLogin = findViewById(R.id.btnProdLogin)
        btnProdLogin.setOnClickListener {
            val activity = this

            selectScopes { scopes ->
                lifecycleScope.launch {
                    try {
                        val result = AppProxyAuthManager.setupAccount(scopes, activity)
                        showMsg("btnProdLogin: ${isSuccessToString(result)}")
                    } catch (exception: Exception) {
                        logger.error("btnProdLogin", exception)
                    }
                }
            }
        }

        btnProdWait = findViewById(R.id.btnProdWait)
        btnProdWait.setOnClickListener {
            selectScopes { scopes ->
                lifecycleScope.launch {
                    try {
                        AppProxyAuthManager.waitForAppProxyAccessReady(scopes, 5000L, 500L)
                        showMsg("btnProdWait: success")
                    } catch (exception: Exception) {
                        logger.error("btnProdWait", exception)
                    }
                }
            }
        }
    }

    private fun selectScopes(callback: (List<String>) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Select Scopes")
            .setSingleChoiceItems(
                scopesAdapter,
                if (currentScopesIndex in scopesSelectItems.indices) currentScopesIndex else -1
            ) { _, which ->
                currentScopesIndex = which
            }.setPositiveButton("OK") { _, _ ->
                if (currentScopesIndex in scopesSelectItems.indices) {
                    callback(scopesSelectItems[currentScopesIndex].scopes)
                } else {
                    showMsg("No Select")
                }
            }.show()
    }

    private fun showMsg(msg: String) {
        lifecycleScope.launch {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun createLogger(tag: String): Logger = object : Logger {
        override fun info(msg: String) {
            Log.d(tag, msg)
            logHistory.appendLine("[$tag] $msg")
        }

        override fun error(msg: String, exception: java.lang.Exception?) {
            Log.e(tag, msg, exception)
            val log = "[$tag] $msg ${exception ?: ""}"
            logHistory.appendLine(log)
            showMsg(log)
        }
    }

    private fun showLog() {
        AlertDialog.Builder(this)
            .setTitle("Log")
            .setMessage(logHistory.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun shortenToken(token: String?): String {
        if (token == null) {
            return "null"
        }
        return "${token.take(10)}...${token.takeLast(10)}"
    }

    private fun isSuccessToString(result: Boolean) = if (result) "success" else "fail"
}

data class ScopesItem(
    val displayName: String,
    val scopes: List<String>
) {
    override fun toString(): String  = displayName
}