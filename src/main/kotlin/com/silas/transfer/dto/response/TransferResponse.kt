package com.silas.transfer.dto.response

import java.util.UUID

data class TransferResponse(
    val transferId: UUID,
    val status: String
)