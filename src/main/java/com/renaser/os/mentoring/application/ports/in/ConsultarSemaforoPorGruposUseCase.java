package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ResumenDelGrupo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * El semáforo de todos los grupos, SIN un solo nombre ni id de aprendiz: para el líder de mentores
 * (RL-07 del SDD 002), el administrador y el alquimista (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md
 * §4.4). La respuesta no tiene dónde llevar un aprendiz: el tipo lo impide, no una convención.
 */
public interface ConsultarSemaforoPorGruposUseCase {

    ResumenPorGrupos resumenDe(ConsultaResumenPorGrupos consulta);

    /** @param semanaHasta null = la ventana vigente; si viene, un viernes (la semana sábado→viernes). */
    record ConsultaResumenPorGrupos(UserId actorId, LocalDate semanaHasta) {

        public ConsultaResumenPorGrupos {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (semanaHasta != null) {
                SemanaDelSemaforo.exigirCierreValido(semanaHasta);
            }
        }
    }

    /**
     * @param totales la suma de los conteos de {@code grupos}
     * @param grupos  los grupos regulares con mentor vigente (sin recepción), por nombre
     */
    record ResumenPorGrupos(PeriodoDelSemaforo periodo, ConteoPorColor totales, List<GrupoDelResumen> grupos) {
    }

    /** @param mentorNombre null si {@code users} no lo conoce */
    record GrupoDelResumen(UUID grupoId, String grupoNombre, String mentorNombre, ResumenDelGrupo resumen) {
    }
}
