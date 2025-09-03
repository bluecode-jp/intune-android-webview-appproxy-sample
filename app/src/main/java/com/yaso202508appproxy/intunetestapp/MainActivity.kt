package com.yaso202508appproxy.intunetestapp

import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
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
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.MAMServiceAuthenticationCallback
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var msalApp: ISingleAccountPublicClientApplication
    private lateinit var mamEnrollmentManager: MAMEnrollmentManager

    private var currentAccount: IAccount? = null

    private var myToken: String? = null

    private lateinit var btnSso: Button
    private lateinit var btnAccount: Button
    private lateinit var txtToken: EditText
    private lateinit var btnToken: Button
    private lateinit var btnTokenShow: Button
    private lateinit var btnSignOut: Button
    private lateinit var btnMamStatus: Button
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
        initMam()
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
            logInfo("acquireTokenSilent", "return: ${shortenToken(accessToken)}", true)
            return accessToken
        } catch (exception: Exception) {
            logError("acquireTokenSilent", exception.message ?: "failed")
            return null
        }
    }

    private fun initMam() {
        mamEnrollmentManager = MAMComponents.get(MAMEnrollmentManager::class.java)!!
        mamEnrollmentManager.registerAuthenticationCallback(object: MAMServiceAuthenticationCallback {
            override fun acquireToken(upn: String, aadId: String, resourceId: String): String? {
                logInfo("MAM.acquireToken", "upn = ${upn}, aadId = ${aadId}, resourceId = ${resourceId}")
                val accessToken = acquireTokenSilent(listOf("${resourceId}/.default"))
                logInfo("MAM.acquireToken", "return = ${shortenToken(accessToken)}")
                return accessToken
            }
        })
    }

    private fun mamRegisterAccount(account: IAccount) {
        mamEnrollmentManager.registerAccountForMAM(
            account.username,
            account.id,
            account.tenantId,
            account.authority
        )
        logInfo("mamRegisterAccount", "success")
    }

    private fun mamUnregisterAccount(account: IAccount) {
        mamEnrollmentManager.unregisterAccountForMAM(
            account.username,
            account.id
        )
        logInfo("mamUnregisterAccount", "success")
    }

    private fun mamShowStatus() {
        val account = currentAccount

        if (account == null) {
            logInfo("mamShowStatus", "account is null", true)
        } else {
            val result = mamEnrollmentManager.getRegisteredAccountStatus(
                account.username,
                account.id
            )
            logInfo("mamShowStatus", result?.name ?: "null", true)
        }
    }

    private fun setAccount(account: IAccount) {
        currentAccount = account
        mamRegisterAccount(account)
    }

    private fun clearAccount() {
        val account = currentAccount
        currentAccount = null
        if (account != null) {
            mamUnregisterAccount(account)
        }
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
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
        WebView.setWebContentsDebuggingEnabled(true)

        btnSso = findViewById(R.id.btnSso)
        btnSso.setOnClickListener {
            sso()
        }

        btnAccount = findViewById(R.id.btnAccount)
        btnAccount.setOnClickListener {
            showAccount()
        }

        txtToken = findViewById(R.id.txtToken)

        btnToken = findViewById(R.id.btnToken)
        btnToken.setOnClickListener {
            Thread {
                myToken = acquireTokenSilent(listOf(txtToken.text.toString()))
            }.start()
        }

        btnTokenShow = findViewById(R.id.btnShowToken)
        btnTokenShow.setOnClickListener {
            logInfo("btnTokenShow", shortenToken(myToken), true)
        }

        btnSignOut = findViewById(R.id.btnSignOut)
        btnSignOut.setOnClickListener {
            signOut()
        }

        btnMamStatus = findViewById(R.id.btnMamStatus)
        btnMamStatus.setOnClickListener {
            mamShowStatus()
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

    private fun logInfo(tag: String, msg: String, show: Boolean = false) {
        Log.d(tag, msg)
        logHistory.appendLine("[${tag}] ${msg}")

        if (show) {
            runOnUiThread {
                Toast.makeText(this, "[${tag}] ${msg}", Toast.LENGTH_LONG).show()
            }
        }
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
        if (myToken == null) {
            webView.webViewClient = WebViewClient()
            webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net")
        } else {
            webView.webViewClient = WebViewClient()
//            webView.webViewClient = object : WebViewClient() {
//                override fun shouldInterceptRequest(
//                    view: WebView,
//                    request: WebResourceRequest
//                ): WebResourceResponse? {
//                    try {
//                        val url = request.url.toString()
//                        val conn = URL(url).openConnection() as HttpURLConnection
//                        conn.instanceFollowRedirects = true
//
//                        // Authorization ヘッダー
//                        conn.setRequestProperty("Authorization", "Bearer $myToken")
//                        request.requestHeaders.forEach { (k, v) ->
//                            if (!k.equals("Authorization", true)) conn.setRequestProperty(k, v)
//                        }
//
//                        conn.connect()
//
//                        val contentType = conn.contentType?.substringBefore(";") ?: "text/html"
//                        val encoding = conn.contentEncoding ?: "utf-8"
//
//                        // ステータスコードを指定
//                        val statusCode = conn.responseCode
//                        val reason = conn.responseMessage ?: "OK"
//
//                        return WebResourceResponse(
//                            contentType,
//                            encoding,
//                            statusCode,
//                            reason,
//                            conn.headerFields.filterKeys { it != null }
//                                .mapValues { it.value.joinToString(",") },
//                            conn.inputStream
//                        )
//
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        return super.shouldInterceptRequest(view, request)
//                    }
//                }
//            }

            val headers = mapOf("Authorization" to "Bearer $myToken")
            webView.loadUrl("https://testwebsite-blucodeinc.msappproxy.net", headers)
        }
    }

    private fun shortenToken(token: String?): String {
        if (token == null) {
            return "null"
        }
        return "${token.take(10)}...${token.takeLast(10)}"
    }
}