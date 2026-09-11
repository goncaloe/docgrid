# 0011 — Autenticação JWT e isolamento por organização

**Estado:** aceite · **Data:** 2026-09-11

## Contexto

A etapa 06 expõe pela primeira vez a API a um cliente externo: precisa de autenticação,
de papéis (`EMPLOYEE`, `FINANCE`, `MANAGER`, `ADMIN`) e de isolamento por organização em
todas as consultas, sem exceção. Até aqui, `CurrentUserProvider` era satisfeito por um
utilizador de demonstração semeado no arranque (`DevBootstrap`, apagado nesta etapa).

Três decisões tinham alternativas defensáveis e por isso ficam aqui, não só no código.

## Decisão 1 — Par de tokens: access JWT stateless, refresh opaco em BD

O access token é um JWT assinado (HS256), de vida curta (15 minutos por omissão), com as
claims `sub` (utilizador), `org` (organização) e `role`. Não é guardado em lado nenhum —
é validado só pela assinatura, o que o torna barato de verificar em cada pedido.

O refresh token é uma string aleatória opaca (256 bits), sem estrutura interna. Só o seu
hash SHA-256 chega à tabela `refresh_tokens`; perder essa tabela nunca expõe um token
utilizável. Pedir um novo access token **roda** o refresh: revoga o apresentado e emite um
novo na mesma chamada. Um token já revogado que volte a ser apresentado é tratado como
sinal de comprometimento — revoga-se a sessão inteira desse utilizador, não só o pedido.

### Alternativas consideradas

- **Refresh token também como JWT stateless**, sem tabela nova. Mais rápido a construir,
  mas revogar (logout, comprometimento) precisaria de uma lista de revogação de qualquer
  forma — a complexidade de ter estado voltava pela porta dos fundos, só que a decifrar um
  JWT primeiro.

### Consequências

Revogar é um `update` trivial. Cada refresh custa uma consulta e uma escrita adicionais —
aceitável, porque não acontece a cada pedido, só quando o access token expira.

## Decisão 2 — Paginação por offset

`GET /api/documents` e as duas filas usam `Pageable`/`Page` do Spring Data, já usado
internamente desde a etapa 02 nos métodos de consulta de `DocumentRepository`.

### Alternativas consideradas

- **Cursor (keyset)**: mais escalável para listas muito grandes, mas exige codificar um
  cursor opaco e não dá contagem total de páginas de graça. O volume esperado (100 a 300
  documentos por mês, por organização) nunca justifica o custo de implementação.

## Decisão 3 — Aprovação de gestor: verificação no momento de aprovar, sem estado novo

Uma despesa acima de `organizations.approval_threshold` só pode ser aprovada por
`MANAGER` ou `ADMIN`. Esta regra não introduz um estado novo em `DocumentStatus` — o
documento continua em `EXTRACTED`/`NEEDS_REVIEW` até ser aprovado. `DocumentApprovalService`
compara `document.getTotalAmount()` com o limite da organização
(`ApprovalThresholdProvider`, já existente desde a etapa 05) e lança `AccessDeniedException`
se quem chama não tiver o papel certo — o `GlobalExceptionHandler` traduz isso em `403`.

### Alternativas consideradas

- **Estado novo** (`PENDING_MANAGER_APPROVAL`): mais explícito no ciclo de vida, mas reabre
  a máquina de 8 estados e 11 transições já fechada e testada (`DocumentStatusTest`,
  `docs/01-PRODUCT.md`) por um resultado observável equivalente.

### Consequências

O limite de aprovação pode mudar sem qualquer migração; a verificação é sempre feita com o
valor corrente da organização. `ApprovalThresholdRule` (etapa 05) continua a ser só
informativa em `validation_results` — quem impõe a regra é o serviço de aprovação, não o
motor de validação.

**Correção pós-revisão de segurança:** comparar só o total corrente abria uma lacuna —
`FINANCE` corrige `TOTAL_AMOUNT` (endpoint que já lhe é permitido) para um valor abaixo do
limite e aprova a seguir sozinho, sem gestor nenhum a validar a correção. `approve` passou
a exigir `MANAGER`/`ADMIN` também quando qualquer campo de montante
(`NET_AMOUNT`/`VAT_AMOUNT`/`VAT_RATE`/`TOTAL_AMOUNT`) tem origem `HUMAN`, independentemente
do valor corrente.

## Isolamento por organização — nota transversal

Toda a consulta de escrita que toca num documento passa a filtrar por organização
(`findByIdAndOrganizationId`), nunca só por id — corrigido em `DocumentApprovalService`,
`DocumentService.transition` e `DocumentUploadService.fileUrl`, que antes desta etapa liam
por id sozinho. Um pedido para um documento de outra organização devolve **404**, nunca
403: não se revela sequer que o documento existe. Um `EMPLOYEE` só vê os documentos que
submeteu — `DocumentController` aplica esse filtro adicional sobre o mesmo princípio.

## RFC 7807 centralizado

`DomainException` passa a carregar o seu próprio `HttpStatus`; `GlobalExceptionHandler`
(`com.docgrid.shared`) traduz qualquer subclasse sem conhecer o caso concreto. Substitui o
`DocumentExceptionHandler` pontual da etapa 02. Falhas de autenticação (sem token, token
inválido) acontecem no filtro de segurança, antes do `DispatcherServlet` — por isso
`SecurityConfig` escreve o mesmo formato `application/problem+json` diretamente, em vez de
depender do `@RestControllerAdvice`.
