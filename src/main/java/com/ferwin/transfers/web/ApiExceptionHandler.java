package com.ferwin.transfers.web;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ferwin.transfers.common.BusinessRuleException;
import com.ferwin.transfers.common.IdempotencyConflictException;
import com.ferwin.transfers.common.InvalidRequestException;
import com.ferwin.transfers.common.NotFoundException;

/**
 * Errores en formato RFC 9457 (application/problem+json). Nunca se devuelven trazas, nombres de tablas
 * ni mensajes internos: eso le daría información a un atacante. El detalle queda solo en el log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(BusinessRuleException.class)
	ProblemDetail business(BusinessRuleException ex) {
		ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Regla de negocio incumplida", ex.getMessage());
		problem.setProperty("code", ex.getCode());
		return problem;
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	ProblemDetail idempotency(IdempotencyConflictException ex) {
		return problem(HttpStatus.CONFLICT, "Conflicto de idempotencia", ex.getMessage());
	}

	@ExceptionHandler(InvalidRequestException.class)
	ProblemDetail badRequest(InvalidRequestException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail notFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail invalid(MethodArgumentNotValidException ex) {
		Map<String, String> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.collect(Collectors.toMap(e -> e.getField(), e -> String.valueOf(e.getDefaultMessage()), (a, b) -> a));
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", "Revisa los campos indicados");
		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadable(HttpMessageNotReadableException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", "El cuerpo de la petición no es un JSON válido");
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail forbidden(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para esta operación");
	}

	@ExceptionHandler({ PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class })
	ProblemDetail busy(RuntimeException ex) {
		log.warn("conflicto de concurrencia: {}", ex.getClass().getSimpleName());
		return problem(HttpStatus.SERVICE_UNAVAILABLE, "Cuenta ocupada", "Intenta de nuevo en unos segundos");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex) {
		log.error("error no controlado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado");
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("about:blank"));
		return problem;
	}
}
