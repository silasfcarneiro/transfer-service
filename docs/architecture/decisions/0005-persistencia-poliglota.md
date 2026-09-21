# ADR 0005 - Persistencia poliglota: PostgreSQL para escrita, MongoDB para leitura

- Status: aceito
- Data: 2026-09-21

## Contexto

O servico tem dois lados com necessidades opostas:

- **Escrita (comando):** executar a transferencia. Exige transacao ACID
  (debito e credito atomicos), lock otimista contra concorrencia e consistencia
  forte. O saldo nao pode ficar errado nem por um instante.
- **Leitura (consulta):** o extrato de uma conta. E imutavel (um lancamento nao
  muda depois de escrito), consultado sempre por conta e periodo, com volume de
  leitura alto e tolerancia a segundos de atraso.

Usar o mesmo modelo e o mesmo banco para os dois lados obriga a um compromisso:
ou o modelo de escrita fica poluido com necessidades de leitura, ou a leitura
paga o custo de estruturas pensadas para escrita transacional.

## Decisao

Separar os dois lados (CQRS) e usar o banco adequado a cada um:

- **PostgreSQL** para o modelo de escrita: contas, transferencias, idempotencia,
  outbox. Transacional, com integridade e lock otimista.
- **MongoDB** para o modelo de leitura: o extrato, como documento indexado por
  conta. Alimentado pelos eventos `transfer.completed` via consumer.

Os dois lados sao conectados por evento: a escrita publica, o consumer atualiza
a leitura.

## Alternativas consideradas

**A. Um so Postgres para tudo.** Funciona. CQRS nao exige bancos diferentes -
poderia ser uma tabela de extrato desnormalizada no mesmo Postgres. E uma opcao
legitima e mais simples de operar. Rejeitada aqui por uma razao deliberada: o
projeto quer exercitar persistencia poliglota, e o extrato (documento por conta,
alto volume de leitura por chave) encaixa bem no modelo de documento do Mongo.

**B. Tudo no Mongo.** O Mongo nao da a garantia transacional forte que o debito
e o credito exigem no mesmo instante (transacoes multi-documento existem, mas
sao mais custosas e menos naturais). Escrita financeira pede ACID relacional.
Rejeitada.

**C. Postgres para escrita, Mongo para leitura (CQRS poliglota).** Escolhida.

## Consequencias

**Positivas**
- Cada lado usa o banco que faz melhor: ACID onde precisa, leitura barata onde
  precisa.
- O extrato escala em leitura sem pesar o banco transacional.
- O extrato e reconstruivel: se perdido, reprocessam-se os eventos do topico.

**Negativas / trade-offs**
- Dois bancos para operar, monitorar e versionar - mais complexidade
  operacional.
- Consistencia eventual entre escrita e leitura: o extrato reflete a
  transferencia alguns instantes depois do commit (o tempo do relay + consumer).
  Aceitavel para extrato; o saldo, que precisa ser exato na hora, vive no
  Postgres e e lido de la.
- O consumer precisa ser idempotente (at-least-once), com sua propria tabela de
  eventos processados no Mongo.

## Nota sobre a decisao em producao

Em producao, a escolha entre "tabela desnormalizada no mesmo Postgres" e "Mongo
separado" dependeria de volume, custo e maturidade operacional do time com cada
banco. CQRS e sobre separar o modelo; usar bancos diferentes e uma escolha
adicional, nao uma obrigacao. Aqui foi feita para demonstrar o padrao poliglota.

## Pergunta de entrevista que esta decisao responde

*"Por que voce usou Postgres e Mongo em vez de um so?"*
CQRS: a escrita exige ACID (debito e credito atomicos, lock otimista) e vive no
Postgres; a leitura (extrato) e imutavel, consultada por conta, alto volume, e
vive no Mongo como documento, alimentada por evento. CQRS nao obriga bancos
diferentes - daria para ter uma tabela de leitura no proprio Postgres -, mas
separar os bancos aproveita o modelo de documento e escala a leitura sem pesar
o transacional. A consistencia entre os dois e eventual, o que e aceitavel para
extrato mas nao para saldo, que fica no Postgres.
