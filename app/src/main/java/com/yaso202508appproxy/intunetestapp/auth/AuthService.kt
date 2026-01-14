package com.yaso202508appproxy.intunetestapp.auth

import android.app.Activity
import android.content.Context
import androidx.annotation.WorkerThread
import com.microsoft.identity.client.IAccount
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.auth.internal.AuthCacheManager
import com.yaso202508appproxy.intunetestapp.auth.internal.IntuneAppProtection
import com.yaso202508appproxy.intunetestapp.auth.internal.MsAuthenticator
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * 認証機能を統合したサービス
 * - auth以外からはこのサービスのみを使うこと。internalは直接使わない。
 */
object AuthService {
    /**
     * 初期化
     * - contextにはapplicationContextを設定すること
     */
    suspend fun initialize(context: Context) {
        MsAuthenticator.initialize(context)
        IntuneAppProtection.initialize()
    }

    /**
     * アカウントをセット
     * - signInScopesには認証前でもアクセス可能なスコープを指定（AppProxyは不可）
     */
    suspend fun setAccount(
        signInScopes: List<String>,
        activity: Activity
    ): IAccount? {
        val authResult = MsAuthenticator.signIn(signInScopes, activity)
        if (authResult !is AuthResult.Success) {
            return null
        }

        val account = authResult.info.account
        IntuneAppProtection.registerMam(account)

        return account
    }

    /**
     * AppProxyアクセス権限確認
     */
    @WorkerThread
    suspend fun checkPermission(
        appProxyScopes: List<String>,
        timeOutMillis: Long,
        intervalMillis: Long
    ): CheckPermissionResult {
        val account = MsAuthenticator.getAccount()
        if (account == null) {
            return CheckPermissionResult.Failure.AuthFailed(
                AuthResult.Failure.NoAccount
            )
        }

        try {
            withTimeout(timeOutMillis) {
                while (IntuneAppProtection.getMamStatus(account) != MAMEnrollmentManager.Result.ENROLLMENT_SUCCEEDED) {
                    delay(intervalMillis)
                }
            }
        } catch (exception: TimeoutCancellationException) {
            return CheckPermissionResult.Failure.Timeout
        }

        return when (val authResult = AuthCacheManager.acquireAuth(appProxyScopes)) {
            is AuthResult.Failure -> {
                CheckPermissionResult.Failure.AuthFailed(authResult)
            }
            else -> {
                CheckPermissionResult.Success
            }
        }
    }

    /**
     * アカウントをクリア
     */
    suspend fun clearAccount(): Boolean {
        val account = MsAuthenticator.signOut()
        if (account == null) {
            return false
        }

        AuthCacheManager.clear()
        IntuneAppProtection.unregisterMam(account)

        return true
    }

    suspend fun getAccount(): IAccount? = MsAuthenticator.getAccount()

    @WorkerThread
    fun acquireAuth(scopes: List<String>): AuthResult = AuthCacheManager.acquireAuth(scopes)

    @WorkerThread
    fun acquireToken(scopes: List<String>): String? {
        val authResult = acquireAuth(scopes)
        return if (authResult is AuthResult.Success) {
            authResult.info.accessToken
        } else {
            null
        }
    }

    fun setLogger(logger: AppLogger) {
        MsAuthenticator.setLogger(logger)
        AuthCacheManager.setLogger(logger)
        IntuneAppProtection.setLogger(logger)
    }

    /**
     * 終了してリソースを解放
     * - アプリケーション終了時に必ず呼び出すこと
     */
    fun close() {
        AuthCacheManager.clear()
    }
}