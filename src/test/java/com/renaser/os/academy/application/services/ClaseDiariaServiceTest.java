package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.CompletarClaseDiariaCommand;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.ClaseDiariaResolution;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Disponible;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.NoIniciado;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase.Proximamente;
import com.renaser.os.academy.application.ports.in.leccion.CompletarLeccionUseCase;
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
import com.renaser.os.academy.domain.model.progreso.ProgresoLeccion;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.RegistroCompletado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Mock
    private CompletarClaseDiariaHabitoUseCase completarHabitoUseCase;
    @Mock
    private CompletarLeccionUseCase completarLeccionUseCase;

    private ClaseDiariaService service() {
        return new ClaseDiariaService(loadCursoPort, loadSeccionCursoPort, loadLeccionPort, progresoPort,
                completarHabitoUseCase, completarLeccionUseCase);
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

    /** Deja armado el mismo escenario de {@code eligeSeccionMasRecienteYLeccionClase}: hoy resuelve a
     * {@code Disponible} con {@code leccionId="l2"}. */
    private void mockClaseDisponibleHoy() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(20, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));

        Curso curso = curso("c1", 1);
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso));
        when(loadCursoPort.byId(CursoId.of("c1"))).thenReturn(Optional.of(curso));

        SeccionCurso seccion = new SeccionCurso(SeccionCursoId.of("s2"), CursoId.of("c1"), "Semana 3", 1, 17);
        when(loadSeccionCursoPort.porCurso(CursoId.of("c1"))).thenReturn(List.of(seccion));

        Leccion clase = new Leccion(LeccionId.of("l2"), CursoId.of("c1"), SeccionCursoId.of("s2"), "Clase del dia 17",
                0, null, null, null, null, null, null, AHORA, AHORA);
        when(loadLeccionPort.porCurso(CursoId.of("c1"))).thenReturn(List.of(clase));
    }

    @Test
    @DisplayName("completar(): resuelve la clase de hoy, cierra el habito DAILY_CLASS y marca la leccion vista, en ese orden")
    void completarCierraHabitoYMarcaLeccion() {
        mockClaseDisponibleHoy();
        UUID registroHabitoId = UUID.randomUUID();
        when(completarHabitoUseCase.completarDeHoy(any())).thenReturn(new RegistroCompletado(registroHabitoId, 10));
        when(completarLeccionUseCase.completar(ACTOR_ID, LeccionId.of("l2")))
                .thenReturn(new ProgresoLeccion(ACTOR_ID, LeccionId.of("l2"), AHORA));

        ClaseDiariaCompletada resultado = service().completar(
                new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "Resumen valido de la clase de hoy"));

        assertThat(resultado.leccionId()).isEqualTo(LeccionId.of("l2"));
        assertThat(resultado.registroHabitoId()).isEqualTo(registroHabitoId);
        assertThat(resultado.puntosOtorgados()).isEqualTo(10);
        verify(completarHabitoUseCase).completarDeHoy(
                new CompletarClaseDiariaHabitoUseCase.CompletarClaseDiariaHabitoCommand(ACTOR_ID,
                        "Resumen valido de la clase de hoy"));
        verify(completarLeccionUseCase).completar(ACTOR_ID, LeccionId.of("l2"));
    }

    @Test
    @DisplayName("completar(): la leccion pedida no es la clase diaria de hoy -> NotAuthorizedException, sin tocar habits")
    void completarRechazaLeccionQueNoEsLaDeHoy() {
        mockClaseDisponibleHoy();

        assertThatThrownBy(() -> service().completar(
                new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("otra-leccion"), "Resumen valido de la clase")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(completarHabitoUseCase, never()).completarDeHoy(any());
        verify(completarLeccionUseCase, never()).completar(any(), any());
    }

    @Test
    @DisplayName("completar(): sin clase diaria disponible hoy (NoIniciado) -> IllegalStateException (409), sin tocar habits")
    void completarRechazaSiNoHayClaseDisponible() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(0, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false)));

        assertThatThrownBy(() -> service().completar(
                new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "Resumen valido de la clase de hoy")))
                .isInstanceOf(IllegalStateException.class);
        verify(completarHabitoUseCase, never()).completarDeHoy(any());
        verify(completarLeccionUseCase, never()).completar(any(), any());
    }

    @Test
    @DisplayName("completar(): cuenta suspendida -> NotAuthorizedException (CLAUDE.MD §0.3), sin tocar habits")
    void completarRechazaSuspendido() {
        when(progresoPort.deParticipante(ACTOR_ID)).thenReturn(
                Optional.of(new ProgresoParticipanteAcademy(20, ZoneId.of("America/Lima"), RolParticipante.TRAINEE, true)));

        assertThatThrownBy(() -> service().completar(
                new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "Resumen valido de la clase de hoy")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(completarHabitoUseCase, never()).completarDeHoy(any());
        verify(completarLeccionUseCase, never()).completar(any(), any());
    }

    @Test
    @DisplayName("CompletarClaseDiariaCommand: resumen menor a 15 caracteres es rechazado en el constructor")
    void comandoRechazaResumenCorto() {
        assertThatThrownBy(() -> new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "muy corto"))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    }

    @Test
    @DisplayName("CompletarClaseDiariaCommand: los bordes exactos del resumen (15 y 2000) se aceptan; 14 y 2001 no")
    void comandoRespetaLosBordesExactosDelResumen() {
        assertThatThrownBy(() -> new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "a".repeat(14)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
        assertThatThrownBy(() -> new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "a".repeat(2001)))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class);

        assertThatCode(() -> new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "a".repeat(15)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new CompletarClaseDiariaCommand(ACTOR_ID, LeccionId.of("l2"), "a".repeat(2000)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Los limites del resumen son EL MISMO valor en academy y en habits — si divergen, el aprendiz "
            + "pasa una validacion y choca con la otra")
    void losLimitesDelResumenNoPuedenDivergirEntreModulos() {
        assertThat(CompletarClaseDiariaUseCase.RESUMEN_MIN_LENGTH)
                .isEqualTo(CompletarClaseDiariaHabitoUseCase.RESUMEN_MIN_LENGTH).isEqualTo(15);
        assertThat(CompletarClaseDiariaUseCase.RESUMEN_MAX_LENGTH)
                .isEqualTo(CompletarClaseDiariaHabitoUseCase.RESUMEN_MAX_LENGTH).isEqualTo(2000);
    }
}
