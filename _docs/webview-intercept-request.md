# WebViewリクエスト捕捉機能の実装

- [WebViewリクエスト捕捉機能の実装](#webviewリクエスト捕捉機能の実装)
  - [概要](#概要)
  - [対応範囲](#対応範囲)
  - [実装アプローチ](#実装アプローチ)
    - [JavaScript Interface の実装](#javascript-interface-の実装)
    - [非同期通信の捕捉（JavaScript側実装）](#非同期通信の捕捉javascript側実装)
      - [主要機能](#主要機能)
      - [Fetch API改造](#fetch-api改造)
      - [XMLHttpRequest改造](#xmlhttprequest改造)
      - [JavaScript実行の管理](#javascript実行の管理)
    - [ページロード時のリクエスト捕捉（Android側実装）](#ページロード時のリクエスト捕捉android側実装)
      - [実装のポイント](#実装のポイント)
  - [未対応のリクエストについて](#未対応のリクエストについて)
    - [フォーム送信](#フォーム送信)
    - [その他のリクエストパターン](#その他のリクエストパターン)

## 概要

AndroidのWebViewからApp ProxyのWebサイトにアクセスするため、すべてのHTTPリクエストを捕捉してAuthorizationヘッダーにアクセストークンを自動付与する機能を実装します。

本サンプルアプリでは、`web/WebViewWrapper.kt`にWebViewラッパークラスとして実装し、JavaScript連携機能、アセット管理機能と組み合わせて動作します。

## 対応範囲

本サンプルアプリでは、以下のリクエストパターンに対応しています：

**対応済み**
- **非同期通信**: XMLHttpRequest、Fetch APIによるリクエスト
- **ページロード時のリクエスト**: HTML、CSS、JavaScript、画像などのリソース取得

**未対応（別途実装が必要）**
- **フォーム送信**: POST形式のフォーム送信
- **ダウンロード**: ファイルダウンロードリクエスト
- **その他、上記以外のリクエスト**

## 実装アプローチ

### JavaScript Interface の実装

AndroidアプリとWebView内JavaScriptの連携機能を`web/JavascriptInterface.kt`に実装しています。

```kotlin
// web/JavascriptInterface.kt
/**
 * Javascriptで実行可能なAndroidアプリケーションの関数
 */
object JavascriptInterface {
    /**
     * App ProxyのORIGINを取得
     */
    @JavascriptInterface
    fun getProxyOrigin() = BuildConfig.PROXY_ORIGIN

    /**
     * App Proxyのscopeを取得
     */
    @JavascriptInterface
    fun getProxyScope() = BuildConfig.PROXY_SCOPE

    /**
     * アクセストークンを取得
     */
    @JavascriptInterface
    fun acquireToken(scopes: Array<String>): String? = AuthService.acquireToken(scopes.toList())
}
```

**WebViewへの適用**
```kotlin
// web/WebViewWrapper.kt
webView.addJavascriptInterface(JavascriptInterface, "Android")
```

これにより、JavaScript側から`Android.acquireToken()`などのメソッドを呼び出せるようになります。

### 非同期通信の捕捉（JavaScript側実装）

`assets/intercept_request.js`にて、XMLHttpRequestとFetch APIを改造する処理を実装しています。

#### 主要機能

**URL判定機能**
- App ProxyのOriginかどうかを判定
- App Proxy以外のリクエストには処理を適用しない

**トークン取得機能**
- App ProxyリクエストでのみAndroidアプリからアクセストークンを取得
- 取得失敗時は元のリクエストをそのまま実行

**重複実行防止**
- `X-Token-Added`ヘッダーで処理済みをマーク
- AndroidのshouldInterceptRequestとの重複処理を回避

#### Fetch API改造

```javascript
// assets/intercept_request.js
const originalFetch = window.fetch;
window.fetch = async function (url, options) {
    const newOptions = options ?? {};

    const token = getProxyToken(url);
    if (token) {
        const newHeaders = new Headers(newOptions.headers);
        newHeaders.set("Authorization", `Bearer ${token}`);
        newHeaders.set(tokenAddedHeaderKey, "true");
        newOptions.headers = newHeaders;
    }

    return originalFetch(url, newOptions);
};
```

#### XMLHttpRequest改造

```javascript
// assets/intercept_request.js
XMLHttpRequest.prototype.send = function (data) {
    const token = getProxyToken(this._requestInfo.url);
    if (token) {
        this.setRequestHeader("Authorization", `Bearer ${token}`);
        this.setRequestHeader(tokenAddedHeaderKey, "true");
    }

    return originalSend.call(this, data);
};
```

#### JavaScript実行の管理

**TextAssetLoaderの実装**
```kotlin
// web/TextAssetLoader.kt
object TextAssetLoader {
    private val cache = mutableMapOf<String, String>()

    fun loadContent(context: Context, assetPath: String): String? {
        // アセットファイルの読み込みとキャッシュ機能
    }
}
```

**ページ読み込み完了時の適用**
```kotlin
// web/WebViewWrapper.kt
override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)

    if (view != null && url != null && isProxyOrigin(url)) {
        val script = TextAssetLoader.loadContent(view.context, "intercept_request.js")
        if (script != null) {
            view.evaluateJavascript(script, null)
        }
    }
}
```

### ページロード時のリクエスト捕捉（Android側実装）

`web/WebViewWrapper.kt`の`shouldInterceptRequest`メソッドでページ遷移やリソース読み込みリクエストを捕捉します。

#### 実装のポイント

**処理対象の判定**
```kotlin
// web/WebViewWrapper.kt
private fun shouldAddToken(request: WebResourceRequest): Boolean {
    if (!isProxyOrigin(request.url.toString())) {
        return false
    }

    if (request.requestHeaders.keys.any {
        it.equals("X-Token-Added", true)
    }) {
        return false
    }

    return request.method in listOf("GET", "HEAD")
}
```

**判定条件**
- GET、HEADメソッドのみ対応
- POST等のボディ付きリクエストは元リクエストのボディ取得が不可能なため非対応
- App ProxyのOriginのみ対応
- JavaScript側で既に処理済みのリクエストは除外

**shouldInterceptRequestの実装**
```kotlin
// web/WebViewWrapper.kt
override fun shouldInterceptRequest(
    view: WebView?,
    request: WebResourceRequest?
): WebResourceResponse? {
    return try {
        if (request != null && shouldAddToken(request)) {
            val token = AuthService.acquireToken(AuthScopes.PROXY.scopes)

            return if (token == null) {
                super.shouldInterceptRequest(view, request)
            } else {
                sendRequestWithToken(request, token)
            }
        } else {
            super.shouldInterceptRequest(view, request)
        }
    } catch(exception: Exception) {
        logger?.error("shouldInterceptRequest", exception)
        super.shouldInterceptRequest(view, request)
    }
}
```

このメソッドは、WebViewが発行するすべてのHTTPリクエストを捕捉し、以下の処理を実行します：

**リクエスト処理フロー**
- 処理対象判定：`shouldAddToken()`でApp Proxyリクエストかどうかを確認
- トークン取得：処理対象の場合、`AuthService.acquireToken()`でアクセストークンを取得
- リクエスト送信：
  - トークン取得成功時→`sendRequestWithToken()`でAuthorizationヘッダーを付与して送信
  - トークン取得失敗時→元のリクエストをそのまま実行
- エラー時：例外をキャッチして元のリクエストを実行

## 未対応のリクエストについて

### フォーム送信

**制約事項**
- shouldInterceptRequestではPOSTリクエストのボディが取得できない
- JavaScript側での対応が必須

**実装が必要な処理**
- submitイベントの捕捉
- フォームデータの取得と再送信
- CSRFトークンが含まれる場合の特別対応

### その他のリクエストパターン

WebViewからアクセスするWebサイトで他に発生する可能性のあるリクエストがあれば、個別に対応方法の調査・実装が必要です。
