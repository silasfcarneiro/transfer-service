package com.silas.transfer.readmodel

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "statement_entry")
class StatementEntry(
    @Id
    val id: String,                    // "${transferId}-${type}" para ser único e idempotente
    val accountId: UUID,
    val transferId: UUID,
    val type: String,                  // DEBIT ou CREDIT
    val amount: Long,
    val counterparty: UUID,
    val at: Instant
)