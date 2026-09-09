# Etapa 00 — Fundações

## Objetivo
Um esqueleto que arranca. `npm run up` levanta Postgres, LocalStack e a aplicação Spring Boot;
`/actuator/health` responde 200. Nada de lógica de negócio.

## Porque conta
Quem clona o repositório e não o consegue correr em dois minutos deixa de o avaliar.
Esta etapa é a diferença entre um projeto que é lido e um que é fechado.

## Contexto a carregar
`docs/02-ARCHITECTURE.md` (secção Ambientes), `docs/03-CONVENTIONS.md`

## Pré-requisitos
Nenhum. É a primeira etapa.

## Âmbito
- Projeto Maven, Java 21, Spring Boot 3.x, com os módulos web, JPA, validation, actuator
- Estrutura de pacotes por funcionalidade (ver convenções), com pastas vazias criadas
- `docker-compose.yml`: Postgres 16 + LocalStack (S3, SQS)
- Comandos do projeto: `up`, `infra`, `down`, `test`, `lint`, `format`, `logs`
- Perfis Spring: `local`, `test`, `aws`
- Flyway configurado, com uma migração vazia `V1__init.sql`
- Spotless ou formatador equivalente, ligado ao `npm run lint`
- `.gitignore`, `.editorconfig`, `README.md` mínimo
- Um teste de integração com Testcontainers que só verifica que o contexto arranca

## Fora
Entidades, endpoints de negócio, autenticação, frontend, CI. Tudo mais tarde.

## Decisões desta etapa
- Estrutura por funcionalidade vs por camada — já decidida nas convenções, confirma que
  o esqueleto a respeita
- Um repositório único com backend e frontend em pastas separadas (`backend/`, `frontend/`)

## Critérios de aceitação
- [ ] `npm run up` levanta tudo sem intervenção manual
- [ ] `curl localhost:8080/actuator/health` devolve `{"status":"UP"}`
- [ ] `npm test` passa e usa Testcontainers, não H2
- [ ] LocalStack cria um bucket e uma fila no arranque (script de init)
- [ ] Não existe nenhuma credencial no repositório
- [ ] `npm run down && npm run up` volta a funcionar do zero

## Esforço estimado
1 sessão · começa pelo plano e confirma-o antes de escrever código
