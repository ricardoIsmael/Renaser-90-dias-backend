package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Accion;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.AprendizQueBaja;
import com.renaser.os.mentoring.domain.model.quesigue.QueSigue.Candidato;
import com.renaser.os.points.api.CumplimientoAprendiz;
import com.renaser.os.points.api.EstadoEvaluacion;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Arma «Qué sigue» y «Quién baja el promedio» de «Mi evaluación» (spec 057, FR-010 a FR-012). La regla vive en {@link QueSigue}; aquí solo se
 * juntan los datos: el detalle por aprendiz que ya calculó el motor de {@code points}, de quienes el mentor acompaña HOY (P-07: el mentor
 * saliente no ve a quien ya no acompaña) y sus nombres.
 */
@Component
class ArmadoDeQueSigue {

    private final AcompanamientoFinder acompanamiento;
    private final UserSummaryFinder usuarios;

    ArmadoDeQueSigue(AcompanamientoFinder acompanamiento, UserSummaryFinder usuarios) {
        this.acompanamiento = acompanamiento;
        this.usuarios = usuarios;
    }

    /** Lo que se suma a la evaluación del mes. */
    record QueSigueDelMes(List<AprendizQueBaja> quienBaja, List<Accion> acciones) {

        static final QueSigueDelMes VACIO = new QueSigueDelMes(List.of(), List.of());
    }

    QueSigueDelMes armar(UserId mentorId, EvaluacionCumplimiento evaluacion, BigDecimal porcentajeAnterior, YearMonth mesAnterior,
                         Instant ahora) {
        if (evaluacion.estado() != EstadoEvaluacion.CALCULADA) {
            return QueSigueDelMes.VACIO;
        }
        List<AprendizQueBaja> quienBaja = QueSigue.quienBaja(candidatosDeHoy(mentorId, evaluacion.detallePorAprendiz(), ahora));
        return new QueSigueDelMes(quienBaja, QueSigue.acciones(evaluacion.porcentaje(), porcentajeAnterior, mesAnterior,
                evaluacion.tardiasFueraDeVentana(), quienBaja));
    }

    /** Cada aprendiz vigente hoy en un grupo que el mentor acompaña hoy, con lo que lleva en el mes; sin detalle o sin nada esperado, no entra. */
    private List<Candidato> candidatosDeHoy(UserId mentorId, List<CumplimientoAprendiz> detalle, Instant ahora) {
        Map<UUID, CumplimientoAprendiz> porAprendiz = new java.util.HashMap<>();
        detalle.stream().filter(CumplimientoAprendiz::evaluable).forEach(d -> porAprendiz.put(d.aprendizId(), d));
        Map<UUID, UUID> grupoDe = new java.util.LinkedHashMap<>();
        for (UUID grupoId : gruposQueAcompanaHoy(mentorId, ahora)) {
            acompanamiento.aprendicesVigentes(grupoId, ahora).stream()
                    .filter(a -> porAprendiz.containsKey(a.value()))
                    .forEach(a -> grupoDe.putIfAbsent(a.value(), grupoId));
        }
        Map<UserId, UserSummary> nombres = usuarios.findByIds(grupoDe.keySet().stream().map(UserId::of).toList());
        List<Candidato> candidatos = new ArrayList<>();
        grupoDe.forEach((aprendizId, grupoId) -> {
            CumplimientoAprendiz c = porAprendiz.get(aprendizId);
            UserSummary persona = nombres.get(UserId.of(aprendizId));
            if (persona != null) {
                candidatos.add(new Candidato(aprendizId, grupoId, persona.fullName(), c.porcentaje(), c.entregadas(), c.esperadas()));
            }
        });
        return candidatos;
    }

    private Set<UUID> gruposQueAcompanaHoy(UserId mentorId, Instant ahora) {
        Set<UUID> grupos = new HashSet<>();
        for (TramoDeAcompanamiento tramo : acompanamiento.tramosDeMentor(mentorId, Instant.EPOCH, null)) {
            if ((tramo.hasta() == null || tramo.hasta().isAfter(ahora)) && acompanamiento.acompanaVigente(mentorId, tramo.grupoId(), ahora)) {
                grupos.add(tramo.grupoId());
            }
        }
        return grupos;
    }
}
