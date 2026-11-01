package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import com.renaser.os.onboarding.domain.model.mapa.MapaRetiradoException;
import com.renaser.os.shared.web.ApiErrorConCodigo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** El 409 del Mapa retirado, con su código, desde cualquier endpoint de onboarding que lo toque (las listas del Mapa y las respuestas de su flujo). */
@RestControllerAdvice(basePackages = "com.renaser.os.onboarding.infrastructure.adapter.in.rest")
class MapaRetiradoHandler {

    @ExceptionHandler(MapaRetiradoException.class)
    ResponseEntity<ApiErrorConCodigo> mapaRetirado(MapaRetiradoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorConCodigo.of(e.getMessage(), MapaRetiradoException.CODIGO, null));
    }
}
