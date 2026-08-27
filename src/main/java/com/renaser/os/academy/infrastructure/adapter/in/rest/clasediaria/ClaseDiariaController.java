package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.CompletarClaseDiariaCommand;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espejo de `GET`/`POST /api/v1/classroom/clase-diaria` (RenaserBack). Completar
 * ademas cierra el habito {@code DAILY_CLASS} (`habits`) y otorga puntos — ver
 * javadoc de {@link CompletarClaseDiariaUseCase} y `docs/MODULO_ACADEMY.md` §6.
 */
@RestController
@RequestMapping("/api/v1/classroom/clase-diaria")
public class ClaseDiariaController {

    private final ConsultarClaseDiariaUseCase claseDiariaUseCase;
    private final CompletarClaseDiariaUseCase completarClaseDiariaUseCase;

    public ClaseDiariaController(ConsultarClaseDiariaUseCase claseDiariaUseCase,
                                  CompletarClaseDiariaUseCase completarClaseDiariaUseCase) {
        this.claseDiariaUseCase = claseDiariaUseCase;
        this.completarClaseDiariaUseCase = completarClaseDiariaUseCase;
    }

    @GetMapping
    public ClaseDiariaResponse claseDeHoy(@ActorAutenticado UserId actorId) {
        return ClaseDiariaResponse.from(claseDiariaUseCase.claseDeHoy(actorId));
    }

    @PostMapping
    public CompletarClaseDiariaResponse completar(@ActorAutenticado UserId actorId,
                                                    @Valid @RequestBody CompletarClaseDiariaRequest request) {
        var comando = new CompletarClaseDiariaCommand(actorId, LeccionId.of(request.leccionId()),
                request.resumen());
        return CompletarClaseDiariaResponse.from(completarClaseDiariaUseCase.completar(comando));
    }
}
