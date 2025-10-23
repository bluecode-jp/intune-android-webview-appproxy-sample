package com.yaso202508appproxy.intunetestapp.auth.internal

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication.ISingleAccountApplicationCreatedListener
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.CurrentAccountCallback
import com.microsoft.identity.client.ISingleAccountPublicClientApplication.SignOutCallback
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.R
import com.yaso202508appproxy.intunetestapp.auth.AuthResult
import com.yaso202508appproxy.intunetestapp.toLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object MsAuthenticator {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var logger: AppLogger? = null

    /**
     * MSALアプリケーション作成
     * - 値を戻す非同期関数として実装
     * - コールバックよりも処理完了のタイミングを検知しやすい
     */
    private suspend fun createMsalApp(
        context: Context
    ): ISingleAccountPublicClientApplication? = suspendCancellableCoroutine { continuation ->
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.msal_config,
            object : ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    if (application != null) {
                        logger?.info("createMsalApp: created")
                    } else {
                        logger?.error("createMsalApp: null")
                    }
                    continuation.resume(application)
                }

                override fun onError(exception: MsalException?) {
                    logger?.error("createMsalApp", exception)
                    continuation.resume(null)
                }

            }
        )
    }

    /**
     * 初期化
     * - contextにはapplicationContextを設定すること
     */
    suspend fun initialize(context: Context): Boolean {
        val app = createMsalApp(context)
        msalApp = app
        return app != null
    }

    /**
     * アカウントを取得
     * - onAccountChangedでは値を戻す処理は不要（onAccountLoadedも必ず呼ばれるため）
     */
    suspend fun getAccount(): IAccount? {
        val app = msalApp
        if (app == null) {
            logger?.error("getAccount: msal app is null")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            app.getCurrentAccountAsync(object : CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    logger?.info("getAccount.onAccountLoaded: account is ${activeAccount?.username}")
                    continuation.resume(activeAccount)
                }

                override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                    logger?.info("getAccount.onAccountChanged: from ${priorAccount?.username} to ${currentAccount?.username}")
                }

                override fun onError(exception: MsalException) {
                    logger?.error("getAccount.onError", exception)
                    continuation.resume(null)
                }
            })
        }
    }

    /**
     * サイレント認証
     * - サインインおよびアクセストークン取得に利用
     */
    suspend fun silentAuth(scopes: List<String>): AuthResult {
        val app = msalApp
        if (app == null) {
            logger?.error("silentAuth: msal app is null")
            return AuthResult.Failure.NotInitialized
        }

        val account = getAccount()
        if (account == null) {
            logger?.error(  "silentAuth: account is null")
            return AuthResult.Failure.NoAccount
        }

        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(scopes)
                    .withCallback(object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                            if (authenticationResult != null) {
                                logger?.info("silentAuth: success${System.lineSeparator()}${authenticationResult.toLog()}")
                                continuation.resume(AuthResult.Success(authenticationResult))
                            } else {
                                logger?.error("silentAuth: result is null")
                                continuation.resume(AuthResult.Failure.NoResult)
                            }
                        }

                        override fun onError(exception: MsalException?) {
                            logger?.error("silentAuth", exception)

                            if (exception is MsalUiRequiredException) {
                                continuation.resume(AuthResult.Failure.UiRequired(exception))
                            } else {
                                continuation.resume(AuthResult.Failure.Unknown(exception))
                            }
                        }
                    })
                    .build()
            )
        }
    }

    /**
     * 画面操作でサインイン
     */
    suspend fun interactiveSignIn(scopes: List<String>, activity: Activity): AuthResult {
        val app = msalApp
        if (app == null) {
            logger?.error("signIn: msal app is null")
            return AuthResult.Failure.NotInitialized
        }

        return suspendCancellableCoroutine { continuation ->
            app.signIn(
                SignInParameters.builder()
                    .withActivity(activity)
                    .withScopes(scopes)
                    .withCallback(object : AuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                            if (authenticationResult != null) {
                                logger?.info("interactiveSignIn: success${System.lineSeparator()}${authenticationResult.toLog()}")
                                continuation.resume(AuthResult.Success(authenticationResult))
                            } else {
                                logger?.error("interactiveSignIn: result is null")
                                continuation.resume(AuthResult.Failure.NoResult)
                            }
                        }

                        override fun onError(exception: MsalException?) {
                            logger?.error("interactiveSignIn", exception)
                            continuation.resume(AuthResult.Failure.Unknown(exception))
                        }

                        override fun onCancel() {
                            logger?.error("interactiveSignIn: onCancel")
                            continuation.resume(AuthResult.Failure.Canceled)
                        }

                    })
                    .build()
            )
        }
    }

    /**
     * サインイン
     */
    suspend fun signIn(scopes: List<String>, activity: Activity): AuthResult {
        val silentResult = silentAuth(scopes)
        if (silentResult is AuthResult.Success) {
            return silentResult
        }

        return interactiveSignIn(scopes, activity)
    }

    /**
     * サインアウト
     * - サインアウトしたアカウントを戻す
     */
    suspend fun signOut(): IAccount? {
        val app = msalApp
        if (app == null) {
            logger?.error("signOut: msal app is null")
            return null
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("signOut: account is null")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            app.signOut(object : SignOutCallback {
                override fun onSignOut() {
                    logger?.info( "signOut: success")
                    continuation.resume(account)
                }

                override fun onError(exception: MsalException) {
                    logger?.error("signOut", exception)
                    continuation.resume(null)
                }
            })
        }
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}