package com.example.ledger.adapter.persistence.spring

import com.example.ledger.adapter.persistence.entity.AuditLogEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpringDataAuditLogRepository : JpaRepository<AuditLogEntity, Long>
