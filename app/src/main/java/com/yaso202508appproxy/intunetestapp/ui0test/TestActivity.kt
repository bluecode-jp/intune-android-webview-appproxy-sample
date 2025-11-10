package com.yaso202508appproxy.intunetestapp.ui0test

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.AuthScopes
import com.yaso202508appproxy.intunetestapp.R
import com.yaso202508appproxy.intunetestapp.auth.AuthService
import com.yaso202508appproxy.intunetestapp.auth.internal.AuthCacheManager
import com.yaso202508appproxy.intunetestapp.auth.internal.IntuneAppProtection
import com.yaso202508appproxy.intunetestapp.auth.internal.MsAuthenticator
import com.yaso202508appproxy.intunetestapp.isSuccessStr
import com.yaso202508appproxy.intunetestapp.toLog
import com.yaso202508appproxy.intunetestapp.web.WebViewWrapper
import kotlinx.coroutines.launch

class TestActivity : AppCompatActivity() {

    private val logHistory = StringBuilder()
    private val logger = createLogger("Main")

    private lateinit var buttonMsalInit: Button
    private lateinit var buttonMsalSignIn: Button
    private lateinit var buttonMsalSilentAuth: Button
    private lateinit var buttonMsalInteractiveToken: Button

    private lateinit var buttonIntuneInit: Button
    private lateinit var buttonIntuneRegisterNotification: Button
    private lateinit var buttonIntuneRegisterMam: Button
    private lateinit var buttonIntuneGetMamStatus: Button
    private lateinit var buttonIntuneRemediateCompliance: Button

    private lateinit var buttonAccountGet: Button
    private lateinit var buttonAccountClear: Button

    private lateinit var buttonWebLoad: Button

    private lateinit var buttonLogShow: Button
    private lateinit var buttonLogClear: Button

    private lateinit var webViewWrapper: WebViewWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupWebView()
        setLogger()
    }

    override fun onDestroy() {
        super.onDestroy()
        AuthCacheManager.clear()
    }

    private fun initViews() {
        buttonMsalInit = findViewById(R.id.buttonMsalInit)
        buttonMsalInit.setOnClickListener { handleMsalInit() }

        buttonMsalSignIn = findViewById(R.id.buttonMsalSignIn)
        buttonMsalSignIn.setOnClickListener { handleMsalSignIn() }

        buttonMsalSilentAuth = findViewById(R.id.buttonMsalSilentAuth)
        buttonMsalSilentAuth.setOnClickListener { handleMsalSilentAuth() }

        buttonMsalInteractiveToken = findViewById(R.id.buttonMsalInteractiveToken)
        buttonMsalInteractiveToken.setOnClickListener { handleMsalInteractiveToken() }

        buttonIntuneInit = findViewById(R.id.buttonIntuneInit)
        buttonIntuneInit.setOnClickListener { handleIntuneInit() }

        buttonIntuneRegisterNotification = findViewById(R.id.buttonIntuneRegisterNotification)
        buttonIntuneRegisterNotification.setOnClickListener { handleIntuneRegisterNotification() }

        buttonIntuneRegisterMam = findViewById(R.id.buttonIntuneRegisterMam)
        buttonIntuneRegisterMam.setOnClickListener { handleIntuneRegisterMam() }

        buttonIntuneGetMamStatus = findViewById(R.id.buttonIntuneGetMamStatus)
        buttonIntuneGetMamStatus.setOnClickListener { handleIntuneGetMamStatus() }

        buttonIntuneRemediateCompliance = findViewById(R.id.buttonIntuneRemediateCompliance)
        buttonIntuneRemediateCompliance.setOnClickListener { handleIntuneRemediateCompliance() }

        buttonAccountGet = findViewById(R.id.buttonAccountGet)
        buttonAccountGet.setOnClickListener { handleAccountGet() }

        buttonAccountClear = findViewById(R.id.buttonAccountClear)
        buttonAccountClear.setOnClickListener { handleAccountClear() }

        buttonWebLoad = findViewById(R.id.buttonWebLoad)
        buttonWebLoad.setOnClickListener { handleWebLoad() }

        buttonLogShow = findViewById(R.id.buttonLogShow)
        buttonLogShow.setOnClickListener { handleLogShow() }

        buttonLogClear = findViewById(R.id.buttonLogClear)
        buttonLogClear.setOnClickListener { handleLogClear() }
    }

    private fun setupWebView() {
        webViewWrapper = WebViewWrapper(findViewById(R.id.webView))
    }

    private fun setLogger() {
        MsAuthenticator.setLogger(createLogger("MsAuth"))
        AuthCacheManager.setLogger(createLogger("AuthCache"))
        IntuneAppProtection.setLogger(createLogger("IntuneMam"))
        webViewWrapper.setLogger(createLogger("Web"))
    }


    private fun handleMsalInit() {
        lifecycleScope.launch {
            try {
                MsAuthenticator.initialize(applicationContext)
                logger.info("msal init: done")
            } catch (e: Exception) {
                logger.error("msal init", e)
            }
        }
    }

    private fun handleMsalSignIn() {
        lifecycleScope.launch {
            try {
                val result = MsAuthenticator.signIn(AuthScopes.GRAPH.scopes, this@TestActivity)
                logger.info(result.toLog())
            } catch (e: Exception) {
                logger.error("msal sign in", e)
            }
        }
    }

    private fun handleMsalSilentAuth() {
        lifecycleScope.launch {
            try {
                val result = MsAuthenticator.silentAuth(AuthScopes.PROXY.scopes)
                logger.info(result.toLog())
            } catch (e: Exception) {
                logger.error("msal sign in", e)
            }
        }
    }

    private fun handleMsalInteractiveToken() {
        lifecycleScope.launch {
            try {
                val result = MsAuthenticator.interactiveToken(AuthScopes.PROXY.scopes, this@TestActivity)
                logger.info(result.toLog())
            } catch (e: Exception) {
                logger.error("msal sign in", e)
            }
        }
    }

    private fun handleIntuneInit() {
        try {
            IntuneAppProtection.initialize()
            logger.info("intune init: done")
        } catch (e: Exception) {
            logger.error("intune init", e)
        }
    }

    private fun handleIntuneRegisterNotification() {
        try {
            IntuneAppProtection.registerNotification(
                { notification ->
                    logger.info(
                        arrayOf(
                            "intune enrollment notification received",
                            "- result: ${notification.enrollmentResult.name}"
                        ).joinToString(System.lineSeparator())
                    )
                },
                { notification ->
                    logger.info(
                        arrayOf(
                            "intune compliance notification received",
                            "- status: ${notification.complianceStatus.name}",
                            "- title: ${notification.complianceErrorTitle}",
                            "- message: ${notification.complianceErrorMessage}"
                        ).joinToString(System.lineSeparator())
                    )
                },
            )
            logger.info("intune register notification: done")
        } catch (e: Exception) {
            logger.error("intune register notification", e)
        }
    }

    private fun handleIntuneRegisterMam() {
        lifecycleScope.launch {
            try {
                val account = MsAuthenticator.getAccount()
                if (account == null) {
                    logger.error("intune register mam: account is null")
                    return@launch
                }

                IntuneAppProtection.registerMam(account)
                logger.info("intune register mam: done")
            } catch (e: Exception) {
                logger.error("intune register mam", e)
            }
        }
    }

    private fun handleIntuneGetMamStatus() {
        lifecycleScope.launch {
            try {
                val account = MsAuthenticator.getAccount()
                if (account == null) {
                    logger.error("intune get mam status: account is null")
                    return@launch
                }

                val status = IntuneAppProtection.getMamStatus(account)
                logger.info(status?.name ?: "null")
            } catch (e: Exception) {
                logger.error("intune get mam status", e)
            }
        }
    }

    private fun handleIntuneRemediateCompliance() {
        lifecycleScope.launch {
            try {
                val account = MsAuthenticator.getAccount()
                if (account == null) {
                    logger.error("intune remediate compliance: account is null")
                    return@launch
                }

                IntuneAppProtection.remediateCompliance(account)
                logger.info("intune remediate compliance: done")
            } catch (e: Exception) {
                logger.error("intune remediate compliance", e)
            }
        }
    }

    private fun handleAccountGet() {
        lifecycleScope.launch {
            try {
                val account = AuthService.getAccount()
                logger.info(account?.toLog() ?: "null")
            } catch (e: Exception) {
                logger.error("account get", e)
            }
        }
    }

    private fun handleAccountClear() {
        lifecycleScope.launch {
            try {
                val result = AuthService.clearAccount()
                logger.info("account clear: ${result.isSuccessStr()}")
            } catch (e: Exception) {
                logger.error("account clear", e)
            }
        }
    }

    private fun handleWebLoad() {
        try {
            webViewWrapper.load()
        } catch (e: Exception) {
            logger.error("web load", e)
        }
    }

    private fun handleLogShow() {
        try {
            showMsgDialog("log", logHistory.toString())
        } catch (e: Exception) {
            logger.error("log show", e)
        }
    }

    private fun handleLogClear() {
        try {
            logHistory.clear()
            showMsg("log clear: done")
        } catch (e: Exception) {
            logger.error("log clear", e)
        }
    }

    private fun createLogger(tag: String): AppLogger = object : AppLogger {
        override fun info(msg: String) {
            Log.d(tag, msg)
            val log = "[$tag] $msg"
            logHistory.appendLine(log)
            showMsg(log)
        }

        override fun error(msg: String, exception: Exception?) {
            Log.e(tag, msg, exception)
            val log = "[$tag] $msg ${exception ?: ""}"
            logHistory.appendLine(log)
            showMsg(log)
        }
    }

    private fun showMsg(msg: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showMsgDialog(title: String, msg: String) {
        runOnUiThread {
            AlertDialog.Builder(this@TestActivity)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}