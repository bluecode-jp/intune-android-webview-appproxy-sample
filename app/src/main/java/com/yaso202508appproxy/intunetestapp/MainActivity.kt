package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication.ISingleAccountApplicationCreatedListener
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.CurrentAccountCallback
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.SignOutCallback
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalException

class MainActivity : AppCompatActivity() {
    private lateinit var msalApp: ISingleAccountPublicClientApplication

    private var currentAccount: IAccount? = null

    private lateinit var btnSso: Button
    private lateinit var btnAccount: Button
    private lateinit var btnToken: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnLoad: Button
    private lateinit var btnLog: Button
    private lateinit var webView: WebView

    private val logHistory = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()

        initMsal()
    }

    private fun initMsal() {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            this,
            R.raw.msal_config,
            object: ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                    logInfo("initMsal", "created")
                }

                override fun onError(exception: MsalException) {
                    logError("initMsal", exception.message ?: "failed")
                }
            }
        )
    }

    private fun sso() {
        msalApp.getCurrentAccountAsync(object: CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount == null) {
                    logInfo("sso.onAccountLoaded", "account is null")
                    signIn()
                } else {
                    logInfo("sso.onAccountLoaded", "account found")
                    setAccount(activeAccount)
                }
            }

            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                if (currentAccount == null) {
                    logInfo("sso.onAccountChanged", "account is null")
                    signIn()
                } else {
                    logInfo("sso.onAccountChanged", "account found")
                    setAccount(currentAccount)
                }
            }

            override fun onError(exception: MsalException) {
                logError("sso.onError", exception.message ?: "failed")
            }
        })
    }

    private fun signIn() {
        val params = SignInParameters.builder()
            .withActivity(this)
            .withScopes(listOf("User.Read"))
            .withCallback(object: AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    logInfo("singIn", "success")
                    setAccount(authenticationResult.account)
                }

                override fun onError(exception: MsalException) {
                    logError("signIn", exception.message ?: "failed")
                }

                override fun onCancel() {
                    logError("signIn", "cancel")
                }
            })
            .build()
        msalApp.signIn(params)
    }

    private fun signOut() {
        msalApp.signOut(object: SignOutCallback {
            override fun onSignOut() {
                logInfo("signOut", "success")
                clearAccount()
            }

            override fun onError(exception: MsalException) {
                logError("signOut", exception.message ?: "failed")
            }
        })
    }

    @WorkerThread
    private fun acquireTokenSilent(scopes: List<String>): String? {
        try {
            logInfo("acquireTokenSilent", "scopes: ${scopes.joinToString(",")}")

            val account = msalApp.currentAccount.currentAccount
            if (account == null) {
                logError("MSAL", "acquireTokenSilent: account is null")
                return null
            }

            val params = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(scopes)
                .build()
            val result = msalApp.acquireTokenSilent(params)
            val accessToken = result?.accessToken
            logInfo("acquireTokenSilent", "return: ${accessToken?.take(10) ?: "null"}")
            return accessToken
        } catch (exception: Exception) {
            logError("acquireTokenSilent", exception.message ?: "failed")
            return null
        }
    }

    private fun setAccount(account: IAccount) {
        currentAccount = account
    }

    private fun clearAccount() {
        currentAccount = null
    }

    private fun showAccount() {
        val strBuilder = StringBuilder()
        val account = currentAccount

        if (account == null) {
            strBuilder.appendLine("No Account")
        } else {
            strBuilder.appendLine("ID: ${account.id}")
            strBuilder.appendLine("Username: ${account.username}")
        }

        AlertDialog.Builder(this)
            .setMessage(strBuilder.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun initViews() {
        webView = findViewById(R.id.webView)
        webView.apply {
            webViewClient = WebViewClient()

            settings.apply {
                javaScriptEnabled = true
            }
        }

        btnSso = findViewById(R.id.btnSso)
        btnSso.setOnClickListener {
            sso()
        }

        btnAccount = findViewById(R.id.btnAccount)
        btnAccount.setOnClickListener {
            showAccount()
        }

        btnToken = findViewById(R.id.btnToken)
        btnToken.setOnClickListener {
            Thread {
                acquireTokenSilent(listOf("User.Read"))
            }.start()
        }

        btnSignOut = findViewById(R.id.btnSignOut)
        btnSignOut.setOnClickListener {
            signOut()
        }

        btnLoad = findViewById(R.id.btnLoad)
        btnLoad.setOnClickListener {
            loadWebSite()
        }

        btnLog = findViewById(R.id.btnLog)
        btnLog.setOnClickListener {
            showLog()
        }
    }

    private fun logInfo(tag: String, msg: String) {
        Log.d(tag, msg)
        logHistory.appendLine("[${tag}] ${msg}")
    }

    private fun logError(tag: String, msg: String) {
        Log.e(tag, msg)
        logHistory.appendLine("[${tag}] ${msg}")
        runOnUiThread {
            Toast.makeText(this, "[${tag}] ${msg}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showLog() {
        AlertDialog.Builder(this)
            .setTitle("Log")
            .setMessage(logHistory.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadWebSite() {
        webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net")
    }
}