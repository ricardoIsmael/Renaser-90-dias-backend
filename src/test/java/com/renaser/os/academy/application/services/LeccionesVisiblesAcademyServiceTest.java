package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort.LeccionCatalogo;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitario, sin Spring, sin Postgres — mockea los 4 puertos de salida y verifica solo la
 * orquestacion (que composicion de curso/seccion/leccion visible entrega
 * {@link LeccionesVisiblesAcademyService}). Las reglas de gating en si
 * ({@code Curso#visibleEnCatalogoPara}/{@code SeccionCurso#visibleEnCatalogoPara}) ya estan
 * cubiertas por sus propios tests de dominio; este test verifica que el servicio las combina
 * bien (curso Y seccion, union entre cursos) sin reimplementarlas.
 */
@ExtendWith(MockitoExtension.class)
class LeccionesVisiblesAcademyServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T12:00:00Z"));
    private static final UserId ACTOR_ID = UserId.of(UUID.randomUUID());

    @Mock
    private LoadCursoPort loadCursoPort;
    @Mock
    private LoadSeccionCursoPort loadSeccionCursoPort;
    @Mock
    private LoadLeccionPort loadLeccionPort;
    @Mock
    private ConsultarProgresoParticipanteAcademyPort progresoPort;

    private LeccionesVisiblesAcademyService service;

    @BeforeEach
    void setUp() {
        service = new LeccionesVisiblesAcademyService(loadCursoPort, loadSeccionCursoPort, loadLeccionPort,
                progresoPort);
    }

    private static Curso curso(String id, Integer diaDesbloqueo) {
        return new Curso(CursoId.of(id), id, "Titulo " + id, null, null, 0, true, AccesoCurso.ABIERTO, "skool",
                diaDesbloqueo, Set.of(), CLOCK.now(), CLOCK.now());
    }

    private static ProgresoParticipanteAcademy progreso(RolParticipante rol, int diaPrograma, boolean suspendido) {
        return new ProgresoParticipanteAcademy(diaPrograma, ZoneId.of("America/Lima"), rol, suspendido);
    }

    @Test
    @DisplayName("actor sin fila de progreso (no existe) -> conjunto vacio")
    void actorInexistenteDevuelveConjuntoVacio() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.empty());

        assertThat(service.leccionesVisiblesPara(ACTOR_ID)).isEmpty();
    }

    @Test
    @DisplayName("actor suspendido -> conjunto vacio, sin consultar cursos")
    void actorSuspendidoDevuelveConjuntoVacio() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, true)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID)).isEmpty();
    }

    @Test
    @DisplayName("TRAINEE en dia 10: solo las lecciones del curso ya desbloqueado, el del dia 30 queda afuera")
    void filtraLeccionesPorCursoBloqueadoPorDia() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, false)));
        Curso accesible = curso("c1", null);
        Curso bloqueado = curso("c2", 30);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(accesible, bloqueado));
        when(loadSeccionCursoPort.listarTodas()).thenReturn(List.of());
        when(loadLeccionPort.listarIdentificadores()).thenReturn(List.of(
                new LeccionCatalogo(LeccionId.of("l1"), CursoId.of("c1"), null),
                new LeccionCatalogo(LeccionId.of("l2"), CursoId.of("c2"), null)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID)).containsExactly("l1");
    }

    @Test
    @DisplayName("una seccion con gate propio bloquea sus lecciones aunque el curso sea visible")
    void filtraLeccionesPorSeccionBloqueadaDentroDeCursoVisible() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, false)));
        Curso curso = curso("c1", null);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso));
        SeccionCurso seccionVisible = new SeccionCurso(SeccionCursoId.of("s1"), CursoId.of("c1"), "S1", 0, null);
        SeccionCurso seccionBloqueada = new SeccionCurso(SeccionCursoId.of("s2"), CursoId.of("c1"), "S2", 1, 30);
        when(loadSeccionCursoPort.listarTodas()).thenReturn(List.of(seccionVisible, seccionBloqueada));
        when(loadLeccionPort.listarIdentificadores()).thenReturn(List.of(
                new LeccionCatalogo(LeccionId.of("suelta"), CursoId.of("c1"), null),
                new LeccionCatalogo(LeccionId.of("de-seccion-visible"), CursoId.of("c1"), SeccionCursoId.of("s1")),
                new LeccionCatalogo(LeccionId.of("de-seccion-bloqueada"), CursoId.of("c1"), SeccionCursoId.of("s2"))));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID))
                .containsExactlyInAnyOrder("suelta", "de-seccion-visible");
    }

    @Test
    @DisplayName("MENTOR (no TRAINEE): el dia de programa no aplica, ve cursos con dia_desbloqueo igual")
    void rolSinDiaDeProgramaIgnoraElGateDeDia() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.MENTOR, 0, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso("c1", 60)));
        when(loadSeccionCursoPort.listarTodas()).thenReturn(List.of());
        when(loadLeccionPort.listarIdentificadores())
                .thenReturn(List.of(new LeccionCatalogo(LeccionId.of("l1"), CursoId.of("c1"), null)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID)).containsExactly("l1");
    }

    @Test
    @DisplayName("sin ningun curso accesible -> conjunto vacio, no consulta secciones ni lecciones")
    void sinCursosAccesiblesNoConsultaSeccionesNiLecciones() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 0, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso("c1", 90)));
        // No se stubean loadSeccionCursoPort/loadLeccionPort a proposito: si el servicio los
        // llamara igual, Mockito con lenient() no fallaria por stub no usado, pero el
        // resultado de todas formas tiene que ser vacio.
        lenient().when(loadSeccionCursoPort.listarTodas()).thenReturn(List.of());
        lenient().when(loadLeccionPort.listarIdentificadores()).thenReturn(List.of());

        assertThat(service.leccionesVisiblesPara(ACTOR_ID)).isEmpty();
    }

    // ─── D-102: la variante por curso (para Sparkie, el tutor de cursos de `rag`) ───

    @Test
    @DisplayName("D-102: por curso devuelve solo las lecciones de ESE curso, aunque otros sean visibles")
    void porCursoDevuelveSoloLasLeccionesDeEseCurso() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso("c1", null), curso("c2", null)));
        when(loadSeccionCursoPort.listarTodas()).thenReturn(List.of());
        when(loadLeccionPort.listarIdentificadores()).thenReturn(List.of(
                new LeccionCatalogo(LeccionId.of("l1"), CursoId.of("c1"), null),
                new LeccionCatalogo(LeccionId.of("l2"), CursoId.of("c2"), null)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID, "c2")).containsExactly("l2");
    }

    @Test
    @DisplayName("D-102: por curso respeta el mismo gate — un curso bloqueado por dia da vacio")
    void porCursoBloqueadoDevuelveVacioSinConsultarLecciones() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso("c1", null), curso("c2", 30)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID, "c2")).isEmpty();
        verify(loadLeccionPort, never()).listarIdentificadores();
    }

    @Test
    @DisplayName("D-102: por curso inexistente -> vacio, no una excepcion")
    void porCursoInexistenteDevuelveVacio() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(progreso(RolParticipante.TRAINEE, 10, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso("c1", null)));

        assertThat(service.leccionesVisiblesPara(ACTOR_ID, "no-existe")).isEmpty();
    }
}
