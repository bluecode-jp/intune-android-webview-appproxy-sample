package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yaso202508appproxy.intunetestapp.auth.AuthService
import com.yaso202508appproxy.intunetestapp.auth.TestAuthService
import com.yaso202508appproxy.intunetestapp.web.WebViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var webViewWrapper: WebViewWrapper

    private lateinit var authScopesSelectDialog: AuthScopesSelectDialog

    private lateinit var btnInit: Button
    private lateinit var btnSetAccount: Button
    private lateinit var btnWaitReady: Button

    private lateinit var btnSignIn: Button
    private lateinit var btnMamRegister: Button
    private lateinit var btnMamStatus: Button

    private lateinit var btnUser: Button
    private lateinit var btnAuth: Button
    private lateinit var btnClearAccount: Button

    private lateinit var btnWeb: Button
    private lateinit var btnLogShow: Button
    private lateinit var btnLogClear: Button

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

        WebView.setWebContentsDebuggingEnabled(true)
        AuthService.setLogger(createLogger("Auth"))

        initViews()
    }

    private fun initViews() {
        webViewWrapper = WebViewWrapper(findViewById(R.id.webView))
        webViewWrapper.setLogger(createLogger("Web"))

        authScopesSelectDialog = AuthScopesSelectDialog(this)

        btnInit = findViewById(R.id.btnInit)
        btnInit.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AuthService.initialize(applicationContext)
                    showMsg("initialize: ${result.isSuccessStr()}")
                } catch (exception: Exception) {
                    logger.error("initialize", exception)
                }
            }
        }

        btnSetAccount = findViewById(R.id.btnSetAccount)
        btnSetAccount.setOnClickListener {
            val activity = this

            authScopesSelectDialog.show { scopes ->
                lifecycleScope.launch {
                    try {
                        val account = AuthService.setAccount(scopes, activity)
                        showMsg("setAccount: ${account?.username}")
                    } catch (exception: Exception) {
                        logger.error("setAccount", exception)
                    }
                }
            }
        }

        btnWaitReady = findViewById(R.id.btnWaitReady)
        btnWaitReady.setOnClickListener {
            authScopesSelectDialog.show { scopes ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        AuthService.waitForAppProxyAccessReady(scopes, 5000L, 500L)
                        showMsg("WaitForReady: success")
                    } catch (exception: Exception) {
                        logger.error("WaitForReady", exception)
                    }
                }
            }
        }

        btnSignIn = findViewById(R.id.btnSignIn)
        btnSignIn.setOnClickListener {
            val activity = this

            authScopesSelectDialog.show { scopes ->
                lifecycleScope.launch {
                    try {
                        val account = TestAuthService.signIn(scopes, activity)
                        showMsg("signIn: ${account?.username}")
                    } catch (exception: Exception) {
                        logger.error("signIn", exception)
                    }
                }
            }
        }

        btnMamRegister = findViewById(R.id.btnMamRegister)
        btnMamRegister.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = TestAuthService.registerMam()
                    showMsg("registerMam: ${result.isSuccessStr()}")
                } catch (exception: Exception) {
                    logger.error("registerMam", exception)
                }
            }
        }

        btnMamStatus = findViewById(R.id.btnMamStatus)
        btnMamStatus.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val status = TestAuthService.getMamStatus()
                    showMsg("getMamStatus: ${status?.name}")
                } catch (exception: Exception) {
                    logger.error("getMamStatus", exception)
                }
            }
        }

        btnUser = findViewById(R.id.btnUser)
        btnUser.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val account = AuthService.getAccount()
                    if (account == null) {
                        showMsg("userInfo: null")
                    } else {
                        showMsgDialog("userInfo", account.toLog())
                    }
                } catch (exception: Exception) {
                    logger.error("userInfo", exception)
                }
            }
        }

        btnAuth = findViewById(R.id.btnAuth)
        btnAuth.setOnClickListener {
            authScopesSelectDialog.show { scopes ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val authInfo = AuthService.acquireAuth(scopes)
                        if (authInfo == null) {
                            showMsg("authInfo: null")
                        } else {
                            showMsgDialog("authInfo", authInfo.toLog())
                        }
                    } catch (exception: Exception) {
                        logger.error("btnAuth", exception)
                    }
                }
            }
        }

        btnClearAccount = findViewById(R.id.btnClearAccount)
        btnClearAccount.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = AuthService.clearAccount()
                    showMsg("clearAccount: ${result.isSuccessStr()}")
                } catch (exception: Exception) {
                    logger.error("clearAccount", exception)
                }
            }
        }

        btnWeb = findViewById(R.id.btnWeb)
        btnWeb.setOnClickListener {
            lifecycleScope.launch {
                try {
                    webViewWrapper.load()
                } catch (exception: Exception) {
                    logger.error("loadWeb", exception)
                }
            }
        }

        btnLogShow = findViewById(R.id.btnLogShow)
        btnLogShow.setOnClickListener {
            try {
                showLog()
            } catch (exception: Exception) {
                logger.error("showLog", exception)
            }
        }

        btnLogClear = findViewById(R.id.btnLogClear)
        btnLogClear.setOnClickListener {
            try {
                logHistory.clear()
                showMsg("clearLog: Done")
            } catch (exception: Exception) {
                logger.error("clearLog", exception)
            }
        }
    }

    private fun createLogger(tag: String): AppLogger = object : AppLogger {
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

    private fun showMsg(msg: String) {
        lifecycleScope.launch {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showMsgDialog(title: String, msg: String) {
        val activity = this
        lifecycleScope.launch {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showLog() = showMsgDialog("log", logHistory.toString())
}
