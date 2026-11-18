package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase.ActualizarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase.CrearCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase.EliminarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase.ProgramarSesionCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase.QuitarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase.QuitarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
import com.renaser.os.community.application.ports.in.celula.SumarMentorAGrupoUseCase.SumarMentorAGrupoCommand;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.EliminarCelulaPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarMiembrosCelulaPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.PerfilMentorFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Desde el 18/11 el líder de mentores arma grupos, pero solo en SUS formaciones (spec 056, FR-008, FR-009, FR-010; decisión del dueño que revierte CL-04).
 * Los cuatro servicios de grupos que antes pedían ADMIN/ALCHEMIST aceptan ahora al líder activo; en una formación que no es suya, 403
 * {@code FUERA_DE_SU_ALCANCE}. Lo que no se le abrió (editar, programar sesión o borrar un grupo) sigue siendo de Administración.
 */
@ExtendWith(MockitoExtension.class)
class GruposDelLiderTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-18T15:00:00Z"));
    private static final String FUERA_DE_SU_ALCANCE = "FUERA_DE_SU_ALCANCE";

    @Mock private LoadCelulaPort loadCelulaPort;
    @Mock private SaveCelulaPort saveCelulaPort;
    @Mock private EliminarCelulaPort eliminarCelulaPort;
    @Mock private LoadCohortePort loadCohortePort;
    @Mock private ConsultarMiembrosCelulaPort consultarMiembrosCelulaPort;
    @Mock private ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    @Mock private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock private UserSummaryFinder userSummaryFinder;
    @Mock private ParticipacionProgramaFinder participacionProgramaFinder;
    @Mock private PerfilMentorFinder perfilMentorFinder;
    @Mock private LoadAsignacionesPort loadAsignacionesPort;
    @Mock private SaveAsignacionPort saveAsignacionPort;
    @Mock private LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    @Mock private ExistePerfilMentorPort existePerfilMentorPort;
    @Mock private AsignacionCelulaPort asignacionCelulaPort;
    @Mock private ConsultarCelulasUseCase consultarCelulas;
    @Mock private CuentasCerradasFinder cuentasCerradas;
    @Mock private ApplicationEventPublisher eventos;
    @Mock private IdGenerator idGenerator;

    private CelulaService celulas;
    private ComposicionDeCelulaService composicion;
    private SumarAprendizAGrupoService sumarAprendiz;
    private SumarMentorAGrupoService sumarMentor;

    private final UserId lider = UserId.of(UUID.randomUUID());
    private final UserId liderSuspendido = UserId.of(UUID.randomUUID());
    private final UserId mentorEscuela = UserId.of(UUID.randomUUID());
    private final UserId aprendizEscuela = UserId.of(UUID.randomUUID());
    private final UserId aprendizDiplomado = UserId.of(UUID.randomUUID());
    private final UserId mentorDiplomado = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());

    private Celula grupoEscuela;
    private Celula grupoDiplomado;

    /** El líder tiene activa la Escuela; cada persona es de una sola formación. */
    private final class AlcanceDelLider extends AlcanceDeFormacionPermisivo {

        private final Map<UserId, Formacion> deCadaUno = Map.of(mentorEscuela, Formacion.ESCUELA, aprendizEscuela, Formacion.ESCUELA,
                aprendizDiplomado, Formacion.DIPLOMADO, mentorDiplomado, Formacion.DIPLOMADO);

        @Override
        public AlcanceDeFormacion de(UserId actor) {
            return actor.equals(lider) ? AlcanceDeFormacion.de(Formacion.ESCUELA) : AlcanceDeFormacion.todas();
        }

        @Override
        public Set<Formacion> formacionesDe(UserId persona) {
            return Set.of(deCadaUno.getOrDefault(persona, Formacion.ESCUELA));
        }

        @Override
        public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
            return personas.stream().filter(p -> alcance.incluye(deCadaUno.getOrDefault(p, Formacion.ESCUELA))).collect(Collectors.toSet());
        }
    }

    @BeforeEach
    void preparar() {
        AlcanceDeFormacionFinder alcance = new AlcanceDelLider();
        FormacionDelGrupo formacion = FormacionDelGrupoDePrueba.con(alcance);
        celulas = new CelulaService(loadCelulaPort, saveCelulaPort, eliminarCelulaPort, loadCohortePort, consultarMiembrosCelulaPort,
                consultarCelulaDeParticipantePort, consultarPerfilUsuarioPort, userSummaryFinder, participacionProgramaFinder,
                perfilMentorFinder, loadAsignacionesPort, loadPoliticaMentoriaPort, eventos, CLOCK, idGenerator, cuentasCerradas, formacion);
        composicion = new ComposicionDeCelulaService(loadCelulaPort, saveCelulaPort, loadAsignacionesPort, saveAsignacionPort,
                loadPoliticaMentoriaPort, existePerfilMentorPort, consultarCelulaDeParticipantePort, userSummaryFinder,
                asignacionCelulaPort, consultarCelulas, eventos, CLOCK, idGenerator, formacion);
        sumarAprendiz = new SumarAprendizAGrupoService(loadCelulaPort, loadAsignacionesPort, saveAsignacionPort, loadPoliticaMentoriaPort,
                consultarCelulaDeParticipantePort, userSummaryFinder, asignacionCelulaPort, consultarCelulas, eventos, CLOCK, idGenerator,
                formacion);
        sumarMentor = new SumarMentorAGrupoService(loadCelulaPort, saveCelulaPort, loadAsignacionesPort, saveAsignacionPort,
                existePerfilMentorPort, userSummaryFinder, asignacionCelulaPort, consultarCelulas, eventos, CLOCK, idGenerator, formacion);

        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(loadAsignacionesPort.porCelula(any())).thenReturn(List.of());
        lenient().when(loadAsignacionesPort.porUsuario(any())).thenReturn(List.of());
        lenient().when(loadPoliticaMentoriaPort.porCohorte(any())).thenReturn(Optional.empty());
        lenient().when(consultarCelulaDeParticipantePort.celulaDeUsuario(any())).thenReturn(Optional.empty());
        lenient().when(existePerfilMentorPort.existe(any())).thenReturn(true);
        lenient().when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(perfilMentorFinder.porUsuarios(any())).thenReturn(Map.of());
        usuario(lider, UserRole.MENTOR_LEAD, UserStatus.ACTIVE);
        usuario(liderSuspendido, UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);
        usuario(mentorEscuela, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(mentorDiplomado, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(mentor, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(aprendizEscuela, UserRole.TRAINEE, UserStatus.ACTIVE);
        usuario(aprendizDiplomado, UserRole.TRAINEE, UserStatus.ACTIVE);
        grupoEscuela = grupo("Fénix", Formacion.ESCUELA);
        grupoDiplomado = grupo("Atlas", Formacion.DIPLOMADO);
    }

    private void usuario(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, rol.name(), null, rol, estado)));
    }

    private Celula grupo(String nombre, Formacion formacion) {
        Celula celula = Celula.rehydrate(CelulaId.of(UUID.randomUUID()), nombre, null, CohorteId.of(UUID.randomUUID()), null, null,
                CLOCK.now(), CLOCK.now(), TipoCelula.REGULAR, null, null, formacion);
        lenient().when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        lenient().when(loadCohortePort.porId(celula.cohorteId())).thenReturn(Optional.of(Cohorte.rehydrate(celula.cohorteId(), "Cohorte",
                LocalDate.of(2026, 11, 1), null, EstadoCohorte.ACTIVA, CLOCK.now(), CLOCK.now())));
        return celula;
    }

    private static void sinAlcance(Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(NotAuthorizedException.class,
                e -> assertThat(e.codigo()).isEqualTo(FUERA_DE_SU_ALCANCE));
    }

    // ── Crear ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-008 FR-010: el líder crea un grupo en su formación")
    void elLiderCreaUnGrupoEnSuFormacion() {
        CohorteId cohorte = grupoEscuela.cohorteId();

        celulas.crear(new CrearCelulaCommand(lider, "Aurora", cohorte, null, null, null, null, null, Formacion.ESCUELA));

        verify(saveCelulaPort).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-010: el líder no crea un grupo en una formación que no es suya: 403 FUERA_DE_SU_ALCANCE")
    void elLiderNoCreaFueraDeSuFormacion() {
        CohorteId cohorte = grupoDiplomado.cohorteId();

        sinAlcance(() -> celulas.crear(new CrearCelulaCommand(lider, "Aurora", cohorte, null, null, null, null, null, Formacion.DIPLOMADO)));

        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-008: sin formación en el pedido, el grupo nace en la Escuela (como siempre) y el líder de la Escuela lo crea")
    void sinFormacionElGrupoNaceEnLaEscuela() {
        celulas.crear(new CrearCelulaCommand(lider, "Aurora", grupoEscuela.cohorteId(), null));

        verify(saveCelulaPort).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-010: un líder suspendido no crea grupos aunque sea de la formación")
    void unLiderSuspendidoNoCreaGrupos() {
        assertThatThrownBy(() -> celulas.crear(new CrearCelulaCommand(liderSuspendido, "Aurora", grupoEscuela.cohorteId(), null)))
                .isInstanceOf(NotAuthorizedException.class);

        verify(saveCelulaPort, never()).save(any());
    }

    // ── Mentor del grupo ────────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder asigna el mentor de un grupo de su formación")
    void elLiderAsignaElMentor() {
        composicion.asignar(new AsignarMentorCelulaCommand(lider, grupoEscuela.id(), mentorEscuela));

        verify(saveAsignacionPort).save(any());
        verify(saveCelulaPort).save(grupoEscuela);
    }

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder no asigna ni quita el mentor de un grupo de otra formación: 403 FUERA_DE_SU_ALCANCE")
    void elLiderNoCambiaElMentorDeOtraFormacion() {
        sinAlcance(() -> composicion.asignar(new AsignarMentorCelulaCommand(lider, grupoDiplomado.id(), mentorDiplomado)));
        sinAlcance(() -> composicion.quitar(new QuitarMentorCelulaCommand(lider, grupoDiplomado.id())));

        verify(saveAsignacionPort, never()).save(any());
        verify(saveCelulaPort, never()).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder suma un mentor a un grupo de su formación, y en otra recibe 403 FUERA_DE_SU_ALCANCE")
    void elLiderSumaUnMentorSoloEnSuFormacion() {
        sumarMentor.sumar(new SumarMentorAGrupoCommand(lider, grupoEscuela.id(), mentorEscuela));
        verify(saveAsignacionPort).save(any());

        sinAlcance(() -> sumarMentor.sumar(new SumarMentorAGrupoCommand(lider, grupoDiplomado.id(), mentorDiplomado)));
    }

    // ── Aprendices del grupo ────────────────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder suma un aprendiz a un grupo de su formación")
    void elLiderSumaUnAprendiz() {
        composicion.asignar(new AsignarAprendizCelulaCommand(lider, grupoEscuela.id(), aprendizEscuela));

        verify(saveAsignacionPort).save(any());
        verify(asignacionCelulaPort).sincronizarAcompanamiento(aprendizEscuela, grupoEscuela.id().value(), null);
    }

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder no suma aprendices a un grupo de otra formación: 403 FUERA_DE_SU_ALCANCE")
    void elLiderNoSumaAprendicesDeOtraFormacion() {
        sinAlcance(() -> composicion.asignar(new AsignarAprendizCelulaCommand(lider, grupoDiplomado.id(), aprendizDiplomado)));
        sinAlcance(() -> sumarAprendiz.sumar(new SumarAprendizAGrupoCommand(lider, grupoDiplomado.id(), aprendizDiplomado)));

        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder suma un aprendiz adicional a un grupo de su formación")
    void elLiderSumaUnAprendizAdicional() {
        sumarAprendiz.sumar(new SumarAprendizAGrupoCommand(lider, grupoEscuela.id(), aprendizEscuela));

        verify(saveAsignacionPort).save(any());
    }

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder retira un aprendiz de un grupo de su formación y no de otra")
    void elLiderRetiraAprendicesSoloDeSuFormacion() {
        AsignacionCelula pertenencia = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), grupoEscuela.id(), aprendizEscuela,
                FuncionAcompanamiento.APRENDIZ, CLOCK.now().minusSeconds(86_400), MotivoAsignacion.ADMINISTRATIVO, lider, "alta");
        when(loadAsignacionesPort.porUsuario(aprendizEscuela)).thenReturn(List.of(pertenencia));

        composicion.quitar(new QuitarAprendizCelulaCommand(lider, grupoEscuela.id(), aprendizEscuela));

        assertThat(pertenencia.vigente()).isFalse();
        sinAlcance(() -> composicion.quitar(new QuitarAprendizCelulaCommand(lider, grupoDiplomado.id(), aprendizDiplomado)));
    }

    // ── Leer, y lo que no se le abrió ───────────────────────────────────────

    @Test
    @DisplayName("spec 056 FR-009 FR-010: el líder abre el detalle de un grupo de su formación; el de otra formación es 403 FUERA_DE_SU_ALCANCE")
    void elLiderAbreElDetalleSoloDeSuFormacion() {
        celulas.obtener(lider, grupoEscuela.id());

        sinAlcance(() -> celulas.obtener(lider, grupoDiplomado.id()));
    }

    @Test
    @DisplayName("spec 056 FR-008 FR-010: el tablero de grupos del líder trae solo los de su formación, y cada uno con su cupo")
    void elTableroDelLiderTraeSoloSuFormacionConCupo() {
        when(loadCelulaPort.todas()).thenReturn(List.of(grupoEscuela, grupoDiplomado));

        var tablero = celulas.dashboard(lider, null);

        assertThat(tablero).singleElement().satisfies(item -> {
            assertThat(item.celula().id()).isEqualTo(grupoEscuela.id());
            assertThat(item.cupoMaximo()).as("cupo de un grupo regular: el de la política por defecto").isEqualTo(10);
        });
    }

    @Test
    @DisplayName("spec 056 FR-008 FR-010: el líder ofrece solo mentores y aprendices de sus formaciones")
    void elLiderOfreceSoloGenteDeSusFormaciones() {
        when(loadCelulaPort.todas()).thenReturn(List.of());
        when(participacionProgramaFinder.usuariosActivosConRol(UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR))
                .thenReturn(List.of(mentorEscuela, mentorDiplomado));
        when(participacionProgramaFinder.participantesInscritosActivos()).thenReturn(List.of(aprendizEscuela, aprendizDiplomado));
        when(participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(aprendizEscuela, aprendizDiplomado));

        assertThat(celulas.mentores(lider, null)).extracting(m -> m.userId()).containsExactly(mentorEscuela);
        assertThat(celulas.mentoresDisponibles(lider, null)).extracting(m -> m.userId()).containsExactly(mentorEscuela);
        assertThat(celulas.aprendicesDisponibles(lider, null)).extracting(a -> a.userId()).containsExactly(aprendizEscuela);
    }

    @Test
    @DisplayName("spec 056 FR-010: editar, programar sesión y eliminar un grupo siguen siendo de Administración: el líder recibe 403")
    void editarProgramarYEliminarSiguenSiendoDeAdministracion() {
        var cambios = new ActualizarCelulaCommand(lider, grupoEscuela.id(), "Otro nombre", null, false, null, null, false, null, false);

        assertThatThrownBy(() -> celulas.actualizar(cambios)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> celulas.programar(new ProgramarSesionCelulaCommand(lider, grupoEscuela.id(), CLOCK.now())))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> celulas.eliminar(new EliminarCelulaCommand(lider, grupoEscuela.id())))
                .isInstanceOf(NotAuthorizedException.class);

        verify(eliminarCelulaPort, never()).eliminar(any());
    }
}
