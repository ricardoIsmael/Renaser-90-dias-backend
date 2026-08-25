package com.renaser.os.habits.infrastructure.adapter.in.rest.racha;

import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase.CerrarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase.IniciarRachaCommand;
import com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase.RomperRachaCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** "Dia sin celular". Contrato viejo: POST .../phone-free/{start,complete,break} — ver docs/MODULO_HABITS.md. */
@RestController
@RequestMapping("/api/v1/habit-tracks")
public class RachaController {

    private static final int EXTENSION_DEFAULT_HORAS = 3;

    private final IniciarRachaUseCase iniciarUseCase;
    private final CerrarRachaUseCase cerrarUseCase;
    private final RomperRachaUseCase romperUseCase;
    private final Clock clock;

    public RachaController(IniciarRachaUseCase iniciarUseCase, CerrarRachaUseCase cerrarUseCase,
                            RomperRachaUseCase romperUseCase, Clock clock) {
        this.iniciarUseCase = iniciarUseCase;
        this.cerrarUseCase = cerrarUseCase;
        this.romperUseCase = romperUseCase;
        this.clock = clock;
    }

    @PostMapping("/{id}/phone-free/start")
    public RachaSinCelularResponse iniciar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String id,
                                            @RequestBody @Valid IniciarRachaRequest request) {
        var racha = iniciarUseCase.iniciar(new IniciarRachaCommand(UserId.of(actorId),
                RegistroHabitoId.of(UUID.fromString(id)), request.horasObjetivo()));
        return RachaSinCelularResponse.from(racha, clock.now(), EXTENSION_DEFAULT_HORAS);
    }

    @PostMapping("/phone-free/complete")
    public RachaSinCelularResponse completar(@RequestHeader("X-Actor-Id") String actorId) {
        var racha = cerrarUseCase.cerrar(new CerrarRachaCommand(UserId.of(actorId)));
        return RachaSinCelularResponse.from(racha, clock.now(), EXTENSION_DEFAULT_HORAS);
    }

    @PostMapping("/phone-free/break")
    public RachaSinCelularResponse romper(@RequestHeader("X-Actor-Id") String actorId,
                                           @RequestBody(required = false) RomperRachaRequest request) {
        String motivo = request != null ? request.motivo() : null;
        var racha = romperUseCase.romper(new RomperRachaCommand(UserId.of(actorId), motivo));
        return RachaSinCelularResponse.from(racha, clock.now(), EXTENSION_DEFAULT_HORAS);
    }
}
