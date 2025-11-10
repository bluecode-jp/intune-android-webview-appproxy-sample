package com.yaso202508appproxy.intunetestapp.web

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.AuthScopes
import com.yaso202508appproxy.intunetestapp.BuildConfig
import com.yaso202508appproxy.intunetestapp.auth.AuthService
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
class WebViewWrapper(private val webView: WebView) {
    private var logger: AppLogger? = null

    init {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
            }

            webViewClient = object: WebViewClient() {
                /**
                 * ページ読み込み完了時に、intercept_request.jsを実行する。
                 * - JavaScriptが発信したHTTPリクエストを書き換える処理が実装される。
                 * - AppProxy以外のページでは実行しない。
                 */
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    if (view != null && url != null && isProxyOrigin(url)) {
                        val script = TextAssetLoader.loadContent(view.context, "intercept_request.js")
                        if (script != null) {
                            view.evaluateJavascript(script, null)
                        }
                    }
                }

                /**
                 * HTTPリクエストを捕捉して書き換える
                 * - Authorizationヘッダーにアクセストークンを付与
                 * - 書き換え対象を判別し、対象外のリクエストは書き換えずそのまま実行する。
                 * - 書き換え対象であっても、アクセストークンを取得できなかった場合はそのまま実行する。
                 */
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return try {
                        if (request != null && shouldAddToken(request)) {
                            sendRequestWithToken(request)
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }
                    } catch(exception: Exception) {
                        logger?.error("shouldInterceptRequest", exception)
                        if (view != null && request != null) {
                            showErrorPage(view, request, exception)
                        }
                        WebResourceResponse(null, null, null)
                    }
                }
            }
        }

        /**
         * 指定したAndroidアプリケーションの関数をJavascriptで実行可能にする
         */
        webView.addJavascriptInterface(JavascriptInterface, "Android")
    }

    /**
     * AppProxy内のURLかを判定
     */
    private fun isProxyOrigin(url: String): Boolean = url.startsWith(BuildConfig.PROXY_ORIGIN, true)

    /**
     * shouldInterceptRequestで書き換え対象にするか判定
     * - GET, HEAD メソッドのみ（元リクエストのボディの取得がAndroidでは不可能なため）
     * - AppProxy内のみ
     * - Javascriptで書き換え済みの場合は対象外
     */
    private fun shouldAddToken(request: WebResourceRequest): Boolean {
        if (!isProxyOrigin(request.url.toString())) {
            return false
        }

        if (request.requestHeaders.keys.any {
            it.equals("X-Token-Added", true)
        }) {
            return false
        }

        if (request.method !in listOf("GET", "HEAD")) {
            throw Exception("${request.method} should be handled in intercept_request.js")
        }

        return true
    }

    /**
     * Authorizationヘッダーにアクセストークンを付与して書き換えたHTTPリクエストを送信する
     */
    private fun sendRequestWithToken(originalRequest: WebResourceRequest): WebResourceResponse {
        val token = AuthService.acquireToken(AuthScopes.PROXY.scopes)
        if (token == null) {
            throw Exception("acquire token failed")
        }

        val connection = URL(originalRequest.url.toString()).openConnection() as HttpURLConnection

        connection.requestMethod = originalRequest.method
        originalRequest.requestHeaders.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        connection.setRequestProperty("Authorization", "Bearer $token")

        connection.connect()

        var mimeType = connection.contentType
        var encoding = connection.contentEncoding

        mimeType?.let { type ->
            if (type.contains(";")) {
                val parts = type.split(";")
                mimeType = parts[0].trim()
                for (i in 1 until parts.size) {
                    val part = parts[i].trim()
                    if (part.startsWith("charset=")) {
                        encoding = part.substring(8)
                    }
                }
            }
        }

        val responseHeaders = mutableMapOf<String, String>()
        connection.headerFields.forEach { (key, values) ->
            if (key != null && values.isNotEmpty()) {
                responseHeaders[key] = values[0]
            }
        }

        val statusCode = connection.responseCode
        val statusText = connection.responseMessage?.takeIf { it.isNotEmpty() }
            ?: getDefaultHttpStatusText(statusCode)

        val inputStream = if (statusCode >= 400) {
            connection.errorStream
        } else {
            connection.inputStream
        }

        logger?.info("method=${originalRequest.method} url=${originalRequest.url} status=$statusCode")

        if (300 <= statusCode && statusCode < 400) {
            throw Exception("status code $statusCode not allowed")
        }

        return WebResourceResponse(
            mimeType,
            encoding,
            statusCode,
            statusText,
            responseHeaders,
            inputStream
        )
    }

    /**
     * レスポンスステータスコードに対応するデフォルトの文字列を取得
     * - WebResourceResponseでは必須項目だが、サーバーからのレスポンスで空になる場合があるため対応。
     */
    private fun getDefaultHttpStatusText(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }
    }

    /**
     * shouldInterceptRequestで予期せぬ例外が発生した時に
     * エラーページを表示する
     */
    private fun showErrorPage(
        view: WebView,
        request: WebResourceRequest,
        exception: Exception
        ) {
        view.post {
            val html = """
                <html>
                    <head>
                        <meta charset='UTF-8'>
                        <title>Error</title>
                    </head>
                    <body style='font-family:sans-serif; padding:20px; background-color:#f8f8f8;'>
                        <h2>WebView.shouldInterceptRequestでエラーが発生しました</h2>
                        <p><strong>Method:</strong> ${request.method}</p>
                        <p><strong>URL:</strong> ${request.url}</p>
                        <p><strong>Message:</strong> ${exception.message ?: "Unknown error"}</p>
                    </body>
                </html>
            """

            view.stopLoading()
            view.loadDataWithBaseURL(
                "about:blank",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    /**
     * AppProxyのWebサイトを開く
     */
    fun load() {
        webView.loadUrl(BuildConfig.PROXY_URL)
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}