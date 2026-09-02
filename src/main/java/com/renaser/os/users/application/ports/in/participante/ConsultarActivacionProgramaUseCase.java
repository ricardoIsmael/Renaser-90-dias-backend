package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * D-66: le dice a la app si tiene que mostrar la pantalla de "elegi tu Dia 1" y, si
 * todavia no eligio, que fechas puede ofrecer. Existe como consulta separada de
 * {@link ActivateProgramUseCase} (en vez de, por ejemplo, devolver siempre las fechas en
 * la respuesta del perfil) porque solo tiene sentido pedirla en el momento exacto en que
 * la app va a decidir si dibuja esa pantalla — igual criterio que documenta
 * {@code useProgramStartDate} en el cliente movil: "un aviso es decoracion y no puede
 * costarle nada a la pantalla que lo muestra".
 */
public interface ConsultarActivacionProgramaUseCase {

    EstadoActivacionPrograma consultarEstado(ConsultarActivacionProgramaQuery query);

    record ConsultarActivacionProgramaQuery(UserId actorId) {

        public ConsultarActivacionProgramaQuery {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }

    /**
     * @param activado      si ya eligio Dia 1 (o nunca tuvo que hacerlo, ej. staff con
     *                      seguimiento personal, que arranca ya activado)
     * @param fechasValidas las 4 fechas (hoy, +1, +2, +3) que {@link ActivateProgramUseCase}
     *                      va a aceptar ahora mismo; vacia si {@code activado} es
     *                      {@code true} (no hay nada que elegir)
     */
    record EstadoActivacionPrograma(boolean activado, List<LocalDate> fechasValidas) {
    }
}
