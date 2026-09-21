package com.silas.transfer.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "transfer")
class Transfer(
    @Id
    val id: UUID = UUID.randomUUID(),   // TODO: migrar para UUID v7

    @Column(name = "source_account_id", nullable = false)
    val sourceAccountId: UUID,

    @Column(name = "target_account_id", nullable = false)
    val targetAccountId: UUID,

    @Column(nullable = false)
    val amount: Long,                   // centavos - inteiro, nunca Double

    @Column(nullable = false)
    val status: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
)