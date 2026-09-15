variable "project_name" {
  description = "Prefixo de nomes dos recursos."
  type        = string
  default     = "docgrid"
}

variable "region" {
  description = "Região de tudo o que este Terraform cria."
  type        = string
  default     = "eu-west-1"
}

variable "instance_type" {
  description = "Instância da aplicação. t4g é Graviton (arm64) — a imagem é multi-arquitetura (ver ADR 0017)."
  type        = string
  default     = "t4g.micro"
}

variable "db_instance_class" {
  description = "Classe do RDS. db.t4g.micro é a classe mais barata com armazenamento elástico."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Armazenamento do RDS em GiB — 20 é o mínimo sensato para o gp2."
  type        = number
  default     = 20
}

variable "image_reference" {
  description = "Imagem do backend a correr na instância (registo ghcr.io — ver ADR 0018)."
  type        = string
}

variable "alert_email" {
  description = "Email das notificações do CloudWatch (DLQ cheia, falha da instância)."
  type        = string
}

variable "log_retention_days" {
  description = "Retenção dos logs do CloudWatch em dias. Sem retenção os logs pagam-se para sempre."
  type        = number
  default     = 7
}