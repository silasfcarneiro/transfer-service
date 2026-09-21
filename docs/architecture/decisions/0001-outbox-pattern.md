# ADR 0001 - Padrão Outbox para publicação de eventos

- Status: aceito
- Data: 2026-09-21

## Contexto

Ao concluir uma transferência, precisamos de duas coisas:

1. Persistir a transferência e os saldos no Postgres.
2. Publicar o evento `transfer.completed` no Kafka, para que extrato,
   antifraude e notificação reajam.

Banco e broker são sistemas transacionais diferentes; não há uma transação única
que abranja os dois. Se fizermos "salvar no banco, depois publicar no Kafka" e o
processo cair entre os dois, a transferência aconteceu mas o evento nunca sai -
o extrato do cliente não registra o lançamento, a antifraude não avalia. Em
domínio financeiro, isso é grave.

## Alternativas consideradas

**A. Publicar no request (`kafkaTemplate.send` dentro do handler).** A escrita no
banco e a publicação não são atômicas - exatamente o cenário de inconsistência
acima. Rejeitada.

**B. Two-Phase Commit (2PC / XA) entre Postgres e Kafka.** O Kafka não suporta
bem XA, o 2PC é lento, acopla os sistemas e trava recursos sob falha do
coordenador. Complexidade alta demais. Rejeitada.

**C. Padrão Outbox.** Escolhida.

## Decisão

Na **mesma transação** que efetua o débito, o crédito e registra a
transferência, gravamos o evento `transfer.completed` numa tabela `outbox` no
próprio Postgres. Como tudo está na mesma transação de banco, ou tudo acontece ou
nada acontece - consistência garantida sem transação distribuída.

Um **relay** (`@Scheduled`) lê as linhas não publicadas da `outbox`, publica no
Kafka e marca como publicadas após o ack do broker.

```
POST /transfers
   │
   ▼
[ transação Postgres ]
   ├─ debita origem / credita destino
   ├─ INSERT transfer
   ├─ INSERT idempotency_key
   └─ INSERT outbox (transfer.completed)
   [ commit ]              <- tudo junto, ou nada
   │
   ▼ (assíncrono)
relay lê outbox nao publicada --> publica no Kafka --> marca published_at
```

## Consequências

**Positivas**
- Consistência entre transferência e evento sem 2PC.
- Resposta rápida: o request só espera o commit local, não o Kafka.
- Broker fora do ar: o evento fica na outbox e é publicado quando o Kafka volta.
  Não se perde.

**Negativas / trade-offs**
- Entrega **at-least-once**: o relay pode republicar o mesmo evento (publica,
  cai antes de marcar, republica ao reiniciar). Consumer precisa ser idempotente
    - ver [ADR 0003](0003-idempotencia.md).
- Latência de propagação igual ao ciclo do relay. Aceitável, pois a propagação
  é assíncrona por design.
- Uma tabela e um processo a mais para operar.

## Pergunta de entrevista que esta decisão responde

*"Como você garante que o evento sai se o banco commitou a transferência?"*
Outbox: o evento é gravado na mesma transação da transferência, e um relay
publica depois. Evita o 2PC, ao custo de entrega at-least-once, que se resolve
com idempotência no consumer.