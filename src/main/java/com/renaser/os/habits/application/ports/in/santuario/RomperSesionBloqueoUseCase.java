package com.renaser.os.habits.application.ports.in.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.MotivoSalidaBloqueo;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface RomperSesionBloqueoUseCase {

    SesionBloqueo romper(RomperSesionBloqueoCommand command);

    record RomperSesionBloqueoCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                       @NotNull MotivoSalidaBloqueo motivo, String evidenciaBucket,
                                       String evidenciaRuta) {
        public RomperSesionBloqueoCommand {
            SelfValidating.validateConstructorArgs(RomperSesionBloqueoCommand.class, actorId, registroId, motivo,
                    evidenciaBucket, evidenciaRuta);
        }
    }
}
