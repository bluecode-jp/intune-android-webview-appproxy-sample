package com.yaso202508appproxy.intunetestapp.auth.internal

import androidx.annotation.WorkerThread
import com.microsoft.identity.client.IAuthenticationResult
import com.yaso202508appproxy.intunetestapp.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

object AuthCacheManager {
    private var logger: AppLogger? = null

    /**
     * 認証情報を取得
     * - キャッシュを使用して同期関数として実装
     */
    @WorkerThread
    fun acquireAuth(scopes: List<String>): IAuthenticationResult? {
        return runBlocking(Dispatchers.IO) {
            MsAuthenticator.silentAuth(scopes)
        }
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}