# 0020 — Isolamento de dados nos testes

**Estado:** aceite · **Data:** 2026-09-15

## Contexto

O Postgres dos testes é um container **por contexto Spring**, não por classe, e o Spring
reaproveita o contexto entre classes com a mesma configuração. Quinze classes de
integração partilham assim a mesma base de dados e a mesma organização — a que o
`DemoIdentityConfiguration` semeia no arranque.

Dessas quinze, onze **não** são `@Transactional`, e por boa razão: exercem fluxos que
atravessam fronteiras de transação — o worker a consumir da SQS noutra thread, a cadeia
de filtros real do MockMvc, serviços `@Transactional` chamados de fora. Uma transação
aberta pelo teste não seria a transação que o código sob teste usa, e os dados nem
sequer estariam visíveis do outro lado. O que essas onze escrevem fica commitado.

Durante dez etapas isso não incomodou ninguém: quase todos os testes procuram pelo id
que acabaram de criar, e o lixo do vizinho não aparece num `findById`. A etapa 11 trouxe
o `DashboardQueriesTest`, que faz perguntas de outra natureza — *quantos documentos tem
esta organização?* — e a essas o lixo responde junto:

```
periodTotals   esperado 4   obtido 16
topSuppliers   esperado "505123452"   obtido "Cantina do Zé, Lda."
```

Pior do que falhar: o `runOrder` por omissão do surefire é `filesystem`, que é alfabético
em NTFS e por hash de dentry em ext4. O teste passava na máquina do autor e falhava no
runner do GitHub, o que é o sítio mais caro para descobrir o problema.

## Decisão — truncar as tabelas antes de cada classe

Um `TestExecutionListener` (`support/DatabaseCleanupListener`) trunca todas as tabelas do
schema `public`, excepto o `flyway_schema_history`, no `beforeTestClass`. Nenhuma classe
herda as linhas de outra.

**Antes e não depois.** Assim a primeira classe da execução também arranca limpa, e uma
classe que rebente a meio não deixa a seguinte a pagar por isso.

**Registado em `META-INF/spring.factories`, não por anotação.** "Nenhuma classe herda as
linhas de outra" é uma regra da suíte inteira, não uma opção que cada teste escolhe. Um
`@ExtendWith` em quinze classes seria mais visível, mas também seria quinze sítios onde
alguém se pode esquecer — e a regra deixaria de valer justamente no teste novo que ainda
ninguém reviu.

**Com re-sementeira.** Truncar leva a organização demo à frente, e o provider ficaria a
apontar para uma linha que já não existe. Os beans que semeiam dados ao nível do contexto
implementam `support/TestDataSeeder`; o listener chama-lhes o `seed()` logo a seguir ao
truncate, e o `seed()` é idempotente.

## Alternativas descartadas

**Uma organização por teste**, trocando a identidade antes de cada método. O
`CurrentUserProvider` é a única via pela qual o código de produção sabe em que organização
age — e só controllers e serviços de pedido lhe tocam, nunca o worker —, portanto bastava
semear uma identidade nova no `@BeforeEach` para cada teste ficar na sua empresa, sem
tocar no corpo de nenhum. Isola mais do que a limpeza (separa até métodos da mesma
classe) e custa menos idas à base de dados. Ficou de fora por ser mais indirecta: o teste
deixa de poder dizer em que organização está sem perguntar ao provider, e a explicação de
porque é que as coisas funcionam passa a viver numa extensão de JUnit.

**Pôr `@Transactional` nas onze.** Resolveria no papel e não resolve na prática, pelo
motivo do Contexto: essas classes existem precisamente para exercer o que acontece
depois do commit. Anotá-las seria fazê-las deixar de testar o que dizem testar.

## O que isto não resolve

- **Métodos dentro da mesma classe** continuam a partilhar a base de dados. Uma classe
  futura com asserções agregadas sobre os seus próprios métodos volta a ter o problema —
  nessa altura, ou é `@Transactional`, ou se muda para a alternativa da organização por
  teste.
- **As filas do LocalStack** não são tabelas e não são truncadas. Se algum dia uma
  mensagem sobrar de uma classe para a seguinte, é outro ADR.
