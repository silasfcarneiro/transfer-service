package com.silas.transfer.repository

import com.silas.transfer.domain.Outbox
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxRepository : JpaRepository<Outbox, UUID>