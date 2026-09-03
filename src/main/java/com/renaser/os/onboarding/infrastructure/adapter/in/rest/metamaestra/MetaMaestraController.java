package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase;
import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ValidarMetaMaestraCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sincrono a proposito, no 202+polling — ver javadoc de {@link ValidarMetaMaestraUseCase}.
 */
@RestController
@RequestMapping("/api/v1/onboarding/master-goal")
public class MetaMaestraController {

    private final ValidarMetaMaestraUseCase validarUseCase;

    public MetaMaestraController(ValidarMetaMaestraUseCase validarUseCase) {
        this.validarUseCase = validarUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/validation")
    public ValidacionMetaMaestraResponse validar(@ActorAutenticado UserId actor,
                                                   @Valid @RequestBody ValidarMetaMaestraRequest request) {
        validarUseCase.aceptar(new ValidarMetaMaestraCommand(actor, request.text()));
        return ValidacionMetaMaestraResponse.aceptada();
    }
}
