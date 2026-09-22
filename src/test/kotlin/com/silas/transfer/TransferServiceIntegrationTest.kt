package com.silas.transfer

import com.silas.transfer.domain.Account
import com.silas.transfer.dto.request.CreateTransferRequest
import com.silas.transfer.repository.AccountRepository
import com.silas.transfer.service.TransferService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID

@SpringBootTest
@Import(TestcontainersConfig::class)
class TransferServiceIntegrationTest(
    @Autowired val transferService: TransferService,
    @Autowired val accountRepository: AccountRepository
) {

    // ---------- Helpers ----------

    private fun criarConta(saldo: Long, nome: String = "Titular"): Account =
        accountRepository.save(
            Account(
                id = UUID.randomUUID(),
                ownerName = nome,
                balance = saldo,
                currency = "BRL"
            )
        )

    private fun saldoDe(id: UUID): Long =
        accountRepository.findById(id).get().balance

    private fun requisicao(origem: UUID, destino: UUID, valor: Long) =
        CreateTransferRequest(origem, destino, valor)

    // ---------- Happy path ----------

    @Test
    fun `transferencia bem-sucedida debita origem e credita destino`() {
        val origem = criarConta(saldo = 100_000)
        val destino = criarConta(saldo = 20_000)

        val transfer = transferService.transfer(
            requisicao(origem.id, destino.id, 30_000),
            "chave-happy-path"
        )

        assertThat(transfer.status).isEqualTo("COMPLETED")
        assertThat(saldoDe(origem.id)).`as`("origem debitada").isEqualTo(70_000)
        assertThat(saldoDe(destino.id)).`as`("destino creditado").isEqualTo(50_000)
    }

    // ---------- Idempotência ----------

    @Test
    fun `mesma Idempotency-Key nao transfere duas vezes`() {
        val origem = criarConta(saldo = 100_000)
        val destino = criarConta(saldo = 50_000)
        val req = requisicao(origem.id, destino.id, 10_000)

        val primeira = transferService.transfer(req, "chave-idempotencia")
        val segunda = transferService.transfer(req, "chave-idempotencia")

        assertThat(segunda.id)
            .`as`("mesma transferência, não uma nova")
            .isEqualTo(primeira.id)
        assertThat(saldoDe(origem.id)).`as`("debitada uma vez só").isEqualTo(90_000)
        assertThat(saldoDe(destino.id)).`as`("creditada uma vez só").isEqualTo(60_000)
    }

    // ---------- Casos de erro ----------

    @Test
    fun `saldo insuficiente nao altera nenhum saldo`() {
        val origem = criarConta(saldo = 5_000)
        val destino = criarConta(saldo = 50_000)

        assertThatThrownBy {
            transferService.transfer(
                requisicao(origem.id, destino.id, 10_000),
                "chave-saldo-insuficiente"
            )
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(saldoDe(origem.id)).`as`("origem intacta").isEqualTo(5_000)
        assertThat(saldoDe(destino.id)).`as`("destino intacto").isEqualTo(50_000)
    }

    @Test
    fun `conta de origem inexistente lanca excecao`() {
        val destino = criarConta(saldo = 50_000)

        assertThatThrownBy {
            transferService.transfer(
                requisicao(UUID.randomUUID(), destino.id, 10_000),
                "chave-origem-inexistente"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `conta de destino inexistente lanca excecao`() {
        val origem = criarConta(saldo = 50_000)

        assertThatThrownBy {
            transferService.transfer(
                requisicao(origem.id, UUID.randomUUID(), 10_000),
                "chave-destino-inexistente"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `valor negativo deve ser rejeitado`() {
        val origem = criarConta(saldo = 50_000)
        val destino = criarConta(saldo = 50_000)

        assertThatThrownBy {
            transferService.transfer(
                requisicao(origem.id, destino.id, -100),
                "chave-valor-negativo"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `transferencia para a mesma conta deve ser rejeitada`() {
        val conta = criarConta(saldo = 50_000)

        assertThatThrownBy {
            transferService.transfer(
                requisicao(conta.id, conta.id, 10_000),
                "chave-mesma-conta"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}