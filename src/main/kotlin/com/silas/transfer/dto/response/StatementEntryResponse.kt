package com.silas.transfer.dto.response

import java.time.Instant
import java.util.UUID

data class StatementEntryResponse(
    val transferId: UUID,
    val type: String,
    val amount: Long,
    val counterparty: UUID,
    val at: Instant
)