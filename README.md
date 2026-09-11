# DocGrid

Plataforma de processamento automático de faturas e despesas. Submetes um PDF ou uma foto
de uma fatura; o sistema extrai os campos (fornecedor, NIF, número, data, base, IVA, total),
valida-os contra regras de negócio e classifica o documento como pronto a aprovar ou a
precisar de revisão humana.

**O sistema não substitui o humano — reduz-lhe o trabalho e diz-lhe onde olhar.** Documentos
com baixa confiança ou que falhem validação vão para uma fila de revisão manual, com o grau
de confiança de cada campo visível até à interface.

Projeto de portefólio, em construção. Domínio fiscal português: NIF com dígito de controlo,
IVA a 6%, 13% e 23%.

## Stack

| Camada | Tecnologia |
| --- | --- |
| Backend | Java 21, Spring Boot 3.5, Maven, PostgreSQL 16, Flyway |
| Testes | JUnit 5, AssertJ, Testcontainers |
| Frontend | React 18, TypeScript, Vite, TanStack Query, Mantine |
| AWS | S3, SQS, Textract, RDS, ECS Fargate *(a partir da etapa 02)* |
| Local | Docker Compose com Postgres e LocalStack |

## Como correr

Precisas de **Docker**, **JDK 21** e **Node 20.6+**. Maven não é preciso — o repositório traz
o Maven Wrapper.

```bash
git clone <repo> && cd docgrid
npm run up
```

Noutro terminal:

```bash
curl localhost:8080/actuator/health     # {"status":"UP"}
```

Não é preciso configurar nada: sem ficheiro `.env`, tudo arranca com valores por omissão.
Para mudar portas ou palavra-passe, copia o `.env.example` para `.env`.

## Comandos

| Comando | O que faz |
| --- | --- |
| `npm run up` | Levanta a infraestrutura e arranca a aplicação no perfil `local` |
| `npm run infra` | Só Postgres e LocalStack — para correres a aplicação no IDE |
| `npm run down` | Pára tudo e apaga os volumes |
| `npm test` | Testes do backend, com Testcontainers |
| `npm run lint` | Verifica a formatação |
| `npm run format` | Corrige a formatação |
| `npm run logs` | Segue os logs dos containers |

> Não há `Makefile`: o `make` não é garantido no Windows e o Node já é preciso para o
> frontend. A decisão está em [`docs/adr/0002`](docs/adr/0002-ambiente-local-e-comandos.md).

## Estrutura

```
backend/         API e worker Spring Boot, organizados por funcionalidade
  src/main/java/com/docgrid/
    document/      submissão, estados, consulta
    extraction/    extração de campos (Textract e stub local)
    validation/    motor de regras
    supplier/      fornecedores
    export/        exportação contabilística
    auth/          autenticação e papéis
    shared/        configuração, exceções, utilitários
docker/          scripts de arranque do LocalStack
frontend/        aplicação React (Vite + TypeScript + Mantine)
  src/
    api/           cliente HTTP, tipos da API, chamadas de autenticação e documentos
    auth/          contexto de sessão e guardas de rota por papel
    layout/        navegação lateral, cabeçalho, indicador de fila de revisão
    features/      páginas por funcionalidade (auth, documents, review, upload)
scripts/         utilitários dos comandos npm
```

## Frontend

O frontend é um projeto Node à parte, com o seu próprio `package.json`:

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, com proxy de /api para o backend em :8080
```

Para a aplicação falar com o backend, tens de ter `npm run up` a correr noutro terminal.
Uma conta cria-se por `POST /api/auth/register` (não há ecrã de registo) — usa o Swagger
UI em `localhost:8080/swagger-ui.html` ou o exemplo `curl` no handoff da etapa 06.

| Comando | O que faz |
| --- | --- |
| `npm run dev` | Servidor de desenvolvimento do Vite |
| `npm run build` | Verificação de tipos e build de produção |
| `npm test` | Testes com Vitest, Testing Library e MSW |
| `npm run lint` | ESLint |

## Ambiente local

O `docker-compose.yml` levanta apenas a infraestrutura; a aplicação corre no host, para o
ciclo de alteração e reinício ser imediato.

| | Local | AWS |
| --- | --- | --- |
| Armazenamento | LocalStack S3 | S3 |
| Fila | LocalStack SQS | SQS + DLQ |
| Base de dados | Postgres em Docker | RDS Postgres |
| Extração | stub local | Textract |

Nenhuma credencial AWS real é necessária para desenvolver. O LocalStack cria no arranque o
bucket `docgrid-documents` e a fila `docgrid-document-processing`.

## Estado

Etapa 00 — fundações. O esqueleto arranca e é testado; ainda não há lógica de negócio.
