# PostgreSQL privado: sin IP pública; solo recibe conexiones desde el conector VPC de App Runner.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

resource "aws_security_group" "apprunner_connector" {
  name        = "${var.project}-apprunner-connector"
  description = "Salida de App Runner hacia la VPC"
  vpc_id      = data.aws_vpc.default.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db"
  description = "PostgreSQL solo desde App Runner"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "PostgreSQL desde App Runner"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.apprunner_connector.id]
  }
}

resource "aws_db_subnet_group" "db" {
  name       = "${var.project}-db"
  subnet_ids = data.aws_subnets.default.ids
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "db" {
  identifier              = var.project
  engine                  = "postgres"
  engine_version          = "16"
  instance_class          = var.db_instance_class
  allocated_storage       = 20
  storage_encrypted       = true
  db_name                 = "transfers"
  username                = "transfers"
  password                = random_password.db.result
  db_subnet_group_name    = aws_db_subnet_group.db.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  publicly_accessible     = false
  backup_retention_period = 1
  skip_final_snapshot     = true # proyecto de portafolio: en un banco sería false + deletion_protection
  deletion_protection     = false
}
