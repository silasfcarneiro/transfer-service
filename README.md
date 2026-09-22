# transfer-service

Serviço de transferências entre contas. Exercício de arquitetura com foco em
consistência, idempotência e mensageria - construído como projeto de portfólio
e preparação técnica.

Transferir dinheiro parece simples, mas concentra as decisões difíceis de
sistemas distribuídos: garantir que o débito e o crédito acontecem juntos, que
um retry de rede não transfere duas vezes, e que o restante do sistema (extrato,
antifraude, notificação) fica sabendo sem travar a resposta ao cliente.

## O que ele faz

- Transfere um valor de uma conta de origem para uma de destino (síncrono),
  com débito e crédito atômicos na mesma transação
- Garante idempotência: reenviar a mesma transferência (mesma `Idempotency-Key`)
  não a executa duas vezes - devolve o resultado original
- Publica o evento `transfer.completed` de forma confiável (padrão Outbox)
- (planejado) Mantém um extrato como read model, alimentado pelos eventos (CQRS)

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

1. **Comando síncrono, propagação assíncrona.** O `POST` responde na hora; o
   resto do mundo reage ao evento depois.
2. **Débito e crédito atômicos.** Os dois lados ocorrem na mesma transação, ou
   nenhum ocorre.
3. **Idempotência.** `Idempotency-Key` no POST; um retry não duplica.
4. **Outbox.** Nunca publicar no Kafka dentro do request; o evento vai para a
   outbox na transação e um relay publica.
5. **At-least-once + consumer idempotente.** Duplicata de evento é tratada;
   falha permanente vai para DLQ.
6. **CQRS.** Modelo de escrita (transferência, saldo) separado do modelo de
   leitura (extrato), conectados por evento.

## Stack

- Kotlin + Spring Boot 3 (Web, Data JPA, Validation)
- PostgreSQL (contas, transferências, outbox) com migrations via Flyway
- Apache Kafka (eventos)
- MongoDB (extrato / read model) - fatia 3
- Docker Compose para o ambiente local
- Gradle (Kotlin DSL)

## Como rodar

```bash
docker compose up -d      # sobe Postgres (e Kafka a partir da fatia 2)
./gradlew bootRun         # sobe a aplicacao
```

Como o servico nao cria contas, insira duas para testar:

```sql
insert into account (id, owner_name, balance, currency, created_at, updated_at, version) values
('11111111-1111-1111-1111-111111111111', 'Alice', 100000, 'BRL', now(), now(), 0),
('22222222-2222-2222-2222-222222222222', 'Bob',    50000, 'BRL', now(), now(), 0);
```

Transferencia (valores em centavos - R$ 100,00 = 10000):

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: chave-001" \
  -d '{"sourceAccountId":"11111111-1111-1111-1111-111111111111","targetAccountId":"22222222-2222-2222-2222-222222222222","amount":10000}'
```

Reenviar com a mesma `Idempotency-Key` devolve a mesma transferencia, sem
debitar de novo.

Detalhes em [`docs/runbook/local-setup.md`](docs/runbook/local-setup.md).

## Endpoints

| Metodo | Rota | Resposta |
|---|---|---|
| POST | `/transfers` | 201 `{transferId, status}` · 409 idempotencia · 422 saldo insuficiente · 400 validacao |
| GET | `/accounts/{accountId}/transfers` | 200 extrato (fatia 3) |

Contratos em [`docs/api/openapi.md`](docs/api/openapi.md).

## Eventos

Catalogo de topicos, schemas e versoes em
[`docs/events/event-catalog.md`](docs/events/event-catalog.md).

## Decisoes de arquitetura (ADRs)

- [0001 - Padrao Outbox para publicacao de eventos](docs/architecture/decisions/0001-outbox-pattern.md)
- [0002 - Particao do Kafka pela conta de origem](docs/architecture/decisions/0002-particao-por-conta.md)
- [0003 - Idempotencia (comando e consumer)](docs/architecture/decisions/0003-idempotencia.md)

## Roadmap de construcao

- [x] Fatia 1 - POST sincrono: debito e credito atomicos, idempotencia, Postgres, Flyway
- [ ] Fatia 2 - Outbox + relay publicando `transfer.completed` no Kafka
- [ ] Fatia 3 - Consumer idempotente com DLQ + read model de extrato (MongoDB, CQRS)
- [ ] Fatia 4 - Testes de integracao com Testcontainers (Postgres + Kafka + Mongo)
- [ ] Fatia 5 - Teste de carga (p95/p99) e infra em Terraform