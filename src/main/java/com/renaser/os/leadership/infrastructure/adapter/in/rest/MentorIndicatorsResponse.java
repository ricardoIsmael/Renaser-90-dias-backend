package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.Fuente;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor;
import com.renaser.os.leadership.application.ports.in.IndicadoresDeMentor.GrupoACargo;
import com.renaser.os.leadership.domain.model.atencion.AtencionDeTickets;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder.EvaluacionDeMentor;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder.SemaforoResumido;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Un mentor con sus indicadores, como lo ven el padrón, la ficha y el reporte (D-241).
 *
 * <p>Cada bloque lleva {@code source}: {@code OK} o {@code UNAVAILABLE}. Con {@code UNAVAILABLE} sus
 * cifras van en null, nunca en cero (RL-05, RL-21). <b>Ningún campo lleva un aprendiz</b>: ni nombre ni
 * id (RL-07).
 *
 * @param traineeCount aprendices activos de sus grupos, sin repetir; null si no se pudo saber
 */
public record MentorIndicatorsResponse(UUID userId, String fullName, String avatarUrl, GroupsBlock groups,
                                       Integer traineeCount, SemaforoBlock semaforo, AttentionBlock attention,
                                       EvaluationBlock evaluation) {

    static final String OK = "OK";
    static final String UNAVAILABLE = "UNAVAILABLE";

    static MentorIndicatorsResponse from(IndicadoresDeMentor i, boolean conPendientes) {
        return new MentorIndicatorsResponse(i.mentorId().value(), i.nombre(), i.avatarUrl(), GroupsBlock.from(i.grupos()),
                i.aprendices(), SemaforoBlock.from(i.semaforo()), AttentionBlock.from(i.atencion(), conPendientes),
                EvaluationBlock.from(i.evaluacion()));
    }

    public record GroupsBlock(String source, List<GroupResponse> items) {
        static GroupsBlock from(Fuente<List<GrupoACargo>> grupos) {
            return grupos.disponible()
                    ? new GroupsBlock(OK, grupos.valor().stream().map(GroupResponse::from).toList())
                    : new GroupsBlock(UNAVAILABLE, null);
        }
    }

    public record GroupResponse(UUID id, String name, int trainees, SemaforoSummary semaforo) {
        static GroupResponse from(GrupoACargo g) {
            return new GroupResponse(g.grupoId(), g.nombre(), g.aprendices(), SemaforoSummary.from(g.semaforo()));
        }
    }

    /** @param summary null = no lidera ningún grupo hoy (con {@code source = OK}) */
    public record SemaforoBlock(String source, SemaforoSummary summary) {
        static SemaforoBlock from(Fuente<SemaforoResumido> semaforo) {
            return semaforo.disponible()
                    ? new SemaforoBlock(OK, SemaforoSummary.from(semaforo.valor()))
                    : new SemaforoBlock(UNAVAILABLE, null);
        }
    }

    /** Mismos nombres que el resumen por grupos ({@code GET /api/v1/semaforo/groups}). */
    public record SemaforoSummary(int verde, int amarillo, int rojo, int sinDatos, int total, BigDecimal promedio,
                                  String color, String etiqueta) {
        static SemaforoSummary from(SemaforoResumido s) {
            return s == null ? null : new SemaforoSummary(s.verde(), s.amarillo(), s.rojo(), s.sinDatos(), s.total(),
                    s.promedio(), s.color(), s.etiqueta());
        }
    }

    /**
     * @param open               tickets abiertos HOY de sus aprendices; null en el reporte de un mes cerrado
     * @param oldestOpenDays     días del pendiente más viejo; null sin pendientes
     * @param answered           los que ÉL respondió en el mes (V88)
     * @param medianResponseHours mediana de horas, con {@code answered} como su n; null sin respuestas
     */
    public record AttentionBlock(String source, Integer open, Integer oldestOpenDays, Integer answered,
                                 BigDecimal medianResponseHours) {
        static AttentionBlock from(Fuente<AtencionDeTickets> atencion, boolean conPendientes) {
            if (!atencion.disponible()) {
                return new AttentionBlock(UNAVAILABLE, null, null, null, null);
            }
            AtencionDeTickets a = atencion.valor();
            return new AttentionBlock(OK, conPendientes ? a.pendientes() : null,
                    conPendientes ? a.diasDelMasAntiguo() : null, a.respondidas(), a.medianaHoras());
        }
    }

    /**
     * La evaluación del mes del mentor: la misma que él ve de sí mismo. {@code percentage} null cuando
     * {@code state} no es CALCULADA (SIN_MUESTRA, SIN_HISTORIAL).
     */
    public record EvaluationBlock(String source, String month, BigDecimal percentage, Integer delivered,
                                  Integer expected, Integer traineesEvaluated, String state, String formulaVersion) {
        static EvaluationBlock from(Fuente<EvaluacionDeMentor> evaluacion) {
            if (!evaluacion.disponible()) {
                return new EvaluationBlock(UNAVAILABLE, null, null, null, null, null, null, null);
            }
            EvaluacionDeMentor e = evaluacion.valor();
            return new EvaluationBlock(OK, e.mes(), e.porcentaje(), e.entregadas(), e.esperadas(), e.alumnosEvaluados(),
                    e.estado(), e.versionFormula());
        }
    }
}
