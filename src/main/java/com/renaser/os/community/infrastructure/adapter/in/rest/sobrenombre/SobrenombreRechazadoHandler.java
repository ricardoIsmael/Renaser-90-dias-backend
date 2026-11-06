package com.renaser.os.community.infrastructure.adapter.in.rest.sobrenombre;

import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException;
import com.renaser.os.shared.web.ApiErrorConCodigo;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spec 046: un sobrenombre rechazado. La forma y las palabras prohibidas son 400; repetido o antes de los 30 días, 409. El código
 * ({@code SOBRENOMBRE_LARGO}, {@code _CARACTERES}, {@code _PROHIBIDO}, {@code _REPETIDO}, {@code _ESPERA}) lo usa la app para
 * marcar el campo; el mensaje es el que ve la persona.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.renaser.os.community.infrastructure.adapter.in.rest.sobrenombre")
class SobrenombreRechazadoHandler {

    @ExceptionHandler(SobrenombreRechazadoException.class)
    ResponseEntity<ApiErrorConCodigo> rechazado(SobrenombreRechazadoException e) {
        HttpStatus estado = switch (e.motivo()) {
            case REPETIDO, ESPERA -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorConCodigo.of(e.getMessage(), "SOBRENOMBRE_" + e.motivo().name(), null));
    }
}
