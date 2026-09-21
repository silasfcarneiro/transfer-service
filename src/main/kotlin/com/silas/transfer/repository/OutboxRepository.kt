package com.silas.transfer.repository

import com.silas.transfer.domain.Outbox
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.util.UUID

interface OutboxRepository : JpaRepository<Outbox, UUID> {

    @Query(
        "select o from Outbox o where o.publishedAt is null order by o.createdAt"
    )
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    fun findPendingBatch(): List<Outbox>
}