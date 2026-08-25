package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espejo de `GET /api/v1/classroom/clase-diaria` (RenaserBack). Solo lectura
 * — completar la clase (que ademas cierra el habito y otorga puntos) queda
 * pendiente de coordinar con `habits`, ver `docs/MODULO_ACADEMY.md` §6.
 */
@RestController
@RequestMapping("/api/v1/classroom/clase-diaria")
public class ClaseDiariaController {

    private final ConsultarClaseDiariaUseCase claseDiariaUseCase;

    public ClaseDiariaController(ConsultarClaseDiariaUseCase claseDiariaUseCase) {
        this.claseDiariaUseCase = claseDiariaUseCase;
    }

    @GetMapping
    public ClaseDiariaResponse claseDeHoy(@RequestHeader("X-Actor-Id") String actorId) {
        return ClaseDiariaResponse.from(claseDiariaUseCase.claseDeHoy(UserId.of(actorId)));
    }
}
