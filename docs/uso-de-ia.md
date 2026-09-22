# IA aplicada al desarrollo en este proyecto

Este proyecto se construyó **con asistencia de IA generativa** (un asistente de código tipo LLM). Lo documento con transparencia porque es parte de cómo trabajo hoy, y porque usar IA bien no es copiar lo que genera: es dirigirla, verificarla y hacerse responsable del resultado.

## Cómo se usó

| Etapa | Qué hizo la IA | Qué hice yo / cómo se verificó |
|---|---|---|
| Diseño | Propuso alternativas (bloqueo optimista vs. pesimista, App Runner vs. ECS) | Elegir según el contexto transaccional; queda registrado en `docs/arquitectura.md` |
| Código | Generó la primera versión de entidades, servicio y controladores | Revisión línea por línea; se guía por las reglas de negocio, no al revés |
| Pruebas | Propuso casos límite y pruebas de concurrencia con hilos | Las pruebas **encontraron un bug real** (entidad con versión vieja al bloquear) que se corrigió con una proyección (`AccountRef`) |
| Seguridad | Checklist OWASP API Top 10 | Cada control tiene una prueba que lo demuestra (`docs/seguridad.md`) |
| Infraestructura | Borrador de Terraform | `terraform validate` + CI; nada se aplica en la nube sin revisión |
| Documentación | Diagramas Mermaid y README | Revisión de que describan lo que el código hace realmente |

## Reglas que sigo al usar IA

1. **Nada entra sin prueba.** Si la IA escribe código, también hay una prueba que falla sin él.
2. **La IA no toma decisiones de seguridad sola.** Se contrasta con OWASP y con la documentación oficial (Spring Security, AWS).
3. **Nunca se le pasan secretos, datos de clientes ni código propietario del empleador.**
4. **Versiones actuales.** Se verifica contra la documentación oficial (por ejemplo, los módulos nuevos de Spring Boot 4), porque los modelos pueden sugerir APIs viejas.
5. **Debo poder explicar cada línea.** Si no la entiendo, no la dejo (ver `GUIA.md`).
