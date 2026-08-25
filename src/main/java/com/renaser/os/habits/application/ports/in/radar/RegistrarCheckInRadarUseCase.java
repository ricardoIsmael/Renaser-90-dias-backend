package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public interface RegistrarCheckInRadarUseCase {

    RegistroRadar registrar(RegistrarCheckInRadarCommand command);

    /**
     * Sin campo `participanteId` distinto del actor en el request HTTP (D-36: el
     * check-in siempre es sobre uno mismo, igual que en radar.ts — no hay forma de
     * pedir el de otro desde el cliente). El command sí separa actorId/participanteId,
     * mismo patron que {@code ConsultarTracksDelDiaUseCase}, para que el servicio pueda
     * aplicar el guard {@code requireSelf} de forma testeable (CLAUDE.MD §5.3.4).
     */
    record RegistrarCheckInRadarCommand(@NotNull UserId actorId, @NotNull UserId participanteId,
                                         @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String queHago,
                                         @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String quePienso,
                                         @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String queSiento,
                                         @NotNull @Min(RegistroRadar.NIVEL_ENERGIA_MIN)
                                         @Max(RegistroRadar.NIVEL_ENERGIA_MAX) Integer nivelEnergia,
                                         @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String queEvito) {

        public RegistrarCheckInRadarCommand {
            SelfValidating.validateConstructorArgs(RegistrarCheckInRadarCommand.class, actorId, participanteId,
                    queHago, quePienso, queSiento, nivelEnergia, queEvito);
        }
    }
}
