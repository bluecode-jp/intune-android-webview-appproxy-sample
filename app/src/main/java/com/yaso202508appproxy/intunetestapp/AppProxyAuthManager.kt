package com.yaso202508appproxy.intunetestapp

import android.app.Activity
import android.content.Context
import androidx.annotation.WorkerThread
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
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.MAMServiceAuthenticationCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppProxyAuthManager {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var mamEnrollmentManager: MAMEnrollmentManager? = null

    private var logger: Logger? = null

    fun initMsal(context: Context) {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.msal_config,
            object : ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    msalApp = application
                    logger?.info("initMsal.onCreated")
                }

                override fun onError(exception: MsalException?) {
                    logger?.error("initMsal.onError", exception)
                }

            }
        )
    }

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

    suspend fun sso(scopes: List<String>, activity: Activity) {
        val account = getAccount()

        if (account == null) {
            logger?.info("sso: account is null")
            signIn(scopes, activity)
        } else {
            logger?.info("sso: account is ${account.username}")
        }
    }

    fun signIn(scopes: List<String>, activity: Activity) {
        val app = msalApp
        if (app == null) {
            logger?.error("signIn: msal app is null")
            return
        }

        val params = SignInParameters.builder()
            .withActivity(activity)
            .withScopes(scopes)
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    logger?.info("signIn.onSuccess: account is ${authenticationResult.account.username}")
                }

                override fun onError(exception: MsalException) {
                    logger?.error("signIn.onError", exception)
                }

                override fun onCancel() {
                    logger?.error("signIn.onCancel")
                }
            })
            .build()
        app.signIn(params)
    }

    suspend fun signOut() {
        val app = msalApp
        if (app == null) {
            logger?.error("signOut: msal app is null")
            return
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("signOut: account is null")
            return
        }

        app.signOut(object : SignOutCallback {
            override fun onSignOut() {
                logger?.info( "signOut.onSignOut")
                unregisterMam(account)
            }

            override fun onError(exception: MsalException) {
                logger?.error("signOut.onError", exception)
            }
        })
    }

    suspend fun acquireToken(scopes: List<String>): String? {
        val app = msalApp
        if (app == null) {
            logger?.error("acquireToken: msal app is null")
            return null
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("acquireToken: account is null")
            return null
        }

        logger?.info("acquireToken: scopes = ${scopes.joinToString(",")}")

        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(scopes)
                    .withCallback(object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            val accessToken = authenticationResult.accessToken
                            logger?.info("acquireToken.onSuccess: ${shortenToken(accessToken)}")
                            continuation.resume(accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            logger?.error("acquireToken.onError", exception)
                            continuation.resume(null)
                        }

                    })
                    .build()
            )
        }
    }

    @WorkerThread
    fun acquireTokenBackground(scopes: List<String>): String? {
        try {
            val app = msalApp
            if (app == null) {
                logger?.error("acquireTokenBackground: msal app is null")
                return null
            }

            val account = app.currentAccount.currentAccount
            if (account == null) {
                logger?.error("acquireTokenBackground: account is null")
                return null
            }

            logger?.info("acquireTokenBackground: scopes = ${scopes.joinToString(",")}")

            val result = app.acquireTokenSilent(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(scopes)
                    .build()
            )
            val accessToken = result.accessToken
            logger?.info("acquireTokenBackground: ${shortenToken(accessToken)}")
            return accessToken
        } catch (exception: Exception) {
            logger?.error("acquireTokenBackground", exception)
            return null
        }
    }

    fun initMam() {
        val manager = MAMComponents.get(MAMEnrollmentManager::class.java)
        if (manager == null) {
            logger?.error("initMam: MAMEnrollmentManager Not Found")
            return
        }

        manager.registerAuthenticationCallback(object : MAMServiceAuthenticationCallback {
            override fun acquireToken(upn: String, aadId: String, resourceId: String): String? {
                logger?.info("MAM.acquireToken: upn = $upn, aadId = $aadId, resourceId = $resourceId")
                val accessToken = acquireTokenBackground(listOf("$resourceId/.default"))
                logger?.info("MAM.acquireToken: return = ${shortenToken(accessToken)}")
                return accessToken
            }
        })
        mamEnrollmentManager = manager
        logger?.info("initMam: Done")
    }

    suspend fun registerMam() {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("registerMam: mam enroll manager is null")
            return
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("registerMam: account is null")
            return
        }

        manager.registerAccountForMAM(
            account.username,
            account.id,
            account.tenantId,
            account.authority
        )
        logger?.info("registerMam: Done")
    }

    private fun unregisterMam(account: IAccount) {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("unregisterMam: mam enroll manager is null")
            return
        }

        manager.unregisterAccountForMAM(
            account.username,
            account.id
        )
        logger?.info("unregisterMam: Done")
    }

    suspend fun getMamStatus(): MAMEnrollmentManager.Result? {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("getMamStatus: mam enroll manager is null")
            return null
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("getMamStatus: account is null")
            return null
        }

        val result = manager.getRegisteredAccountStatus(
            account.username,
            account.id
        )
        logger?.info("getMamStatus: ${result?.name}")
        return result
    }


    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    private fun shortenToken(token: String?): String {
        if (token == null) {
            return "null"
        }
        return "${token.take(10)}...${token.takeLast(10)}"
    }
}