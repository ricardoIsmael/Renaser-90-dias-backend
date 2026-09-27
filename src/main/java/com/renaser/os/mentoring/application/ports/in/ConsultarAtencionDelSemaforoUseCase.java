package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * «¿A quién atiendo hoy?» de Administración (S-4 de docs/specs/RETROALIMENTACION_2026-09-26.md): todos
 * los aprendices activos en rojo o amarillo en la ventana vigente, estén en un grupo regular, en la
 * recepción, en un grupo sin mentor o en ningún grupo.
 *
 * <p><b>Por qué no alcanza el resumen por grupos.</b> {@link ConsultarSemaforoPorGruposUseCase} mira
 * solo grupos regulares con mentor vigente (su contrato, §4.4, y el aviso del sábado dependen de eso):
 * un aprendiz de la recepción, de un grupo sin mentor o sin grupo no aparecía en ninguna vista de
 * administración. Esta lista parte del padrón de aprendices activos, no de los grupos.
 *
 * <p>Solo lectura y con nombres: la ven ADMIN y ALCHEMIST activos, nunca el líder de mentores
 * (RL-07: él ve cantidades, no personas).
 */
public interface ConsultarAtencionDelSemaforoUseCase {

    AtencionDelSemaforo atencionDe(ConsultaAtencion consulta);

    record ConsultaAtencion(UserId actorId) {

        public ConsultaAtencion {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
        }
    }

    /**
     * @param aprendices rojo primero, después amarillo; dentro de cada color, por nombre
     */
    record AtencionDelSemaforo(PeriodoDelSemaforo periodo, int rojo, int amarillo,
                               List<AprendizQueNecesitaAtencion> aprendices) {
    }

    /** @param grupos vacío = no está en ningún grupo que esté corriendo */
    record AprendizQueNecesitaAtencion(UUID aprendizId, String nombre, String avatarUrl, MedicionDelAprendiz medicion,
                                       List<GrupoDelAprendiz> grupos) {
    }

    /**
     * @param recepcion    el grupo de bienvenida
     * @param mentorNombre null si el grupo no tiene mentor vigente
     */
    record GrupoDelAprendiz(UUID grupoId, String grupoNombre, boolean recepcion, String mentorNombre) {
    }
}
