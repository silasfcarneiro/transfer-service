# mortmain-character-service

Serviço de criação e gerenciamento de personagens do Mortmain (MMORPG).

O wizard de criação de personagem roda no client (servidor dedicado da UE, não
o jogador). Antes, ele persistia local; agora chama esta API. O serviço guarda
os dados do personagem e publica eventos para o restante do ecossistema
(ranking, analytics, matchmaking) consumir.

## O que ele faz

- Cria personagem com validação de nome (3-16 caracteres, único por conta)
- Lista os personagens de uma conta
- Remove personagem
- Publica eventos `character.created` e `character.deleted` de forma confiável
  (padrão Outbox, sem perder evento em caso de falha do broker)
- Mantém um read model simples (contagem de personagens por origem e sexo),
  alimentado pelos próprios eventos (CQRS)

## Arquitetura em uma frase

Comando síncrono (o client espera a confirmação da criação), propagação
assíncrona (o resto do mundo reage ao evento). A escrita é transacional no
Postgres; o evento vai para a tabela `outbox` na mesma transação e é publicado
no Kafka por um relay, garantindo consistência entre banco e mensageria.

Detalhes em [`docs/architecture/overview.md`](docs/architecture/overview.md).

## Stack

- Kotlin + Spring Boot 3 (Web, Data JPA)
- PostgreSQL (dados + outbox) com migrations via Flyway
- Apache Kafka (eventos)
- Docker Compose para o ambiente local
- Gradle (Kotlin DSL)

## Como rodar

Ver [`docs/runbook/local-setup.md`](docs/runbook/local-setup.md).

```bash
docker compose up -d      # sobe Postgres (e Kafka nas próximas fatias)
./gradlew bootRun         # sobe a aplicação
```

## Endpoints

| Método | Rota | Resposta |
|---|---|---|
| POST | `/accounts/{accountId}/characters` | 201 `{id}` · 409 nome em uso · 400 validação |
| GET | `/accounts/{accountId}/characters` | 200 lista |
| DELETE | `/characters/{id}` | 204 |

Contratos em [`docs/api/openapi.md`](docs/api/openapi.md).

## Eventos

Catálogo de tópicos, schemas e versões em
[`docs/events/event-catalog.md`](docs/events/event-catalog.md).

## Decisões de arquitetura (ADRs)

Cada decisão relevante é registrada em `docs/architecture/decisions/`:

- [0001 - Padrão Outbox para publicação de eventos](docs/architecture/decisions/0001-outbox-pattern.md)
- [0002 - Partição do Kafka por accountId](docs/architecture/decisions/0002-particao-por-accountid.md)
- [0003 - Idempotência no consumer](docs/architecture/decisions/0003-idempotencia-consumer.md)

## Roadmap de construção

- [ ] Fatia 1 - POST síncrono, validação, Postgres, Flyway
- [ ] Fatia 2 - Outbox + relay publicando no Kafka
- [ ] Fatia 3 - Consumer idempotente com DLQ, retry e read model
- [ ] Fatia 4 - Testes de integração com Testcontainers