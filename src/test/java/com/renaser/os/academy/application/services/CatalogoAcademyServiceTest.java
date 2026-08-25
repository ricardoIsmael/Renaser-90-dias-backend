package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase.CursoBloqueado;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.CursoConProgreso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.BloqueadoPorDia;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.MotivoBloqueoCurso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.NoBloqueado;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadRecursoLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.application.ports.out.progreso.LoadProgresoLeccionPort;
import com.renaser.os.academy.application.ports.out.progreso.SaveProgresoLeccionPort;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoAcademyServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T12:00:00Z"));
    private static final UserId ACTOR_ID = UserId.of(UUID.randomUUID());

    @Mock
    private LoadCursoPort loadCursoPort;
    @Mock
    private LoadSeccionCursoPort loadSeccionCursoPort;
    @Mock
    private LoadLeccionPort loadLeccionPort;
    @Mock
    private LoadRecursoLeccionPort loadRecursoLeccionPort;
    @Mock
    private LoadProgresoLeccionPort loadProgresoLeccionPort;
    @Mock
    private SaveProgresoLeccionPort saveProgresoLeccionPort;
    @Mock
    private ConsultarProgresoParticipanteAcademyPort progresoPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;

    private CatalogoAcademyService service;

    @BeforeEach
    void setUp() {
        service = new CatalogoAcademyService(loadCursoPort, loadSeccionCursoPort, loadLeccionPort,
                loadRecursoLeccionPort, loadProgresoLeccionPort, saveProgresoLeccionPort, progresoPort,
                almacenamientoPort, CLOCK);
    }

    private static Curso curso(String id, boolean publicado, AccesoCurso acceso, Integer diaDesbloqueo) {
        return new Curso(CursoId.of(id), id, "Titulo " + id, null, null, 0, publicado, acceso, "skool",
                diaDesbloqueo, Set.of(), CLOCK.now(), CLOCK.now());
    }

    private static ProgresoParticipanteAcademy progresoTrainee(int diaPrograma) {
        return new ProgresoParticipanteAcademy(diaPrograma, ZoneId.of("America/Lima"), RolParticipante.TRAINEE,
                false);
    }

    @Test
    @DisplayName("misCursos: solo devuelve los cursos visibles para el rol/dia del actor")
    void misCursosFiltraPorAcceso() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Curso accesible = curso("c1", true, AccesoCurso.ABIERTO, null);
        Curso bloqueadoPorDia = curso("c2", true, AccesoCurso.ABIERTO, 30);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(accesible, bloqueadoPorDia));
        when(loadLeccionPort.contarTotalPorCurso()).thenReturn(Map.of(CursoId.of("c1"), 5));
        when(loadProgresoLeccionPort.completadasPorCurso(ACTOR_ID)).thenReturn(Map.of(CursoId.of("c1"), 2));

        List<CursoConProgreso> resultado = service.misCursos(ACTOR_ID);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).curso().id()).isEqualTo(CursoId.of("c1"));
        assertThat(resultado.get(0).progreso().totalLecciones()).isEqualTo(5);
        assertThat(resultado.get(0).progreso().completadas()).isEqualTo(2);
    }

    @Test
    @DisplayName("misCursos: cuenta suspendida -> 403")
    void misCursosSuspendidoNoAutorizado() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(new ProgresoParticipanteAcademy(0, null, RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.misCursos(ACTOR_ID)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("detalle: curso inexistente -> NoSuchElementException")
    void detalleCursoInexistente() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        when(loadCursoPort.byId(CursoId.of("no-existe"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalle(ACTOR_ID, CursoId.of("no-existe")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("detalle: sin acceso -> NotAuthorizedException (403)")
    void detalleSinAcceso() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(5)));
        Curso bloqueado = curso("c1", true, AccesoCurso.ABIERTO, 30);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> service.detalle(ACTOR_ID, CursoId.of("c1")))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("motivo del curso: bloqueado por dia revela el motivo")
    void motivoBloqueadoPorDia() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Curso bloqueado = curso("c1", true, AccesoCurso.ABIERTO, 30);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(bloqueado));

        MotivoBloqueoCurso motivo = service.motivo(ACTOR_ID, CursoId.of("c1"));

        assertThat(motivo).isInstanceOf(BloqueadoPorDia.class);
        BloqueadoPorDia bloqueadoPorDia = (BloqueadoPorDia) motivo;
        assertThat(bloqueadoPorDia.diaDesbloqueo()).isEqualTo(30);
        assertThat(bloqueadoPorDia.programDayActual()).isEqualTo(10);
    }

    @Test
    @DisplayName("motivo del curso: curso inexistente no revela nada (NoBloqueado)")
    void motivoCursoInexistenteNoRevelaNada() {
        when(loadCursoPort.byId(CursoId.of("no-existe"))).thenReturn(Optional.empty());

        MotivoBloqueoCurso motivo = service.motivo(ACTOR_ID, CursoId.of("no-existe"));

        assertThat(motivo).isInstanceOf(NoBloqueado.class);
    }

    @Test
    @DisplayName("completar leccion: sin acceso al curso -> 403, no guarda progreso")
    void completarSinAccesoNoGuarda() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(5)));
        Leccion leccion = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), null, "Leccion 1", 0, null, null, null,
                null, null, null, CLOCK.now(), CLOCK.now());
        when(loadLeccionPort.byId(LeccionId.of("l1"))).thenReturn(Optional.of(leccion));
        Curso bloqueado = curso("c1", true, AccesoCurso.ABIERTO, 30);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> service.completar(ACTOR_ID, LeccionId.of("l1")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveProgresoLeccionPort, org.mockito.Mockito.never()).marcarCompletada(any());
    }

    @Test
    @DisplayName("completar leccion: con acceso, marca completada")
    void completarConAccesoMarcaCompletada() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Leccion leccion = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), null, "Leccion 1", 0, null, null, null,
                null, null, null, CLOCK.now(), CLOCK.now());
        when(loadLeccionPort.byId(LeccionId.of("l1"))).thenReturn(Optional.of(leccion));
        Curso accesible = curso("c1", true, AccesoCurso.ABIERTO, null);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(accesible));
        ProgresoLeccion esperado = new ProgresoLeccion(ACTOR_ID, LeccionId.of("l1"), CLOCK.now());
        when(saveProgresoLeccionPort.marcarCompletada(any())).thenReturn(esperado);

        ProgresoLeccion resultado = service.completar(ACTOR_ID, LeccionId.of("l1"));

        assertThat(resultado).isEqualTo(esperado);
        verify(saveProgresoLeccionPort).marcarCompletada(any());
    }

    @Test
    @DisplayName("firma la portada solo si no es una URL http(s) externa")
    void firmaPortadaSoloRutasInternas() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Curso conPortadaExterna = new Curso(CursoId.of("c1"), "c1", "Titulo", null, "https://cdn.skool.com/x.png", 0,
                true, AccesoCurso.ABIERTO, "skool", null, Set.of(), CLOCK.now(), CLOCK.now());
        when(loadCursoPort.listarTodos()).thenReturn(List.of(conPortadaExterna));
        when(loadLeccionPort.contarTotalPorCurso()).thenReturn(Map.of());
        when(loadProgresoLeccionPort.completadasPorCurso(ACTOR_ID)).thenReturn(Map.of());

        List<CursoConProgreso> resultado = service.misCursos(ACTOR_ID);

        assertThat(resultado.get(0).portadaFirmada()).isEqualTo("https://cdn.skool.com/x.png");
        verifyNoFirmaLlamada();
    }

    private void verifyNoFirmaLlamada() {
        org.mockito.Mockito.verifyNoInteractions(almacenamientoPort);
    }

    @Test
    @DisplayName("cursosBloqueados: solo lista los que estan bloqueados EXCLUSIVAMENTE por dia (AC-15)")
    void cursosBloqueadosSoloPorDia() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Curso bloqueadoPorDia = curso("c1", true, AccesoCurso.ABIERTO, 30);
        Curso yaVisible = curso("c2", true, AccesoCurso.ABIERTO, null);
        Curso borrador = curso("c3", false, AccesoCurso.ABIERTO, 30);
        Curso restringido = curso("c4", true, AccesoCurso.RESTRINGIDO, 30);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(bloqueadoPorDia, yaVisible, borrador, restringido));

        List<CursoBloqueado> resultado = service.cursosBloqueados(ACTOR_ID);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).curso().id()).isEqualTo(CursoId.of("c1"));
        assertThat(resultado.get(0).diaDesbloqueo()).isEqualTo(30);
        assertThat(resultado.get(0).programDayActual()).isEqualTo(10);
    }

    @Test
    @DisplayName("cursosBloqueados: rol distinto de TRAINEE -> lista vacia, nunca error")
    void cursosBloqueadosVacioParaNoTrainee() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(new ProgresoParticipanteAcademy(null, null, RolParticipante.MENTOR, false)));

        List<CursoBloqueado> resultado = service.cursosBloqueados(ACTOR_ID);

        assertThat(resultado).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(loadCursoPort);
    }

    @Test
    @DisplayName("cursosBloqueados: cuenta suspendida -> 403")
    void cursosBloqueadosSuspendidoNoAutorizado() {
        when(progresoPort.deParticipante(ACTOR_ID))
                .thenReturn(Optional.of(new ProgresoParticipanteAcademy(0, null, RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service.cursosBloqueados(ACTOR_ID)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("descompletar: sin acceso al curso -> 403, no borra progreso")
    void descompletarSinAccesoNoBorra() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(5)));
        Leccion leccion = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), null, "Leccion 1", 0, null, null, null,
                null, null, null, CLOCK.now(), CLOCK.now());
        when(loadLeccionPort.byId(LeccionId.of("l1"))).thenReturn(Optional.of(leccion));
        Curso bloqueado = curso("c1", true, AccesoCurso.ABIERTO, 30);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(bloqueado));

        assertThatThrownBy(() -> service.descompletar(ACTOR_ID, LeccionId.of("l1")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveProgresoLeccionPort, org.mockito.Mockito.never()).desmarcarCompletada(any(), any());
    }

    @Test
    @DisplayName("descompletar: con acceso, borra el progreso")
    void descompletarConAccesoBorraProgreso() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(Optional.of(progresoTrainee(10)));
        Leccion leccion = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), null, "Leccion 1", 0, null, null, null,
                null, null, null, CLOCK.now(), CLOCK.now());
        when(loadLeccionPort.byId(LeccionId.of("l1"))).thenReturn(Optional.of(leccion));
        Curso accesible = curso("c1", true, AccesoCurso.ABIERTO, null);
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(accesible));

        service.descompletar(ACTOR_ID, LeccionId.of("l1"));

        verify(saveProgresoLeccionPort).desmarcarCompletada(ACTOR_ID, LeccionId.of("l1"));
    }
}
