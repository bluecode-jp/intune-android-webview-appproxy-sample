package com.yaso202508appproxy.intunetestapp

import android.webkit.JavascriptInterface

object JavascriptInterface {
    @JavascriptInterface
    fun getProxyOrigin() = BuildConfig.PROXY_ORIGIN

    @JavascriptInterface
    fun getProxyScope() = BuildConfig.PROXY_SCOPE

    @JavascriptInterface
    fun acquireToken(scopes: Array<String>): String? = AccessTokenManager.acquire(scopes.toList())
}