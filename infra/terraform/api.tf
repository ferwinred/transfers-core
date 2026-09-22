# API en AWS App Runner: contenedores administrados con TLS, autoescalado y despliegue automático desde ECR.
resource "aws_ecr_repository" "api" {
  name                 = var.project
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true # escaneo de vulnerabilidades de la imagen
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Conservar las ultimas 10 imagenes"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# Rol con el que App Runner descarga la imagen de ECR.
resource "aws_iam_role" "apprunner_access" {
  name = "${var.project}-apprunner-access"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "build.apprunner.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "apprunner_access" {
  role       = aws_iam_role.apprunner_access.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSAppRunnerServicePolicyForECRAccess"
}

# Rol con el que corre la aplicación: solo puede leer sus dos secretos.
resource "aws_iam_role" "apprunner_instance" {
  name = "${var.project}-apprunner-instance"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "tasks.apprunner.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "apprunner_secrets" {
  name = "${var.project}-read-secrets"
  role = aws_iam_role.apprunner_instance.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.db_password.arn, aws_secretsmanager_secret.jwt.arn]
    }]
  })
}

resource "aws_apprunner_vpc_connector" "api" {
  vpc_connector_name = "${var.project}-connector"
  subnets            = data.aws_subnets.default.ids
  security_groups    = [aws_security_group.apprunner_connector.id]
}

# Nota: la primera vez, la imagen :latest debe existir en ECR antes de crear el servicio
# (ver README → "Despliegue en AWS").
resource "aws_apprunner_service" "api" {
  service_name = var.project

  source_configuration {
    auto_deployments_enabled = true

    authentication_configuration {
      access_role_arn = aws_iam_role.apprunner_access.arn
    }

    image_repository {
      image_identifier      = "${aws_ecr_repository.api.repository_url}:latest"
      image_repository_type = "ECR"

      image_configuration {
        port = "8080"
        runtime_environment_variables = {
          SPRING_PROFILES_ACTIVE = "prod"
          DB_URL                 = "jdbc:postgresql://${aws_db_instance.db.address}:5432/transfers"
          DB_USER                = aws_db_instance.db.username
        }
        runtime_environment_secrets = {
          DB_PASSWORD = aws_secretsmanager_secret.db_password.arn
          JWT_SECRET  = aws_secretsmanager_secret.jwt.arn
        }
      }
    }
  }

  instance_configuration {
    cpu               = "1024"
    memory            = "2048"
    instance_role_arn = aws_iam_role.apprunner_instance.arn
  }

  network_configuration {
    egress_configuration {
      egress_type       = "VPC"
      vpc_connector_arn = aws_apprunner_vpc_connector.api.arn
    }
  }

  health_check_configuration {
    protocol = "HTTP"
    path     = "/actuator/health/readiness"
  }

  depends_on = [aws_iam_role_policy_attachment.apprunner_access]
}
