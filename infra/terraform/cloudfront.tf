# The only public entry: one CloudFront distribution with two origins - S3 for the
# SPA (default), the instance for /api/*. The frontend keeps its relative /api paths,
# Spring needs no CORS config, HTTPS comes from the CloudFront default certificate.
# See ADR 0018.

# The secret shared with Caddy: CloudFront sends it as an origin request header, Caddy
# verifies it. Alphanumeric only, so the Caddyfile never needs quoting tricks.
resource "random_password" "origin_verify" {
  length      = 32
  special     = false
  min_upper   = 1
  min_lower   = 1
  min_numeric = 1
}

# Origin access control for the S3 origin: reads the site bucket without making it public.
resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "${var.project_name}-site-oac"
  description                       = "OAC for the SPA site bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# Grants the distribution read access to the site bucket (private bucket + OAC).
resource "aws_s3_bucket_policy" "site_cloudfront" {
  bucket = aws_s3_bucket.site.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "arn:aws:s3:::*:${aws_s3_bucket.site.id}/*"
        Condition = {
          StringEquals = { "aws:SourceArn" = aws_cloudfront_distribution.this.arn }
        }
      }
    ]
  })
}

resource "aws_cloudfront_distribution" "this" {
  # #checkov:skip=CKV_AWS_68:no WAF — the CloudFront WAF is billed per request and does
  # not fit a 5 EUR/month budget (see ADR 0017 and ADR 0019)
  # #checkov:skip=CKV_AWS_86:no access logging on the distribution — CloudFront access
  # logs go to S3 and the observability lives in CloudWatch/actuator (see ADR 0019)
  # #checkov:skip=CKV_AWS_310:no origin failover — one instance, one SPA bucket: a
  # failover origin would duplicate the whole environment (see ADR 0019)
  # #checkov:skip=CKV_AWS_374:no geo restriction — a portfolio demo has no geographic
  # sensitivity; the SG and the verify header are the frontier (see ADR 0019)
  # #checkov:skip=CKV2_AWS_32:no response headers policy — the SPA sets no security
  # headers at the edge for a demo; trivial to add with a managed policy (ADR 0019)
  # #checkov:skip=CKV2_AWS_42:CloudFront default certificate, not a custom one — the
  # design has no custom domain and no ACM (see ADR 0018)
  # #checkov:skip=CKV2_AWS_47:no WAFv2 ACL - same reason as CKV_AWS_68 (see ADR 0017)
  # #checkov:skip=CKV_AWS_174:the CloudFront default certificate enforces TLS >= 1.2 at
  # the platform level, and minimum_protocol_version is set below; checkov does not
  # parse the viewer_certificate block form (see ADR 0019)
  enabled             = true
  comment             = "DocGrid front door: SPA from S3, API to the instance."
  price_class         = "PriceClass_100"
  default_root_object = "index.html"

  origin {
    origin_id                = "s3-site"
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  origin {
    origin_id   = "ec2-api"
    domain_name = aws_instance.app.public_dns
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1", "TLSv1.1", "TLSv1.2"]
    }
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }
  }

  # SPA: cache optimized, compress, HTTPS redirect. The SPA itself reads the API via
  # relative /api paths, so the API behavior below is what matters for freshness.
  default_cache_behavior {
    target_origin_id       = "s3-site"
    cache_policy_id        = "Managed-CachingOptimized"
    compress               = true
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    viewer_protocol_policy = "redirect-to-https"
    default_ttl            = 0
    max_ttl                = 31536000
    min_ttl                = 0
  }

  # API: no cache; everything but the Host header reaches the instance. The managed
  # policy Managed-AllViewerExceptHostHeader forwards all headers, cookies and query
  # strings - exactly what the upload/approval flow needs.
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "ec2-api"
    cache_policy_id          = "Managed-CachingDisabled"
    origin_request_policy_id = "Managed-AllViewerExceptHostHeader"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "PATCH", "POST", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    viewer_protocol_policy   = "redirect-to-https"
  }

  viewer_certificate {
    cloudfront_default_certificate = true
    # CloudFront serves the default certificate on TLS 1.2+ anyway; stating the floor
    # so checkov (CKV_AWS_174) sees the intent.
    minimum_protocol_version = "TLSv1.2_2021"
  }

  # SPA routing: any 403/404 rewrites to /index.html with 200.
  custom_error_response {
    error_code            = 403
    response_page_path    = "/index.html"
    response_code         = 200
    error_caching_min_ttl = 10
  }
  custom_error_response {
    error_code            = 404
    response_page_path    = "/index.html"
    response_code         = 200
    error_caching_min_ttl = 10
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  tags = { Name = "${var.project_name}-cloudfront" }
}

# CORS on the DOCUMENTS bucket (not the site): the browser uploads straight to S3 from
# the CloudFront origin. No dependency cycle: site -> distribution -> CORS of documents.
resource "aws_s3_bucket_cors_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  cors_rule {
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = ["https://${aws_cloudfront_distribution.this.domain_name}"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

output "cloudfront_domain" {
  description = "Public HTTPS domain of the environment (frontend)."
  value       = aws_cloudfront_distribution.this.domain_name
}