package com.renaser.os.onboarding.infrastructure.adapter.in.rest.media;

import com.renaser.os.onboarding.application.ports.in.media.VerFirmaDelPactoUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La firma del Pacto de quien pregunta, para Yo → Pacto ya firmado (D-253). Sin parámetro de usuario: no
 * hay forma de pedir la de otra persona. 200 con la URL, 404 si todavía no hay firma guardada, 403 si la
 * cuenta está suspendida o sin sesión.
 */
@RestController
@RequestMapping("/api/v1/onboarding/pact")
public class FirmaDelPactoController {

    private final VerFirmaDelPactoUseCase verFirmaUseCase;

    public FirmaDelPactoController(VerFirmaDelPactoUseCase verFirmaUseCase) {
        this.verFirmaUseCase = verFirmaUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/signature")
    public FirmaDelPactoResponse firma(@ActorAutenticado UserId actor) {
        return FirmaDelPactoResponse.from(verFirmaUseCase.deActor(actor));
    }
}
