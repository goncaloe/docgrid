# Observability: one log group, one SNS alert channel, two alarms. Nothing here is
# billed per-event: CloudWatch Logs up to 5 GB and SNS are free at this scale.

# The app log group. Retention is a variable (7 days default): without it, logs are
# kept forever and billed forever.
resource "aws_cloudwatch_log_group" "app" {
  # #checkov:skip=CKV_AWS_338:retention is 7 days on purpose - demo logs are volatile and
  # a year of logs is a year of billing; the app itself keeps no secrets in logs (ADR 0019)
  name              = "/docgrid/app"
  retention_in_days = var.log_retention_days
  # Encryption with the AWS-managed logs key - free.
  kms_key_id = "alias/aws/cloudwatch"
}

# Alert channel shared by both alarms.
resource "aws_sns_topic" "alerts" {
  name              = "${var.project_name}-alerts"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# DLQ not empty: coherent with the stage-10 decision - the DLQ warns, it does not
# take the health down. Messages land here after 3 failed attempts.
resource "aws_cloudwatch_metric_alarm" "dlq" {
  alarm_name          = "${var.project_name}-dlq-not-empty"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 1
  alarm_description   = "DLQ not empty: messages gave up the processing queue."
  dimensions = {
    QueueName = "${var.project_name}-document-processing-dlq"
  }
  alarm_actions = [aws_sns_topic.alerts.arn]
}

# Instance failed its status check (AWS publishes StatusCheckFailed for free).
resource "aws_cloudwatch_metric_alarm" "instance" {
  alarm_name          = "${var.project_name}-instance-failed"
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = "StatusCheckFailed"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Maximum"
  threshold           = 1
  alarm_description   = "Instance failed its EC2 status check."
  dimensions = {
    InstanceId = aws_instance.app.id
  }
  alarm_actions = [aws_sns_topic.alerts.arn]
}