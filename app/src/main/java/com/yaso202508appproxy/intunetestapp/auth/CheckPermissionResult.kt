package com.yaso202508appproxy.intunetestapp.auth

import com.microsoft.identity.client.exception.MsalUiRequiredException

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
        data object Timeout: Failure()

        data class MfaRequired(val exception: MsalUiRequiredException) : Failure()

        data class AuthFailed(val authResult: AuthResult.Failure) : Failure()
    }
}