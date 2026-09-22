# Front en S3 privado detrás de CloudFront. CloudFront también enruta /api/* hacia App Runner:
# el navegador ve un único origen, así que no hace falta abrir CORS.
data "aws_caller_identity" "current" {}

locals {
  managed_cache_optimized    = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  managed_cache_disabled     = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
  managed_all_viewer_no_host = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
  managed_security_headers   = "67f7725c-6f97-4210-82d7-5512b31e9d03"
  apprunner_domain           = replace(aws_apprunner_service.api.service_url, "https://", "")
  web_bucket_name            = "${var.project}-web-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "web" {
  bucket        = local.web_bucket_name
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${var.project}-web"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = "PriceClass_100"

  origin {
    origin_id                = "web"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  origin {
    origin_id   = "api"
    domain_name = local.apprunner_domain

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id           = "web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = local.managed_cache_optimized
    response_headers_policy_id = local.managed_security_headers
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "api"
    viewer_protocol_policy   = "https-only"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = local.managed_cache_disabled # las respuestas de la API nunca se cachean
    origin_request_policy_id = local.managed_all_viewer_no_host
  }

  # SPA: las rutas desconocidas devuelven index.html.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "CloudFrontRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.web.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.web.arn } }
    }]
  })
}
