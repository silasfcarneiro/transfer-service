package com.silas.transfer.controller

import com.silas.transfer.dto.response.StatementEntryResponse
import com.silas.transfer.service.StatementService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class StatementController(
    private val statementService: StatementService
) {
    @GetMapping("/accounts/{accountId}/transfers")
    fun getStatement(@PathVariable accountId: UUID): List<StatementEntryResponse> =
        statementService.getStatement(accountId).map { entry ->
            StatementEntryResponse(
                transferId = entry.transferId,
                type = entry.type,
                amount = entry.amount,
                counterparty = entry.counterparty,
                at = entry.at
            )
        }
}