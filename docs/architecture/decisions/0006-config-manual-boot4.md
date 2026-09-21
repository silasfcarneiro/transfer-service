# ADR 0006 - Configuracao manual de infraestrutura (Spring Boot 4)

- Status: aceito
- Data: 2026-09-21

## Contexto

O projeto foi iniciado com **Spring Boot 4** (Spring Framework 7), a geracao mais
recente na data. Ao integrar Kafka (producer e consumer) e MongoDB, a
auto-configuracao do Spring Boot **nao criou os beans esperados**:

- `KafkaTemplate` nao foi criado, apesar de `spring-kafka` no classpath e das
  propriedades `spring.kafka.*` definidas.
- `kafkaListenerContainerFactory` (necessario para `@KafkaListener`) tambem nao
  foi criado.
- A propriedade `spring.data.mongodb.uuid-representation` nao teve efeito, e o
  driver recusou-se a gravar UUID; o database default tambem nao respeitou a
  propriedade e caiu em `test`.

A causa e a maturidade da versao: o Boot 4 e novo o suficiente para que partes
da auto-configuracao ainda nao correspondam ao comportamento documentado (a
documentacao majoritaria e do Boot 3).

## Decisao

Configurar manualmente, via `@Bean`, a infraestrutura que a auto-configuracao
nao entregou:

- `KafkaConfig`: `ProducerFactory` + `KafkaTemplate`; `ConsumerFactory` +
  `ConcurrentKafkaListenerContainerFactory` com `DefaultErrorHandler` e
  `DeadLetterPublishingRecoverer` (retry + DLQ).
- `MongoConfig`: `MongoClient` com `uuidRepresentation(STANDARD)` e
  `MongoDatabaseFactory` apontando explicitamente o database `transfer`.

## O que NAO era problema do framework

Vale registrar, porque a distincao importa: durante a implementacao da DLQ, o
consumer entrou em retry infinito e a DLQ parecia nao funcionar. A investigacao
mostrou que **nao era aresta do Boot 4** - eram dois problemas nossos:

1. Um bug no consumer usando `kotlin.time.Clock` (tipo errado) em vez de
   `java.time.Instant`, que fazia todo evento falhar.
2. O topico de dead-letter chamava-se `transfer-events-dlt` (sufixo `-dlt` com
   hifen, padrao do recoverer), e procuravamos em `transfer-events.DLT` (ponto).
   A DLQ estava funcionando o tempo todo; o log `Successful dead-letter
   publication` confirmou.

Licao: nem todo problema numa versao nova e da versao. A DLQ funciona por
auto-comportamento padrao do `DeadLetterPublishingRecoverer`; o que precisou de
bean manual foi a criacao das factories, nao a DLQ em si.

## Alternativas consideradas

**A. Voltar para o Spring Boot 3.4 (estavel).** A auto-configuracao das factories
funcionaria sem beans manuais, com documentacao madura. Custo: recriar o
esqueleto. Para um projeto com prazo, provavelmente a escolha mais pragmatica.

**B. Manter o Boot 4 e configurar manualmente.** Escolhida. O custo ja tinha sido
pago no Kafka, e a configuracao explicita deixa claro o que a auto-config faria
escondido - valor de controle e de aprendizado.

## Consequencias

**Positivas**
- Controle explicito sobre producer, consumer, error handler e cliente Mongo.
- Entendimento de baixo nivel do que o Spring Boot automatiza.

**Negativas / trade-offs**
- Cada nova integracao no Boot 4 tende a exigir configuracao manual das
  factories.
- Mais codigo de configuracao para manter; parte dele seria removivel numa
  versao estavel.

## Pergunta de entrevista que esta decisao responde

*"Por que voce configurou Kafka e Mongo manualmente?"*
Iniciei no Spring Boot 4, cuja auto-configuracao ainda tem arestas: KafkaTemplate,
container factory do listener e uuid-representation do Mongo nao vieram pelas
propriedades. Configurei os beans na mao - o que me deu controle e o entendimento
do que a auto-config faz por baixo. Aprendi tambem a nao atribuir tudo a versao:
parte dos problemas que pareciam do framework eram meus (tipo de data errado,
nome do topico de DLQ). Para um projeto com prazo, a alternativa correta seria o
Boot 3.4 estavel.