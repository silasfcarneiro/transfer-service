# ADR 0003 - Idempotência (comando e consumer)

- Status: aceito
- Data: 2026-09-21

## Contexto

Idempotência aparece em dois pontos diferentes deste serviço, por razões
diferentes. Vale tratar os dois aqui.

1. **No comando (POST /transfers):** redes falham. O cliente envia a
   transferência, não recebe resposta (timeout), e reenvia. Sem proteção, a
   transferência acontece duas vezes - o cliente é debitado em dobro.
2. **No consumer:** o Outbox entrega at-least-once (ver
   [ADR 0001](0001-outbox-pattern.md)). O mesmo evento `transfer.completed` pode
   chegar duas vezes; sem proteção, o extrato registra o lançamento em duplicata.

## Decisão

### Idempotência do comando: `Idempotency-Key`

O cliente envia um header `Idempotency-Key` (um UUID que ele gera por tentativa
lógica de transferência). Na **mesma transação** que efetua a transferência, o
serviço faz `INSERT` dessa chave na tabela `idempotency_key`.

- Se a chave é nova: a transferência é executada e a chave gravada. 201.
- Se a chave já existe: o INSERT viola a PK, a transação faz rollback, e o
  serviço responde com o resultado da transferência **original** (guardada em
  `idempotency_key.transfer_id`) - sem executar de novo.

Assim, N envios da mesma tentativa produzem **uma** transferência.

### Idempotência do consumer: `processed_event`

O consumer, ao receber um evento, grava o `eventId` em `processed_event` na
mesma transação (no Mongo, no caso do extrato) que aplica o lançamento. Se o
`eventId` já existe, ignora. Processar o mesmo evento N vezes tem o efeito de
processar uma vez.

```
comando:  INSERT idempotency_key  ┐ mesma transação da transferência
                                   ┘ duplicada -> devolve resultado original

consumer: INSERT processed_event  ┐ mesma transação do lançamento
                                   ┘ duplicada -> ignora
```

## Alternativas consideradas

- **Exactly-once do Kafka** (transações + isolation): complexo, mais lento,
  acopla produtor e consumer. Canhão para mosca. Rejeitado - at-least-once +
  idempotência resolve com mais simplicidade.
- **Sem Idempotency-Key, confiando no cliente não reenviar:** ingênuo; redes
  reenviam por conta própria. Rejeitado.

## Mensagem venenosa e DLQ

Idempotência trata duplicata, não veneno. Um evento que falha sempre (payload
inválido, bug) não pode travar a partição em retry infinito. `DefaultErrorHandler`
com backoff exponencial (3 tentativas) e, ao esgotar, `DeadLetterPublishingRecoverer`
publica em `transfer-events.DLT`. Mensagem parada na DLT gera alerta e análise.

## Consequências

**Positivas**
- Transferência segura contra retry de rede.
- Consumo seguro contra reentrega, sem exactly-once complexo.
- Simples de testar (enviar duas vezes com a mesma chave -> uma transferência;
  processar o mesmo evento duas vezes -> um lançamento).

**Negativas / trade-offs**
- `idempotency_key` e `processed_event` crescem; precisam de expiração por janela
  de tempo (fora do escopo do MVP, registrado).
- Um acesso a mais por operação (o INSERT de dedup).

## Pergunta de entrevista que esta decisão responde

*"Como você evita que um retry transfira duas vezes? E que o consumer processe
duas vezes?"*
No comando, Idempotency-Key com constraint única na mesma transação - o segundo
envio devolve o resultado original. No consumer, tabela de eventos processados,
também na transação - at-least-once é o normal, então o consumo é idempotente. E
para veneno, retry com backoff e DLQ.