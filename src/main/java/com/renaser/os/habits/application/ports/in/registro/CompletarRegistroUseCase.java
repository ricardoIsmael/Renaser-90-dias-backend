package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
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
     * @param medicion el numero del dia de un habito medible (D-226: los km de {@code DAILY_KM}), o
     *                 {@code null}. Solo lo acepta un habito cuya politica declara unidad; en los demas
     *                 es un 400.
     */
    record CompletarRegistroCommand(@NotNull UserId actorId, @NotNull RegistroHabitoId registroId,
                                     String respuestaTexto, Integer calificacionProductividad,
                                     @NotNull GestoCompletar gesto, MedicionDiaria medicion) {

        public CompletarRegistroCommand {
            SelfValidating.validateConstructorArgs(CompletarRegistroCommand.class, actorId, registroId,
                    respuestaTexto, calificacionProductividad, gesto, medicion);
        }

        /** Sin medicion: la forma de todos los llamadores anteriores a D-226. */
        public CompletarRegistroCommand(UserId actorId, RegistroHabitoId registroId, String respuestaTexto,
                                         Integer calificacionProductividad, GestoCompletar gesto) {
            this(actorId, registroId, respuestaTexto, calificacionProductividad, gesto, null);
        }

        /**
         * El gesto generico, que es el de la enorme mayoria de los llamadores. Existe como
         * constructor y no como factoria estatica para que el valor por defecto sea el
         * <b>seguro</b>: quien no piensa en el gesto queda gobernado por la politica del habito,
         * y saltearla exige escribirlo a proposito con {@link GestoCompletar#PROPIO_DEL_HABITO}.
         */
        public CompletarRegistroCommand(UserId actorId, RegistroHabitoId registroId, String respuestaTexto,
                                         Integer calificacionProductividad) {
            this(actorId, registroId, respuestaTexto, calificacionProductividad, GestoCompletar.GENERICO, null);
        }

        /** El gesto generico con el numero que escribio la persona (D-226). {@code valor} nulo = sin medicion. */
        public static CompletarRegistroCommand conValorManual(UserId actorId, RegistroHabitoId registroId,
                                                              String respuestaTexto,
                                                              Integer calificacionProductividad,
                                                              java.math.BigDecimal valor) {
            return new CompletarRegistroCommand(actorId, registroId, respuestaTexto, calificacionProductividad,
                    GestoCompletar.GENERICO, MedicionDiaria.manualSiHay(valor));
        }
    }
}
