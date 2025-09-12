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
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.R
import com.yaso202508appproxy.intunetestapp.toLog
import com.yaso202508appproxy.intunetestapp.truncate
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
     * - 成功した場合は認証情報を戻す
     */
    suspend fun silentAuth(scopes: List<String>): IAuthenticationResult? {
        val app = msalApp
        if (app == null) {
            logger?.error("silentAuth: msal app is null")
            return null
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("silentAuth: account is null")
            return null
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
                            } else {
                                logger?.error("silentAuth: result is null")
                            }
                            continuation.resume(authenticationResult)
                        }

                        override fun onError(exception: MsalException?) {
                            logger?.error("silentAuth", exception)
                            continuation.resume(null)
                        }
                    })
                    .build()
            )
        }
    }

    /**
     * 画面操作でサインイン
     * - 認証情報を返す
     */
    suspend fun interactiveSignIn(scopes: List<String>, activity: Activity): IAuthenticationResult? {
        val app = msalApp
        if (app == null) {
            logger?.error("signIn: msal app is null")
            return null
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
                            } else {
                                logger?.error("interactiveSignIn: result is null")
                            }
                            continuation.resume(authenticationResult)
                        }

                        override fun onError(exception: MsalException?) {
                            logger?.error("interactiveSignIn", exception)
                            continuation.resume(null)
                        }

                        override fun onCancel() {
                            logger?.error("interactiveSignIn: onCancel")
                            continuation.resume(null)
                        }

                    })
                    .build()
            )
        }
    }

    /**
     * サインイン
     * - 認証情報を戻す
     */
    suspend fun signIn(scopes: List<String>, activity: Activity): IAuthenticationResult? {
        return silentAuth(scopes) ?: interactiveSignIn(scopes, activity)
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