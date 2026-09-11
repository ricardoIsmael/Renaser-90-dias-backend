package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.evidence.api.EntregaDeEvidencia;
import com.renaser.os.evidence.api.EntregasPorRegistroFinder;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.points.api.CalculoCumplimientoPort;
import com.renaser.os.points.domain.model.cumplimiento.CalculoCumplimiento;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dobles compartidos por las pruebas de avisos y de ranking.
 *
 * <p>El cálculo NO se dobla: se usa el motor real de {@code points}. Doblarlo dejaría sin probar
 * lo que puede salir mal acá, que es cómo se arman sus entradas.
 */
class BancoDeMentoria {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    final List<AcompanamientoFinder.GrupoAcompanado> grupos = new ArrayList<>();
    final Map<UUID, List<AcompanamientoFinder.TramoDeAprendiz>> tramosPorGrupo = new LinkedHashMap<>();
    final List<ObligacionHabito> obligaciones = new ArrayList<>();
    final Map<UUID, EntregaDeEvidencia> entregas = new LinkedHashMap<>();
    final Map<UserId, String> nombres = new LinkedHashMap<>();

    final AcompanamientoFinder acompanamiento = new AcompanamientoFinder() {
        @Override
        public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
            return grupos.stream().filter(g -> g.mentorId().equals(mentorId))
                    .map(g -> new TramoDeAcompanamiento(g.grupoId(), g.nombre(), desde, hasta)).toList();
        }

        @Override
        public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
            return tramosPorGrupo.getOrDefault(grupoId, List.of()).stream()
                    .filter(t -> !instante.isBefore(t.desde()) && (t.hasta() == null || instante.isBefore(t.hasta())))
                    .map(TramoDeAprendiz::aprendizId).toList();
        }

        @Override
        public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
            return tramosPorGrupo.getOrDefault(grupoId, List.of()).stream()
                    .filter(t -> t.hasta() == null || t.hasta().isAfter(desde))
                    .filter(t -> hasta == null || t.desde().isBefore(hasta))
                    .map(t -> new TramoDeAprendiz(t.aprendizId(),
                            t.desde().isAfter(desde) ? t.desde() : desde,
                            t.hasta() == null ? hasta : t.hasta()))
                    .toList();
        }
        @Override
        public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
            // Aprendices + acompanantes. Estas pruebas solo pueblan aprendices.
            return aprendicesVigentes(grupoId, instante);
        }

        @Override
        public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
            return aprendicesVigentes(grupoId, instante).contains(usuarioId)
                    || acompanaVigente(usuarioId, grupoId, instante);
        }


        @Override
        public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
            return grupos.stream().anyMatch(g -> g.grupoId().equals(grupoId) && g.mentorId().equals(actorId));
        }

        @Override
        public Optional<GrupoBasico> grupo(UUID grupoId) {
            return grupos.stream().filter(g -> g.grupoId().equals(grupoId))
                    .map(g -> new GrupoBasico(g.grupoId(), g.nombre(), g.cohorteId(), g.zonaHoraria()))
                    .findFirst();
        }

        @Override
        public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
            return grupos;
        }
    };

    final ObligacionesHistoricasFinder obligacionesFinder = (participantes, desde, hasta) -> obligaciones.stream()
            .filter(o -> participantes.contains(o.participanteId()))
            .filter(o -> !o.fecha().isBefore(desde) && !o.fecha().isAfter(hasta))
            .toList();

    final EntregasPorRegistroFinder entregasFinder = ids -> {
        Map<UUID, EntregaDeEvidencia> encontradas = new LinkedHashMap<>();
        ids.forEach(id -> {
            EntregaDeEvidencia e = entregas.get(id);
            if (e != null) {
                encontradas.put(id, e);
            }
        });
        return encontradas;
    };

    final CalculoCumplimientoPort calculo = CalculoCumplimiento::evaluar;

    final UserSummaryFinder usuarios = new UserSummaryFinder() {
        @Override
        public Optional<UserSummary> findById(UserId id) {
            return Optional.of(perfil(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(Collection<UserId> ids) {
            Map<UserId, UserSummary> encontrados = new LinkedHashMap<>();
            ids.forEach(id -> encontrados.put(id, perfil(id)));
            return encontrados;
        }
        @Override
        public java.util.Optional<UserSummary> findByEmail(String email) {
            return java.util.Optional.empty();
        }


        private UserSummary perfil(UserId id) {
            return new UserSummary(id, nombres.getOrDefault(id, "Alguien"), null, UserRole.TRAINEE,
                    UserStatus.ACTIVE);
        }
    };

    // ── armado ──────────────────────────────────────────────────────────────
    AcompanamientoFinder.GrupoAcompanado grupo(UUID id, String nombre, UserId mentor, UUID cohorte, int umbral) {
        var g = new AcompanamientoFinder.GrupoAcompanado(id, nombre, mentor, cohorte, "America/Lima", umbral);
        grupos.add(g);
        return g;
    }

    void alumno(UUID grupoId, UserId alumno, String nombre, Instant desde, Instant hasta) {
        tramosPorGrupo.computeIfAbsent(grupoId, g -> new ArrayList<>())
                .add(new AcompanamientoFinder.TramoDeAprendiz(alumno, desde, hasta));
        nombres.put(alumno, nombre);
    }

    ObligacionHabito obligacion(UserId alumno, LocalDate fecha, boolean cumplida, boolean requiereEvidencia) {
        ObligacionHabito o = new ObligacionHabito(UUID.randomUUID(), alumno, fecha, 24, "Habito",
                cumplida ? EstadoObligacion.COMPLETADO : EstadoObligacion.FALLIDO, requiereEvidencia, false);
        obligaciones.add(o);
        return o;
    }

    void entregada(ObligacionHabito o, LocalDate cuando) {
        entregas.put(o.registroId(), new EntregaDeEvidencia(o.registroId(), UUID.randomUUID(),
                cuando.atTime(12, 0).atZone(LIMA).toInstant(), EstadoValidacion.PENDIENTE, 1));
    }
}
