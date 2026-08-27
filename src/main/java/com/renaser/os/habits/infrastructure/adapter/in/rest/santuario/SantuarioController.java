package com.renaser.os.habits.infrastructure.adapter.in.rest.santuario;

import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase.CompletarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase.IniciarSesionBloqueoCommand;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase.RomperSesionBloqueoCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Santuario (habitos BLOQUEO). Autoservicio; el actor sale de la sesion, con respaldo por el
 * header temporal `X-Actor-Id` (D-29). */
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
    public SesionBloqueoResponse iniciar(@ActorAutenticado UserId actor, @PathVariable String id) {
        var sesion = iniciarUseCase.iniciar(new IniciarSesionBloqueoCommand(actor, registroId(id)));
        return SesionBloqueoResponse.from(sesion);
    }

    @PostMapping("/complete")
    public SesionBloqueoResponse completar(@ActorAutenticado UserId actor, @PathVariable String id) {
        var sesion = completarUseCase.completar(new CompletarSesionBloqueoCommand(actor, registroId(id)));
        return SesionBloqueoResponse.from(sesion);
    }

    @PostMapping("/break")
    public SesionBloqueoResponse romper(@ActorAutenticado UserId actor, @PathVariable String id,
                                         @RequestBody @Valid RomperSantuarioRequest request) {
        var sesion = romperUseCase.romper(new RomperSesionBloqueoCommand(actor, registroId(id),
                request.motivo(), request.evidenciaBucket(), request.evidenciaRuta()));
        return SesionBloqueoResponse.from(sesion);
    }

    private static RegistroHabitoId registroId(String id) {
        return RegistroHabitoId.of(UUID.fromString(id));
    }
}
