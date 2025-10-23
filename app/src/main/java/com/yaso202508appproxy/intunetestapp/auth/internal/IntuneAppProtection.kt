package com.yaso202508appproxy.intunetestapp.auth.internal

import com.microsoft.identity.client.IAccount
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.MAMServiceAuthenticationCallback
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.auth.AuthResult

object IntuneAppProtection {
    private var mamEnrollmentManager: MAMEnrollmentManager? = null
    private var logger: AppLogger? = null

    /**
     * MAMEnrollmentManagerを作成
     */
    private fun createMamEnrollManager(): MAMEnrollmentManager? {
        val manager = MAMComponents.get(MAMEnrollmentManager::class.java)
        if (manager == null) {
            logger?.error("createMamEnrollManager: MAMEnrollmentManager Not Found")
            return null
        }

        manager.registerAuthenticationCallback(object : MAMServiceAuthenticationCallback {
            override fun acquireToken(upn: String, aadId: String, resourceId: String): String? {
                logger?.info("MAM.acquireToken: upn = $upn, aadId = $aadId, resourceId = $resourceId")
                val authResult = AuthCacheManager.acquireAuth(listOf("$resourceId/.default"))
                return if (authResult is AuthResult.Success) {
                    authResult.info.accessToken
                } else {
                    null
                }
            }
        })

        logger?.info("createMamEnrollManager: created")
        return manager
    }

    /**
     * 初期化
     */
    fun initialize(): Boolean {
        val manager = createMamEnrollManager()
        mamEnrollmentManager = manager
        return manager != null
    }

    /**
     * MAM登録
     */
    suspend fun registerMam(): Boolean {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("registerMam: MAMEnrollmentManager is null")
            return false
        }

        val account = MsAuthenticator.getAccount()
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

    /**
     * MAM登録解除
     */
    fun unregisterMam(account: IAccount): Boolean {
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

    /**
     * MAM登録状態を取得
     */
    suspend fun getMamStatus(): MAMEnrollmentManager.Result? {
        val manager = mamEnrollmentManager
        if (manager == null) {
            logger?.error("getMamStatus: MAMEnrollmentManager is null")
            return null
        }

        val account = MsAuthenticator.getAccount()
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

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}