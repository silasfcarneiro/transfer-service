package com.silas.transfer.service

import com.silas.transfer.domain.Account
import com.silas.transfer.domain.Transfer
import com.silas.transfer.dto.request.CreateTransferRequest
import com.silas.transfer.repository.AccountRepository
import com.silas.transfer.repository.IdempotencyKeyRepository
import com.silas.transfer.repository.OutboxRepository
import com.silas.transfer.repository.TransferRepository
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class TransferServiceUnitTest {

    // os mocks - versões falsas dos repositories
    private val accountRepository: AccountRepository = mock()
    private val transferRepository: TransferRepository = mock()
    private val idempotencyKeyRepository: IdempotencyKeyRepository = mock()
    private val outboxRepository: OutboxRepository = mock()

    // o objeto sob teste, recebendo os mocks
    private lateinit var service: TransferService

    @BeforeEach
    fun setup() {
        service = TransferService(
            accountRepository,
            transferRepository,
            idempotencyKeyRepository,
            outboxRepository
        )
    }

    // helper pra montar conta
    private fun conta(id: UUID, saldo: Long) =
        Account(id, "Titular", saldo, "BRL")

    @Test
    fun `valor negativo lanca IllegalArgumentException`() {
        val req = CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), -100)

        assertThatThrownBy { service.transfer(req, "k") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `transferencia para a mesma conta lanca IllegalArgumentException`() {
        val id = UUID.randomUUID()
        val req = CreateTransferRequest(id, id, 100)

        assertThatThrownBy { service.transfer(req, "k") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `saldo insuficiente lanca IllegalStateException e nao salva transferencia`() {
        val origemId = UUID.randomUUID()
        val destinoId = UUID.randomUUID()

        // ARRANGE: ensina os mocks a responder
        whenever(idempotencyKeyRepository.findById("k")).thenReturn(Optional.empty())
        whenever(accountRepository.findById(origemId))
            .thenReturn(Optional.of(conta(origemId, saldo = 500)))   // saldo baixo
        whenever(accountRepository.findById(destinoId))
            .thenReturn(Optional.of(conta(destinoId, saldo = 1000)))

        val req = CreateTransferRequest(origemId, destinoId, 10_000)  // mais que o saldo

        assertThatThrownBy { service.transfer(req, "k") }
            .isInstanceOf(IllegalStateException::class.java)

        // ASSERT: verifica que NUNCA salvou a transferência (a lógica parou antes)
        verify(transferRepository, never()).save(any())
    }

    @Test
    fun `chave idempotente existente devolve transferencia original sem processar`() {
        val transferOriginalId = UUID.randomUUID()

        // ARRANGE: finge que a chave JÁ existe
        val chaveExistente = com.silas.transfer.domain.IdempotencyKey("k", transferOriginalId)
        whenever(idempotencyKeyRepository.findById("k"))
            .thenReturn(Optional.of(chaveExistente))
        whenever(transferRepository.findById(transferOriginalId))
            .thenReturn(Optional.of(mock<Transfer>().apply { }))

        val req = CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), 100)
        service.transfer(req, "k")

        // ASSERT: não buscou conta, não debitou - devolveu a original
        verify(accountRepository, never()).findById(any())
        verify(transferRepository, never()).save(any())
    }

    @Test
    fun `conta de origem inexistente lanca IllegalArgumentException`() {
        val origemId = UUID.randomUUID()
        val destinoId = UUID.randomUUID()

        whenever(idempotencyKeyRepository.findById("k")).thenReturn(Optional.empty())
        whenever(accountRepository.findById(origemId)).thenReturn(Optional.empty())  // não existe

        val req = CreateTransferRequest(origemId, destinoId, 100)

        assertThatThrownBy { service.transfer(req, "k") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `transferencia valida salva transferencia e grava evento na outbox`() {
        val origemId = UUID.randomUUID()
        val destinoId = UUID.randomUUID()

        whenever(idempotencyKeyRepository.findById("k")).thenReturn(Optional.empty())
        whenever(accountRepository.findById(origemId))
            .thenReturn(Optional.of(conta(origemId, saldo = 100_000)))
        whenever(accountRepository.findById(destinoId))
            .thenReturn(Optional.of(conta(destinoId, saldo = 0)))
        whenever(transferRepository.save(any())).thenAnswer { it.arguments[0] }

        val req = CreateTransferRequest(origemId, destinoId, 10_000)
        service.transfer(req, "k")

        // ASSERT: verifica que os efeitos aconteceram
        verify(transferRepository).save(any())      // salvou a transferência
        verify(outboxRepository).save(any())        // gravou o evento
        verify(idempotencyKeyRepository).save(any())// gravou a chave
    }
}