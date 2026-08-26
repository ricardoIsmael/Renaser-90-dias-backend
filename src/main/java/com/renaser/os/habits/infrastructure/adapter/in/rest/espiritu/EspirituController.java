package com.renaser.os.habits.infrastructure.adapter.in.rest.espiritu;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Actor por header `X-Actor-Id` (D-29, temporal). Rutas y forma del contrato tomadas
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
    public SpiritStatusResponse status(@RequestHeader("X-Actor-Id") String actorId) {
        return SpiritStatusResponse.from(consultarUseCase.consultar(UserId.of(actorId)));
    }

    @PostMapping("/submit")
    public SubmitSpiritSummaryResponse submit(@RequestHeader("X-Actor-Id") String actorId,
                                               @RequestBody @Valid SubmitSpiritSummaryRequest request) {
        var resultado = entregarUseCase.entregar(new EntregarResumenEspirituCommand(UserId.of(actorId), request.day(),
                request.summaryText()));
        return SubmitSpiritSummaryResponse.from(resultado);
    }
}
