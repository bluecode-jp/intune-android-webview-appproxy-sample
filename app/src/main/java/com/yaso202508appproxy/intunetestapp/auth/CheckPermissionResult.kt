package com.yaso202508appproxy.intunetestapp.auth

/**
 * アクセス権限確認結果
 */
sealed class CheckPermissionResult {
    /**
     * 成功
     */
    data object Success : CheckPermissionResult()

    /**
     * 失敗
     */
    sealed class Failure : CheckPermissionResult() {

        /**
         * タイムアウト
         */
        data object Timeout: Failure()

        /**
         * 認証エラー
         */
        data class AuthFailed(val authResult: AuthResult.Failure) : Failure()
    }
}