variable "aws_region" {
  description = "Región de AWS"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Prefijo de los recursos"
  type        = string
  default     = "transfers-core"
}

variable "owner" {
  description = "Responsable de los recursos"
  type        = string
  default     = "edwin-arias"
}

variable "github_repository" {
  description = "Repositorio autorizado a desplegar con OIDC (owner/repo)"
  type        = string
  default     = "ferwinred/transfers-core"
}

variable "db_instance_class" {
  description = "Tamaño de la base de datos (t4g.micro entra en la capa gratuita)"
  type        = string
  default     = "db.t4g.micro"
}
