package com.silas.transfer.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "idempotency_key")
class IdempotencyKey(

    @Id
    val key: String,          // a PK é o próprio texto da chave, não um UUID gerado

    @Column(name = "transfer_id", nullable = false)
    val transferId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)