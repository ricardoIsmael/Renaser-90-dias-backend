package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * El detalle del semáforo de UN aprendiz, visto por quien acompaña VIGENTEMENTE su grupo
 * (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1). Además de acompañar el grupo, el aprendiz
 * pedido tiene que pertenecer a él: sin eso, un mentor legítimo podría leer a cualquiera pasando el
 * id de su propio grupo (V12).
 */
public interface ConsultarSemaforoDelAprendizUseCase {

    /** Cuántas semanas cerradas se pueden pedir (§4.1): por defecto 8, entre 1 y 13. */
    int SEMANAS_MINIMAS = 1;
    int SEMANAS_MAXIMAS = 13;

    DetalleDelSemaforo detalleDe(ConsultaDetalleDelAprendiz consulta);

    /** Lo que venga en {@code semanas} se acota a [{@value #SEMANAS_MINIMAS}, {@value #SEMANAS_MAXIMAS}]. */
    static int acotarSemanas(int semanas) {
        return Math.clamp(semanas, SEMANAS_MINIMAS, SEMANAS_MAXIMAS);
    }

    /** @param semanas cuántas semanas cerradas traer; se acota a 1..13 */
    record ConsultaDetalleDelAprendiz(UserId actorId, UUID grupoId, UUID aprendizId, int semanas) {

        public ConsultaDetalleDelAprendiz {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
            semanas = acotarSemanas(semanas);
        }
    }
}
