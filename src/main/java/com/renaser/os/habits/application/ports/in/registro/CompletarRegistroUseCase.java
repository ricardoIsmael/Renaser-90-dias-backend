package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface CompletarRegistroUseCase {

    RegistroHabito completar(CompletarRegistroCommand command);

    /**
     * Sin campo `puntos`: el otorgamiento lo calcula el servicio contra la ventana real del habito
     * (mismo blindaje anti mass-assignment que CLAUDE.MD §5.3.3).
     *
     * @param gesto con que gesto llega el pedido — decide si la politica del habito gobierna esta
     *              invocacion. Ver {@link GestoCompletar}: nunca viaja desde el cliente.
     */
    record CompletarRegistroCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                     String respuestaTexto, Integer calificacionProductividad,
                                     @NotNull GestoCompletar gesto) {

        public CompletarRegistroCommand {
            SelfValidating.validateConstructorArgs(CompletarRegistroCommand.class, actorId, registroId,
                    respuestaTexto, calificacionProductividad, gesto);
        }

        /**
         * El gesto generico, que es el de la enorme mayoria de los llamadores. Existe como
         * constructor y no como factoria estatica para que el valor por defecto sea el
         * <b>seguro</b>: quien no piensa en el gesto queda gobernado por la politica del habito,
         * y saltearla exige escribirlo a proposito con {@link GestoCompletar#PROPIO_DEL_HABITO}.
         */
        public CompletarRegistroCommand(UserId actorId, RegistroHabitoId registroId, String respuestaTexto,
                                         Integer calificacionProductividad) {
            this(actorId, registroId, respuestaTexto, calificacionProductividad, GestoCompletar.GENERICO);
        }
    }
}
