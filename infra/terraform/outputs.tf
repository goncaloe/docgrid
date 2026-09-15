output "vpc_id" {
  description = "VPC do ambiente."
  value       = aws_vpc.this.id
}

output "public_subnet_id" {
  description = "Subnet pública onde corre a instância da aplicação."
  value       = aws_subnet.public.id
}

output "private_subnet_ids" {
  description = "Subnets privadas do subnet group do RDS."
  value       = [aws_subnet.private_a.id, aws_subnet.private_b.id]
}

output "app_security_group_id" {
  description = "SG da instância da aplicação."
  value       = aws_security_group.app.id
}

output "db_security_group_id" {
  description = "SG do RDS."
  value       = aws_security_group.db.id
}