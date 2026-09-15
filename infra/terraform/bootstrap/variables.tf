variable "project_name" {
  description = "Prefixo dos recursos de bootstrap (bucket de estado, tópico SNS)."
  type        = string
  default     = "docgrid"
}

variable "region" {
  description = "Região onde vive o estado e os alertas de orçamento."
  type        = string
  default     = "eu-west-1"
}

variable "alert_email" {
  description = "Email que recebe as notificações de orçamento (50%, 80% e 100%, real e previsto). Sem este email não há alerta — não tem omissão de propósito."
  type        = string
}