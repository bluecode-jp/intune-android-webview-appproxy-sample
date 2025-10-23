package com.yaso202508appproxy.intunetestapp.auth.internal

import androidx.annotation.WorkerThread
import com.microsoft.identity.client.IAuthenticationResult
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.auth.AuthResult
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import java.util.concurrent.ConcurrentHashMap

typealias AuthCacheKey = Set<String>

object AuthCacheManager {
    private var logger: AppLogger? = null

    private const val EXPIRED_MINUTES = 2
    private const val SHOULD_REFRESH_MINUTES = 5

    /**
     * 有効期限切れか判定
     */
    private fun IAuthenticationResult.expired() = System.currentTimeMillis() >= (this.expiresOn.time - EXPIRED_MINUTES * 60 * 1000)

    /**
     * 有効期限間近か判定
     */
    private fun IAuthenticationResult.shouldRefresh() = System.currentTimeMillis() >= (this.expiresOn.time - SHOULD_REFRESH_MINUTES * 60 * 1000)

    /**
     * 有効期限間近のキャッシュを更新するCoroutineScope
     */
    private var backgroundScope: CoroutineScope? = null

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
    fun acquireAuth(scopes: List<String>): AuthResult {
        val key = scopesToKey(scopes)
        val cache = cacheMap[key]

        if (cache != null && !cache.expired()) {
            if (cache.shouldRefresh()) {
                ensureBackgroundScope().launch {
                    fetchCache(scopes)
                }
            }

            return AuthResult.Success(cache)
        }

        return runBlocking(Dispatchers.IO) {
            fetchCache(scopes)
        }
    }

    /**
     * 認証情報を取得してキャッシュ
     * - Mutexを使って重複実行を回避
     */
    private suspend fun fetchCache(scopes: List<String>): AuthResult {
        val key = scopesToKey(scopes)
        val mutex = mutexMap.computeIfAbsent(key) { Mutex() }

        return mutex.withLock {
            val cache = cacheMap[key]
            if (cache != null && !cache.shouldRefresh()) {
                return@withLock AuthResult.Success(cache)
            }

            val result = MsAuthenticator.silentAuth(scopes)
            if (result is AuthResult.Success) {
                cacheMap[key] = result.info
            }
            result
        }
    }

    /**
     * 有効なbackgroundScopeが無ければ作成する
     */
    private fun ensureBackgroundScope(): CoroutineScope {
        var scope = backgroundScope
        if (scope == null || scope.coroutineContext[Job]?.isCancelled == true) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        backgroundScope = scope
        return scope
    }

    /**
     * リソースをクリア
     * - サインアウトおよびアカウント切り替え時に必ず呼び出すこと
     * - アプリケーション終了時に必ず呼び出すこと
     */
    fun clear() {
        backgroundScope?.cancel()
        cacheMap.clear()
        mutexMap.clear()
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}