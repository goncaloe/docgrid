# Rede do ambiente AWS-alvo (ADR 0017): VPC sem NAT e sem endpoints de interface.
# A poupança que o briefing assumia com endpoints inverte-se a esta escala — cinco
# endpoints de interface custam mais do que o NAT que substituiriam. Só entra o
# endpoint gateway de S3, que é gratuito. A segurança fica nos security groups.

data "aws_availability_zones" "available" {
  state = "available"
}

# A lista de prefixos do CloudFront (origin-facing) — data source que contacta a AWS no
# plan, não no validate. É o que permite que o SG da app aceite só tráfego do CloudFront.
data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_vpc" "this" {
  # #checkov:skip=CKV2_AWS_11:Flow logs desligados — custo por GB no CloudWatch, escala de
  # demonstração; o rastro de gestão está no CloudTrail e SSM (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_12:SG por omissão da VPC nunca é usado — todos os recursos usam
  # SGs explícitos (app/db); o por omissão não se pode editar via Terraform (ver ADR 0019)
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${var.project_name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.project_name}-igw" }
}

# Pública: a instância da aplicação, com IP público — sem NAT, este é o caminho para fora
# (ver ADR 0017: a máquina nasce e morre, não há IP elástico).
resource "aws_subnet" "public" {
  # #checkov:skip=CKV_AWS_130:IP público por omissão é o desenho — a instância tem IP
  # público de propósito, sem NAT e sem elastic IP (ver ADR 0017)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = "10.0.0.0/24"
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.project_name}-public" }
}

# Privadas: o RDS exige um subnet group com duas AZ, mesmo em single-AZ.
resource "aws_subnet" "private_a" {
  vpc_id            = aws_vpc.this.id
  cidr_block        = "10.0.10.0/24"
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = { Name = "${var.project_name}-private-a" }
}

resource "aws_subnet" "private_b" {
  vpc_id            = aws_vpc.this.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = data.aws_availability_zones.available.names[1]

  tags = { Name = "${var.project_name}-private-b" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.project_name}-public" }
}

resource "aws_route" "public_igw" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Privada SEM rota por omissão: nada nas subnets privadas sai para a internet — o RDS não
# precisa, e não há NAT para pagar.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.project_name}-private" }
}

resource "aws_route_table_association" "private_a" {
  subnet_id      = aws_subnet.private_a.id
  route_table_id = aws_route_table.private.id
}

resource "aws_route_table_association" "private_b" {
  subnet_id      = aws_subnet.private_b.id
  route_table_id = aws_route_table.private.id
}

# Endpoint gateway de S3 (gratuito) nas duas tabelas de rotas: o acesso ao bucket a partir
# das subnets privadas e públicas não sai da AWS.
resource "aws_vpc_endpoint" "s3" {
  vpc_id          = aws_vpc.this.id
  service_name    = "com.amazonaws.${var.region}.s3"
  route_table_ids = [aws_route_table.public.id, aws_route_table.private.id]

  tags = { Name = "${var.project_name}-s3-gateway" }
}

# Security groups. A entrada da app é só do CloudFront; a da base é só da app.

resource "aws_security_group" "app" {
  name        = "${var.project_name}-app"
  description = "Instância da aplicação (Caddy na 80): entrada só do CloudFront, saída livre."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "HTTP from CloudFront (only public entry of the environment)"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin.id]
  }

  # #checkov:skip=CKV_AWS_382:Saída livre de propósito — fora, a instância fala com S3, SQS,
  # Textract, SSM, ECR, CloudWatch e yum; a frontera do ambiente é toda a entrada (ADR 0019)
  egress {
    description = "Free egress - the instance talks to AWS services and the registry"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-app" }
}

resource "aws_security_group" "db" {
  name        = "${var.project_name}-db"
  description = "RDS Postgres da aplicacao: entrada 5432 so do SG da app, sem saida."
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "Postgres only from the app"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
  # Sem regra de saída de propósito: um SG é stateful, as respostas a ligações
  # estabelecidas fluem mesmo sem egress explícito.

  tags = { Name = "${var.project_name}-db" }
}