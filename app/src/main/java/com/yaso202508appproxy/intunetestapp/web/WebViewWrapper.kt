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

        return request.method in listOf("GET", "HEAD")
    }

    /**
     * Authorizationヘッダーにアクセストークンを付与して書き換えたHTTPリクエストを送信する
     */
    private fun sendRequestWithToken(originalRequest: WebResourceRequest, token: String): WebResourceResponse {
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

        val statusText = connection.responseMessage?.takeIf { it.isNotEmpty() }
            ?: getDefaultHttpStatusText(connection.responseCode)

        return WebResourceResponse(
            mimeType,
            encoding,
            connection.responseCode,
            statusText,
            responseHeaders,
            connection.inputStream
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
     * AppProxyのWebサイトを開く
     */
    fun load() {
        webView.loadUrl(BuildConfig.PROXY_URL)
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}