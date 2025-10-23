package com.yaso202508appproxy.intunetestapp.auth

import android.app.Activity
import com.microsoft.identity.client.IAccount
import com.yaso202508appproxy.intunetestapp.auth.internal.IntuneAppProtection
import com.yaso202508appproxy.intunetestapp.auth.internal.MsAuthenticator

/**
 * テスト用の認証機能統合サービス
 * - 本稼働ではこのサービスは不要
 */
object TestAuthService {
    /**
     * サインインのみ行う
     *  - MAM登録は行わない
     * - signInScopesには認証前でもアクセス可能なスコープを指定（AppProxyは不可）
     */
    suspend fun signIn(
        signInScopes: List<String>,
        activity: Activity
    ): IAccount? {
        val authResult = MsAuthenticator.signIn(signInScopes, activity)
        return if (authResult is AuthResult.Success) {
            authResult.info.account
        } else {
            null
        }
    }


    /**
     * MAM登録のみを行う
     * - サインイン済みである前提
     */
    suspend fun registerMam() = IntuneAppProtection.registerMam()

    /**
     * MAM登録状態を取得
     */
    suspend fun getMamStatus() = IntuneAppProtection.getMamStatus()
}