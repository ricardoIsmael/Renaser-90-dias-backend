package com.renaser.os.academy.application.services;

import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.progreso.LoadProgresoLeccionPort;
import com.renaser.os.academy.domain.model.curso.AccesoCurso;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder.UsuarioConDiaPrograma;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Mockito, sin Postgres — cubre la orquestacion de {@code PorcentajeCursosService}
 * (D-43). La prueba de que NO hay N+1 real (una consulta, no una por usuario)
 * queda en el adaptador de persistencia (Testcontainers); aca se prueba el
 * criterio equivalente al nivel de puertos: cada puerto se llama EXACTAMENTE
 * UNA VEZ sin importar cuantos participantes se pidan.
 */
@ExtendWith(MockitoExtension.class)
class PorcentajeCursosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T12:00:00Z");

    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private LoadCursoPort loadCursoPort;
    @Mock
    private LoadLeccionPort loadLeccionPort;
    @Mock
    private LoadProgresoLeccionPort loadProgresoLeccionPort;

    private PorcentajeCursosService service;

    @BeforeEach
    void setUp() {
        service = new PorcentajeCursosService(participacionFinder, loadCursoPort, loadLeccionPort,
                loadProgresoLeccionPort);
    }

    private static Curso curso(String id, Integer diaDesbloqueo) {
        return new Curso(CursoId.of(id), id, "Titulo " + id, null, null, 0, true, AccesoCurso.ABIERTO, "skool",
                diaDesbloqueo, Set.of(), AHORA, AHORA);
    }

    @Test
    @DisplayName("sin participantes -> mapa vacio, no toca ningun puerto")
    void sinParticipantesNoTocaNadaTodavia() {
        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of());

        assertThat(resultado).isEmpty();
        verifyNoMoreInteractions(participacionFinder, loadCursoPort, loadLeccionPort, loadProgresoLeccionPort);
    }

    @Test
    @DisplayName("participante que no es TRAINEE activo -> ausente del mapa, no consulta catalogo/progreso")
    void participanteNoTraineeActivoQuedaAusente() {
        UserId noTrainee = UserId.of(UUID.randomUUID());
        when(participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE))).thenReturn(List.of());

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(noTrainee));

        assertThat(resultado).isEmpty();
        verify(loadCursoPort, never()).listarTodos();
        verify(loadProgresoLeccionPort, never()).completadasPorCursoEnLote(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("sin cursos accesibles -> 100.0, sin importar el progreso")
    void sinCursosAccesiblesDa100() {
        UserId trainee = UserId.of(UUID.randomUUID());
        when(participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(new UsuarioConDiaPrograma(trainee, 0)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of());
        when(loadLeccionPort.contarTotalPorCurso()).thenReturn(Map.of());
        when(loadProgresoLeccionPort.completadasPorCursoEnLote(Set.of(trainee))).thenReturn(Map.of());

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(trainee));

        assertThat(resultado.get(trainee)).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("dos participantes con distinto dia de programa -> gate por curso aplicado por separado, UNA sola consulta por puerto")
    void dosParticipantesUnaSolaConsultaPorPuerto() {
        UserId traineeDia0 = UserId.of(UUID.randomUUID());
        UserId traineeDia30 = UserId.of(UUID.randomUUID());
        // curso-libre: sin dia de desbloqueo, accesible para ambos.
        // curso-avanzado: se desbloquea el dia 20, solo accesible para traineeDia30.
        Curso cursoLibre = curso("curso-libre", null);
        Curso cursoAvanzado = curso("curso-avanzado", 20);

        when(participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(new UsuarioConDiaPrograma(traineeDia0, 0),
                        new UsuarioConDiaPrograma(traineeDia30, 30)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(cursoLibre, cursoAvanzado));
        when(loadLeccionPort.contarTotalPorCurso())
                .thenReturn(Map.of(CursoId.of("curso-libre"), 4, CursoId.of("curso-avanzado"), 6));
        when(loadProgresoLeccionPort.completadasPorCursoEnLote(Set.of(traineeDia0, traineeDia30))).thenReturn(Map.of(
                traineeDia0, Map.of(CursoId.of("curso-libre"), 2),
                traineeDia30, Map.of(CursoId.of("curso-libre"), 4, CursoId.of("curso-avanzado"), 3)));

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(traineeDia0, traineeDia30));

        // traineeDia0: solo ve curso-libre (4 lecciones), completo 2 -> 50.0.
        assertThat(resultado.get(traineeDia0)).isEqualByComparingTo("50.0");
        // traineeDia30: ve ambos cursos (4 + 6 = 10 lecciones), completo 4 + 3 = 7 -> 70.0.
        assertThat(resultado.get(traineeDia30)).isEqualByComparingTo("70.0");

        verify(participacionFinder, times(1)).usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE));
        verify(loadCursoPort, times(1)).listarTodos();
        verify(loadLeccionPort, times(1)).contarTotalPorCurso();
        verify(loadProgresoLeccionPort, times(1)).completadasPorCursoEnLote(Set.of(traineeDia0, traineeDia30));
    }

    @Test
    @DisplayName("no cuenta completadas de un curso ya no accesible (curso re-bloqueado, mismo criterio que el catalogo)")
    void noCuentaCompletadasDeCursoNoAccesible() {
        UserId trainee = UserId.of(UUID.randomUUID());
        Curso cursoBloqueado = curso("curso-bloqueado", 90);

        when(participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(new UsuarioConDiaPrograma(trainee, 5)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(cursoBloqueado));
        when(loadLeccionPort.contarTotalPorCurso()).thenReturn(Map.of(CursoId.of("curso-bloqueado"), 5));
        when(loadProgresoLeccionPort.completadasPorCursoEnLote(Set.of(trainee)))
                .thenReturn(Map.of(trainee, Map.of(CursoId.of("curso-bloqueado"), 3)));

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(trainee));

        // Sin cursos accesibles (el unico esta bloqueado por dia) -> 100.0, no 60.0.
        assertThat(resultado.get(trainee)).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("1 de 3 lecciones -> 33.3, el decimal significativo que un Integer perderia")
    void unDeTresLeccionesConservaElDecimal() {
        UserId trainee = UserId.of(UUID.randomUUID());
        Curso curso = curso("curso-unico", null);

        when(participacionFinder.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE)))
                .thenReturn(List.of(new UsuarioConDiaPrograma(trainee, 10)));
        when(loadCursoPort.listarTodos()).thenReturn(List.of(curso));
        when(loadLeccionPort.contarTotalPorCurso()).thenReturn(Map.of(CursoId.of("curso-unico"), 3));
        when(loadProgresoLeccionPort.completadasPorCursoEnLote(Set.of(trainee)))
                .thenReturn(Map.of(trainee, Map.of(CursoId.of("curso-unico"), 1)));

        Map<UserId, BigDecimal> resultado = service.porcentajePorParticipante(List.of(trainee));

        assertThat(resultado.get(trainee)).isEqualByComparingTo("33.3");
    }
}
