package com.yaso202508appproxy.intunetestapp.auth

import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.exception.MsalUiRequiredException

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
         * また初期化されていない
         */
        data object NotInitialized : Failure()

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
         * 画面操作要求エラー
         */
        data class UiRequired(val exception: MsalUiRequiredException) : Failure()

        /**
         * 予期せぬエラー
         */
        data class Unknown(val exception: Exception?) : Failure()
    }
}
