package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.audiencia.AudienciaRechazadaException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * La audiencia elegida rechazada, con su código y, si es de alcance, los ids rechazados (spec 044, contrato §1): 403 {@code FUERA_DE_TU_ALCANCE},
 * 400 {@code AUDIENCIA_VACIA} o {@code AUDIENCIA_INVALIDA}. El formato es el de {@code ApiErrorConCodigo} más {@code ids}.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.renaser.os.calendar.infrastructure.adapter.in.rest")
class AudienciaRechazadaHandler {

    record Error(String message, Instant timestamp, String codigo, List<String> ids) {
    }

    @ExceptionHandler(AudienciaRechazadaException.class)
    ResponseEntity<Error> rechazada(AudienciaRechazadaException e) {
        HttpStatus estado = e.esDeAlcance() ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON)
                .body(new Error(e.getMessage(), Instant.now(), e.codigo(), e.ids().stream().map(Object::toString).toList()));
    }
}
