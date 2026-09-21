package com.silas.transfer.repository

import com.silas.transfer.readmodel.StatementEntry
import org.springframework.data.mongodb.repository.MongoRepository
import java.util.UUID

interface StatementRepository : MongoRepository<StatementEntry, String> {
    fun findByAccountIdOrderByAtDesc(accountId: UUID): List<StatementEntry>
}