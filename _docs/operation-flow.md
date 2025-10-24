# 操作フローの実装

- [操作フローの実装](#操作フローの実装)
  - [概要](#概要)
  - [操作フローの構成要素](#操作フローの構成要素)
    - [WebViewの初期化](#webviewの初期化)
    - [認証機能の初期化](#認証機能の初期化)
    - [アカウントセットアップ](#アカウントセットアップ)
    - [WebViewでWebサイトを開く](#webviewでwebサイトを開く)
  - [サンプル](#サンプル)
  - [注意事項](#注意事項)

## 概要

アプリ起動からWebViewでApp ProxyのWebサイトにアクセスするまでの操作フローを実装する手順を説明します。

**使用するコンポーネント**
- `AuthService`（`auth/AuthService.kt`）：統合認証サービス
- `WebViewWrapper`（`web/WebViewWrapper.kt`）：HTTPリクエスト捕捉機能付きWebViewラッパー

**注意事項**
本サンプルアプリの画面は検証用途のため、ボタン押下で段階的に操作を進める仕組みになっています。
この実装ガイドでは、サンプルアプリの画面コードの説明ではなく、実際の開発現場で操作フローをどのように実装すべきかを説明します。

## 操作フローの構成要素

### WebViewの初期化

画面起動時にWebViewWrapperを初期化します。

```kotlin
// webViewWrapperは画面クラスのインスタンス変数
webViewWrapper = WebViewWrapper(findViewById(R.id.webView))
```

### 認証機能の初期化

アプリ起動時に認証機能を初期化します。
- 失敗した場合は適切なエラーハンドリングを行います。

```kotlin
val result = AuthService.initialize(applicationContext)
if (!result) {
    // エラーハンドリング
}
```

### アカウントセットアップ

Microsoft アカウントのセットアップを行います。
- `User.Read`など認証前でもアクセス可能なスコープを指定します。
- 失敗した場合は適切なエラーハンドリングを行います。

```kotlin
val account = AuthService.setAccount(listOf("User.Read"), this@MainActivity)
if (account == null) {
    // エラーハンドリング
}
```

### WebViewでWebサイトを開く

App Proxyアクセスの準備が完了するまで待機し、その後WebViewでサイトを開きます。
- `AuthService.waitForAppProxyAccessReady()`はIOスレッドで実行します。
- App Proxyスコープを指定します。
- 失敗した場合は適切なエラーハンドリングを行います。

```kotlin
try {
    withContext(Dispatchers.IO) {
        AuthService.waitForAppProxyAccessReady(listOf("https://my-website.biz/user_impersonation"), 5000L, 500L)
    }

    webViewWrapper.load()
} catch (e: TimeoutCancellationException) {
    // エラーハンドリング
}
```

## サンプル

***パターン1: 起動時にWebサイトまで自動で開く場合***

`ui1auto/AutoLaunchActivity.kt` をご覧ください。

***パターン2: アカウント設定とWebサイト表示をそれぞれボタンで操作する場合***

```kotlin
// MainActivity.kt
class MainActivity : AppCompatActivity() {
    private lateinit var webViewWrapper: WebViewWrapper
    private lateinit var signInButton: Button
    private lateinit var loadWebsiteButton: Button
    private var isSignedIn = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // UI要素の初期化
        signInButton = findViewById(R.id.signInButton)
        loadWebsiteButton = findViewById(R.id.loadWebsiteButton)
        
        // 初期状態では Web サイト読み込みボタンは無効
        loadWebsiteButton.isEnabled = false
        
        // WebView初期化
        setupWebView()
        
        // 認証機能初期化
        lifecycleScope.launch {
            if (!initializeAuth()) return@launch
            setupButtons()
        }
    }
    
    private fun setupWebView() {
        webViewWrapper = WebViewWrapper(findViewById(R.id.webView))
    }
    
    private suspend fun initializeAuth(): Boolean {
        val success = AuthService.initialize(applicationContext)
        if (!success) {
            showErrorDialog("認証機能の初期化に失敗しました")
        }
        return success
    }
    
    private fun setupButtons() {
        // サインインボタン
        signInButton.setOnClickListener {
            lifecycleScope.launch {
                signInButton.isEnabled = false
                
                val account = setupAccount()
                if (account != null) {
                    isSignedIn = true
                    signInButton.text = "サインイン完了"
                    loadWebsiteButton.isEnabled = true
                } else {
                    signInButton.isEnabled = true
                }
            }
        }
        
        // Webサイト読み込みボタン
        loadWebsiteButton.setOnClickListener {
            lifecycleScope.launch {
                loadWebsiteButton.isEnabled = false
                loadWebsiteButton.text = "読み込み中..."
                
                openWebsite()
                
                loadWebsiteButton.text = "Webサイトを読み込む"
                loadWebsiteButton.isEnabled = true
            }
        }
    }
    
    private suspend fun setupAccount(): IAccount? {
        val scopes = listOf("User.Read")
        val account = AuthService.setAccount(scopes, this@MainActivity)
        if (account == null) {
            showErrorDialog("サインインに失敗しました")
        }
        return account
    }
    
    private suspend fun openWebsite() {
        if (!isSignedIn) {
            showErrorDialog("先にサインインを完了してください")
            return
        }
        
        try {
            withContext(Dispatchers.IO) {
                AuthService.waitForAppProxyAccessReady(listOf("https://my-website.biz/user_impersonation"), 5000L, 500L)
            }
            webViewWrapper.load()
        } catch (e: TimeoutCancellationException) {
            showErrorDialog("App Proxyへの接続準備がタイムアウトしました")
        } catch (e: Exception) {
            Log.e("MainActivity", "Webサイト読み込みエラー", e)
            showErrorDialog("Webサイトの読み込みに失敗しました")
        }
    }
    
    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("エラー")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        AuthService.close()
    }
}
```

## 注意事項

**リソース管理**
- アプリケーション終了時に`AuthService.close()`を呼び出してリソースを解放してください

**エラーハンドリング**
- 各段階でのエラーを適切にキャッチし、ユーザーに分かりやすいメッセージを表示してください
- タイムアウト設定は実際のネットワーク環境に応じて調整してください

**スコープの設定**
- `setupAccount()`のスコープは認証前でもアクセス可能なものを指定してください
- App Proxyスコープは`checkPermission()`で使用してください
