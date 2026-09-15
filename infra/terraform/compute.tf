# The app instance: t4g.micro (Graviton, arm64) on the public subnet, no elastic IP.
# The environment is born and dies with terraform; the instance ships its own user-data
# (user_data_replace_on_change = true) so it is cattle, not a pet.

# Amazon Linux 2023 ARM64 AMI id, from the public AWS SSM parameter.
data "aws_ssm_parameter" "ami" {
  name = "/aws/service/ami-al2023-latest/al2023-ami-kernel-default-arm64"
}

resource "aws_instance" "app" {
  # #checkov:skip=CKV_AWS_88:public IP by design — no NAT, no elastic IP; the instance
  # is cattle born and dies with terraform (see ADR 0017)
  # #checkov:skip=CKV_AWS_126:default admin username is fine - there is no SSH at all,
  # admin happens through SSM Session Manager (see ADR 0017)
  # #checkov:skip=CKV_AWS_135:no ebs_optimized — meaningless for t4g (SSD-backed vCPU);
  # the root volume is already gp3 + encrypted (see ADR 0019)
  ami                         = data.aws_ssm_parameter.ami.value
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public.id
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.this.name

  # Render the three templates once: the shell script embeds the compose.yml and the
  # Caddyfile as heredocs (the heredoc delimiters are quoted, so bash expands nothing -
  # the templates already rendered).
  user_data = templatefile("${path.module}/templates/user-data.sh.tftpl", {
    project_name = var.project_name
    region       = var.region
    compose_yml = indent(6, templatefile("${path.module}/templates/compose.yml.tftpl", {
      image_reference = var.image_reference
      region          = var.region
      project_name    = var.project_name
    }))
    caddyfile = indent(6, templatefile("${path.module}/templates/Caddyfile.tftpl", {
      verify = random_password.origin_verify.result
    }))
  })
  user_data_replace_on_change = true

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 10
    encrypted   = true
  }

  tags = { Name = "${var.project_name}-app" }
}

output "instance_public_dns" {
  description = "Public DNS of the app instance - feeds the CloudFront API origin."
  value       = aws_instance.app.public_dns
}