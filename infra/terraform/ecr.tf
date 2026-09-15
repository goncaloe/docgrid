# ECR repository: part of the AWS-shaped design, but the real registry is ghcr.io
# (see ADR 0018). The lifecycle policy matters either way: without it, each push
# leaves ~300 MB accumulating forever.

resource "aws_ecr_repository" "this" {
  # #checkov:skip=CKV_AWS_136:AES256 encryption with the AWS-managed default key
  # (alias/aws/ecr) is free - the same criterion as S3 and SSM (see ADR 0018/0019)
  name = var.project_name

  # Immutable tags + AES256 with the AWS-managed key (alias/aws/ecr) - both default
  # behaviour of a new repository, stated explicitly so checkov sees the intent.
  image_tag_mutability = "IMMUTABLE"
  encryption_configuration {
    encryption_type = "AES256"
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = { Name = "${var.project_name}-ecr" }
}

# Keep the last 5 images.
resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep the last 5 images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = { type = "expire" }
      }
    ]
  })
}