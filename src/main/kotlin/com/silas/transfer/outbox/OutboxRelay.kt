package com.silas.transfer.outbox

import com.silas.transfer.repository.OutboxRepository
import jakarta.transaction.Transactional
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class OutboxRelay(
    private val outboxRepository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) {

    @Scheduled(fixedDelay = 500)
    @Transactional
    fun publishPending() {
        val pending = outboxRepository.findPendingBatch()

        for (event in pending) {
            kafkaTemplate.send(event.topic, event.messageKey, event.payload)
            event.publishedAt = OffsetDateTime.now()
            event.attempts += 1
        }
    }
}