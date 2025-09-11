package com.yaso202508appproxy.intunetestapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

object AccessTokenManager {
    fun acquire(scopes: List<String>): String? = runBlocking(Dispatchers.IO) {
        withTimeout(1000L) {
            AppProxyAuthManager.acquireTokenSync(scopes)
        }
    }
}