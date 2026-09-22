output "web_url" {
  description = "URL pública (front + /api)"
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}

output "api_url" {
  value = aws_apprunner_service.api.service_url
}

output "ecr_repository" {
  value = aws_ecr_repository.api.repository_url
}

output "web_bucket" {
  description = "Variable de GitHub WEB_BUCKET"
  value       = aws_s3_bucket.web.bucket
}

output "cloudfront_id" {
  description = "Variable de GitHub CLOUDFRONT_ID"
  value       = aws_cloudfront_distribution.web.id
}

output "deploy_role_arn" {
  description = "Secreto de GitHub AWS_DEPLOY_ROLE_ARN"
  value       = aws_iam_role.deploy.arn
}
