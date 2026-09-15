# TFLint config. The aws plugin adds the AWS-specific rules; the terraform ruleset
# rules run by default. `tflint --chdir=infra/terraform` from the repo root.

plugin "aws" {
  enabled = true
  version = "~> 0.24.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}

rule "terraform_unused_declarations" {
  enabled = true
}

rule "terraform_standard_module_structure" {
  enabled = false
}