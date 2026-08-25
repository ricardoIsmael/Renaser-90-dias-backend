package com.renaser.os.habits.infrastructure.adapter.in.rest.santuario;

import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase.CompletarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase.IniciarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase.RomperSesionBloqueoCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Santuario (habitos BLOQUEO). Autoservicio, actor por `X-Actor-Id` (D-29). */
@RestController
@RequestMapping("/api/v1/habit-tracks/{id}/santuario")
public class SantuarioController {

    private final IniciarSesionBloqueoUseCase iniciarUseCase;
    private final CompletarSesionBloqueoUseCase completarUseCase;
    private final RomperSesionBloqueoUseCase romperUseCase;

    public SantuarioController(IniciarSesionBloqueoUseCase iniciarUseCase,
                                CompletarSesionBloqueoUseCase completarUseCase,
                                RomperSesionBloqueoUseCase romperUseCase) {
        this.iniciarUseCase = iniciarUseCase;
        this.completarUseCase = completarUseCase;
        this.romperUseCase = romperUseCase;
    }

    @PostMapping("/start")
    public SesionBloqueoResponse iniciar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String id) {
        var sesion = iniciarUseCase.iniciar(new IniciarSesionBloqueoCommand(UserId.of(actorId), registroId(id)));
        return SesionBloqueoResponse.from(sesion);
    }

    @PostMapping("/complete")
    public SesionBloqueoResponse completar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String id) {
        var sesion = completarUseCase.completar(new CompletarSesionBloqueoCommand(UserId.of(actorId), registroId(id)));
        return SesionBloqueoResponse.from(sesion);
    }

    @PostMapping("/break")
    public SesionBloqueoResponse romper(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String id,
                                         @RequestBody @Valid RomperSantuarioRequest request) {
        var sesion = romperUseCase.romper(new RomperSesionBloqueoCommand(UserId.of(actorId), registroId(id),
                request.motivo(), request.evidenciaBucket(), request.evidenciaRuta()));
        return SesionBloqueoResponse.from(sesion);
    }

    private static RegistroHabitoId registroId(String id) {
        return RegistroHabitoId.of(UUID.fromString(id));
    }
}
