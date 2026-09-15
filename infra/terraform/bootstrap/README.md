# Bootstrap — Estado Remoto e Alerta de Orçamento

Esta configuração aplica-se **uma única vez**, antes de tudo o resto. Ela cria:

- O bucket S3 do estado remoto da configuração principal, com versionamento, encriptação SSE-S3 e bloqueio total de acesso público;
- O tópico SNS e a subscrição por email;
- O alerta de orçamento mensal de 5 EUR aos 50%, 80% e 100%, no valor real e no previsto.

O alerta fica ativo antes de existir um único recurso pago — o requisito do briefing da etapa 11.

## Ordem de aplicação

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply   # pede o email do alerta, por terraform.tfvars ou -var
# depois aplicar a configuração principal (infra/terraform)
```

Bootstrap primeiro, sempre: a configuração principal assume que o bucket de estado já existe e aponta para ele no bloco `backend "s3"` do ficheiro `versions.tf`.

## Nota

> O estado deste diretório é local e não é versionado (`.gitignore` tem `*.tfstate`). Só existe para resolver o problema do ovo e da galinha do estado remoto. Tudo o que este Terraform guarda fica neste diretório, ao contrário do estado da configuração principal, que vive no bucket.

## E se o alerta não chegar?

O email de confirmação da subscrição SNS tem de ser confirmado: quem receber o correio do passo de criação deve clicar no link; caso contrário, a subscrição fica pendente e o alerta nunca chega.