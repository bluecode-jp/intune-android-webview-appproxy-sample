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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

object AppProxyAuthManager {
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private var mamEnrollmentManager: MAMEnrollmentManager? = null

    @ForInvestigationOnly
    private var logger: Logger? = null

    suspend fun initialize(context: Context): Boolean {
        val app = createMsalApp(context)
        if (app == null) {
            return false
        }
        msalApp = app

        val manager = createMamEnrollManager()
        if (manager == null) {
            return false
        }
        mamEnrollmentManager = manager

        return true
    }

    suspend fun setupAccount(
        signInScopes: List<String>,
        activity: Activity
    ): Boolean {
        val account = sso(signInScopes, activity)
        if (account == null) {
            return false
        }

        val result = registerMam()
        return result
    }

    suspend fun waitForAppProxyAccessReady(
        appProxyScopes: List<String>,
        timeOutMillis: Long,
        intervalMillis: Long
    ) {
        withTimeout(timeOutMillis) {
            while (getMamStatus() != MAMEnrollmentManager.Result.ENROLLMENT_SUCCEEDED) {
                delay(intervalMillis)
            }

            while (acquireTokenAsync(appProxyScopes) == null) {
                delay(intervalMillis)
            }
        }
    }

    suspend fun cleanupAccount(): Boolean {
        val account = signOut()
        if (account == null) {
            return false
        }

        unregisterMam(account)
        return true
    }

    private suspend fun createMsalApp(
        context: Context
    ): ISingleAccountPublicClientApplication? = suspendCancellableCoroutine { continuation ->
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.msal_config,
            object : ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    logger?.info("createMsalApp.onCreated")
                    continuation.resume(application)
                }

                override fun onError(exception: MsalException?) {
                    logger?.error("createMsalApp.onError", exception)
                    continuation.resume(null)
                }
            }
        )
    }

    @ForInvestigationOnly
    suspend fun initMsal(context: Context): Boolean {
        val app = createMsalApp(context)
        msalApp = app
        return app != null
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

    private suspend fun signIn(scopes: List<String>, activity: Activity): IAccount? {
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
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            val account = authenticationResult.account
                            logger?.info("signIn.onSuccess: account is ${account.username}")
                            continuation.resume(account)
                        }

                        override fun onError(exception: MsalException) {
                            logger?.error("signIn.onError", exception)
                            continuation.resume(null)
                        }

                        override fun onCancel() {
                            logger?.error("signIn.onCancel")
                            continuation.resume(null)
                        }
                    })
                    .build()
            )
        }
    }

    suspend fun sso(scopes: List<String>, activity: Activity): IAccount? {
        var account = getAccount()

        if (account == null) {
            logger?.info("sso: no account -> sign in")
            account = signIn(scopes, activity)
        } else {
            logger?.info("sso: account already exists")
        }

        logger?.info("sso: account is ${account?.username}")
        return account
    }

    private suspend fun signOut(): IAccount? {
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
                    logger?.info( "signOut.onSignOut")
                    continuation.resume(account)
                }

                override fun onError(exception: MsalException) {
                    logger?.error("signOut.onError", exception)
                    continuation.resume(null)
                }
            })
        }
    }

    suspend fun acquireTokenAsync(scopes: List<String>): String? {
        val app = msalApp
        if (app == null) {
            logger?.error("acquireTokenAsync: msal app is null")
            return null
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("acquireTokenAsync: account is null")
            return null
        }

        logger?.info("acquireTokenAsync: scopes = ${scopes.joinToString(",")}")

        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(scopes)
                    .withCallback(object : SilentAuthenticationCallback {
                        override fun onSuccess(authenticationResult: IAuthenticationResult) {
                            val accessToken = authenticationResult.accessToken
                            logger?.info("acquireTokenAsync.onSuccess: ${shortenToken(accessToken)}")
                            continuation.resume(accessToken)
                        }

                        override fun onError(exception: MsalException) {
                            logger?.error("acquireTokenAsync.onError", exception)
                            continuation.resume(null)
                        }

                    })
                    .build()
            )
        }
    }

    @WorkerThread
    fun acquireTokenSync(scopes: List<String>): String? {
        try {
            val app = msalApp
            if (app == null) {
                logger?.error("acquireTokenSync: msal app is null")
                return null
            }

            val account = app.currentAccount.currentAccount
            if (account == null) {
                logger?.error("acquireTokenSync: account is null")
                return null
            }

            logger?.info("acquireTokenSync: scopes = ${scopes.joinToString(",")}")

            val result = app.acquireTokenSilent(
                AcquireTokenSilentParameters.Builder()
                    .forAccount(account)
                    .fromAuthority(account.authority)
                    .withScopes(scopes)
                    .build()
            )
            val accessToken = result.accessToken
            logger?.info("acquireTokenSync: ${shortenToken(accessToken)}")
            return accessToken
        } catch (exception: Exception) {
            logger?.error("acquireTokenSync", exception)
            return null
        }
    }

    fun createMamEnrollManager(): MAMEnrollmentManager? {
        val manager = MAMComponents.get(MAMEnrollmentManager::class.java)
        if (manager == null) {
            logger?.error("createMamEnrollManager: MAMEnrollmentManager Not Found")
            return null
        }

        manager.registerAuthenticationCallback(object : MAMServiceAuthenticationCallback {
            override fun acquireToken(upn: String, aadId: String, resourceId: String): String? {
                logger?.info("MAM.acquireToken: upn = $upn, aadId = $aadId, resourceId = $resourceId")
                val accessToken = acquireTokenSync(listOf("$resourceId/.default"))
                logger?.info("MAM.acquireToken: return = ${shortenToken(accessToken)}")
                return accessToken
            }
        })

        logger?.info("createMamEnrollManager: created")
        return manager
    }

    @ForInvestigationOnly
    fun initMam(): Boolean {
        val manager = createMamEnrollManager()
        mamEnrollmentManager = manager
        return manager != null
    }

    suspend fun registerMam(): Boolean {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("registerMam: MAMEnrollmentManager is null")
            return false
        }

        val account = getAccount()
        if (account == null) {
            logger?.error("registerMam: account is null")
            return false
        }

        manager.registerAccountForMAM(
            account.username,
            account.id,
            account.tenantId,
            account.authority
        )
        logger?.info("registerMam: Done")
        return true
    }

    private fun unregisterMam(account: IAccount): Boolean {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("unregisterMam: MAMEnrollmentManager is null")
            return false
        }

        manager.unregisterAccountForMAM(
            account.username,
            account.id
        )
        logger?.info("unregisterMam: Done")
        return true
    }

    suspend fun getMamStatus(): MAMEnrollmentManager.Result? {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("getMamStatus: MAMEnrollmentManager is null")
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

    @ForInvestigationOnly
    fun setLogger(logger: Logger) {
        this.logger = logger
    }

    @ForInvestigationOnly
    private fun shortenToken(token: String?): String {
        if (token == null) {
            return "null"
        }
        return "${token.take(10)}...${token.takeLast(10)}"
    }
}