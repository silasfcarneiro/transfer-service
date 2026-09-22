package com.silas.transfer

import com.silas.transfer.domain.Account
import com.silas.transfer.dto.request.CreateTransferRequest
import com.silas.transfer.repository.AccountRepository
import com.silas.transfer.service.StatementService
import com.silas.transfer.service.TransferService
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfig::class)
class TransferEndToEndTest(
    @Autowired val transferService: TransferService,
    @Autowired val statementService: StatementService,
    @Autowired val accountRepository: AccountRepository
) {

    @Test
    fun `transferencia propaga para o extrato via evento`() {
        // ARRANGE
        val origem = accountRepository.save(
            Account(UUID.randomUUID(), "Ana", 100_000, "BRL")
        )
        val destino = accountRepository.save(
            Account(UUID.randomUUID(), "Bento", 20_000, "BRL")
        )

        // ACT: a transferência (escrita síncrona no Postgres)
        val transfer = transferService.transfer(
            CreateTransferRequest(origem.id, destino.id, 30_000),
            "chave-e2e"
        )

        // ASSERT: o extrato é ASSÍNCRONO - espera a propagação (relay -> Kafka -> consumer -> Mongo)
        await()
            .atMost(Duration.ofSeconds(15))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted {
                val extratoOrigem = statementService.getStatement(origem.id)
                assertThat(extratoOrigem)
                    .`as`("origem deve ter o lançamento de DÉBITO")
                    .anyMatch { it.transferId == transfer.id && it.type == "DEBIT" }

                val extratoDestino = statementService.getStatement(destino.id)
                assertThat(extratoDestino)
                    .`as`("destino deve ter o lançamento de CRÉDITO")
                    .anyMatch { it.transferId == transfer.id && it.type == "CREDIT" }
            }
    }
}