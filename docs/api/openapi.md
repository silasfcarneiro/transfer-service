# API - transfer-service

Contrato dos endpoints HTTP. Valores monetários sempre em **centavos** (inteiro):
R$ 100,00 = `10000`.

Autenticação e versionamento de rota (ex. `/v1`) ficam fora do escopo do MVP.

---

## POST /transfers

Executa uma transferência entre duas contas. Síncrono: a resposta confirma o
resultado na hora.

### Request

**Headers**

| Header | Obrigatório | Descrição |
|---|---|---|
| `Content-Type: application/json` | sim | |
| `Idempotency-Key` | sim | identificador único da tentativa; reenviar a mesma chave não repete a transferência |

**Body**

```json
{
  "sourceAccountId": "11111111-1111-1111-1111-111111111111",
  "targetAccountId": "22222222-2222-2222-2222-222222222222",
  "amount": 10000
}
```

| Campo | Tipo | Regra |
|---|---|---|
| `sourceAccountId` | uuid | conta debitada; deve existir |
| `targetAccountId` | uuid | conta creditada; deve existir e ser diferente da origem |
| `amount` | inteiro (long) | valor em centavos; > 0 |

### Respostas

**201 Created** - transferência efetuada (ou já efetuada antes, com a mesma
`Idempotency-Key`).

```json
{
  "transferId": "bc6c730c-21e2-4706-8cf5-ef5a0c2ecf3d",
  "status": "COMPLETED"
}
```

**422 Unprocessable Entity** - saldo insuficiente na origem.

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "Saldo insuficiente"
}
```

**400 Bad Request** - payload inválido (valor <= 0, conta ausente, contas
iguais, uuid malformado).

**404 Not Found** - conta de origem ou destino não encontrada.

> Padronização de erro em `ProblemDetail` (RFC 9457) via `@ControllerAdvice` é
> uma melhoria planejada; hoje os erros ainda saem no formato padrão do Spring.

### Idempotência na prática

Enviar duas vezes o mesmo request com a mesma `Idempotency-Key`:

1. Primeira vez: executa a transferência, retorna 201 com o `transferId`.
2. Segunda vez: **não** executa de novo; retorna o **mesmo** `transferId`. O
   saldo é debitado uma única vez.

É a proteção contra retry de rede - ver
[ADR 0003](../architecture/decisions/0003-idempotencia.md).

### Exemplo (curl)

```bash
curl -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: chave-001" \
  -d '{"sourceAccountId":"11111111-1111-1111-1111-111111111111","targetAccountId":"22222222-2222-2222-2222-222222222222","amount":10000}'
```

---

## GET /accounts/{accountId}/transfers

Extrato de uma conta: transferências que ela enviou e recebeu. Lê do read model
(CQRS), alimentado pelos eventos - **fatia 3, ainda não implementado**.

### Request

| Parâmetro | Onde | Descrição |
|---|---|---|
| `accountId` | path | conta cujo extrato se quer |
| `page` / `cursor` | query | paginação (a definir) |

### Resposta (prevista)

**200 OK**

```json
{
  "accountId": "11111111-1111-1111-1111-111111111111",
  "entries": [
    { "transferId": "uuid", "type": "DEBIT",  "amount": 10000, "counterparty": "22222222-...", "at": "2026-09-21T18:06:12Z" }
  ]
}
```

`type` é `DEBIT` (a conta enviou) ou `CREDIT` (a conta recebeu).

---

## Sobre gerar isto automaticamente

Este documento é escrito à mão no MVP. A evolução natural é o
**springdoc-openapi**: a partir das anotações dos controllers e DTOs, ele gera a
spec OpenAPI e uma UI Swagger em `/swagger-ui.html` automaticamente, sempre em
sincronia com o código. Fica como melhoria.