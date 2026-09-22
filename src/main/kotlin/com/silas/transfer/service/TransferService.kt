package com.silas.transfer.service

import com.silas.transfer.domain.Account
import com.silas.transfer.domain.IdempotencyKey
import com.silas.transfer.domain.Outbox
import com.silas.transfer.domain.Transfer
import com.silas.transfer.dto.request.CreateTransferRequest
import com.silas.transfer.repository.AccountRepository
import com.silas.transfer.repository.IdempotencyKeyRepository
import com.silas.transfer.repository.OutboxRepository
import com.silas.transfer.repository.TransferRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TransferService(
    private val accountRepository: AccountRepository,
    private val transferRepository: TransferRepository,
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val outboxRepository: OutboxRepository
) {

    @Transactional
    fun transfer(request: CreateTransferRequest, idempotencyKey: String): Transfer {

        require(request.amount > 0) { "O valor da transferência deve ser positivo" }
        require(request.sourceAccountId != request.targetAccountId) {
            "Conta de origem e destino não podem ser a mesma"
        }

        findExistingTransfer(idempotencyKey)?.let { return it }

        val source = loadAccount(request.sourceAccountId, "origem")
        val target = loadAccount(request.targetAccountId, "destino")

        moveFunds(source, target, request.amount)

        val transfer = recordTransfer(source, target, request.amount)
        registerIdempotencyKey(idempotencyKey, transfer.id)
        publishEvent(transfer)

        return transfer
    }

    /** Se a chave já foi usada, devolve a transferência original (idempotência). */
    private fun findExistingTransfer(idempotencyKey: String): Transfer? =
        idempotencyKeyRepository.findById(idempotencyKey)
            .map { transferRepository.findById(it.transferId).get() }
            .orElse(null)

    private fun loadAccount(id: UUID, papel: String): Account =
        accountRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Conta de $papel não encontrada") }

    /** Debita a origem e credita o destino, validando o saldo. */
    private fun moveFunds(source: Account, target: Account, amount: Long) {
        if (source.balance < amount) {
            throw IllegalStateException("Saldo insuficiente")
        }
        source.balance -= amount
        target.balance += amount
        accountRepository.save(source)
        accountRepository.save(target)
    }

    private fun recordTransfer(source: Account, target: Account, amount: Long): Transfer =
        transferRepository.save(
            Transfer(
                sourceAccountId = source.id,
                targetAccountId = target.id,
                amount = amount,
                status = "COMPLETED"
            )
        )

    private fun registerIdempotencyKey(key: String, transferId: UUID) {
        idempotencyKeyRepository.save(IdempotencyKey(key = key, transferId = transferId))
    }

    private fun publishEvent(transfer: Transfer) {
        outboxRepository.save(
            Outbox(
                id = UUID.randomUUID(),
                aggregateId = transfer.sourceAccountId,
                topic = "transfer-events",
                messageKey = transfer.sourceAccountId.toString(),
                payload = buildEventPayload(transfer)
            )
        )
    }

    private fun buildEventPayload(transfer: Transfer): String = """
        {
          "eventId": "${UUID.randomUUID()}",
          "eventType": "transfer.completed",
          "schemaVersion": 1,
          "aggregateId": "${transfer.sourceAccountId}",
          "payload": {
            "transferId": "${transfer.id}",
            "sourceAccountId": "${transfer.sourceAccountId}",
            "targetAccountId": "${transfer.targetAccountId}",
            "amount": ${transfer.amount}
          }
        }
    """.trimIndent()
}