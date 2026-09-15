# Bootstrap — estado remoto e alerta de orçamento

Esta configuração é aplicada **uma única vez, antes de tudo o resto**. Cria:

- o bucket S3 do estado remoto da configuração principal, com versionamento,
  encriptação SSE-S3 e bloqueio total de acesso público;
- o tópico SNS e a subscrição por email;
- o alerta de orçamento mensal de **5 EUR** (50%, 80% e 100%, no valor real e no previsto).

O alerta fica ativo **antes de existir um único recurso pago — o requisito do briefing da
etapa 11.

## Ordem

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply          # pede o email do alerta (terraform.tfvars ou -var)
# ... aplicar a configuração principal (infra/terraform) ...
```

`bootstrap` primeiro, sempre. A configuração principal assume que o bucket de estado já
existe e aponta para ele no bloco `backend "s3"` (`versions.tf).

> O estado deste diretório é **local e não é versionado** (`.gitignore` tem `*.tfstate`).
Só existe para resolver o ovo-e-a-galinha do estado remoto. Tudo o que este Terraform
guarda fica neste diretório, ao contrário do estado da configuração principal, que vive
no bucket.

## E se o alerta não chegar a disparar?

O email de confirmação da subscrição SNS tem de ser confirmado: quem receber o email do passo 1 clica no link, senão a subscrição fica pendente e o alerta nunca chega.