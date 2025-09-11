package com.yaso202508appproxy.intunetestapp

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("SetJavaScriptEnabled")
class WebViewWrapper(private val webView: WebView) {
    @ForInvestigationOnly
    private var logger: Logger? = null

    init {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
            }

            webViewClient = object: WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return try {
                        if (request != null && shouldAddHeaders(request)) {
                            val token = AccessTokenManager.acquire(AuthScopes.PROXY.scopes)

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
    }

    private fun shouldAddHeaders(request: WebResourceRequest): Boolean {
        if (!request.url.toString().startsWith(BuildConfig.PROXY_ORIGIN)) {
            return false
        }

        return request.method in listOf("GET", "HEAD")
    }

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

        return WebResourceResponse(
            mimeType,
            encoding,
            connection.responseCode,
            connection.responseMessage,
            responseHeaders,
            connection.inputStream
        )
    }

    fun load() {
        webView.loadUrl(BuildConfig.PROXY_URL)
    }

    @ForInvestigationOnly
    fun setLogger(logger: Logger) {
        this.logger = logger
    }
}