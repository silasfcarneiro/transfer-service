package com.silas.transfer.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "outbox")
class Outbox(

    @Id
    val id: UUID,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: UUID,

    @Column(nullable = false)
    val topic: String,

    @Column(name = "message_key", nullable = false)
    val messageKey: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val headers: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "published_at")
    var publishedAt: OffsetDateTime? = null,

    @Column(nullable = false)
    var attempts: Int = 0
)