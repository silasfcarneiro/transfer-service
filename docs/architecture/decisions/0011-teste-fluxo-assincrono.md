# ADR 0011 - Teste do fluxo assincrono (consistencia eventual)

- Status: aceito
- Data: 2026-09-22

## Contexto

A transferencia grava no Postgres de forma sincrona, mas o extrato so aparece
depois de um caminho assincrono: relay le a outbox -> publica no Kafka ->
consumer processa -> grava no Mongo. Entre a transferencia e o extrato existe um
atraso indeterminado (consistencia eventual).

Um teste ponta a ponta desse fluxo nao pode assertar imediatamente apos a
transferencia - o extrato ainda estaria vazio, porque o consumer nao processou.
Assertar na hora daria falso negativo: o classico erro de testar um fluxo
assincrono como se fosse sincrono.

## Decisao

Usar **Awaitility** para esperar a consistencia eventual, com timeout:

```
await()
  .atMost(Duration.ofSeconds(20))
  .pollInterval(Duration.ofMillis(500))
  .untilAsserted {
      // assercoes sobre o extrato
  }
```

O `untilAsserted` reexecuta as assercoes periodicamente; enquanto falharem
(extrato vazio), espera; quando passarem (extrato propagado), o teste passa. Se
nao acontecer dentro do timeout, o teste falha - sinal de que algo travou no
fluxo.

## Aresta do Kafka com @ServiceConnection

No teste E2E, o `@ServiceConnection` funcionou para Postgres e Mongo, mas nao
para o `KafkaContainer` no Spring Boot 4 (`ConnectionDetailsNotFoundException`).
Contornado com `@DynamicPropertySource`: o teste le o `bootstrapServers` do
container e injeta manualmente em `spring.kafka.bootstrap-servers`. E o mesmo
padrao do codigo principal - quando a auto-config do Boot 4 nao entrega, assume-se
a configuracao explicita (ver [ADR 0006](0006-config-manual-boot4.md)).

## Alternativas consideradas

**A. Sleep fixo (Thread.sleep) antes de assertar.** Frageis: um sleep curto falha
sob carga, um longo torna a suite lenta. E chute, nao espera inteligente.
Rejeitado.

**B. Awaitility com untilAsserted.** Escolhida. Espera exatamente o necessario,
com teto de paciencia. Rapido quando propaga rapido, e falha claramente se
estourar o timeout.

## Consequencias

**Positivas**
- Testa o fluxo distribuido real (Postgres -> Kafka -> Mongo) de ponta a ponta.
- Trata consistencia eventual de forma correta, sem sleep magico.

**Negativas / trade-offs**
- Teste mais lento e pesado (tres containers, espera de propagacao).
- Timeout precisa ser calibrado: curto demais gera falso negativo sob carga,
  longo demais atrasa a suite. 20s e folgado para o ambiente local.

## Pergunta de entrevista que esta decisao responde

*"Como voce testa um fluxo assincrono, tipo um evento que alimenta um read model?"*
Com Awaitility: nao asserto imediatamente, porque a propagacao (relay, Kafka,
consumer) leva um tempo indeterminado - consistencia eventual. Uso untilAsserted
com timeout: o teste espera a condicao virar verdade e falha se nao acontecer no
limite. Sleep fixo seria fragil. E o erro comum e testar assincrono como sincrono,
assertando na hora e recebendo falso negativo.
