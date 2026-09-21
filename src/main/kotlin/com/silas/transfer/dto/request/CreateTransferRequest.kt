package com.silas.transfer.dto.request

import java.util.UUID

data class CreateTransferRequest(
    val sourceAccountId: UUID,
    val targetAccountId: UUID,
    val amount: Long
)