# Etapa 06 — API REST e autenticação

## Objetivo
Expor o sistema numa API completa, com JWT, papéis e tratamento de erros consistente.

## Porque conta
É onde se vê se sabes desenhar uma API que outra pessoa consegue consumir sem te perguntar nada.

## Contexto a carregar
`docs/01-PRODUCT.md` (personas e permissões), `docs/03-CONVENTIONS.md`, handoff da etapa 05

## Pré-requisitos
Etapa 05: pipeline completo a funcionar.

## Âmbito
- Autenticação JWT com refresh token; registo de organização + utilizador administrador
- Papéis `EMPLOYEE`, `FINANCE`, `MANAGER`, `ADMIN` com `@PreAuthorize` nos métodos
- Isolamento por organização em **todas** as consultas, sem exceção
- Endpoints: listar documentos (filtro por estado, período, fornecedor; paginado),
  detalhe, corrigir campos, aprovar, rejeitar, fila de revisão, fila de aprovação
- Correções humanas gravam `HUMAN` como origem do campo e guardam o valor anterior
- Tratamento de erros RFC 7807 (`application/problem+json`), uniforme
- Documentação OpenAPI com Swagger UI, com exemplos preenchidos
- Testes: um teste de autorização negada por cada endpoint; um funcionário não vê documentos
  de outro; um utilizador não vê nada de outra organização

## Fora
Frontend (etapa 07), dashboard e exportação (etapa 09).

## Decisões desta etapa
- Onde guardar o refresh token e como o revogar
- Paginação por offset ou por cursor
- Aprovação de gestor: estado próprio ou flag no documento?

## Critérios de aceitação
- [ ] Swagger UI em `/swagger-ui.html`, navegável e com exemplos
- [ ] Um `FINANCE` de outra organização recebe 404 (não 403 — não revelar existência)
- [ ] Corrigir um campo grava origem `HUMAN` e mantém o valor original no histórico
- [ ] Todos os erros têm o mesmo formato, incluindo os de validação de entrada
- [ ] Nenhum endpoint devolve entidades JPA diretamente
- [ ] Revisão de segurança feita e as observações tratadas

## Esforço estimado
1 a 2 sessões · revisão de segurança obrigatória
