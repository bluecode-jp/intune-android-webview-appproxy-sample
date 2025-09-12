package com.yaso202508appproxy.intunetestapp.auth.internal

import androidx.annotation.WorkerThread
import com.microsoft.identity.client.IAuthenticationResult
import com.yaso202508appproxy.intunetestapp.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.util.concurrent.ConcurrentHashMap

typealias AuthCacheKey = Set<String>

object AuthCacheManager {
    private var logger: AppLogger? = null

    const val EXPIRES_MINUTES = 2
    const val SHOULD_REFRESH_MINUTES = 5
    const val FETCH_TIMEOUT_MILLIS = 3000L

    /**
     * 有効期限切れか判定
     */
    fun IAuthenticationResult.expired() = System.currentTimeMillis() >= (this.expiresOn.time - EXPIRES_MINUTES * 60 * 1000)

    /**
     * 有効期限間近か判定
     */
    fun IAuthenticationResult.shouldRefresh() = System.currentTimeMillis() >= (this.expiresOn.time - SHOULD_REFRESH_MINUTES * 60 * 1000)

    /**
     * 有効期限間近のキャッシュを更新するCoroutineScope
     */
    private var backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * scopesごとにキャッシュを保持
     */
    private val cacheMap = ConcurrentHashMap<AuthCacheKey, IAuthenticationResult>()

    /**
     * scopesごとにロック機構を保持
     */
    private val mutexMap = ConcurrentHashMap<AuthCacheKey, Mutex>()

    /**
     * scopesをキーに変換
     */
    private fun scopesToKey(scopes: List<String>): AuthCacheKey = scopes.toSet()

    /**
     * 認証情報を取得
     * - キャッシュを使用して同期関数として実装
     */
    @WorkerThread
    fun acquireAuth(scopes: List<String>): IAuthenticationResult? {
        val key = scopesToKey(scopes)
        val cache = cacheMap[key]

        if (cache != null && !cache.expired()) {
            if (cache.shouldRefresh()) {
                backgroundScope.launch {
                    fetchCache(scopes)
                }
            }

            return cache
        }

        return runBlocking(Dispatchers.IO) {
            fetchCache(scopes)
        }
    }

    /**
     * 認証情報を取得してキャッシュ
     * - Mutexを使って重複実行を回避
     */
    private suspend fun fetchCache(scopes: List<String>): IAuthenticationResult? {
        val key = scopesToKey(scopes)
        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }

        return mutex.withLock {
            val cache = cacheMap[key]
            if (cache != null && !cache.shouldRefresh()) {
                return@withLock cache
            }

            val result = withTimeout(FETCH_TIMEOUT_MILLIS) {
                MsAuthenticator.silentAuth(scopes)
            }
            if (result != null) {
                cacheMap[key] = result
            }
            result
        }
    }

    /**
     * 新しいアカウントのためにリフレッシュ
     * - サインアウトおよびアカウント切り替え時に必ず呼び出すこと
     */
    fun refresh() {
        close()
        backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    /**
     * 終了してリソースを解放
     * - アプリケーション終了時に必ず呼び出すこと
     */
    fun close() {
        backgroundScope.cancel()
        cacheMap.clear()
        mutexMap.clear()
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}