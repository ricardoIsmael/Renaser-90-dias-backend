package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.CajaDelMentorUseCase;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.EstadoResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** El chip de la caja en la ficha del aprendiz, para su mentor (D-219). */
@RestController
@RequestMapping("/api/v1/mentor/trainees/{aprendizId}/caja")
public class CajaDelMentorController {

    private final CajaDelMentorUseCase cajaDelMentor;

    public CajaDelMentorController(CajaDelMentorUseCase cajaDelMentor) {
        this.cajaDelMentor = cajaDelMentor;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "solo el mentor que lo acompaña hoy (o el Admin): el servicio rechaza al resto (D-219)")
    @GetMapping
    public EstadoResponse estado(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return new EstadoResponse(cajaDelMentor.estadoDe(actorId, UserId.of(aprendizId)));
    }
}
