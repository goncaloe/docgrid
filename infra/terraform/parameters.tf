# Segredos e endereços da app, em SSM Parameter Store. Os dois segredos (jwt e password)
# são SecureString (encriptados com a chave gerida pela AWS, gratuita); o url e o user são
# String. A instância lê-os no arranque com --with-decryption (ver compute.tf).
#
# ATENÇÃO: estes valores vivem em claro no tfstate — é por isso que o bucket de estado é
# privado e encriptado (bootstrap). Registo completo em docs/adr/0018.

resource "random_password" "jwt_secret" {
  # Sem / " @ no conjunto: o valor vai para um .env e a chave KMS de SSM não aceita o
  # cifrado de caracteres estranhos. Suficiente para um secret JWT de demonstração.
  length           = 48
  special          = true
  override_special = "!#$%&*+-_=?"
  min_upper        = 1
  min_lower        = 1
  min_numeric      = 1
  min_special      = 1
}

resource "random_password" "db_password" {
  # RDS exige: 8-128 caracteres imprimíveis exceto / " @. Este conjunto cumpre.
  length           = 24
  special          = true
  override_special = "!#$%&*+-_"
  min_upper        = 1
  min_lower        = 1
  min_numeric      = 1
  min_special      = 1
}

resource "aws_ssm_parameter" "jwt_secret" {
  # #checkov:skip=CKV_AWS_337:SecureString com a chave gerida pela AWS (alias/aws/ssm) —
  # gratuita; uma CMK própria custa $1/mês para o mesmo efeito (ver ADR 0018)
  name  = "/docgrid/jwt-secret"
  type  = "SecureString"
  value = random_password.jwt_secret.result
}

resource "aws_ssm_parameter" "db_password" {
  # #checkov:skip=CKV_AWS_337:SecureString com a chave gerida pela AWS (ver ADR 0018)
  name  = "/docgrid/db-password"
  type  = "SecureString"
  value = random_password.db_password.result
}

resource "aws_ssm_parameter" "db_url" {
  # #checkov:skip=CKV2_AWS_34:String de propósito — um URL de JDBC não é segredo; torná-lo
  # SecureString seria teatro e obrigaria a KMS (ver ADR 0018)
  name = "/docgrid/db-url"
  type = "String"
  # O endpoint do RDS é um DNS, não um segredo — daí ser String e não SecureString.
  value = "jdbc:postgresql://${aws_db_instance.this.endpoint}/docgrid"
}

resource "aws_ssm_parameter" "db_user" {
  # #checkov:skip=CKV2_AWS_34:String de propósito — o user da BD não é segredo (ADR 0018)
  name  = "/docgrid/db-user"
  type  = "String"
  value = "docgrid"
}