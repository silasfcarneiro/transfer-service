package com.silas.transfer.outbox

import com.silas.transfer.readmodel.StatementEntry
import com.silas.transfer.repository.StatementRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime
import java.util.UUID
import java.time.Instant

@Component
class TransferEventConsumer(
    private val statementRepository: StatementRepository,
    private val objectMapper: ObjectMapper
) {

    @KafkaListener(
        topics = ["transfer-events"],
        groupId = "statement-builder",
        containerFactory = "dlqListenerContainerFactory"
    )
    fun onTransferCompleted(message: String) {
        val event = objectMapper.readTree(message)
        val payload = event.get("payload")

        val transferId = UUID.fromString(payload.get("transferId").asText())
        val source = UUID.fromString(payload.get("sourceAccountId").asText())
        val target = UUID.fromString(payload.get("targetAccountId").asText())
        val amount = payload.get("amount").asLong()
        val now = OffsetDateTime.now()

        // DEBIT na origem
        statementRepository.save(
            StatementEntry(
                id = "$transferId-DEBIT",
                accountId = source,
                transferId = transferId,
                type = "DEBIT",
                amount = amount,
                counterparty = target,
                at = Instant.now()
            )
        )

        // CREDIT no destino
        statementRepository.save(
            StatementEntry(
                id = "$transferId-CREDIT",
                accountId = target,
                transferId = transferId,
                type = "CREDIT",
                amount = amount,
                counterparty = source,
                at = Instant.now()
            )
        )
    }
}