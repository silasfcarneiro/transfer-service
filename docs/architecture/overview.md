# Arquitetura - transfer-service

Como o serviço funciona, do request à propagação do evento. Notação: C4 Model.

## O problema

Transferir valor de uma conta para outra exige três garantias que, juntas, são o
que torna o problema interessante:

1. **Atomicidade:** debitar a origem e creditar o destino tem que ser tudo ou
   nada. Nunca debitar sem creditar.
2. **Idempotência:** se o cliente reenvia a mesma transferência (timeout, retry),
   ela não pode acontecer duas vezes.
3. **Propagação confiável:** extrato, antifraude e notificação precisam saber da
   transferência, sem que o cliente espere por isso.

## Princípio central: comando síncrono, propagação assíncrona

- **Comando (síncrono):** a transferência em si. O cliente precisa da resposta na
  hora - deu certo, saldo insuficiente, ou duplicata. Responde 201/409/422/400.
- **Propagação (assíncrona):** avisar o resto do sistema. O extrato, a antifraude
  e a notificação reagem ao evento `transfer.completed` depois, fora do request.

> O Kafka desacopla os **consumidores** do evento, não o **cliente** do comando.

Por que o débito/crédito é síncrono e não bufferizado numa fila: a resposta ao
cliente depende de uma verificação de consistência (saldo suficiente). Não dá
para responder "ok" antes de validar o saldo e efetuar o débito - senão dois
débitos concorrentes poderiam estourar o saldo. Validação e escrita da mesma
invariante ficam na mesma transação síncrona.

## C4 - Nível 1: Contexto

```mermaid
flowchart TD
    Client["Cliente / canal<br/>(app, outro serviço)"]
    Svc["transfer-service<br/>executa transferências<br/>publica eventos"]
    Consumers["Consumidores<br/>extrato, antifraude,<br/>notificação"]

    Client -- "POST /transfers<br/>Idempotency-Key" --> Svc
    Svc -- "transfer.completed" --> Consumers

    classDef ext fill:#E6F1FB,stroke:#185FA5,color:#0C447C
    classDef sys fill:#EEEDFE,stroke:#534AB7,color:#3C3489
    class Client,Consumers ext
    class Svc sys
```

## C4 - Nível 2: Container

```mermaid
flowchart TD
    C["Cliente / canal"]

    subgraph SVC["transfer-service (Spring Boot)"]
        direction TB
        API["Controller REST<br/>valida entrada, status HTTP"]
        DomainSvc["Service<br/>idempotência, débito e crédito<br/>na mesma transação"]
        Repo["Repository (JPA)"]
        Relay["Outbox Relay<br/>@Scheduled"]
        Consumer["Consumer extrato<br/>read model"]
        API --> DomainSvc --> Repo
    end

    PG[("PostgreSQL<br/>account · transfer ·<br/>idempotency_key · outbox")]
    K{{"Kafka<br/>transfer-events"}}
    MG[("MongoDB<br/>extrato (read model)")]

    C -- "HTTP" --> API
    Repo -- "débito + crédito + transfer<br/>+ outbox (mesma transação)" --> PG
    Relay -- "lê outbox não publicada" --> PG
    Relay -- "publica" --> K
    K -- "consome" --> Consumer
    Consumer -- "grava lançamento<br/>+ processed_event" --> MG

    classDef ext fill:#E6F1FB,stroke:#185FA5,color:#0C447C
    classDef comp fill:#EEEDFE,stroke:#534AB7,color:#3C3489
    classDef store fill:#E1F5EE,stroke:#0F6E56,color:#04342C
    class C ext
    class API,DomainSvc,Repo,Relay,Consumer comp
    class PG,K,MG store
```

Repare no CQRS: a **escrita** (saldo, transferência) é transacional no Postgres;
a **leitura** (extrato) vive no MongoDB, alimentada pelo evento. O extrato é
imutável e consultado por conta e período - padrão de acesso conhecido, volume
alto, tolera atraso de segundos.

## Fluxo 1: executar transferência (síncrono)

```mermaid
sequenceDiagram
    participant C as Cliente
    participant API as Controller
    participant SVC as Service
    participant DB as PostgreSQL

    C->>API: POST /transfers<br/>{origem, destino, valor}<br/>Idempotency-Key
    API->>SVC: valida payload (valor > 0, contas != )
    SVC->>DB: BEGIN
    SVC->>DB: já existe essa Idempotency-Key?
    alt chave já usada
        DB-->>API: transferência anterior
        API-->>C: 409 (ou 200 com o resultado original)
    else chave nova
        SVC->>DB: saldo da origem suficiente?
        alt saldo insuficiente
            DB-->>API: falha
            API-->>C: 422 INSUFFICIENT_FUNDS
        else saldo ok
            SVC->>DB: debita origem, credita destino
            SVC->>DB: INSERT transfer
            SVC->>DB: INSERT idempotency_key
            SVC->>DB: INSERT outbox (transfer.completed)
            DB->>DB: COMMIT (tudo junto ou nada)
            API-->>C: 201 {transferId}
        end
    end
```

Tudo dentro de um `BEGIN/COMMIT`: verificação de saldo, débito, crédito, registro
da transferência, chave de idempotência e evento na outbox. É a atomicidade que
garante que nunca há débito sem crédito, nem evento sem transferência.

## Fluxo 2: propagação (assíncrono)

```mermaid
sequenceDiagram
    participant Relay as Outbox Relay
    participant DB as PostgreSQL
    participant K as Kafka
    participant C as Consumer extrato
    participant MG as MongoDB

    loop a cada 500ms
        Relay->>DB: SELECT outbox WHERE published_at IS NULL (SKIP LOCKED)
        Relay->>K: publica transfer.completed (chave = contaOrigem)
        K-->>Relay: ack
        Relay->>DB: UPDATE outbox SET published_at = now()
    end

    K->>C: entrega transfer.completed
    C->>MG: eventId já processado?
    alt duplicata
        MG-->>C: ignora
    else primeira vez
        C->>MG: grava lançamento no extrato das duas contas
    end
```

## Modelo de dados (resumo)

| Tabela / coleção | Onde | Papel |
|---|---|---|
| `account` | Postgres | conta e saldo; escrita transacional |
| `transfer` | Postgres | registro da transferência (origem, destino, valor, status) |
| `idempotency_key` | Postgres | chaves já usadas, para não duplicar transferência |
| `outbox` | Postgres | eventos pendentes, escritos na transação do comando |
| `processed_event` | Mongo | ids de eventos consumidos (idempotência do consumer) |
| `statement` (extrato) | Mongo | read model: lançamentos por conta (CQRS) |

Detalhe em [`data-model.md`](data-model.md).

## Por que este desenho responde bem em entrevista

| Pergunta | Onde |
|---|---|
| Débito e crédito não podem divergir | mesma transação (Fluxo 1) |
| Retry não pode transferir duas vezes | Idempotency-Key (Fluxo 1 + ADR 0003) |
| Banco e broker consistentes | Outbox (ADR 0001) |
| Duplicata no consumer | processed_event (ADR 0003) |
| Ordem dos eventos | partição por conta de origem (ADR 0002) |
| Extrato sem pesar o banco de escrita | read model no Mongo via evento (CQRS) |
| Síncrono vs assíncrono | princípio central + os dois fluxos |