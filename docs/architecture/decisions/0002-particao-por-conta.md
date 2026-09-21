# ADR 0002 - Partição do Kafka pela conta de origem

- Status: aceito
- Data: 2026-09-21

## Contexto

O evento `transfer.completed` é publicado no tópico `transfer-events`. A chave da
mensagem decide a partição (mesma chave -> mesma partição), e o Kafka só garante
ordem dentro de uma partição.

Aqui há uma nuance que não existia em domínios de agregado único: uma
transferência envolve **duas** contas - origem e destino. Qual delas vira a
chave de partição?

## Decisão

A chave é a **conta de origem** (`source_account_id`).

## Por que a conta de origem

A conta de origem é a que sofre o **débito** - a operação que consome saldo e
cuja ordem importa mais. Se a mesma conta faz várias transferências em sequência,
queremos que os eventos saiam na ordem em que os débitos ocorreram, para que o
extrato e qualquer saldo derivado batam.

- **Conta de origem como chave:** ordena os eventos por conta pagadora. Débitos
  da mesma conta são processados em ordem; contas diferentes, em paralelo.
  Escolhido.
- **Conta de destino como chave:** ordenaria por quem recebe, mas o lado crítico
  (o débito, que pode faltar saldo) é o da origem. Rejeitado.
- **transferId como chave:** paralelismo máximo, zero ordem entre transferências
  da mesma conta. Rejeitado.

## O ponto que esta decisão NÃO resolve (e é importante dizer)

A partição garante **ordem**, não **consistência de saldo**. O que impede dois
débitos concorrentes de estourarem o saldo é o **lock otimista** (`version`) no
Postgres, dentro da transação síncrona - não a partição do Kafka. Partição
ordena o evento *depois* que a transferência já aconteceu; a exclusão acontece
antes, no banco.

> Distinção de entrevista: partição (Kafka) resolve ordem; lock/constraint
> (banco) resolve exclusão. Uma não substitui a outra.

## Consequências

**Positivas**
- Ordem dos eventos por conta pagadora, que é onde a ordem importa.
- Paralelismo entre contas diferentes.

**Negativas / trade-offs**
- O extrato da conta de **destino** recebe os eventos na ordem de publicação das
  origens, não necessariamente na ordem cronológica global de créditos que ela
  recebeu. Para extrato isso é aceitável (cada lançamento tem timestamp próprio).
- Hot key: uma conta de origem com volume muito acima das outras viraria gargalo
  na sua partição. Não ocorre no MVP, registrado como risco do padrão.
- O número de partições define o teto de paralelismo do consumer.

## Pergunta de entrevista que esta decisão responde

*"O evento envolve duas contas - como você particiona?"*
Pela conta de origem, porque o débito é o lado crítico e cuja ordem importa. E
deixo claro que a partição garante ordem, não exclusão de saldo - isso é o lock
otimista no banco.