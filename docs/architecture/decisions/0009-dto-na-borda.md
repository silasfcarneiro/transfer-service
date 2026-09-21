# ADR 0009 - Conversao de DTO na borda (service retorna dominio)

- Status: aceito
- Data: 2026-09-21

## Contexto

A aplicacao troca dados com o cliente via DTOs (`CreateTransferRequest`,
`TransferResponse`, `StatementEntryResponse`) e internamente trabalha com
entidades de dominio (`Transfer`, `Account`, `StatementEntry`). A questao e:
**onde acontece a conversao entre dominio e DTO** - no service ou no controller?

Isto e uma convencao de camadas, e ha duas escolas legitimas.

## Decisao

- O **service retorna dominio** (entidades ou tipos de dominio).
- O **controller converte** dominio para DTO de response, e DTO de request para
  os parametros que o service espera.
- O DTO e tratado como contrato da camada web (borda), nao da camada de negocio.

```
Cliente <--DTO--> Controller <--dominio--> Service <--dominio--> Repository
                       ^
                 conversao aqui
```

## Alternativas consideradas

**A. Service retorna o DTO (convencao comum no ecossistema .NET).** No .NET, o
service costuma ser visto como a camada de aplicacao e retorna o DTO diretamente
(frequentemente com AutoMapper). E uma pratica valida e difundida la. Nao
adotada aqui.

**B. Service retorna o dominio, controller converte (convencao comum no Java/Spring).**
Escolhida. Alinha-se ao ecossistema do projeto.

## Justificativa

A camada de negocio (service) nao deve conhecer o formato da apresentacao. O DTO
de response existe por causa da API HTTP; faze-lo nascer no service acopla o
negocio a um detalhe da borda. Mantendo o service em cima do dominio, o mesmo
service pode alimentar outras bordas no futuro (um relatorio, uma mensagem para
outro sistema, outro protocolo), cada uma convertendo para o seu proprio formato.

E uma diferenca de convencao entre ecossistemas, nao de certo/errado: no .NET o
DTO tende a pertencer a aplicacao; no Java, a borda web. O projeto segue a
convencao do ecossistema em que roda.

## Consequencias

**Positivas**
- Service reusavel e desacoplado do protocolo de entrega.
- Responsabilidades claras: negocio no service, formato na borda.

**Negativas / trade-offs**
- A conversao (o `.map`) fica no controller, que pode crescer se houver muitos
  campos; extrai-se para um mapper dedicado se necessario.
- Em arquiteturas com camada de aplicacao/use-case explicita (Clean/Hexagonal
  mais rigida), a conversao poderia morar num application service com output
  proprio - fora do escopo deste projeto, que usa controller-service-repository
  classico.

## Pergunta de entrevista que esta decisao responde

*"Onde voce converte entidade para DTO?"*
No controller. O service retorna o dominio, porque o DTO de response e contrato
da camada web e o negocio nao deve conhecer o formato de apresentacao - assim o
service serve a qualquer borda. Vindo do .NET, onde o service costuma retornar o
DTO, ajustei para a convencao do Java; e diferenca de ecossistema, e sigo a do
contexto em que estou.
