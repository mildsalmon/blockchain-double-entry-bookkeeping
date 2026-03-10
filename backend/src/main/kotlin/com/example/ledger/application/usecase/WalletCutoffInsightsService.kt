package com.example.ledger.application.usecase

import com.example.ledger.application.dto.WalletCutoffPreflightResponse
import com.example.ledger.application.dto.WalletCutoffSignOffResponse
import com.example.ledger.application.dto.WalletOmittedSuspectedResponse
import com.example.ledger.application.dto.WalletTokenPreviewResponse
import com.example.ledger.domain.model.Wallet
import com.example.ledger.domain.model.WalletSyncMode
import com.example.ledger.domain.port.AuditLogRepository
import com.example.ledger.domain.service.TokenMetadataService
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant

private const val NATIVE_ETH_TOKEN_ADDRESS = "0x0000000000000000000000000000000000000000"

data class WalletCutoffInsights(
    val seededTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val discoveredTokens: List<WalletTokenPreviewResponse> = emptyList(),
    val omittedSuspectedTokens: List<WalletOmittedSuspectedResponse> = emptyList(),
    val latestCutoffSignOff: WalletCutoffSignOffResponse? = null
)

@Service
class WalletCutoffInsightsService(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val tokenMetadataService: TokenMetadataService,
    private val auditLogRepository: AuditLogRepository
) {
    companion object {
        const val SIGN_OFF_ENTITY_TYPE = "WALLET_CUTOFF_SEED_SIGNOFF"
        const val SIGN_OFF_ACTION = "APPROVE_SEED"
        private const val OMITTED_SUSPECTED_BLOCK_WINDOW = 1_000L
    }

    fun buildPreflight(address: String, cutoffBlock: Long, trackedTokens: List<String>): WalletCutoffPreflightResponse {
        val seededTokens = buildSeededTokensForPreflight(trackedTokens, cutoffBlock)
        return WalletCutoffPreflightResponse(
            address = address,
            cutoffBlock = cutoffBlock,
            includesNativeEth = true,
            seededTokens = seededTokens,
            summaryHash = buildSignOffSummaryHash(address, cutoffBlock, trackedTokens),
            warning = "이번 스냅샷은 ETH와 아래 seed 토큰만 포함합니다. 누락 토큰은 자동 복구되지 않습니다."
        )
    }

    fun enrich(wallet: Wallet): WalletCutoffInsights {
        if (wallet.syncMode != WalletSyncMode.BALANCE_FLOW_CUTOFF) return WalletCutoffInsights()
        val cutoffBlock = wallet.cutoffBlock ?: wallet.snapshotBlock ?: return WalletCutoffInsights()

        val signOffSnapshot = findLatestCutoffSignOff(wallet.address)
        val seededTokens = signOffSnapshot?.seededTokens ?: buildSeededTokensForRead(wallet.trackedTokens)
        val trackedAddresses = wallet.trackedTokens.mapNotNull(tokenMetadataService::normalizeContractAddress).toSet()
        val discoveredTokens = findDiscoveredTokens(wallet.address, cutoffBlock, trackedAddresses)
        val omittedSuspected = discoveredTokens
            .filter { (it.firstSeenBlock ?: Long.MAX_VALUE) <= cutoffBlock + OMITTED_SUSPECTED_BLOCK_WINDOW }
            .map {
                WalletOmittedSuspectedResponse(
                    tokenAddress = requireNotNull(it.tokenAddress) { "tokenAddress is required for omitted-suspected items" },
                    tokenSymbol = it.tokenSymbol,
                    displayLabel = it.displayLabel,
                    firstSeenBlock = requireNotNull(it.firstSeenBlock) { "firstSeenBlock is required for omitted-suspected items" },
                    firstSeenAt = it.firstSeenAt,
                    reason = "컷오프 직후 활동으로 인해 누락 가능성이 의심됩니다."
                )
            }

        return WalletCutoffInsights(
            seededTokens = seededTokens,
            discoveredTokens = discoveredTokens,
            omittedSuspectedTokens = omittedSuspected,
            latestCutoffSignOff = signOffSnapshot?.response
        )
    }

    fun buildSignOffSummaryHash(
        address: String,
        cutoffBlock: Long,
        trackedTokens: List<String>
    ): String {
        val seed = buildString {
            append(address.lowercase())
            append('|')
            append(cutoffBlock)
            append('|')
            append(trackedTokens.joinToString(","))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun buildSeededTokensForPreflight(
        trackedTokens: List<String>,
        cutoffBlock: Long
    ): List<WalletTokenPreviewResponse> {
        val eth = WalletTokenPreviewResponse(
            tokenAddress = null,
            tokenSymbol = "ETH",
            displayLabel = "ETH (Ethereum)"
        )
        val seededContracts = trackedTokens.map { tokenAddress ->
            val resolved = tokenMetadataService.resolveForPreview(tokenAddress, null, cutoffBlock)
            WalletTokenPreviewResponse(
                tokenAddress = resolved.tokenAddress,
                tokenSymbol = resolved.tokenSymbol,
                displayLabel = resolved.displayLabel
            )
        }
        return listOf(eth) + seededContracts
    }

    private fun buildSeededTokensForRead(trackedTokens: List<String>): List<WalletTokenPreviewResponse> {
        val eth = WalletTokenPreviewResponse(
            tokenAddress = null,
            tokenSymbol = "ETH",
            displayLabel = "ETH (Ethereum)"
        )
        val seededContracts = trackedTokens.map { tokenAddress ->
            val resolved = tokenMetadataService.resolveCachedForRead(tokenAddress, null, TokenMetadataService.ETHEREUM_CHAIN)
            WalletTokenPreviewResponse(
                tokenAddress = resolved.tokenAddress,
                tokenSymbol = resolved.tokenSymbol,
                displayLabel = resolved.displayLabel
            )
        }
        return listOf(eth) + seededContracts
    }

    private fun findDiscoveredTokens(
        walletAddress: String,
        cutoffBlock: Long,
        trackedAddresses: Set<String>
    ): List<WalletTokenPreviewResponse> {
        val params = MapSqlParameterSource()
            .addValue("walletAddress", walletAddress)
            .addValue("cutoffBlock", cutoffBlock)
            .addValue("nativeTokenAddress", NATIVE_ETH_TOKEN_ADDRESS)

        val rows = jdbcTemplate.query(
            """
            SELECT
              ae.token_address,
              MIN(ae.token_symbol) AS fallback_symbol,
              MIN(rt.block_number) AS first_seen_block,
              MIN(rt.block_timestamp) AS first_seen_at
            FROM accounting_events ae
            JOIN raw_transactions rt ON rt.id = ae.raw_transaction_id
            WHERE rt.wallet_address = :walletAddress
              AND rt.block_number > :cutoffBlock
              AND ae.token_address IS NOT NULL
              AND ae.token_address <> :nativeTokenAddress
            GROUP BY ae.token_address
            ORDER BY MIN(rt.block_number) ASC, ae.token_address ASC
            """.trimIndent(),
            params
            ) { rs, _ ->
            DiscoveredTokenRow(
                tokenAddress = rs.getString("token_address"),
                fallbackSymbol = rs.getString("fallback_symbol"),
                firstSeenBlock = rs.getLong("first_seen_block"),
                firstSeenAt = (rs.getObject("first_seen_at") as Timestamp?)?.toInstant()
            )
        }

        return rows
            .mapNotNull { row ->
                val normalizedAddress = tokenMetadataService.normalizeContractAddress(row.tokenAddress) ?: return@mapNotNull null
                row.copy(tokenAddress = normalizedAddress)
            }
            .filter { row -> trackedAddresses.contains(row.tokenAddress).not() }
            .map { row ->
                val resolved = tokenMetadataService.resolveCachedForRead(
                    row.tokenAddress,
                    row.fallbackSymbol,
                    TokenMetadataService.ETHEREUM_CHAIN
                )
                WalletTokenPreviewResponse(
                    tokenAddress = resolved.tokenAddress,
                    tokenSymbol = resolved.tokenSymbol,
                    displayLabel = resolved.displayLabel,
                    firstSeenBlock = row.firstSeenBlock,
                    firstSeenAt = row.firstSeenAt
                )
            }
    }

    private fun findLatestCutoffSignOff(walletAddress: String): CutoffSignOffSnapshot? {
        val entry = auditLogRepository.findLatest(SIGN_OFF_ENTITY_TYPE, walletAddress, SIGN_OFF_ACTION) ?: return null
        val reviewedBy = entry.actor?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val payload = entry.newValue.orEmpty()

        val cutoffBlock = payload["cutoffBlock"].asLong() ?: return null
        val seededTokenCount = payload["seededTokenCount"].asInt() ?: 0
        val summaryHash = payload["summaryHash"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val seededTokens = payload["seededTokens"].asSeededTokens()

        return CutoffSignOffSnapshot(
            response = WalletCutoffSignOffResponse(
                reviewedBy = reviewedBy,
                reviewedAt = entry.createdAt,
                cutoffBlock = cutoffBlock,
                seededTokenCount = seededTokenCount,
                summaryHash = summaryHash
            ),
            seededTokens = seededTokens
        )
    }

    private fun Any?.asLong(): Long? {
        return when (this) {
            is Long -> this
            is Int -> this.toLong()
            is String -> this.toLongOrNull()
            else -> null
        }
    }

    private fun Any?.asInt(): Int? {
        return when (this) {
            is Int -> this
            is Long -> this.toInt()
            is String -> this.toIntOrNull()
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any?.asSeededTokens(): List<WalletTokenPreviewResponse> {
        val rawItems = this as? List<Map<String, Any?>> ?: return emptyList()
        return rawItems.mapNotNull { raw ->
            val tokenSymbol = raw["tokenSymbol"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val displayLabel = raw["displayLabel"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WalletTokenPreviewResponse(
                tokenAddress = raw["tokenAddress"]?.toString(),
                tokenSymbol = tokenSymbol,
                displayLabel = displayLabel,
                firstSeenBlock = raw["firstSeenBlock"].asLong(),
                firstSeenAt = null
            )
        }
    }

    private data class DiscoveredTokenRow(
        val tokenAddress: String,
        val fallbackSymbol: String?,
        val firstSeenBlock: Long,
        val firstSeenAt: Instant?
    )

    private data class CutoffSignOffSnapshot(
        val response: WalletCutoffSignOffResponse,
        val seededTokens: List<WalletTokenPreviewResponse>
    )
}
