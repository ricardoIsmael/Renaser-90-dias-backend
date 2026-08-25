package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.ClaseDiariaResolution;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.NoIniciado;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Proximamente;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.Leccion;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaseDiariaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T12:00:00Z");
    private static final UserId ACTOR_ID = UserId.of(UUID.randomUUID());

    @Mock
    private LoadCursoPort loadCursoPort;
    @Mock
    private LoadSeccionCursoPort loadSeccionCursoPort;
    @Mock
    private LoadLeccionPort loadLeccionPort;
    @Mock
    private ConsultarProgresoParticipanteAcademyPort progresoPort;

    private ClaseDiariaService service() {
        return new ClaseDiariaService(loadCursoPort, loadSeccionCursoPort, loadLeccionPort, progresoPort);
    }

    private static Curso curso(String id, Integer diaDesbloqueo) {
        return new Curso(CursoId.of(id), id, "Titulo " + id, null, null, 0, true, AccesoCurso.ABIERTO, "skool",
                diaDesbloqueo, Set.of(), AHORA, AHORA);
    }

    @Test
    @DisplayName("dia_programa 0 -> NoIniciado")
    void diaCeroNoIniciado() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(0, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));

        ClaseDiariaResolution resolucion = service().claseDeHoy(ACTOR_ID);

        assertThat(resolucion).isInstanceOf(NoIniciado.class);
    }

    @Test
    @DisplayName("sin ningun curso/seccion desbloqueada para el dia -> Proximamente")
    void sinContenidoProximamente() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(5, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of());

        ClaseDiariaResolution resolucion = service().claseDeHoy(ACTOR_ID);

        assertThat(resolucion).isInstanceOf(Proximamente.class);
        assertThat(((Proximamente) resolucion).programDay()).isEqualTo(5);
    }

    @Test
    @DisplayName("elige la seccion con dia_desbloqueo mas reciente que ya alcanzo, y dentro de ella la leccion \"clase\"")
    void eligeSeccionMasRecienteYLeccionClase() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(20, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));

        Curso curso = curso("c1", 1);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso));

        SeccionCurso seccionVieja = new SeccionCurso(SeccionCursoId.of("s1"), CursoId.of("c1"), "Semana 1", 0, 1);
        SeccionCurso seccionReciente = new SeccionCurso(SeccionCursoId.of("s2"), CursoId.of("c1"), "Semana 3", 1, 17);
        when(loadSeccionCursoPort.porCurso(CursoId.of("c1"))).thenReturn(List.of(seccionVieja, seccionReciente));

        Leccion tutorial = new Leccion(LeccionId.of("l1"), CursoId.of("c1"), SeccionCursoId.of("s2"), "Tutorial", 0,
                null, null, null, null, null, null, AHORA, AHORA);
        Leccion clase = new Leccion(LeccionId.of("l2"), CursoId.of("c1"), SeccionCursoId.of("s2"), "Clase del dia 17",
                1, null, null, null, null, null, null, AHORA, AHORA);
        when(loadLeccionPort.porCurso(CursoId.of("c1"))).thenReturn(List.of(tutorial, clase));
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(curso));

        ClaseDiariaResolution resolucion = service().claseDeHoy(ACTOR_ID);

        assertThat(resolucion).isInstanceOf(Disponible.class);
        Disponible disponible = (Disponible) resolucion;
        assertThat(disponible.leccionId()).isEqualTo(LeccionId.of("l2"));
        assertThat(disponible.leccionTitulo()).isEqualTo("Clase del dia 17");
    }
}
