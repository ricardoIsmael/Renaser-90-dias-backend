package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.SesionNoIniciadaException;
import com.renaser.os.shared.domain.TokenResetInvalidoException;
import com.renaser.os.shared.domain.TokenVerificacionEmailInvalidoException;
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

    /** Login fallido: 401, no 403 — todavia no hay identidad establecida que autorizar o no. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiErrorResponse> handleCredencialesInvalidas(CredencialesInvalidasException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(SesionNoIniciadaException.class)
    public ResponseEntity<ApiErrorResponse> handleSesionNoIniciada(SesionNoIniciadaException ex) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /** Token de reset inexistente, vencido o ya usado: el request es invalido, no una falla de autorizacion. */
    @ExceptionHandler(TokenResetInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenResetInvalido(TokenResetInvalidoException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CodigoVerificacionInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> handleCodigoVerificacionInvalido(CodigoVerificacionInvalidoException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TokenVerificacionEmailInvalidoException.class)
    public ResponseEntity<ApiErrorResponse> handleTokenVerificacionEmailInvalido(
            TokenVerificacionEmailInvalidoException ex) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Login social fallido: 401, misma categoria que {@link CredencialesInvalidasException}. Se
     * loguea con la excepcion completa (a diferencia de {@link #respond}) porque la causa real
     * (firma invalida, proveedor caido, `iss`/`aud` que no corresponden) importa para
     * diagnosticar — nunca viaja al cliente, solo al log del servidor.
     */
    @ExceptionHandler(IdentidadProveedorInvalidaException.class)
    public ResponseEntity<ApiErrorResponse> handleIdentidadProveedorInvalida(IdentidadProveedorInvalidaException ex) {
        log.warn("401 -> login social rechazado", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiErrorResponse.of(ex.getMessage()));
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

    /**
     * Fecha/hora mal formada en un parametro que el controller parsea a mano
     * ({@code Instant.parse(from)}, cursores de paginacion, {@code occurrenceStart}...).
     *
     * <p>Sin este handler devolvia 500 con el stacktrace COMPLETO en el cuerpo — filtrando
     * al cliente rutas de clases internas y la cadena de filtros de Spring Security (E-38,
     * encontrado probando {@code GET /calendar/events?from=notadate}). No alcanzaba con el
     * handler de {@code IllegalArgumentException}: {@code DateTimeParseException} extiende
     * {@code DateTimeException}, que NO es un {@code IllegalArgumentException} — un detalle
     * facil de asumir al reves. Se resuelve aca, en el unico lugar que conoce HTTP, en vez
     * de repetir un try/catch por cada parseo en cada controller.
     */
    @ExceptionHandler(java.time.format.DateTimeParseException.class)
    public ResponseEntity<ApiErrorResponse> handleFechaInvalida(java.time.format.DateTimeParseException ex) {
        return respond(HttpStatus.BAD_REQUEST,
                "Fecha u hora con formato invalido: se espera ISO-8601 (ej. 2026-08-25T10:00:00Z)");
    }

    /**
     * Cualquier otro rechazo del API de fecha/hora que NO sea de parseo.
     *
     * <p>El handler de arriba cubria solo {@code DateTimeParseException} y por eso dejaba
     * pasar a sus hermanas: {@code ZoneId.of("basura")} lanza {@code ZoneRulesException}, que
     * cuelga de {@code DateTimeException} por otra rama, y devolvia 500 con stacktrace al
     * crear un evento con zona horaria invalida. Se captura la clase padre para cerrar la
     * familia completa de una vez, en lugar de ir agregando una subclase por cada bug nuevo.
     */
    @ExceptionHandler(java.time.DateTimeException.class)
    public ResponseEntity<ApiErrorResponse> handleValorTemporalInvalido(java.time.DateTimeException ex) {
        return respond(HttpStatus.BAD_REQUEST,
                "Valor de fecha, hora o zona horaria invalido");
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
