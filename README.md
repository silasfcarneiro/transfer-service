# transfer-service

Serviço de transferências entre contas. Exercício de arquitetura com foco em
consistência, idempotência e mensageria - construído como projeto de portfólio
e preparação técnica.

Transferir dinheiro parece simples, mas concentra as decisões difíceis de
sistemas distribuídos: garantir que o débito e o crédito acontecem juntos, que
um retry de rede não transfere duas vezes, e que o restante do sistema (extrato,
antifraude, notificação) fica sabendo sem travar a resposta ao cliente.

## O que ele faz

- Transfere um valor de uma conta de origem para uma de destino (síncrono)
- Garante idempotência: reenviar a mesma transferência não a executa duas vezes
- Lista o extrato de uma conta (read model, via CQRS)
- Publica o evento `transfer.completed` de forma confiável (padrão Outbox)

Escopo do MVP: as contas já existem com saldo; o serviço movimenta saldo, não
cria conta.

## Arquitetura em uma frase

Comando síncrono (o cliente precisa saber na hora se a transferência passou),
propagação assíncrona (extrato, antifraude e notificação reagem ao evento). O
débito e o crédito acontecem na mesma transação do Postgres; o evento vai para a
tabela `outbox` na mesma transação e é publicado no Kafka por um relay - sem
transação distribuída.

Detalhes em [`docs/architecture/overview.md`](docs/architecture/overview.md).

## Princípios

1. **Comando síncrono, propagação assíncrona.** O `POST` responde na hora
   (201/409/422); o resto do mundo reage ao evento depois.
2. **Débito e crédito atômicos.** Os dois lados da transferência ocorrem na mesma
   transação, ou nenhum ocorre.
3. **Idempotência.** `Idempotency-Key` no POST; um retry não duplica a
   transferência.
4. **Outbox.** Nunca publicar no Kafka dentro do request; o evento vai para a
   outbox na transação e um relay publica.
5. **At-least-once + consumer idempotente.** Duplicata de evento é tratada; falha
   permanente vai para DLQ.
6. **CQRS.** Modelo de escrita (transferência, saldo) separado do modelo de
   leitura (extrato), conectados por evento.

## Stack

- Kotlin + Spring Boot 3 (Web, Data JPA, Validation)
- PostgreSQL (contas, transferências, outbox) com migrations via Flyway
- Apache Kafka (eventos) - fatia 2
- MongoDB (extrato / read model) - fatia 3
- Docker Compose para o ambiente local
- Gradle (Kotlin DSL)

## Endpoints

| Método | Rota | Resposta |
|---|---|---|
| POST | `/transfers` | 201 `{transferId}` · 409 idempotência · 422 saldo insuficiente · 400 validação |
| GET | `/accounts/{accountId}/transfers` | 200 extrato |

Contratos em [`docs/api/openapi.md`](docs/api/openapi.md).

## Decisões de arquitetura (ADRs)

- [0001 - Padrão Outbox para publicação de eventos](docs/architecture/decisions/0001-outbox-pattern.md)
- [0002 - Partição do Kafka pela conta de origem](docs/architecture/decisions/0002-particao-por-conta.md)
- [0003 - Idempotência (comando e consumer)](docs/architecture/decisions/0003-idempotencia.md)

## Roadmap de construção

- [ ] Fatia 1 - POST síncrono: débito e crédito atômicos, idempotência, Postgres, Flyway
- [ ] Fatia 2 - Outbox + relay publicando `transfer.completed` no Kafka
- [ ] Fatia 3 - Consumer idempotente com DLQ + read model de extrato (MongoDB, CQRS)
- [ ] Fatia 4 - Testes de integração com Testcontainers (Postgres + Kafka + Mongo)
- [ ] Fatia 5 - Teste de carga (p95/p99) e infra em Terraform