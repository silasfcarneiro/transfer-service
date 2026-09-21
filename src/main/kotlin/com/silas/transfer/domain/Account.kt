package com.silas.transfer.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "account")
class Account (
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "owner_name", nullable = false)
    var ownerName: String,

    @Column(nullable = false)
    var balance: Long,

    @Column(nullable = false)
    var currency: String,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Version
    @Column(nullable = false)
    var version: Long = 0,
)