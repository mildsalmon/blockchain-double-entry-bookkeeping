package com.example.ledger.application.usecase

import com.example.ledger.adapter.web.AdminCorrectionCredentialStore
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import org.springframework.stereotype.Component

@Component
class AdminCorrectionAvailability(
    private val credentialStore: AdminCorrectionCredentialStore,
    private val walletCutoffInsightsService: WalletCutoffInsightsService
) {
    fun isEnabled(): Boolean = credentialStore.status().enabled

    fun unavailableReason(): String? = credentialStore.status().unavailableReason

    fun readinessFor(wallet: Wallet, hasApprovedCutoffBaseline: Boolean? = null): AdminCorrectionReadiness {
        if (!isEnabled()) {
            return AdminCorrectionReadiness(
                enabled = false,
                unavailableReason = unavailableReason(),
                eligible = false,
                ineligibleReason = "Admin correction is disabled on this server."
            )
        }

        val approvedCutoffBaseline = hasApprovedCutoffBaseline
            ?: walletCutoffInsightsService.hasApprovedCutoffBaseline(wallet.address)
        val ineligibleReason = when {
            wallet.syncMode != WalletSyncMode.BALANCE_FLOW_CUTOFF ->
                "Admin correction requires BALANCE_FLOW_CUTOFF mode."
            wallet.cutoffBlock == null ->
                "Cutoff wallet is missing cutoffBlock."
            wallet.snapshotBlock == null ->
                "Admin correction requires an existing cutoff snapshot."
            !approvedCutoffBaseline ->
                "Admin correction requires an existing cutoff sign-off baseline."
            wallet.syncStatus == SyncStatus.SYNCING ->
                "Admin correction is unavailable while sync is in progress."
            else -> null
        }

        return AdminCorrectionReadiness(
            enabled = true,
            unavailableReason = null,
            eligible = ineligibleReason == null,
            ineligibleReason = ineligibleReason
        )
    }
}

data class AdminCorrectionReadiness(
    val enabled: Boolean,
    val unavailableReason: String?,
    val eligible: Boolean,
    val ineligibleReason: String?
)
