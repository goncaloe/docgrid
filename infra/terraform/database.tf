# RDS Postgres 16. Subnet group com as duas subnets privadas (o RDS exige duas AZ, mesmo
# em single-AZ). Sem backups, sem Multi-AZ, sem Performance Insights — ambiente de
# demonstração que nasce e morre com o Terraform (ver ADR 0017 e ADR 0019).

resource "aws_db_subnet_group" "this" {
  name       = "${var.project_name}-db"
  subnet_ids = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

resource "aws_db_instance" "this" {
  # #checkov:skip=CKV_AWS_133:Sem backups de propósito — ambiente de demonstração, a BD
  # é dados de teste regeneráveis; backups pagos para nada (ver ADR 0017 e ADR 0019)
  # #checkov:skip=CKV_AWS_129:Sem exportação de logs — o Postgres não está a fazer nada
  # que valha a pena arquivar; pagam-se por GB (ver ADR 0019)
  # #checkov:skip=CKV_AWS_161:Autenticação por password (SSM), não IAM — o desenho tem um
  # só processo e uma conta de BD; IAM auth é complexidade sem ganho aqui (ver ADR 0018)
  # #checkov:skip=CKV_AWS_293:deletion_protection = false de propósito — o requisito do
  # briefing é `terraform destroy` remover tudo sem órfãos a faturar (ver ADR 0019)
  # #checkov:skip=CKV_AWS_353:Performance Insights desligado — paga-se (ver ADR 0017)
  # #checkov:skip=CKV2_AWS_30:Log query desligado — sem exportação de logs (CKV_AWS_129)
  # #checkov:skip=CKV2_AWS_60:Sem snapshots, sem copy_tags_to_snapshot — backup_retention=0
  identifier     = "${var.project_name}-db"
  db_name        = "docgrid"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage          = var.db_allocated_storage
  storage_type               = "gp2"
  storage_encrypted          = true
  auto_minor_version_upgrade = true
  backup_retention_period    = 0
  skip_final_snapshot        = true
  deletion_protection        = false
  multi_az                   = false
  # #checkov:skip=CKV2_AWS_16:sem chave KMS própria — storage_encrypted usa a chave gerida
  # pela AWS, gratuita; o desenho não depende de KMS nenhum (ver ADR 0019)
  # #checkov:skip=CKV_AWS_118:Enhanced monitoring desligado — exige role e paga-se por
  # métricas por minuto que CloudWatch já dá (ver ADR 0019)
  # #checkov:skip=CKV_AWS_157:sem Multi-AZ de propósito — ambiente de demonstração (ADR 0017)
  performance_insights_enabled = false
  publicly_accessible          = false

  username = "docgrid"
  password = random_password.db_password.result

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]

  tags = { Name = "${var.project_name}-db" }
}