package com.yaso202508appproxy.intunetestapp.auth

import com.microsoft.identity.client.IAuthenticationResult

/**
 * 認証結果
 */
sealed class AuthResult {

    /**
     * 成功
     */
    data class Success(
        val info: IAuthenticationResult
    ) : AuthResult()

    /**
     * 失敗
     */
    sealed class Failure : AuthResult() {

        /**
         * アカウントなし
         */
        data object NoAccount: Failure()

        /**
         * 認証結果なし
         */
        data object NoResult: Failure()

        /**
         * キャンセル
         */
        data object Canceled: Failure()

        /**
         * 予期せぬエラー
         */
        data class Unknown(val exception: Exception?) : Failure()
    }
}
