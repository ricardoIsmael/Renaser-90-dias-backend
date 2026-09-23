package com.renaser.os.academy.application.services;

import com.renaser.os.academy.api.ClaseDiariaPort;
import com.renaser.os.academy.api.CursosDelAprendizFinder.CursosDelAprendiz;
import com.renaser.os.academy.api.CursosDelAprendizFinder.MotivoBloqueo;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;
import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.CompletarClaseDiariaCommand;
import com.renaser.os.academy.application.ports.in.clasediaria.ConsultarClaseDiariaUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase.CursoBloqueado;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.CursoConProgreso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.ProgresoCurso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.BloqueadoPorDia;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.NoBloqueado;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarMotivoBloqueoLeccionUseCase;
import com.renaser.os.academy.application.ports.in.recomendacion.ConsultarRecomendacionDiariaUseCase;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los dos contratos publicos que usa el acompanante (2026-09-23): traducen lo que devuelven los
 * casos de uso de siempre, sin agregar ni perder nada.
 */
class AcademiaParaAcompananteTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final ConsultarClaseDiariaUseCase consultarClase = mock(ConsultarClaseDiariaUseCase.class);
    private final CompletarClaseDiariaUseCase completarClase = mock(CompletarClaseDiariaUseCase.class);
    private final ConsultarMisCursosUseCase misCursos = mock(ConsultarMisCursosUseCase.class);
    private final ConsultarCursosBloqueadosUseCase bloqueados = mock(ConsultarCursosBloqueadosUseCase.class);
    private final ConsultarMotivoBloqueoCursoUseCase motivoCurso = mock(ConsultarMotivoBloqueoCursoUseCase.class);
    private final ConsultarMotivoBloqueoLeccionUseCase motivoLeccion = mock(ConsultarMotivoBloqueoLeccionUseCase.class);

    private final ConsultarRecomendacionDiariaUseCase recomendacion = mock(ConsultarRecomendacionDiariaUseCase.class);

    private final ClaseDiariaPortService claseDiaria = new ClaseDiariaPortService(consultarClase, completarClase,
            recomendacion);
    private final CursosDelAprendizFinderService cursos = new CursosDelAprendizFinderService(misCursos, bloqueados,
            motivoCurso, motivoLeccion);

    private static Curso curso(String id, String titulo, Integer diaDesbloqueo) {
        return new Curso(CursoId.of(id), id, titulo, null, null, 0, true, AccesoCurso.ABIERTO, "skool",
                diaDesbloqueo, Set.of(), Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    void claseDeHoyTraduceLasTresRamas() {
        when(consultarClase.claseDeHoy(APRENDIZ)).thenReturn(new ConsultarClaseDiariaUseCase.Disponible(12,
                CursoId.of("c-1"), "Fundamentos", LeccionId.of("l-12"), "Clase 12", true));
        ClaseDiariaPort.ClaseDeHoy clase = claseDiaria.claseDeHoy(APRENDIZ);
        assertThat(clase).isEqualTo(new ClaseDiariaPort.ClaseDeHoy(ClaseDiariaPort.Estado.DISPONIBLE, 12, "c-1",
                "Fundamentos", "l-12", "Clase 12", true));

        assertThat(ClaseDiariaPortService.aClaseDeHoy(new ConsultarClaseDiariaUseCase.NoIniciado()).estado())
                .isEqualTo(ClaseDiariaPort.Estado.NO_INICIADO);
        assertThat(ClaseDiariaPortService.aClaseDeHoy(new ConsultarClaseDiariaUseCase.Proximamente(40)).diaPrograma())
                .isEqualTo(40);
    }

    @Test
    void entregarDelegaEnElCasoDeUsoDeLaApp() {
        CompletarClaseDiariaCommand comando = new CompletarClaseDiariaCommand(APRENDIZ, LeccionId.of("l-12"),
                "Entendi que la constancia pesa mas.");
        when(completarClase.completar(comando)).thenReturn(new ClaseDiariaCompletada(LeccionId.of("l-12"),
                UUID.randomUUID(), 8));

        ClaseDiariaPort.Entrega entrega = claseDiaria.entregar(APRENDIZ, "l-12", "Entendi que la constancia pesa mas.");

        verify(completarClase).completar(comando);
        assertThat(entrega).isEqualTo(new ClaseDiariaPort.Entrega("l-12", 8));
    }

    @Test
    void recomendacionDeHoyUsaSoloLaLecturaDeCache() {
        when(recomendacion.recomendacionDeHoySiExiste(APRENDIZ)).thenReturn(Optional.of(
                new ConsultarRecomendacionDiariaUseCase.Disponible(LeccionId.of("l-3"), "Respirar",
                        CursoId.of("c-1"), "Fundamentos", "Tu energia vino baja")));

        assertThat(claseDiaria.recomendacionDeHoySiExiste(APRENDIZ)).contains(new ClaseDiariaPort.Recomendacion(
                "c-1", "Fundamentos", "l-3", "Respirar", "Tu energia vino baja"));
        verify(recomendacion, never()).recomendacion(APRENDIZ);
    }

    @Test
    void cursosJuntaAccesiblesYBloqueados() {
        when(misCursos.misCursos(APRENDIZ)).thenReturn(List.of(new CursoConProgreso(curso("c-1", "Fundamentos", 1),
                new ProgresoCurso(CursoId.of("c-1"), 20, 7, null), "https://firmada")));
        when(bloqueados.cursosBloqueados(APRENDIZ)).thenReturn(List.of(new CursoBloqueado(
                curso("c-2", "Liderazgo", 30), 30, 12, null)));

        CursosDelAprendiz resultado = cursos.cursosDe(APRENDIZ);

        assertThat(resultado.accesibles()).singleElement()
                .satisfies(c -> assertThat(c.cursoId()).isEqualTo("c-1"))
                .satisfies(c -> assertThat(c.leccionesCompletadas()).isEqualTo(7))
                .satisfies(c -> assertThat(c.totalLecciones()).isEqualTo(20));
        assertThat(resultado.bloqueados()).singleElement()
                .satisfies(c -> assertThat(c.diaDesbloqueo()).isEqualTo(30))
                .satisfies(c -> assertThat(c.diaProgramaActual()).isEqualTo(12));
    }

    @Test
    void motivoSoloRevelaElBloqueoPorDia() {
        when(motivoCurso.motivo(APRENDIZ, CursoId.of("c-2"))).thenReturn(new BloqueadoPorDia("Liderazgo", 30, 12));
        when(motivoLeccion.motivo(APRENDIZ, LeccionId.of("l-9"))).thenReturn(new NoBloqueado());

        assertThat(cursos.motivoBloqueoCurso(APRENDIZ, "c-2")).isEqualTo(new MotivoBloqueo(true, "Liderazgo", 30, 12));
        assertThat(cursos.motivoBloqueoLeccion(APRENDIZ, "l-9")).isEqualTo(MotivoBloqueo.noBloqueado());
    }
}
