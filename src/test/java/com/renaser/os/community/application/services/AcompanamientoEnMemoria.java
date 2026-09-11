package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Banco de dobles en memoria para probar traslado y rotación sin base.
 *
 * <p>Dos cosas lo hacen útil más allá de evitar Postgres. Primera: {@link #operaciones} anota
 * cada escritura en orden, así un test puede afirmar que el cierre ocurrió ANTES de la apertura
 * — que es la parte del intercambio A↔B que la base exige y que un assert sobre el estado final
 * no distingue. Segunda: {@link #simularUnicidad} reproduce el índice único de V45, para que un
 * fallo de orden reviente acá y no recién en integración.
 */
class AcompanamientoEnMemoria {

    final List<AsignacionCelula> asignaciones = new ArrayList<>();
    final Map<UUID, Celula> celulas = new LinkedHashMap<>();
    final List<Cohorte> cohortes = new ArrayList<>();
    final Map<UserId, ParticipacionPrograma> participaciones = new LinkedHashMap<>();
    final Map<CohorteId, PoliticaMentoria> politicas = new LinkedHashMap<>();
    final Set<UserId> conPerfilMentor = new java.util.HashSet<>();
    final List<String> operaciones = new ArrayList<>();
    /** Punteros de proyección que escribiría `users`. */
    final Map<UserId, String> punteros = new LinkedHashMap<>();

    /** Cuando está activo, reproduce el UNIQUE de V45 sobre el mentor vigente de cada célula. */
    boolean simularUnicidad = true;

    private final AtomicInteger secuencia = new AtomicInteger();

    final IdGenerator idGenerator = () ->
            UUID.fromString("00000000-0000-4000-8000-%012d".formatted(secuencia.incrementAndGet()));

    // ── puertos ─────────────────────────────────────────────────────────────
    final LoadAsignacionesPort cargaAsignaciones = new LoadAsignacionesPort() {
        @Override
        public List<AsignacionCelula> porUsuario(UserId usuarioId) {
            return asignaciones.stream().filter(a -> a.usuarioId().equals(usuarioId)).toList();
        }

        @Override
        public List<AsignacionCelula> porCelula(CelulaId celulaId) {
            return asignaciones.stream().filter(a -> a.celulaId().equals(celulaId)).toList();
        }

        @Override
        public Optional<AsignacionCelula> porClaveOperacion(String claveOperacion) {
            return asignaciones.stream().filter(a -> a.claveOperacion().equals(claveOperacion)).findFirst();
        }
    };

    final SaveAsignacionPort guardaAsignacion = asignacion -> {
        boolean nueva = asignaciones.stream().noneMatch(a -> a.id().equals(asignacion.id()));
        if (nueva) {
            if (simularUnicidad && asignacion.funcion() == FuncionAcompanamiento.MENTOR
                    && asignacion.vigente()) {
                boolean choca = asignaciones.stream()
                        .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR && a.vigente())
                        .anyMatch(a -> a.celulaId().equals(asignacion.celulaId())
                                || a.usuarioId().equals(asignacion.usuarioId()));
                if (choca) {
                    throw new IllegalStateException(
                            "asignaciones_un_mentor_por_celula / _una_celula_por_mentor: intervalo vigente duplicado");
                }
            }
            asignaciones.add(asignacion);
            operaciones.add("abrir:" + asignacion.funcion() + ":" + asignacion.celulaId().value()
                    + ":" + asignacion.usuarioId().value());
        } else {
            operaciones.add("cerrar:" + asignacion.funcion() + ":" + asignacion.celulaId().value()
                    + ":" + asignacion.usuarioId().value());
        }
        return asignacion;
    };

    final LoadCelulaPort cargaCelulas = new LoadCelulaPort() {
        @Override
        public Optional<Celula> porId(CelulaId id) {
            return Optional.ofNullable(celulas.get(id.value()));
        }

        @Override
        public List<Celula> porCohorte(CohorteId cohorteId) {
            return celulas.values().stream().filter(c -> c.cohorteId().equals(cohorteId)).toList();
        }

        @Override
        public List<Celula> todas() {
            return List.copyOf(celulas.values());
        }

        @Override
        public Optional<Celula> porMentor(UserId mentorId) {
            return celulas.values().stream().filter(c -> mentorId.equals(c.mentorId())).findFirst();
        }
    };

    final SaveCelulaPort guardaCelula = celula -> {
        if (simularUnicidad && celula.mentorId() != null) {
            boolean choca = celulas.values().stream()
                    .filter(c -> !c.id().equals(celula.id()))
                    .anyMatch(c -> celula.mentorId().equals(c.mentorId()));
            if (choca) {
                throw new IllegalStateException("celulas.mentor_id UNIQUE: ese mentor ya lidera otra celula");
            }
        }
        celulas.put(celula.id().value(), celula);
        operaciones.add("celula:" + celula.id().value() + ":mentor="
                + (celula.mentorId() == null ? "null" : celula.mentorId().value()));
        return celula;
    };

    final LoadCohortePort cargaCohortes = new LoadCohortePort() {
        @Override
        public Optional<Cohorte> porId(CohorteId id) {
            return cohortes.stream().filter(c -> c.id().equals(id)).findFirst();
        }

        @Override
        public List<Cohorte> listar(EstadoCohorte filtroEstado) {
            return cohortes;
        }

        @Override
        public int contarCelulas(CohorteId id) {
            return (int) celulas.values().stream().filter(c -> c.cohorteId().equals(id)).count();
        }
    };

    final LoadPoliticaMentoriaPort cargaPolitica = cohorteId -> Optional.ofNullable(politicas.get(cohorteId));

    final ExistePerfilMentorPort existePerfil = conPerfilMentor::contains;

    final AsignacionCelulaPort punteroDeUsers = new AsignacionCelulaPort() {
        @Override
        public void asignarCelula(UserId actorId, UserId traineeId, UUID celulaId) {
            throw new UnsupportedOperationException("el acompanamiento usa sincronizarAcompanamiento");
        }

        @Override
        public void quitarCelula(UserId actorId, UserId traineeId) {
            throw new UnsupportedOperationException("el acompanamiento usa sincronizarAcompanamiento");
        }

        @Override
        public void sincronizarAcompanamiento(UserId traineeId, UUID celulaId, UserId mentorId) {
            punteros.put(traineeId, celulaId + "/" + (mentorId == null ? "null" : mentorId.value()));
            operaciones.add("puntero:" + traineeId.value() + ":" + (mentorId == null ? "null" : mentorId.value()));
            // Igual que el adaptador real: escribe participantes_programa, asi que la SIGUIENTE
            // lectura de deParticipante ya ve la celula nueva. Sin esto el doble mentiria y una
            // segunda llamada volveria a "trasladar" a alguien que ya esta en su grupo.
            ParticipacionPrograma previa = participaciones.get(traineeId);
            if (previa != null) {
                participaciones.put(traineeId, new ParticipacionPrograma(previa.participanteId(),
                        previa.inscrito(), previa.diaPrograma(), previa.fechaInicio(), previa.zona(),
                        previa.fase(), celulaId, mentorId, previa.rol(), previa.suspendido(), previa.activado()));
            }
        }
    };

    final ParticipacionProgramaFinder buscaParticipacion = new ParticipacionProgramaFinder() {
        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            return Optional.ofNullable(participaciones.get(participanteId));
        }

        @Override
        public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> miembrosDeCelula(UUID celulaId) {
            return List.of();
        }

        @Override
        public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
            return participaciones.values().stream()
                    .filter(p -> roles.contains(p.rol()))
                    .map(ParticipacionPrograma::participanteId)
                    .toList();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return participaciones.values().stream().filter(ParticipacionPrograma::inscrito)
                    .map(ParticipacionPrograma::participanteId).toList();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    };

    // ── armado ──────────────────────────────────────────────────────────────
    Cohorte cohorte(CohorteId id, PoliticaMentoria politica) {
        Cohorte cohorte = Cohorte.crear(id, "Cohorte", LocalDate.of(2026, 8, 1), null, Instant.EPOCH);
        cohortes.add(cohorte);
        politicas.put(id, politica);
        return cohorte;
    }

    Celula grupo(CelulaId id, CohorteId cohorteId, TipoCelula tipo, UserId mentorId, Instant ahora) {
        Celula celula = Celula.rehydrate(id, "Grupo " + id.value().toString().substring(30), mentorId, cohorteId,
                null, null, ahora, ahora, tipo, null);
        celulas.put(id.value(), celula);
        return celula;
    }

    void mentorConPerfil(UserId id) {
        conPerfilMentor.add(id);
        participaciones.put(id, new ParticipacionPrograma(id, false, 0, null, ZoneId.of("America/Lima"),
                FasePrograma.PHASE_1_REBIRTH, null, null, UserRole.MENTOR, false, false));
    }

    void aprendiz(UserId id, int diaPrograma, boolean activado, UUID celulaId) {
        participaciones.put(id, new ParticipacionPrograma(id, true, diaPrograma, LocalDate.of(2026, 9, 1),
                ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH, celulaId, null, UserRole.TRAINEE,
                false, activado));
    }

    AsignacionCelula asignar(CelulaId celula, UserId usuario, FuncionAcompanamiento funcion, Instant desde,
                              Instant hasta) {
        AsignacionCelula a = AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), celula, usuario, funcion,
                desde, MotivoAsignacion.MIGRACION, null, "previa:" + UUID.randomUUID());
        if (hasta != null) {
            a.cerrar(hasta, MotivoAsignacion.ROTACION);
        }
        asignaciones.add(a);
        return a;
    }

    /**
     * Publicador que anota los eventos de composición. El chat depende de ellos para reconciliar
     * sus participantes, así que una rotación que no los emite deja el chat congelado.
     */
    final List<java.util.UUID> composicionesAvisadas = new ArrayList<>();

    final org.springframework.context.ApplicationEventPublisher publicador = evento -> {
        if (evento instanceof com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent cambio) {
            composicionesAvisadas.add(cambio.celulaId());
        }
    };

    /** Índice de la primera operación cuyo texto empieza con el prefijo, o -1. */
    int primeraOperacion(String prefijo) {
        for (int i = 0; i < operaciones.size(); i++) {
            if (operaciones.get(i).startsWith(prefijo)) {
                return i;
            }
        }
        return -1;
    }
}
