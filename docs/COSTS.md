# Custos mensais do ambiente AWS

> Preços de catálogo em **setembro de 2026** para a região **eu-west-1**.  
> Valores a confirmar na calculadora da AWS antes de qualquer aplicação.

## Estimativa mensal

| Recurso                          | Especificação                    | Custo estimado (USD/mês) |
| -------------------------------- | -------------------------------- | ------------------------: |
| EC2                              | `t4g.micro`                      | 6,70 |
| EBS                              | 10 GB `gp3`                      | 0,80 |
| IPv4 público                     | 1 endereço associado             | 3,65 |
| RDS                              | `db.t4g.micro`                   | 12,50 |
| Armazenamento do RDS             | 20 GB                            | 2,60 |
| **TOTAL**                        |                                  | **~26,00** |

O total arredonda para **~24 EUR/mês**.

**Nota:** sem conta AWS ativa, não é pago nada disto — ver ADR 0019.

## Gratuito para sempre a esta escala

| Serviço                      | Limite gratuito                                             |
| ---------------------------- | ----------------------------------------------------------- |
| SQS                          | até 1 milhão de pedidos por mês                              |
| CloudFront                   | até 1 TB de saída por mês e 10.000 pedidos HTTPS             |
| CloudWatch Logs              | até 5 GB ingeridos                                           |
| AWS Budgets                  | orçamentos configurados                                      |
| SNS                          | notificações dentro dos limites gratuitos                    |
| KMS                          | chaves geradas pela AWS                                      |
| ECR                          | até 500 MB de armazenamento por mês                          |
| Endpoint gateway de S3       | sem custo pelo endpoint                                      |
| SSM Parameter Store          | parâmetros standard dentro dos limites gratuitos             |

## Por uso desde o primeiro dia

- **Textract**: ~0,01 USD por página analisada.  
  Cada fatura processada de verdade custa cêntimos.

## As duas armadilhas que o Terraform fecha

1. **Registo ECR sem política de ciclo de vida**  
   Cada push deixa ~300 MB acumulados sem limite. A política configurada no Terraform guarda apenas as últimas 5 imagens e elimina o resto automaticamente.

2. **Log group sem retenção**  
   Os logs ficam armazenados e pagam-se para sempre. O Terraform define a retenção para 7 dias, impedindo acumulação de custos.

## Como desligar tudo

- `terraform destroy` da configuração principal remove todos os recursos que faturam: instância, RDS, S3, SQS, CloudFront, ECR, logs e alarmes.
- Em seguida, `terraform destroy` do *bootstrap* remove o bucket de estado, o tópico e o orçamento.
- Com o *destroy* completo, não permanece nenhum recurso a faturar.

## Nota final

Nada do que está descrito foi aplicado. Os números apresentados são de catálogo em setembro de 2026 e devem ser confirmados na calculadora da AWS. Para compreender por que esta infraestrutura está escrita e não aplicada, ver ADR 0019.