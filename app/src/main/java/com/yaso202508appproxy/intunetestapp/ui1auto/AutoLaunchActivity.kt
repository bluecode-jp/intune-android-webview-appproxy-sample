package com.yaso202508appproxy.intunetestapp.ui1auto

import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.microsoft.identity.client.IAccount
import com.yaso202508appproxy.intunetestapp.AuthScopes
import com.yaso202508appproxy.intunetestapp.R
import com.yaso202508appproxy.intunetestapp.auth.AuthService
import com.yaso202508appproxy.intunetestapp.auth.CheckPermissionResult
import com.yaso202508appproxy.intunetestapp.web.WebViewWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoLaunchActivity : AppCompatActivity() {

    private lateinit var lytLoading: LinearLayout

    private lateinit var lytRetry: ConstraintLayout
    private lateinit var btnRetry: Button
    private lateinit var btnSwitchAccount: Button
    private lateinit var txtSwitchAccount: TextView

    private lateinit var lytWeb: LinearLayout
    private lateinit var webViewWrapper: WebViewWrapper
    private lateinit var webView: WebView

    private val logger = createLogger("Main")

    private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_auto_launch)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 画面初期化
        initViews()

        // WebView初期化
        setupWebView()

        // ログ設定
        AuthService.setLogger(createLogger("Auth"))
        webViewWrapper.setLogger(createLogger("Web"))

        // 起動
        handleBoot()
    }

    override fun onDestroy() {
        super.onDestroy()

        // AuthServiceリソース解放
        AuthService.close()
    }

    /**
     * 画面初期化
     */
    private fun initViews() {
        lytLoading = findViewById(R.id.lytLoading)

        lytRetry = findViewById(R.id.lytRetry)
        btnRetry = findViewById(R.id.btnRetry)
        btnRetry.setOnClickListener { handleRetryClick() }
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount)
        btnSwitchAccount.setOnClickListener { handleSwitchAccountClick() }
        txtSwitchAccount = findViewById(R.id.txtSwitchAccount)

        lytWeb = findViewById(R.id.lytWeb)
        webView = findViewById(R.id.webView)

        // サンプル用の戻るボタン
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            showRetryLayout(true)
        }
    }

    /**
     * WebView初期化
     */
    private fun setupWebView() {
        webViewWrapper = WebViewWrapper(findViewById(R.id.webView))
    }

    /**
     * 起動
     * - OnCreateから呼び出す
     */
    private fun handleBoot() {
        lifecycleScope.launch {
            try {
                autoLaunch()
            } catch (exception: Exception) {
                logger.error("boot", exception)
                showDialog(
                    "予期せぬエラー",
                    "予期せぬエラーが発生しました。アプリケーション管理者にお問い合わせください。"
                )
                showRetryLayout(false)
            }
        }
    }

    /**
     * 再試行ボタン押下
     */
    private fun handleRetryClick() {
        lifecycleScope.launch {
            try {
                autoLaunch()
            } catch (exception: Exception) {
                logger.error("retry", exception)
                showDialog(
                    "予期せぬエラー",
                    "予期せぬエラーが発生しました。アプリケーション管理者にお問い合わせください。"
                )
                showRetryLayout(false)
            }
        }
    }

    /**
     * 別のアカウントを利用ボタン押下
     */
    private fun handleSwitchAccountClick() {
        showConfirmDialog(
            "別アカウントを利用",
            "セキュリティ保護のためアプリが一度終了する場合があります。続行しますか？"
        ) { _, _ ->
            lifecycleScope.launch {
                try {
                    switchAccount()
                } catch (exception: Exception) {
                    logger.error("switch account", exception)
                    showDialog(
                        "予期せぬエラー",
                        "予期せぬエラーが発生しました。アプリケーション管理者にお問い合わせください。"
                    )
                    showRetryLayout(true)
                }
            }
        }
    }

    /**
     * サインインからWebサイト表示まで一括で自動実行
     */
    private suspend fun autoLaunch() {
        showLoadingLayout()
        initializeAuth()
        setupAccount() ?: return
        openWebsite()
    }

    /**
     * 認証サービス初期化
     */
    private suspend fun initializeAuth() {
        AuthService.initialize(applicationContext)
    }

    /**
     * アカウント設定
     */
    private suspend fun setupAccount(): IAccount? {
        val account = AuthService.setAccount(AuthScopes.GRAPH.scopes, this)
        if (account == null) {
            showDialog(
                "サインインエラー",
                "サインインに失敗しました。再度お試しください。"
            )
            showRetryLayout(false)
        }
        return account
    }

    /**
     * Webサイトを開く
     * - アクセス権限を確認してOKの場合
     */
    private suspend fun openWebsite() {
        val checkResult = withContext(Dispatchers.IO) {
            AuthService.checkPermission(AuthScopes.PROXY.scopes, 5000L, 500L)
        }

        when (checkResult) {
            is CheckPermissionResult.Success -> {
                webViewWrapper.load()
                showWebLayout()
            }
            is CheckPermissionResult.Failure.Timeout -> {
                showDialog(
                    "アクセス権限確認エラー",
                    "アクセス権限の確認中にタイムアウトが発生しました。再度お試しください。"
                )
                showRetryLayout(true)
            }
            is CheckPermissionResult.Failure.AuthFailed -> {
                showDialog(
                    "アクセス権限確認エラー",
                    "アクセス権限がありません。アプリケーション管理者にお問い合わせいただくか、別のアカウントをご利用ください。"
                )
                showRetryLayout(true)
            }
        }
    }

    /**
     * 別のアカウントに切り替え
     */
    private suspend fun switchAccount() {
        AuthService.clearAccount()
        autoLaunch()
    }

    /**
     * 読込中画面を表示
     */
    private fun showLoadingLayout() {
        lytLoading.visibility = View.VISIBLE
        lytRetry.visibility = View.GONE
        lytWeb.visibility = View.GONE
    }

    /**
     * 再試行画面を表示
     */
    private fun showRetryLayout(showsSwitchAccount: Boolean) {
        lytLoading.visibility = View.GONE
        lytRetry.visibility = View.VISIBLE
        lytWeb.visibility = View.GONE

        if (showsSwitchAccount) {
            btnSwitchAccount.visibility = View.VISIBLE
            txtSwitchAccount.visibility = View.VISIBLE
        } else {
            btnSwitchAccount.visibility = View.GONE
            txtSwitchAccount.visibility = View.GONE
        }
    }

    /**
     * Web画面を表示
     */
    private fun showWebLayout() {
        lytLoading.visibility = View.GONE
        lytRetry.visibility = View.GONE
        lytWeb.visibility = View.VISIBLE
    }

    /**
     * ダイアログ表示
     */
    private fun showDialog(title: String, message: String, onConfirm: OnClickListener? = null) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", onConfirm)
            .show()
    }

    /**
     * 確認ダイアログ表示
     */
    private fun showConfirmDialog(title: String, message: String, onConfirm: OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", onConfirm )
            .setNegativeButton("Cancel", null)
            .show()
    }
}