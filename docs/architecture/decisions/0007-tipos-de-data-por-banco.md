# ADR 0007 - Tipos de data: OffsetDateTime no Postgres, Instant no Mongo

- Status: aceito
- Data: 2026-09-21

## Contexto

As entidades tem campos de data/hora. No lado da escrita (Postgres/JPA), a
entidade `Transfer` usa `OffsetDateTime`, que mapeia naturalmente para
`timestamptz` e preserva o offset de fuso. No lado da leitura (Mongo), a tentativa
de gravar `OffsetDateTime` no documento `StatementEntry` falhou:

```
Can't find a codec for class java.time.OffsetDateTime
```

O driver do MongoDB nao tem codec nativo para `OffsetDateTime`.

## Decisao

Usar o tipo mais adequado a cada banco:

- **Postgres (escrita):** `OffsetDateTime`, mapeado para `timestamptz`.
- **Mongo (leitura / read model):** `Instant`, que o driver grava nativamente.

## Justificativa

Nao e apenas um contorno do codec. `Instant` representa um ponto no tempo em UTC,
sem offset - e o tipo canonico para registrar "quando algo aconteceu". Num read
model de extrato, o que importa e o instante do lancamento; o fuso de exibicao e
responsabilidade da apresentacao, nao do dado armazenado. Guardar em UTC e
converter para o fuso do usuario apenas na exibicao e boa pratica e evita
ambiguidade.

O `OffsetDateTime` continua adequado no Postgres, onde o `timestamptz` lida bem
com offset e a informacao de fuso pode ser util no dominio transacional.

## Consequencias

**Positivas**
- Cada banco usa o tipo que grava melhor, sem conversores customizados.
- Read model em UTC, sem ambiguidade de fuso.

**Negativas / trade-offs**
- Dois tipos de data no codigo (um por lado); ao mapear de um para outro, a
  conversao precisa ser consciente (Instant perde o offset, o que aqui e
  aceitavel).

## Pergunta de entrevista que esta decisao responde

*"Como voce representa data e hora?"*
No dominio transacional (Postgres), OffsetDateTime com timestamptz. No read model
(Mongo), Instant em UTC - o tipo canonico para registrar quando algo aconteceu,
deixando a conversao de fuso para a exibicao. Evito guardar fuso onde nao
preciso, e nunca uso tipos sem fuso definido, que sao fonte de bug.
