package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actor resuelto desde la sesion, con respaldo por el header temporal `X-Actor-Id`
 * (D-29). Rutas y forma del contrato tomadas
 * literal del backend viejo ({@code /api/v1/spirit-audio/status}, {@code .../submit}) —
 * D-36. Autoservicio, exclusivo de TRAINEE (mismo criterio que `radar`, RD-3).
 */
@RestController
@RequestMapping("/api/v1/spirit-audio")
public class EspirituController {

    private final ConsultarEstadoEspirituUseCase consultarUseCase;
    private final EntregarResumenEspirituUseCase entregarUseCase;

    public EspirituController(ConsultarEstadoEspirituUseCase consultarUseCase,
                               EntregarResumenEspirituUseCase entregarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.entregarUseCase = entregarUseCase;
    }

    @GetMapping("/status")
    public SpiritStatusResponse status(@ActorAutenticado UserId actor) {
        return SpiritStatusResponse.from(consultarUseCase.consultar(actor));
    }

    @PostMapping("/submit")
    public SubmitSpiritSummaryResponse submit(@ActorAutenticado UserId actor,
                                               @RequestBody @Valid SubmitSpiritSummaryRequest request) {
        var resultado = entregarUseCase.entregar(new EntregarResumenEspirituCommand(actor, request.day(),
                request.summaryText()));
        return SubmitSpiritSummaryResponse.from(resultado);
    }
}
