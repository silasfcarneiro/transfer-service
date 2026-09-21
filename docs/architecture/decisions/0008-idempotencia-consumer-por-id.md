# ADR 0008 - Idempotencia do consumer por id deterministico

- Status: aceito
- Data: 2026-09-21
- Substitui parcialmente: a abordagem prevista no [ADR 0003](0003-idempotencia.md)

## Contexto

O [ADR 0003](0003-idempotencia.md) previu, para a idempotencia do consumer, uma
tabela `processed_event(event_id)`: antes de aplicar o evento, o consumer gravaria
o `eventId` na mesma transacao; um `eventId` repetido violaria a PK e o evento
seria ignorado.

Na implementacao do consumer que monta o extrato (`StatementEntry` no Mongo),
surgiu uma alternativa mais simples que aproveita a natureza da operacao.

## Decisao

Em vez de uma tabela de eventos processados, o `_id` de cada lancamento do extrato
e **deterministico**: `"{transferId}-{type}"` (ex.: `abc-DEBIT`, `abc-CREDIT`).

Como o `_id` e o mesmo para o mesmo lancamento, reprocessar o mesmo evento faz um
`save` sobre o mesmo documento - **sobrescreve em vez de duplicar**. O efeito de
processar N vezes e identico a processar uma vez.

```
evento transfer.completed (transferId = abc)
  save StatementEntry(_id = "abc-DEBIT", ...)    # 1a vez cria; Na vez sobrescreve
  save StatementEntry(_id = "abc-CREDIT", ...)   # idem
```

## Por que esta abordagem aqui

A operacao do consumer e um **upsert idempotente por natureza**: gravar o
lancamento X do extrato. Como o resultado nao e incremental (nao e "somar 1", e
"registrar este lancamento"), um id deterministico ja garante que a repeticao nao
tem efeito colateral. Nao e preciso rastrear eventos processados a parte.

Isto e a "alternativa B" mencionada no proprio ADR 0003 (tornar a operacao
naturalmente idempotente) - viavel aqui porque a operacao permite.

## Quando a tabela processed_event ainda seria necessaria

Se o consumer fizesse uma operacao **incremental ou com efeito colateral nao
regravavel** - por exemplo, `saldo += valor`, enviar uma notificacao, chamar um
servico externo -, o id deterministico nao bastaria (somar duas vezes duplica),
e a tabela de eventos processados voltaria a ser a solucao. A escolha depende da
natureza da operacao do consumer.

## Consequencias

**Positivas**
- Sem tabela extra e sem transacao de dedup no consumer; mais simples.
- Idempotencia garantida pela chave, natural ao upsert.

**Negativas / trade-offs**
- So funciona para operacoes regravaveis (upsert). Nao e uma solucao geral de
  idempotencia de consumer.
- O id do read model fica acoplado ao id do evento de origem (transferId), o que
  aqui e desejavel, mas e uma restricao a ter em mente.

## Pergunta de entrevista que esta decisao responde

*"Como o consumer lida com entrega duplicada?"*
A operacao dele e um upsert do lancamento do extrato, entao usei um id
deterministico (transferId + tipo): reprocessar sobrescreve em vez de duplicar,
idempotencia natural sem tabela extra. Isso vale porque a operacao e regravavel;
se fosse incremental, como somar saldo ou disparar notificacao, eu voltaria a uma
tabela de eventos processados para deduplicar.
