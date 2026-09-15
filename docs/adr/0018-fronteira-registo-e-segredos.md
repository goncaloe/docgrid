# 0018 — Fronteira, registo e segredos

**Estado:** aceptado · **Data:** 2026-09-15

## Contexto

O frontend usa caminhos relativos /api (frontend/src/api/client.ts).  
O Spring não tem configuração CORS.  
O frontend final vai para S3 e CloudFront.  
O briefing pede Secrets Manager para os segredos e ECR para a imagem.

## Decisões

### Decisão 1 — CloudFront com duas origens

CloudFront com duas origens: S3 por omissão (o SPA) e EC2 em /api/*.  
Mantém os caminhos relativos: zero linhas de React alteradas e zero CORS no Spring.  
HTTPS com o certificado por omissão do CloudFront: sem domínio próprio nem ACM.

### Decisão 2 — Header de verificação e prefix list

O security group da aplicação só aceita o prefix list com.amazonaws.global.cloudfront.origin-facing.  
O Caddy rejeita com 403 quem não trouxer o header X-Origin-Verify correto.  
O valor é um random_password alfanumérico de 32 caracteres gerado pelo Terraform.  
O CloudFront envia-o como header de origem, o Caddy compara.  
A origem bloqueia também /actuator/* na borda.

### Decisão 3 — CORS no bucket de documentos, não no site

O browser faz o upload direto para o S3.  
Permite-se https://<domínio-cloudfront> em PUT, GET e HEAD, e expõe-se o ETag.  
Não há ciclo de dependências: site -> distribuição -> CORS dos documentos.

### Decisão 4 — ECR no design, ghcr.io no CI

ECR são 15 linhas que um design AWS usa e documenta.  
ghcr.io é o registo real, gratuito num repositório público, autenticado com o GITHUB_TOKEN (não há segredo a gerir).  
Nos dois casos, a política de ciclo de vida guarda as últimas 5 imagens: sem ela, cada push deixa ~300 MB a acumular (no ghcr.io, a limpeza seria manual).

### Decisão 5 — SSM Parameter Store em vez de Secrets Manager

Secrets Manager custa 0,40 USD por segredo e por mês (2 segredos = 0,80 USD/mês).  
Parameter Store SecureString é grátis com a chave KMS gerida pela AWS (alias/aws/ssm).  
A esta escala faz exatamente o mesmo trabalho.  
AVISO: os valores vivem em claro no tfstate — é por isso que o bucket de estado é privado e encriptado (ver bootstrap).  
db-url e db-user são String de propósito: não são segredos.

## Alternativas consideradas

- Domínio de API à parte com CORS no Spring: mais móveis, um domínio a mais, certificado ACM.  
- ALB com certificado: paga mais da conta e adiciona uma peça.  
- Chave SSH: ver ADR 0017.

## Consequências

Zero CORS no Spring.  
Segredos em SSM.  
O segredo X-Origin-Verify existe em dois pontos (Terraform e o Caddyfile renderizado) — o Terraform é a fonte única.