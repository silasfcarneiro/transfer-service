# Runbook - Ambiente Local

Como subir e rodar o transfer-service na sua maquina.

## Pre-requisitos

| Ferramenta | Versao | Para que |
|---|---|---|
| JDK | 21 | compilar e rodar a aplicacao |
| Docker + Docker Compose | recente | subir Postgres (e Kafka a partir da fatia 2) |
| Git | qualquer | versionar |

Confirme o Java:

```bash
java -version   # deve indicar 21
```

## 1. Subir a infraestrutura

O `docker-compose.yml` na raiz sobe o Postgres (e o Kafka a partir da fatia 2).

```bash
docker compose up -d
docker compose ps      # confira que os containers estao "Up"
```

Parametros do Postgres (do compose):

| | valor |
|---|---|
| database | transfer |
| user | transfer |
| password | transfer |
| porta | 5432 |

> Se a porta 5432 estiver ocupada (outro Postgres rodando), pare o outro
> (`docker compose down` no projeto dele) ou mude o mapeamento para
> `5433:5432` no compose e ajuste a URL em `application.properties`.

## 2. Subir a aplicacao

```bash
./gradlew bootRun
```

No startup, o **Flyway** roda as migrations automaticamente e cria as tabelas
(`account`, `transfer`, `idempotency_key`, `outbox`). O log mostra:

```
Migrating schema "public" to version "1 - create account and transfer"
Tomcat started on port 8080
```

O `bootRun` nao termina - a aplicacao fica no ar. Pare com `Ctrl+C`.

Config relevante (`application.properties`):

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/transfer
spring.datasource.username=transfer
spring.datasource.password=transfer
spring.jpa.hibernate.ddl-auto=validate    # Flyway cria; Hibernate so valida
spring.jpa.show-sql=true
```

O `ddl-auto=validate` e proposital: o Flyway e a fonte de verdade do schema; o
Hibernate apenas confere que as entidades batem com as tabelas e falha o startup
se divergirem.

## 3. Inserir contas de teste

O servico movimenta saldo mas nao cria conta (fora do escopo do MVP). Insira
duas para testar:

```bash
docker compose exec postgres psql -U transfer -d transfer -c "
insert into account (id, owner_name, balance, currency, created_at, updated_at, version) values
('11111111-1111-1111-1111-111111111111', 'Alice', 100000, 'BRL', now(), now(), 0),
('22222222-2222-2222-2222-222222222222', 'Bob',    50000, 'BRL', now(), now(), 0);"
```

Saldo em centavos: Alice com R$ 1.000,00, Bob com R$ 500,00.

## 4. Testar uma transferencia

Alice envia R$ 100,00 (10000 centavos) para o Bob:

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: chave-001" \
  -d '{"sourceAccountId":"11111111-1111-1111-1111-111111111111","targetAccountId":"22222222-2222-2222-2222-222222222222","amount":10000}'
```

Esperado: `201` com `{"transferId":"...","status":"COMPLETED"}`.

Confira os saldos (Alice 90000, Bob 60000):

```bash
docker compose exec postgres psql -U transfer -d transfer -c "select owner_name, balance from account order by owner_name;"
```

Confira o evento na outbox (published_at nulo ate a fatia 2 publicar):

```bash
docker compose exec postgres psql -U transfer -d transfer -c "select topic, message_key, published_at from outbox;"
```

## 5. Testar a idempotencia

Rode o mesmo curl da etapa 4 de novo, com a mesma `Idempotency-Key`. Deve
retornar o mesmo `transferId` e **nao** alterar os saldos - a transferencia nao
e executada duas vezes.

## Comandos uteis

| Comando | O que faz |
|---|---|
| `docker compose up -d` | sobe a infraestrutura |
| `docker compose ps` | status dos containers |
| `docker compose logs postgres` | logs do Postgres |
| `docker compose down` | derruba os containers (dados persistem no volume) |
| `docker compose down -v` | derruba e **apaga os dados** (volume incluso) |
| `./gradlew bootRun` | sobe a aplicacao |
| `./gradlew build` | compila e roda os testes |
| `docker compose exec postgres psql -U transfer -d transfer` | abre o psql no banco |

## Troubleshooting

| Sintoma | Causa provavel |
|---|---|
| App nao sobe: "port 5432 already in use" | outro Postgres rodando; pare-o ou mude a porta no compose |
| App nao sobe: erro de validacao do Hibernate | uma entidade diverge da tabela; a mensagem indica a coluna |
| 500 no POST: "column X is of type jsonb but expression is character varying" | falta `@JdbcTypeCode(SqlTypes.JSON)` nos campos jsonb da entidade Outbox |
| 500 no POST: conta nao encontrada | as contas de teste nao foram inseridas (etapa 3) |
| bootRun parece travado em "Tomcat started" | e normal - a aplicacao fica rodando; use outra janela para testar |