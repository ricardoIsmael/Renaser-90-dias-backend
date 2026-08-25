package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAuthorized(NotAuthorizedException ex) {
        return respond(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoSuchElementException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(IllegalStateException ex) {
        return respond(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return respond(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    /**
     * `@Valid` fallido sobre un `@RequestBody`. Sin este handler, Spring Boot devolvia el
     * error Whitelabel por defecto con el stacktrace completo en el cuerpo (paquetes
     * internos, version de Spring/Tomcat, cadena de filtros de seguridad) — encontrado por
     * los agentes de prueba en `community`, `habits/rocks` y `users/points` por separado:
     * no era un defecto de un modulo, era este handler faltante.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, mensaje.isBlank() ? "Solicitud invalida" : mensaje);
    }

    /** Falta un `@RequestParam` obligatorio (ej. `?cohortId=`). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Falta el parametro obligatorio '" + ex.getParameterName() + "'");
    }

    /** Falta un `@RequestHeader` obligatorio (ej. `X-Actor-Id`). */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return respond(HttpStatus.BAD_REQUEST, "Falta el header obligatorio '" + ex.getHeaderName() + "'");
    }

    /** JSON malformado, tipo incorrecto (ej. `energyLevel:"alto"` en vez de numero), o body vacio donde se requiere. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es invalido o esta mal formado");
    }

    /** Path variable o param con el tipo equivocado (ej. un id que no es UUID). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "El valor de '" + ex.getName() + "' no tiene el formato esperado");
    }

    /** Verbo HTTP no soportado en esa ruta (ej. GET donde solo hay POST). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    /**
     * Violacion de UNIQUE/CHECK/FK en Postgres. Ocurre cuando dos requests concurrentes
     * pasan la misma validacion de negocio y la BD frena a la segunda — el caso tipico es
     * el doble tap del cliente movil sobre "firmar pacto" o "aprobar solicitud". La
     * restriccion de la BD hace bien su trabajo (los datos quedan integros); lo que
     * faltaba era traducir eso a un 409 en vez de un 500 con stacktrace.
     *
     * <p>Se responde 409 CONFLICT, no 400: el request era valido, perdio una carrera.
     * El detalle de la restriccion NO se expone al cliente (revelaria nombres de tablas
     * e indices); queda en el log del servidor.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegridad(DataIntegrityViolationException ex) {
        log.warn("409 -> Conflict: violacion de integridad en la base", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of("La operacion entra en conflicto con datos que ya existen"));
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String message) {
        log.warn("{} -> {}: {}", status.value(), status.getReasonPhrase(), message);
        return ResponseEntity.status(status).body(ApiErrorResponse.of(message));
    }
}
