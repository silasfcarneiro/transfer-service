package com.silas.transfer.service

import com.silas.transfer.readmodel.StatementEntry
import com.silas.transfer.repository.StatementRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class StatementService(
    private val statementRepository: StatementRepository
) {
    fun getStatement(accountId: UUID): List<StatementEntry> =
        statementRepository.findByAccountIdOrderByAtDesc(accountId)
}