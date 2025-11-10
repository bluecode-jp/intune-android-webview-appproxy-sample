package com.yaso202508appproxy.intunetestapp.auth.internal

import com.microsoft.identity.client.IAccount
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.client.notification.MAMNotificationReceiver
import com.microsoft.intune.mam.client.notification.MAMNotificationReceiverRegistry
import com.microsoft.intune.mam.policy.MAMComplianceManager
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.MAMServiceAuthenticationCallback
import com.microsoft.intune.mam.policy.notification.MAMComplianceNotification
import com.microsoft.intune.mam.policy.notification.MAMEnrollmentNotification
import com.microsoft.intune.mam.policy.notification.MAMNotification
import com.microsoft.intune.mam.policy.notification.MAMNotificationType
import com.yaso202508appproxy.intunetestapp.AppLogger
import com.yaso202508appproxy.intunetestapp.auth.AuthResult

object IntuneAppProtection {
    private lateinit var mamEnrollmentManager: MAMEnrollmentManager
    private lateinit var mamComplianceManager: MAMComplianceManager
    private lateinit var mamNotificationReceiverRegistry: MAMNotificationReceiverRegistry
    private var logger: AppLogger? = null

    /**
     * MAMEnrollmentManagerを作成
     */
    private fun createMamEnrollManager(): MAMEnrollmentManager {
        val manager = MAMComponents.get(MAMEnrollmentManager::class.java)
        if (manager == null) {
            logger?.error("createMamEnrollManager: MAMEnrollmentManager is null")
            throw Exception("MAMEnrollmentManager is null")
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
     * MAMComplianceManagerを作成
     */
    private fun createMamComplianceManager(): MAMComplianceManager {
        val manager = MAMComponents.get(MAMComplianceManager::class.java)
        if (manager == null) {
            logger?.error("createMamComplianceManager: MAMComplianceManager is null")
            throw Exception("MAMComplianceManager is null")
        }
        return manager
    }

    /**
     * MAMNotificationReceiverRegistryを作成
     */
    private fun createMamNotificationReceiverRegistry(): MAMNotificationReceiverRegistry {
        val registry = MAMComponents.get(MAMNotificationReceiverRegistry::class.java)
        if (registry == null) {
            logger?.error("createMamNotificationReceiverRegistry: MAMNotificationReceiverRegistry is null")
            throw Exception("MAMNotificationReceiverRegistry is null")
        }
        return registry
    }

    /**
     * 初期化
     */
    fun initialize() {
        mamEnrollmentManager = createMamEnrollManager()
        mamComplianceManager = createMamComplianceManager()
        mamNotificationReceiverRegistry = createMamNotificationReceiverRegistry()
    }

    /**
     * MAM登録
     */
    fun registerMam(account: IAccount) {
        mamEnrollmentManager.registerAccountForMAM(
            account.username,
            account.id,
            account.tenantId,
            account.authority
        )
    }

    /**
     * MAM登録解除
     */
    fun unregisterMam(account: IAccount) {
        mamEnrollmentManager.unregisterAccountForMAM(
            account.username,
            account.id
        )
    }

    /**
     * MAM登録状態を取得
     */
    fun getMamStatus(account: IAccount): MAMEnrollmentManager.Result? {
        val result = mamEnrollmentManager.getRegisteredAccountStatus(
            account.username,
            account.id
        )
        logger?.info("getMamStatus: ${result?.name}")
        return result
    }

    fun remediateCompliance(account: IAccount) {
        mamComplianceManager.remediateCompliance(
            account.username,
            account.id,
            account.tenantId,
            account.authority,
            true
        )
    }

    fun registerNotification(
        onReceiveEnrollmentResult: (notification: MAMEnrollmentNotification) -> Unit,
        onReceiveComplianceStatus: (notification: MAMComplianceNotification) -> Unit,
    ) {
        mamNotificationReceiverRegistry.registerReceiver(
            { notification ->
                onReceiveEnrollmentResult(notification as MAMEnrollmentNotification)
                true
            },
            MAMNotificationType.MAM_ENROLLMENT_RESULT
        )

        mamNotificationReceiverRegistry.registerReceiver(
            { notification ->
                onReceiveComplianceStatus(notification as MAMComplianceNotification)
                true
            },
            MAMNotificationType.COMPLIANCE_STATUS
        )
    }

    fun setLogger(logger: AppLogger) {
        this.logger = logger
    }
}