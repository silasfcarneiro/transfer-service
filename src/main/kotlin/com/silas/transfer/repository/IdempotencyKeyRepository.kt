package com.silas.transfer.repository

import com.silas.transfer.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, String>