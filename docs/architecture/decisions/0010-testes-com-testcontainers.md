# ADR 0010 - Testes de integracao com Testcontainers

- Status: aceito
- Data: 2026-09-22

## Contexto

O servico precisa de testes que deem confianca real: idempotencia, atomicidade
(rollback), validacoes. Testar isso apenas com mocks nao pega problemas de
integracao - a migration Flyway, a constraint unica da idempotencia, o
comportamento transacional real do banco. Por outro lado, depender do ambiente
local (o `docker compose` ligado) torna os testes frageis e nao reproduziveis
em CI.

## Decisao

Usar **Testcontainers** para os testes de integracao: cada execucao sobe os
bancos reais (PostgreSQL, MongoDB) e o broker (Kafka) em containers efemeros,
via Docker, e os derruba ao final.

- Testes de comportamento do `TransferService` (idempotencia, saldo, validacoes)
  usam um Postgres real via Testcontainers.
- O teste ponta a ponta usa Postgres, Kafka e Mongo simultaneamente.

A conexao e resolvida por `@ServiceConnection` (o Spring le a URL/credenciais do
container automaticamente), com uma excecao tratada no [ADR 0011](0011-teste-fluxo-assincrono.md).

## Alternativas consideradas

**A. Só mocks (unitario puro).** Rapido, mas nao valida integracao real -
migration, constraint, transacao. Nao pega a classe de bug que mais importa aqui.
Complementar, nao suficiente.

**B. Banco em memoria (H2).** Rapido e sem Docker, mas H2 nao e Postgres: nao tem
`jsonb`, `FOR UPDATE SKIP LOCKED`, tipos e comportamentos especificos. Testar
contra um banco diferente do de producao da falsa confianca. Rejeitado.

**C. Depender do docker compose local.** O teste conectaria no Postgres do
Compose. Fragil: exige o ambiente ligado, polui dados, nao roda em CI limpo.
Rejeitado.

**D. Testcontainers.** Escolhida. Banco igual ao de producao, efemero,
reproduzivel em qualquer maquina e no CI.

## Consequencias

**Positivas**
- Testa contra Postgres/Mongo/Kafka reais - a mesma tecnologia da producao.
- Reproduzivel: roda em qualquer maquina com Docker, sem setup manual.
- Encontrou bugs reais (valor negativo, transferencia para a mesma conta) que
  viraram validacoes.

**Negativas / trade-offs**
- Exige Docker rodando na maquina de teste (e no CI).
- Mais lento que teste em memoria (sobe container). Aceitavel para a suite de
  integracao; testes puramente unitarios com mock ficam para a logica isolada.
- Consumo de recursos: o teste E2E sobe tres containers.

## Pergunta de entrevista que esta decisao responde

*"Como voce testa a camada de dados?"*
Com Testcontainers: cada teste sobe um Postgres/Mongo/Kafka real e efemero via
Docker, entao testo contra a mesma tecnologia da producao, sem H2 (que nao tem
jsonb nem skip locked) e sem depender do ambiente local. E reproduzivel no CI.
Inclusive os testes acharam bugs reais - valor negativo e transferencia para a
mesma conta - que virei validacoes.
