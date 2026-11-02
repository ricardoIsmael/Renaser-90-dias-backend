package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * D-66: le dice a la app si tiene que mostrar la pantalla de activacion del programa y, si
 * todavia no lo activo, que fecha de inicio le corresponde (spec 036: una sola, mañana). Existe como consulta separada de
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
     * @param fechasValidas la fecha que {@link ActivateProgramUseCase} va a aceptar ahora mismo: UNA, mañana en la zona del
     *                      participante (<b>nunca hoy</b>; spec 036, D-309 — antes eran mañana, +2 y +3 a elegir); vacia
     *                      si {@code activado} es {@code true} (no hay nada que activar). Sigue siendo una lista para que
     *                      el APK anterior, que dibuja una opcion por fecha, siga funcionando
     * @param fechaInicio   el Dia 1 ya activado, {@code null} si todavia no
     *                      activo (D-84). Sin este dato la app puede decir "todavia no
     *                      arrancaste" pero no "arrancas el 5 de septiembre", que es lo que
     *                      de verdad calma a alguien mirando un plan vacio
     */
    record EstadoActivacionPrograma(boolean activado, List<LocalDate> fechasValidas, LocalDate fechaInicio) {

        /** Firma historica (sin fechaInicio): se conserva para no obligar a los llamadores
         * existentes a pasar un campo que no tienen. */
        public EstadoActivacionPrograma(boolean activado, List<LocalDate> fechasValidas) {
            this(activado, fechasValidas, null);
        }
    }
}
