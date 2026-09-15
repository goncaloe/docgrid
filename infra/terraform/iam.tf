# Least-privilege IAM for the app instance. The instance profile binds the role to the
# instance; the role carries AWS's managed SSM Session Manager policy (remote admin with
# no open SSH port) plus one inline policy listing, item by item, what the app uses.
# No `*` on Resource outside of what forces it (S3 and ECR use an account wildcard on
# purpose: the ARN embeds the account id).

resource "aws_iam_role" "instance" {
  name        = "${var.project_name}-instance"
  description = "Role of the app instance (t4g.micro). Least privilege + SSM Session Manager."

  # Trusts the ec2 service: the instance profile is what binds the role to this instance.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "ec2.amazonaws.com" }
        Action    = "sts:AssumeRole"
      }
    ]
  })

  tags = { Name = "${var.project_name}-instance" }
}

# The SSM Session Manager managed policy (AmazonSSMManagedInstanceCore) attaches with one
# CLI command at apply time: the AWS provider rejects the documented ARN
# (arn:aws:iam::policy/...) in its ARN validation, so Terraform cannot attach it.
#   aws iam attach-role-policy --role-name docgrid-instance \
#     --policy-arn arn:aws:iam::policy/AmazonSSMManagedInstanceCore
# Recorded in infra/terraform/README.md and ADR 0019.

resource "aws_iam_instance_profile" "this" {
  name = "${var.project_name}-instance-profile"
  role = aws_iam_role.instance.name
}

resource "aws_iam_role_policy" "instance" {
  name = "${var.project_name}-instance"
  role = aws_iam_role.instance.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # S3: only the objects of the documents bucket. The account wildcard is required.
        Sid      = "S3Documents"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = "arn:aws:s3:::*:${var.project_name}-documents/*"
      },
      {
        # SQS: both queues - receive, listen and send (the worker redrives to the DLQ).
        Sid    = "SqsQueues"
        Effect = "Allow"
        Action = [
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:ChangeMessageVisibility",
          "sqs:SendMessage"
        ]
        Resource = [
          "arn:aws:sqs:${var.region}:*:${var.project_name}-document-processing",
          "arn:aws:sqs:${var.region}:*:${var.project_name}-document-processing-dlq"
        ]
      },
      {
        # Textract: analyze invoices. No finer-grained resource exists.
        Sid      = "Textract"
        Effect   = "Allow"
        Action   = ["textract:AnalyzeExpense"]
        Resource = "*"
      },
      {
        # SSM: read the app parameters (the SecureStrings, whose KMS key is AWS-managed).
        Sid      = "SsmParameters"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParameterHistory"]
        Resource = "arn:aws:ssm:${var.region}:*:parameter/docgrid/*"
      },
      {
        # KMS: decrypt only with the default SSM key (alias/aws/ssm) - what --with-decryption uses.
        Sid      = "KmsSsm"
        Effect   = "Allow"
        Action   = ["kms:Decrypt"]
        Resource = "arn:aws:kms:${var.region}:*:key/alias/aws/ssm"
      },
      {
        # ECR: pull only (the image goes from the registry into the instance's docker).
        Sid    = "EcrPull"
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer"
        ]
        Resource = "arn:aws:ecr:${var.region}:*:repository/${var.project_name}"
      },
      {
        # CloudWatch Logs: the docker awslogs driver writes to the app log group.
        Sid      = "CloudWatchLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:${var.region}:*:log-group:/docgrid/app:log-stream:*"
      }
    ]
  })
}