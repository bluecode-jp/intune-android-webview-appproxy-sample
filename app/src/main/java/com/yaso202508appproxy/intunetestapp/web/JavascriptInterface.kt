package com.yaso202508appproxy.intunetestapp.web

import android.webkit.JavascriptInterface
import com.yaso202508appproxy.intunetestapp.BuildConfig
import com.yaso202508appproxy.intunetestapp.auth.AuthService

object JavascriptInterface {
    @JavascriptInterface
    fun getProxyOrigin() = BuildConfig.PROXY_ORIGIN

    @JavascriptInterface
    fun getProxyScope() = BuildConfig.PROXY_SCOPE

    @JavascriptInterface
    fun acquireToken(scopes: Array<String>): String? = AuthService.acquireToken(scopes.toList())
}