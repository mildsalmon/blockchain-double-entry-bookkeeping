package com.example.ledger.adapter.persistence

import com.example.ledger.adapter.persistence.entity.AccountEntity
import com.example.ledger.adapter.persistence.entity.AccountingEventEntity
import com.example.ledger.adapter.persistence.entity.CostBasisLotEntity
import com.example.ledger.adapter.persistence.entity.PriceCacheEntity
import com.example.ledger.adapter.persistence.entity.RawTransactionEntity
import com.example.ledger.adapter.persistence.entity.WalletEntity
import com.example.ledger.domain.model.Account
import com.example.ledger.domain.model.AccountCategory
import com.example.ledger.domain.model.AccountingEvent
import com.example.ledger.domain.model.CostBasisLot
import com.example.ledger.domain.model.EventType
import com.example.ledger.domain.model.PriceInfo
import com.example.ledger.domain.model.PriceSource
import com.example.ledger.domain.model.RawTransaction
import com.example.ledger.domain.model.SyncStatus
import com.example.ledger.domain.model.Wallet
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode

internal fun WalletEntity.toDomain(): Wallet = Wallet(
    id = id,
    address = address,
    label = label,
    syncStatus = SyncStatus.valueOf(syncStatus),
    lastSyncedAt = lastSyncedAt,
    lastSyncedBlock = lastSyncedBlock,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Wallet.toEntity(): WalletEntity = WalletEntity(
    id = id,
    address = address,
    label = label,
    syncStatus = syncStatus.name,
    lastSyncedAt = lastSyncedAt,
    lastSyncedBlock = lastSyncedBlock,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    code = code,
    name = name,
    category = AccountCategory.valueOf(category),
    system = isSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    code = code,
    name = name,
    category = category.name,
    isSystem = system,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun RawTransactionEntity.toDomain(objectMapper: ObjectMapper): RawTransaction = RawTransaction(
    id = id,
    walletAddress = walletAddress,
    txHash = txHash,
    blockNumber = blockNumber,
    txIndex = txIndex,
    blockTimestamp = blockTimestamp,
    rawData = rawData,
    txStatus = txStatus.toInt(),
    createdAt = createdAt
)

internal fun RawTransaction.toEntity(objectMapper: ObjectMapper): RawTransactionEntity = RawTransactionEntity(
    id = id,
    walletAddress = walletAddress,
    txHash = txHash,
    blockNumber = blockNumber,
    txIndex = txIndex,
    blockTimestamp = blockTimestamp,
    rawData = rawData,
    txStatus = txStatus.toShort(),
    createdAt = createdAt
)

internal fun AccountingEventEntity.toDomain(objectMapper: ObjectMapper): AccountingEvent = AccountingEvent(
    id = id,
    rawTransactionId = rawTransactionId,
    eventType = EventType.valueOf(eventType),
    classifierId = classifierId,
    tokenAddress = tokenAddress,
    tokenSymbol = tokenSymbol,
    amountRaw = amountRaw.toBigInteger(),
    amountDecimal = amountDecimal,
    counterparty = counterparty,
    priceKrw = priceKrw,
    priceSource = priceSource?.let { PriceSource.valueOf(it) } ?: PriceSource.UNKNOWN,
    metadata = metadata?.let {
        @Suppress("UNCHECKED_CAST")
        objectMapper.convertValue(it, Map::class.java) as Map<String, Any?>
    } ?: emptyMap(),
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun AccountingEvent.toEntity(objectMapper: ObjectMapper): AccountingEventEntity = AccountingEventEntity(
    id = id,
    rawTransactionId = rawTransactionId,
    eventType = eventType.name,
    classifierId = classifierId,
    tokenAddress = tokenAddress,
    tokenSymbol = tokenSymbol,
    amountRaw = amountRaw.toBigDecimal(),
    amountDecimal = amountDecimal,
    counterparty = counterparty,
    priceKrw = priceKrw,
    priceSource = priceSource.name,
    metadata = if (metadata.isEmpty()) null else objectMapper.valueToTree(metadata),
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun CostBasisLotEntity.toDomain(): CostBasisLot = CostBasisLot(
    id = id,
    walletAddress = walletAddress,
    tokenSymbol = tokenSymbol,
    acquisitionDate = acquisitionDate,
    quantity = quantity,
    remainingQuantity = remainingQty,
    unitCostKrw = unitCostKrw,
    rawTransactionId = rawTransactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun CostBasisLot.toEntity(): CostBasisLotEntity = CostBasisLotEntity(
    id = id,
    walletAddress = walletAddress,
    tokenSymbol = tokenSymbol,
    acquisitionDate = acquisitionDate,
    quantity = quantity,
    remainingQty = remainingQuantity,
    unitCostKrw = unitCostKrw,
    rawTransactionId = rawTransactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun PriceCacheEntity.toDomain(): PriceInfo = PriceInfo(
    tokenAddress = tokenAddress,
    tokenSymbol = tokenSymbol,
    date = priceDate,
    priceKrw = priceKrw,
    source = PriceSource.valueOf(source)
)

internal fun PriceInfo.toEntity(): PriceCacheEntity = PriceCacheEntity(
    tokenAddress = tokenAddress,
    tokenSymbol = tokenSymbol,
    priceDate = date,
    priceKrw = priceKrw.setScale(8, RoundingMode.HALF_UP),
    source = source.name
)
