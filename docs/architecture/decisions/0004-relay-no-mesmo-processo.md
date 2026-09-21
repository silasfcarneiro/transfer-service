# ADR 0004 - Relay do Outbox no mesmo processo da API

- Status: aceito
- Data: 2026-09-21

## Contexto

O relay do Outbox (ver [ADR 0001](0001-outbox-pattern.md)) le a tabela `outbox`
periodicamente e publica os eventos pendentes no Kafka. Ele e um worker: roda
por agendamento (`@Scheduled`), fora do fluxo de request.

A questao e onde esse worker executa:

- **Junto da API:** o `@Scheduled` roda dentro da mesma aplicacao que atende os
  requests HTTP. Um processo, um deploy.
- **Separado:** a partir do mesmo codigo, sobe-se um segundo processo dedicado
  ao worker (perfil Spring "worker"), com deploy proprio.

## Decisao

O relay roda **no mesmo processo da API**, ativado por `@Scheduled` +
`@EnableScheduling`.

## Justificativa

Para o escopo atual (e para a maioria dos servicos), o worker junto da API e o
suficiente: um deploy, menos infraestrutura, o relay compartilha o mesmo pool de
conexao. Separar seria complexidade operacional sem ganho concreto agora.

A separacao se justifica quando:

- O processamento assincrono tem **perfil de recurso diferente** do request
  (volume alto de eventos, picos, processamento pesado) e precisa **escalar
  independentemente** da API.
- Precisa-se **isolar falha**: um relay sobrecarregado nao pode degradar o
  atendimento de requests.

Nesses casos, o mesmo codigo sobe com um perfil dedicado, sem reescrever nada -
so muda o que e ativado em cada processo.

## Concorrencia entre relays (consequencia importante)

Mesmo no modelo "junto da API", ao escalar horizontalmente a aplicacao existem
**varias instancias**, cada uma com seu relay. Dois relays podem ler a mesma
linha da outbox e publicar o evento duas vezes.

Por isso o relay le os pendentes com `SELECT ... FOR UPDATE SKIP LOCKED`: cada
instancia trava as linhas que pegou, e as outras **pulam** essas linhas em vez
de esperar. Isso distribui o trabalho entre os relays sem duplicar nem
bloquear.

Vale notar que isso reduz, mas nao elimina, a duplicidade (o relay pode publicar
e cair antes de marcar como publicado). A entrega continua **at-least-once**, e a
protecao final e a idempotencia no consumer (ver
[ADR 0003](0003-idempotencia.md)).

## Consequencias

**Positivas**
- Simplicidade: um deploy, uma coisa para operar.
- Caminho de evolucao claro (perfil dedicado) sem reescrita.

**Negativas / trade-offs**
- O relay compete por CPU, memoria e conexoes com a API no mesmo processo.
- Escalar a API multiplica os relays; exige `SKIP LOCKED` para coordena-los.

## Pergunta de entrevista que esta decisao responde

*"O worker roda junto da API ou separado?"*
Junto, por simplicidade - e o suficiente na maioria dos casos. Separo em deploy
proprio quando o assincrono precisa escalar ou falhar de forma independente do
request; e o mesmo codigo com outro perfil. E como pode haver varias instancias
com relay, uso FOR UPDATE SKIP LOCKED para os relays nao publicarem a mesma linha
em duplicata.
