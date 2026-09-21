# Catálogo de Eventos - transfer-service

Contrato dos eventos publicados. Consumidores dependem deste documento.

## Envelope (comum a todos os eventos)

```json
{
  "eventId": "uuid-v7",
  "eventType": "transfer.completed",
  "schemaVersion": 1,
  "occurredAt": "2026-09-21T14:00:00Z",
  "aggregateId": "uuid",
  "payload": { }
}
```

| Campo | Papel |
|---|---|
| `eventId` | UUID único; idempotência do consumer |
| `eventType` | tipo do evento |
| `schemaVersion` | versão do payload; começa em 1 |
| `occurredAt` | quando o fato ocorreu (UTC) |
| `aggregateId` | id do agregado; é a chave de partição (conta de origem) |
| `payload` | dados do evento |

Headers Kafka: `eventType`, `schemaVersion`, `traceId`.

## Tópico: `transfer-events`

Partição pela **conta de origem** (ver
[ADR 0002](../architecture/decisions/0002-particao-por-conta.md)).

### `transfer.completed`

Publicado após uma transferência concluída (mesma transação, via outbox).

- **Chave:** `sourceAccountId`
- **Payload (v1):**

```json
{
  "transferId": "uuid",
  "sourceAccountId": "uuid",
  "targetAccountId": "uuid",
  "amount": 1050,
  "occurredAt": "2026-09-21T14:00:00Z"
}
```

`amount` em centavos (inteiro).

## Consumidores conhecidos

| Consumer | Reage a | O que faz |
|---|---|---|
| extrato (interno) | transfer.completed | grava um DEBIT na origem e um CREDIT no destino (read model) |
| antifraude | transfer.completed | futuro; avalia padrão de transferências |
| notificação | transfer.completed | futuro; avisa o cliente |

Novos consumidores entram sem o produtor mudar - inscrevem no tópico e reagem.

## Regras de evolução de schema

**Permitido (compatível para trás):** adicionar campo opcional novo. Consumidores
antigos ignoram.

**Proibido (quebra consumidores):** renomear, remover, mudar tipo ou significado
de campo.

**Mudança incompatível:** incrementa `schemaVersion` e suporta as duas versões
durante a migração. Nunca altera a v1 em cima.

> Evolução: Avro + Schema Registry valida compatibilidade automaticamente na
> publicação. Fora do escopo do MVP.

## DLQ

Falha após as tentativas com backoff -> `transfer-events.DLT`. Mensagem parada
gera alerta.

## Perguntas de entrevista que este catálogo responde

| Pergunta | Resposta |
|---|---|
| Evoluir schema sem quebrar consumer | campo opcional sim; renomear/remover nunca; incompatível vira nova versão |
| Serviço novo reagir a transferências | inscreve no tópico; produtor não muda |
| Por que envelope comum | eventId, eventType e schemaVersion padronizados em todo evento |