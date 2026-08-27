package com.renaser.os.onboarding.infrastructure.adapter.in.rest.metamaestra;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase;
import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ValidarMetaMaestraCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @PostMapping("/validation")
    public ValidacionMetaMaestraResponse validar(@RequestHeader("X-Actor-Id") String actorId,
                                                   @Valid @RequestBody ValidarMetaMaestraRequest request) {
        var comando = new ValidarMetaMaestraCommand(UserId.of(actorId), request.text());
        return ValidacionMetaMaestraResponse.from(validarUseCase.validar(comando));
    }
}
