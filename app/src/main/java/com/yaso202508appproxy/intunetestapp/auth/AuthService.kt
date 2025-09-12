package com.yaso202508appproxy.intunetestapp.auth

import android.app.Activity
import android.content.Context
import androidx.annotation.WorkerThread
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.auth.internal.AuthCacheManager
import com.yaso202508appproxy.intunetestapp.auth.internal.IntuneAppProtection
import com.yaso202508appproxy.intunetestapp.auth.internal.MsAuthenticator
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
    suspend fun initialize(context: Context): Boolean {
        var result = MsAuthenticator.initialize(context)
        if (!result) {
            return false
        }

        result = IntuneAppProtection.initialize()
        if (!result) {
            return false
        }

        return true
    }

    /**
     * アカウントをセット
     * - signInScopesには認証前でもアクセス可能なスコープを指定（AppProxyは不可）
     */
    suspend fun setAccount(
        signInScopes: List<String>,
        activity: Activity
    ): IAccount? {
        val authInfo = MsAuthenticator.signIn(signInScopes, activity)
        if (authInfo == null) {
            return null
        }

        val result = IntuneAppProtection.registerMam()
        if (!result) {
            return null
        }

        return authInfo.account
    }

    /**
     * AppProxyにアクセス可能になるまで待機
     */
    @WorkerThread
    suspend fun waitForAppProxyAccessReady(
        appProxyScopes: List<String>,
        timeOutMillis: Long,
        intervalMillis: Long
    ) {
        withTimeout(timeOutMillis) {
            while (IntuneAppProtection.getMamStatus() != MAMEnrollmentManager.Result.ENROLLMENT_SUCCEEDED) {
                delay(intervalMillis)
            }

            while (AuthCacheManager.acquireAuth(appProxyScopes) == null) {
                delay(intervalMillis)
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

        IntuneAppProtection.unregisterMam(account)
        return true
    }

    suspend fun getAccount(): IAccount? = MsAuthenticator.getAccount()

    @WorkerThread
    fun acquireAuth(scopes: List<String>): IAuthenticationResult? = AuthCacheManager.acquireAuth(scopes)

    @WorkerThread
    fun acquireToken(scopes: List<String>): String? = acquireAuth(scopes)?.accessToken

    fun setLogger(logger: AppLogger) {
        MsAuthenticator.setLogger(logger)
        AuthCacheManager.setLogger(logger)
        IntuneAppProtection.setLogger(logger)
    }
}