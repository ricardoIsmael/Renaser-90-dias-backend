package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase.ConsultaAprendices;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase.PaginaAprendices;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase.ContextoAcompanamiento;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La autorización del acompañamiento, con dobles en memoria. El caso que más importa es el
 * negativo: manipular el {@code groupId} no debe devolver ni un nombre de otro grupo (V12), y
 * un exmentor con la asignación cerrada tampoco pasa aunque su token siga vivo (V11).
 */
class AcompanamientoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T15:00:00Z");
    private static final FixedClock CLOCK = FixedClock.at(AHORA);

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId MI_GRUPO = CelulaId.of(UUID.randomUUID());
    private static final CelulaId GRUPO_AJENO = CelulaId.of(UUID.randomUUID());

    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId EXMENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());
    private static final UserId AJENO = UserId.of(UUID.randomUUID());

    // ── dobles ──────────────────────────────────────────────────────────────
    private final List<AsignacionCelula> asignaciones = new ArrayList<>();
    private final Map<UUID, Celula> celulas = new HashMap<>();
    private final Map<UserId, UserSummary> usuarios = new HashMap<>();
    private boolean mentorInscrito = false;

    private final LoadAsignacionesPort cargaAsignaciones = new LoadAsignacionesPort() {
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

    private final LoadCelulaPort cargaCelulas = new LoadCelulaPort() {
        @Override
        public Optional<Celula> porId(CelulaId id) {
            return Optional.ofNullable(celulas.get(id.value()));
        }

        @Override
        public List<Celula> porCohorte(CohorteId cohorteId) {
            return List.copyOf(celulas.values());
        }

        @Override
        public List<Celula> todas() {
            return List.copyOf(celulas.values());
        }

        @Override
        public Optional<Celula> porMentor(UserId mentorId) {
            return Optional.empty();
        }
    };

    private final LoadPoliticaMentoriaPort cargaPolitica = cohorteId -> Optional.empty();

    private final UserSummaryFinder buscaUsuarios = new UserSummaryFinder() {
        @Override
        public Optional<UserSummary> findById(UserId id) {
            return Optional.ofNullable(usuarios.get(id));
        }

        @Override
        public Map<UserId, UserSummary> findByIds(java.util.Collection<UserId> ids) {
            Map<UserId, UserSummary> encontrados = new HashMap<>();
            ids.forEach(id -> {
                UserSummary perfil = usuarios.get(id);
                if (perfil != null) {
                    encontrados.put(id, perfil);
                }
            });
            return encontrados;
        }
            @Override
            public java.util.Optional<UserSummary> findByEmail(String email) {
                return java.util.Optional.empty();
            }

    };

    private final ParticipacionProgramaFinder buscaParticipacion = new ParticipacionProgramaFinder() {
        @Override
        public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
            return Optional.of(new ParticipacionPrograma(participanteId, mentorInscrito,
                    mentorInscrito ? 12 : 0, null, ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH,
                    null, null, UserRole.MENTOR, false, mentorInscrito));
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
            return List.of();
        }

        @Override
        public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
            return List.of();
        }

        @Override
        public List<UserId> participantesInscritosActivos() {
            return List.of();
        }

        @Override
        public int contarMiembrosDeCelula(UUID celulaId) {
            return 0;
        }
    };

    private AcompanamientoService servicio() {
        return new AcompanamientoService(cargaAsignaciones, cargaCelulas, cargaPolitica, buscaParticipacion,
                buscaUsuarios, CLOCK);
    }

    // ── armado ──────────────────────────────────────────────────────────────
    private void grupo(CelulaId id, String nombre) {
        celulas.put(id.value(), Celula.crear(id, nombre, COHORTE, null, AHORA));
    }

    private void persona(UserId id, String nombre) {
        usuarios.put(id, new UserSummary(id, nombre, null, UserRole.TRAINEE, UserStatus.ACTIVE));
    }

    private AsignacionCelula asignar(CelulaId celula, UserId usuario, FuncionAcompanamiento funcion,
                                      Instant desde, Instant hasta) {
        AsignacionCelula a = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), celula, usuario, funcion,
                desde, MotivoAsignacion.ADMINISTRATIVO, null, UUID.randomUUID().toString());
        if (hasta != null) {
            a.cerrar(hasta, MotivoAsignacion.ROTACION);
        }
        asignaciones.add(a);
        return a;
    }

    private void escenarioBase() {
        grupo(MI_GRUPO, "Grupo Amanecer");
        grupo(GRUPO_AJENO, "Grupo Ajeno");
        persona(ANA, "Ana Perez");
        persona(LUIS, "Luis Gomez");
        persona(AJENO, "Persona Ajena");
        asignar(MI_GRUPO, MENTOR, FuncionAcompanamiento.MENTOR, AHORA.minusSeconds(86_400), null);
        asignar(MI_GRUPO, ANA, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(86_400), null);
        asignar(MI_GRUPO, LUIS, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(86_400), null);
        asignar(GRUPO_AJENO, AJENO, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(86_400), null);
    }

    // ── pruebas ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("el mentor ve su grupo, con cobertura y cantidad de aprendices")
    void contextoDelMentor() {
        escenarioBase();

        ContextoAcompanamiento contexto = servicio().contexto(MENTOR);

        assertThat(contexto.puedeAcompanar()).isTrue();
        assertThat(contexto.asignaciones()).hasSize(1);
        assertThat(contexto.asignaciones().getFirst().grupoNombre()).isEqualTo("Grupo Amanecer");
        assertThat(contexto.asignaciones().getFirst().aprendices()).isEqualTo(2);
        assertThat(contexto.asignaciones().getFirst().cobertura().name()).isEqualTo("CON_MENTOR");
        assertThat(contexto.asignaciones().getFirst().cupo()).isEqualTo(10);
    }

    @Test
    @DisplayName("acompañar no exige cursar: sin programa personal el contexto igual responde (D-07)")
    void acompanarSinProgramaPersonal() {
        escenarioBase();
        mentorInscrito = false;

        ContextoAcompanamiento contexto = servicio().contexto(MENTOR);

        assertThat(contexto.participaEnPrograma()).isFalse();
        // null, no 0: "no participa" no es "va por el dia cero".
        assertThat(contexto.diaDePrograma()).isNull();
        assertThat(contexto.puedeAcompanar()).isTrue();
    }

    @Test
    @DisplayName("para el mentor el programa NO es obligatorio, y puede iniciarlo si quiere (D-07)")
    void capacidadesDelMentorSinPrograma() {
        escenarioBase();
        mentorInscrito = false;

        var capacidades = servicio().contexto(MENTOR).capacidades();

        // Es lo que le permite al cliente dejarlo pasar sin onboarding: si lo dedujera solo,
        // le mostraria un bloqueo que no le corresponde.
        assertThat(capacidades.programaObligatorio()).isFalse();
        assertThat(capacidades.puedeActivarPrograma()).isTrue();
        assertThat(capacidades.acompanar()).isTrue();
    }

    @Test
    @DisplayName("un mentor que ya activo su programa no puede volver a activarlo")
    void capacidadesDelMentorQueYaCursa() {
        escenarioBase();
        mentorInscrito = true;

        var capacidades = servicio().contexto(MENTOR).capacidades();

        assertThat(capacidades.puedeActivarPrograma()).isFalse();
        assertThat(capacidades.programaObligatorio()).isFalse();
    }

    @Test
    @DisplayName("el aprendiz no acompaña nada: su pertenencia no lo convierte en mentor")
    void aprendizNoAcompana() {
        escenarioBase();

        ContextoAcompanamiento contexto = servicio().contexto(ANA);

        assertThat(contexto.puedeAcompanar()).isFalse();
        assertThat(contexto.asignaciones()).isEmpty();
    }

    @Test
    @DisplayName("el mentor lee el roster de SU grupo")
    void rosterDelGrupoPropio() {
        escenarioBase();

        PaginaAprendices pagina = servicio().aprendices(new ConsultaAprendices(MENTOR, MI_GRUPO.value(), null, 25));

        assertThat(pagina.total()).isEqualTo(2);
        assertThat(pagina.aprendices()).extracting("nombre").containsExactly("Ana Perez", "Luis Gomez");
        assertThat(pagina.siguienteCursor()).isNull();
    }

    @Test
    @DisplayName("manipular el groupId no devuelve ni un nombre de otro grupo (V12)")
    void grupoAjenoNoSeFiltra() {
        escenarioBase();

        assertThatThrownBy(() -> servicio().aprendices(
                new ConsultaAprendices(MENTOR, GRUPO_AJENO.value(), null, 25)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("un grupo inexistente responde igual que uno ajeno: no confirma que exista")
    void grupoInexistenteNoSeDistingue() {
        escenarioBase();

        assertThatThrownBy(() -> servicio().aprendices(
                new ConsultaAprendices(MENTOR, UUID.randomUUID(), null, 25)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("el exmentor con asignacion cerrada no lee el roster aunque su token siga vivo (V11)")
    void exmentorNoLee() {
        escenarioBase();
        asignar(MI_GRUPO, EXMENTOR, FuncionAcompanamiento.MENTOR,
                AHORA.minusSeconds(2_592_000), AHORA.minusSeconds(86_400));

        assertThatThrownBy(() -> servicio().aprendices(
                new ConsultaAprendices(EXMENTOR, MI_GRUPO.value(), null, 25)))
                .isInstanceOf(NotAuthorizedException.class);

        // Y su contexto ya no lista el grupo.
        assertThat(servicio().contexto(EXMENTOR).asignaciones()).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin mentor sigue siendo un grupo: soporte cubre y el roster responde")
    void grupoCubiertoPorSoporte() {
        grupo(MI_GRUPO, "Grupo Sin Mentor");
        persona(ANA, "Ana Perez");
        UserId soporte = UserId.of(UUID.randomUUID());
        asignar(MI_GRUPO, soporte, FuncionAcompanamiento.SOPORTE, AHORA.minusSeconds(86_400), null);
        asignar(MI_GRUPO, ANA, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(86_400), null);

        PaginaAprendices pagina = servicio().aprendices(new ConsultaAprendices(soporte, MI_GRUPO.value(), null, 25));

        assertThat(pagina.cobertura()).isEqualTo("SOPORTE");
        assertThat(pagina.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("la paginacion no repite ni saltea entre paginas")
    void paginacionEstable() {
        grupo(MI_GRUPO, "Grupo Amanecer");
        asignar(MI_GRUPO, MENTOR, FuncionAcompanamiento.MENTOR, AHORA.minusSeconds(86_400), null);
        for (int i = 0; i < 5; i++) {
            UserId alumno = UserId.of(UUID.randomUUID());
            persona(alumno, "Alumno " + i);
            asignar(MI_GRUPO, alumno, FuncionAcompanamiento.APRENDIZ, AHORA.minusSeconds(86_400), null);
        }

        PaginaAprendices primera = servicio().aprendices(new ConsultaAprendices(MENTOR, MI_GRUPO.value(), null, 2));
        assertThat(primera.aprendices()).hasSize(2);
        assertThat(primera.siguienteCursor()).isNotNull();

        PaginaAprendices segunda = servicio().aprendices(
                new ConsultaAprendices(MENTOR, MI_GRUPO.value(), primera.siguienteCursor(), 2));
        assertThat(segunda.aprendices()).hasSize(2);
        assertThat(segunda.aprendices()).doesNotContainAnyElementsOf(primera.aprendices());

        PaginaAprendices tercera = servicio().aprendices(
                new ConsultaAprendices(MENTOR, MI_GRUPO.value(), segunda.siguienteCursor(), 2));
        assertThat(tercera.aprendices()).hasSize(1);
        assertThat(tercera.siguienteCursor()).isNull();
    }
}
