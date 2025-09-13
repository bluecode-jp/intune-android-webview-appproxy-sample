package com.yaso202508appproxy.intunetestapp.web

import android.webkit.JavascriptInterface
import com.yaso202508appproxy.intunetestapp.BuildConfig
import com.yaso202508appproxy.intunetestapp.auth.AuthService

/**
 * Javascriptで実行可能なAndroidアプリケーションの関数
 */
object JavascriptInterface {
    /**
     * AppProxyのORIGINを取得
     */
    @JavascriptInterface
    fun getProxyOrigin() = BuildConfig.PROXY_ORIGIN

    /**
     * AppProxyのscopeを取得
     */
    @JavascriptInterface
    fun getProxyScope() = BuildConfig.PROXY_SCOPE

    /**
     * アクセストークンを取得
     */
    @JavascriptInterface
    fun acquireToken(scopes: Array<String>): String? = AuthService.acquireToken(scopes.toList())
}