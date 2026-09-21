package com.silas.transfer.repository

import com.silas.transfer.domain.Transfer
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransferRepository : JpaRepository<Transfer, UUID>